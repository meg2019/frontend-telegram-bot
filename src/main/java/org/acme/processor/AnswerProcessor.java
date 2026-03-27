package org.acme.processor;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.model.QuizPair;
import org.acme.model.UserQuizSession;
import org.acme.service.MenuService;
import org.acme.service.SessionManagerService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import java.util.Arrays;

import static org.apache.camel.component.telegram.TelegramConstants.TELEGRAM_CHAT_ID;

/**
 * Handles user answers during an active quiz session.
 * <p>
 * Compares the user's answer to the expected answer,
 * provides feedback, and advances to the next question
 * or shows results when the quiz is complete.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class AnswerProcessor implements Processor {

    private final SessionManagerService sessionManager;
    private final MenuService menuService;


    @Override
    public void process(Exchange exchange) throws Exception {
        // 1. Get the chat ID from the exchange header
        String chatIdStr = exchange.getIn().getHeader(TELEGRAM_CHAT_ID, String.class);
        long chatId = Long.parseLong(chatIdStr);

        // 2. Retrieve the active session
        UserQuizSession session = sessionManager.getSession(chatId)
                .orElseThrow(() -> new IllegalStateException(
                        "❌ No active session for chatId: " + chatId));

        // 3. Get the current question and the user's answer
        QuizPair currentQuestion = session.getCurrentQuestion().orElseThrow(
                () -> new IllegalStateException("No current question for session: %s.".formatted(session.getChatId()))
        );
        String userAnswer = exchange.getIn().getBody(String.class).strip();
        Log.infof("🔤 User answered: %s", userAnswer);
        String expectedAnswer = currentQuestion.wrdAnswer();

        // 4. Compare answers using compareToIgnoreCase (case-insensitive)
        String[] splitUserAnswer = expectedAnswer.split("/");
        boolean isCorrect = Arrays.stream(splitUserAnswer).anyMatch(a -> a.equalsIgnoreCase(userAnswer));

        // 5. Record the result
        if (isCorrect) {
            session.incrementCorrectAnswers();
        }

        // 6. Move to the next question
        session.moveToNextQuestion();

        // 7. Build response: feedback + next question or results
        String responseText;

        if (!session.isCompleted()) {
            // Build feedback line + next question
            String feedback = isCorrect
                    ? "✅ *Correct!*"
                    : menuService.getIncorrectAnswerMessage(expectedAnswer,
                    String.join(System.lineSeparator(), MenuService.longStringFormatter(currentQuestion.wrdQuestionDesc()))
            );

            QuizPair nextQuestion = session.getCurrentQuestion().orElseThrow(
                    () -> new IllegalStateException("❌ No current question for session: %s.".formatted(session.getChatId()))
            );
            int questionNumber = session.getCurrentQuestionIndex() + 1;
            int totalQuestions = session.getTotalQuestions();

            responseText = "%s\n\n%s".formatted(feedback,
                    menuService.getQuestionMessage(questionNumber, totalQuestions, nextQuestion.wrdQuestion())
            );
        } else {
            // Quiz is complete — build the feedback for the last answer,
            // then delegate to ResultProcessor for final results
            String feedback = isCorrect
                    ? "✅ *Correct!*\n\n"
                    : String.format("❌ *Incorrect.* The answer was: *%s*\n\n", expectedAnswer);

            responseText = "%s%s".formatted(feedback, menuService.getResultMessage(
                    session.getUserName(),
                    session.getTopicName(),
                    session.getCorrectAnswersCount(),
                    session.getTotalQuestions() - session.getCorrectAnswersCount(),
                    session.getScore()
            ));

            // Clean up the session
            Log.infof("🧹Quiz completed. Cleaning up session for chatId: %s.", chatId);
            sessionManager.removeSession(chatId);
            Log.infof("🏁 Old session removed. Now we have %d active sessions", sessionManager.getActiveSessionsCount());
        }
        // 8. Send the response
        OutgoingTextMessage outgoingTextMessage = OutgoingTextMessage.builder()
                .parseMode("Markdown")
                .text(responseText)
                .build();

        exchange.getIn().setBody(outgoingTextMessage);
    }
}
