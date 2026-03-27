package org.acme.processor;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.model.QuizPair;
import org.acme.model.UserQuizSession;
import org.acme.service.CachedQuizWordService;
import org.acme.service.KeyboardBuilderService;
import org.acme.service.MenuService;
import org.acme.service.SessionManagerService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.IncomingCallbackQuery;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.acme.service.KeyboardBuilderService.CALLBACK_PREFIX;
import static org.acme.service.KeyboardBuilderService.LENGTH_CALLBACK_PREFIX;
import static org.apache.camel.component.telegram.TelegramConstants.TELEGRAM_CHAT_ID;

/**
 * Handles callback queries from InlineKeyboard button presses.
 * Extracts the selected topic, creates a quiz session,
 * and sends the first question.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class CallbackQueryProcessor implements Processor {

    private final CachedQuizWordService cachedQuizWordService;
    private final KeyboardBuilderService keyboardBuilderService;
    private final MenuService menuService;
    private final SessionManagerService sessionManager;

    @ConfigProperty(name = "quiz.source.lang", defaultValue = "he")
    String sourceLang;

    @ConfigProperty(name = "quiz.target.lang", defaultValue = "en")
    String targetLang;


    @Override
    public void process(Exchange exchange) throws Exception {

        // 1. Extract the callback query from the exchange body
        var callbackQuery = exchange.getIn().getBody(IncomingCallbackQuery.class);
        String callbackData = callbackQuery.getData();

        // 2. Extract chat ID from the callback query's message
        String chatIdStr = callbackQuery.getMessage().getChat().getId();
        long chatId = Long.parseLong(chatIdStr);

        // 3. Get the user's display name
        Optional<User> userOptional = Optional.ofNullable(callbackQuery.getFrom());
        String userName = userOptional.map(User::getFirstName).orElse("QuizUser");

        // 4. Route based on callback type using modern switch expression with arrow syntax
        switch (callbackData) {
            case String s when s.startsWith(CALLBACK_PREFIX) ->
                    handleTopicSelection(exchange, callbackData, chatIdStr, chatId, userName);
            case String s when s.startsWith(LENGTH_CALLBACK_PREFIX) ->
                    handleLengthSelection(exchange, callbackData, chatIdStr, chatId, userName);
            default -> Log.warnf("Unknown callback data: %s", callbackData);
        }
    }

    /**
     * Handles topic selection callback - fetches word pairs and shows length keyboard.
     */
    private void handleTopicSelection(Exchange exchange,
                                      String callbackData,
                                      String chatIdStr,
                                      long chatId,
                                      String userName) throws Exception {

        // Parse topic name from callback data (format: "topic: Animals")
        String topicName = callbackData.replace(CALLBACK_PREFIX, "").strip();
        Log.infof("✅ User: %s selected topic: %s", userName, topicName);

        // Fetch all word pairs for the selected topic
        List<QuizPair> allWordPairs = getAllWordPairs(topicName, sourceLang, targetLang);
        Log.infof("✅ Fetched %d word pairs for topic: %s", allWordPairs.size(), topicName);

        // Build and send the length selection keyboard
        String lengthMenuText = menuService.getQuizLengthMenuText(topicName, allWordPairs.size());

        OutgoingTextMessage outgoingTextMessage = OutgoingTextMessage.builder()
                .parseMode("Markdown")
                .text(lengthMenuText)
                .replyMarkup(keyboardBuilderService.buildQuizLengthKeyboard(topicName, allWordPairs.size()))
                .build();

        // Set the chat ID header and body
        exchange.getIn().setHeader(TELEGRAM_CHAT_ID, chatIdStr);
        exchange.getIn().setBody(outgoingTextMessage);

        // Store word pairs temporarily for this user (we'll retrieve them on length selection)
        sessionManager.storePendingWordPairs(chatId, allWordPairs);
    }

    /**
     * Handles length selection callback - creates session and starts quiz.
     */
    private void handleLengthSelection(Exchange exchange,
                                       String callbackData,
                                       String chatIdStr,
                                       long chatId,
                                       String userName) throws Exception {

        // Parse length portion and topic name from callback data (format: "length: 1|Animals")
        String[] parts = callbackData.replace(LENGTH_CALLBACK_PREFIX, "").strip().split("\\|");
        int portion = Integer.parseInt(parts[0].strip());
        String topicName = parts[1].strip();

        Log.infof("✅ User: %s selected length: %d/4 for topic: %s", userName, portion, topicName);

        // Get the pending word pairs from session manager
        List<QuizPair> allWordPairs = sessionManager.getPendingWordPairs(chatId)
                .orElseThrow(() -> new RuntimeException(
                        "❌ No pending word pairs found. Please start again with /start"));

        // Slice the word pairs based on selection (1/4, 2/4, 3/4, 4/4)
        int sliceCount = (allWordPairs.size() * portion) / 4;
        // Ensure at least 1 word if there are any words
        sliceCount = Math.max(1, sliceCount);
//      List<QuizPair> slicedWordPairs = allWordPairs.subList(0, sliceCount);
//      Collections.shuffle(slicedWordPairs);
        List<QuizPair> slicedWordPairs = getNRandomElements(allWordPairs, sliceCount);

        Log.infof("✅ Sliced to %d word pairs for quiz and shuffled it", slicedWordPairs.size());

        // Clear the pending word pairs
        sessionManager.clearPendingWordPairs(chatId);

        // Create a new quiz session
        UserQuizSession session = sessionManager.createSession(chatId,
                userName,
                topicName,
                slicedWordPairs);
        Log.infof("✅ Created quiz session for user: %s with %d questions. Now we have %d active sessions",
                session.getUserName(),
                session.getTotalQuestions(),
                sessionManager.getActiveSessionsCount());

        // Build and send the first question
        String questionText = buildQuestionText(session);

        OutgoingTextMessage outgoingTextMessage = OutgoingTextMessage.builder()
                .parseMode("Markdown")
                .text(questionText)
                .build();

        // Set the chat ID header so Camel knows where to send the response
        exchange.getIn().setHeader(TELEGRAM_CHAT_ID, chatIdStr);
        exchange.getIn().setBody(outgoingTextMessage);
    }

    /**
     * Fetches all word pairs for a topic (without shuffling).
     */
    private List<QuizPair> getAllWordPairs(String topicName, String sourceLang, String targetLang) {
        return cachedQuizWordService.getWordPairs(topicName, sourceLang, targetLang)
                .await().atMost(Duration.ofSeconds(10L));
    }

    /**
     * Builds a formatted question text from the current session state.
     */
    private String buildQuestionText(UserQuizSession session) {
        QuizPair question = session.getCurrentQuestion()
                .orElseThrow(
                        () -> new RuntimeException("❌ No questions are available in the session")
                );
        int questionNumber = session.getCurrentQuestionIndex() + 1;
        int totalQuestions = session.getTotalQuestions();

        return menuService.getQuestionMessage(questionNumber, totalQuestions, question.wrdQuestion());
    }

    public static <T> List<T> getNRandomElements(List<T> list, int n) {
        Objects.requireNonNull(list, "List must not be null");
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive, but was: " + n);
        }
        // If n is greater than or equal to the list size, return a shuffled copy of the list
        // (This ensures all elements are selected, but in a random order)
        if (n >= list.size()) {
            ArrayList<T> shuffledList = new ArrayList<>(list);
            Collections.shuffle(shuffledList);
            return shuffledList;
        }
        // Create a copy to avoid modifying the original list
        List<T> resultList = new ArrayList<>(list);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Perform n swaps to select n random elements
        for (int i = 0; i < n; i++) {
            // Generate a random index between i (inclusive) and list size (exclusive)
            int randomIndex = i + random.nextInt(resultList.size() - i);
            // Swap the element at the random index with the element at the current index 'i'
            Collections.swap(resultList, i, randomIndex);
        }
        // The first n elements are the random unique selection
        return new ArrayList<>(resultList.subList(0, n));
    }
}
