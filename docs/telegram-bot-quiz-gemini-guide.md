# Telegram Quiz Bot with Quarkus and Apache Camel

This guide will walk you through creating a Telegram Quiz Bot using Quarkus and the Apache Camel Telegram extension. We'll build a bot that helps users learn foreign language words through a topic-based quiz.

## Prerequisites

- JDK 17+
- Apache Maven 3.8+
- A Telegram Bot Token (from @BotFather)
- An existing Quarkus project (or create one via `quarkus create app`)

## Step 1: Dependencies

Add the following dependencies to your `pom.xml`. We need `camel-quarkus-telegram` for the bot functionality and `lombok` to reduce boilerplate code.

```xml
<dependencies>
    <!-- Camel Telegram Extension -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-telegram</artifactId>
    </dependency>
    <!-- Lombok for boilerplate reduction -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.30</version>
        <scope>provided</scope>
    </dependency>
    <!-- Arc for Dependency Injection -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-arc</artifactId>
    </dependency>
</dependencies>
```

Also, configure your bot token in `src/main/resources/application.properties`:

```properties
# Replace with your actual token
camel.component.telegram.authorization-token=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
```

## Step 2: Domain Models

First, let's define the data structures for our quiz.

### 2.1 QuizPair Record
Create a simple Java record to hold the question and answer pair.

`src/main/java/org/acme/model/QuizPair.java`
```java
package org.acme.model;

/**
 * Represents a single flashcard/quiz item.
 * @param wrdQuesion The word in the source language (Question)
 * @param wrdAnswer  The translation/answer in the target language
 */
public record QuizPair(String wrdQuesion, String wrdAnswer) {}
```

### 2.2 UserQuizSession
Use the provided helper class to manage the user's state.

`src/main/java/org/acme/model/UserQuizSession.java`
(See the provided `UserQuizSession.java` file content from the project description. Ensure it is placed in the `org.acme.model` package.)

## Step 3: Services

We need services to provide data, build keyboards, and manage user sessions.

### 3.1 QuizWordService
This service provides the mock data for the quiz. In a real app, this might fetch from a database.

`src/main/java/org/acme/service/QuizWordService.java`
```java
package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.model.QuizPair;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class QuizWordService {

    private final Map<String, List<QuizPair>> topicData = Map.of(
        "Basic Greetings", List.of(
            new QuizPair("Shalom", "Peace"),
            new QuizPair("Toda", "Thanks"),
            new QuizPair("Boker Tov", "Good Morning")
        ),
        "Numbers", List.of(
            new QuizPair("Echad", "One"),
            new QuizPair("Shtayim", "Two"),
            new QuizPair("Shalosh", "Three")
        ),
        "Food", List.of(
            new QuizPair("Lechem", "Bread"),
            new QuizPair("Mayim", "Water"),
            new QuizPair("Chalav", "Milk")
        )
    );

    public List<String> getAllTopicsNameSorted() {
        return topicData.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<QuizPair> getWordPairs(String topicName) {
        return topicData.getOrDefault(topicName, Collections.emptyList());
    }
}
```

### 3.2 KeyboardBuilderService
This service constructs the Inline Keyboard for topic selection.

`src/main/java/org/acme/service/KeyboardBuilderService.java`
```java
package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class KeyboardBuilderService {

    public static final String CALLBACK_PREFIX = "topic:";

    @Inject
    QuizWordService quizWordService;

    public InlineKeyboardMarkup buildTopicKeyboard() {
        List<String> topicsName = quizWordService.getAllTopicsNameSorted();
        InlineKeyboardMarkup.Builder kbBuilder = InlineKeyboardMarkup.builder();

        topicsName.stream()
                .map(topic -> InlineKeyboardButton.builder()
                        .text(topic)
                        // Callback data format: "topic:TopicName"
                        .callbackData(CALLBACK_PREFIX + topic)
                        .build())
                .map(Collections::singletonList)
                .forEach(kbBuilder::addRow);

        return kbBuilder.build();
    }
}
```

### 3.3 UserSessionService
A singleton bean to hold the active sessions for each user (Chat ID).

`src/main/java/org/acme/service/UserSessionService.java`
```java
package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.model.UserQuizSession;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class UserSessionService {

    private final ConcurrentHashMap<Long, UserQuizSession> sessions = new ConcurrentHashMap<>();

    public void startSession(Long chatId, UserQuizSession session) {
        sessions.put(chatId, session);
    }

    public Optional<UserQuizSession> getSession(Long chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }

    public void clearSession(Long chatId) {
        sessions.remove(chatId);
    }
}
```

## Step 4: Route and Processors

The core logic resides in the Camel processors and the route definition.

### 4.1 TopicCallbackProcessor
Handles the button press, creates a session, and returns the first question.

`src/main/java/org/acme/processor/TopicCallbackProcessor.java`
```java
package org.acme.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.model.QuizPair;
import org.acme.model.UserQuizSession;
import org.acme.service.KeyboardBuilderService;
import org.acme.service.QuizWordService;
import org.acme.service.UserSessionService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.IncomingCallbackQuery;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import java.util.List;

@ApplicationScoped
public class TopicCallbackProcessor implements Processor {

    @Inject
    QuizWordService quizWordService;
    
    @Inject
    UserSessionService sessionService;

    @Override
    public void process(Exchange exchange) throws Exception {
        IncomingCallbackQuery callback = exchange.getMessage().getBody(IncomingCallbackQuery.class);
        String data = callback.getData(); // e.g., "topic:Numbers"
        Long chatId = Long.parseLong(exchange.getMessage().getHeader(TelegramConstants.TELEGRAM_CHAT_ID, String.class));
        String userName = callback.getFrom().getUsername();

        if (data.startsWith(KeyboardBuilderService.CALLBACK_PREFIX)) {
            String topicName = data.substring(KeyboardBuilderService.CALLBACK_PREFIX.length()).trim();
            
            // 1. Get Questions
            List<QuizPair> questions = quizWordService.getWordPairs(topicName);
            
            if (questions.isEmpty()) {
                OutgoingTextMessage errorMsg = new OutgoingTextMessage();
                errorMsg.setText("⚠️ Error: No questions found for topic: " + topicName);
                exchange.getMessage().setBody(errorMsg);
                return;
            }

            // 2. Start Session
            UserQuizSession session = UserQuizSession.create(chatId, userName != null ? userName : "User", topicName, questions);
            sessionService.startSession(chatId, session);

            // 3. Prepare First Question Message
            QuizPair firstQ = session.getCurrentQuestion().get(); // Safe because we checked isEmpty
            
            String text = """
                📚 *Question %d/%d*
                
                What is the Russian translation of:
                🔤 *%s*
                
                Type your answer below:
                """.formatted(1, session.getTotalQuestions(), firstQ.wrdQuesion());
            
            OutgoingTextMessage msg = new OutgoingTextMessage();
            msg.setText(text);
            msg.setParseMode("Markdown");
            
            exchange.getMessage().setBody(msg);
        }
    }
}
```

### 4.2 QuizAnswerProcessor
Handles text inputs (answers), validates them, and moves the quiz forward.

`src/main/java/org/acme/processor/QuizAnswerProcessor.java`
```java
package org.acme.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.model.QuizPair;
import org.acme.model.UserQuizSession;
import org.acme.service.UserSessionService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

@ApplicationScoped
public class QuizAnswerProcessor implements Processor {

    @Inject
    UserSessionService sessionService;

    @Override
    public void process(Exchange exchange) throws Exception {
        String answerText = exchange.getMessage().getBody(String.class);
        Long chatId = Long.parseLong(exchange.getMessage().getHeader(TelegramConstants.TELEGRAM_CHAT_ID, String.class));

        // Get active session
        var sessionOpt = sessionService.getSession(chatId);
        if (sessionOpt.isEmpty()) {
            OutgoingTextMessage msg = new OutgoingTextMessage();
            msg.setText("⚠️ You don't have an active quiz.\nType /start to begin!");
            exchange.getMessage().setBody(msg);
            return;
        }

        UserQuizSession session = sessionOpt.get();
        QuizPair currentQ = session.getCurrentQuestion().orElseThrow(); // Should exist if session is active

        // Check Answer
        boolean isCorrect = currentQ.wrdAnswer().equalsIgnoreCase(answerText.trim());
        if (isCorrect) {
            session.incrementCorrectAnswers();
        }

        // Move to next
        session.moveToNextQuestion();

        if (session.hasNextQuestion()) {
            // SHOW NEXT QUESTION
            QuizPair nextQ = session.getCurrentQuestion().get();
            String responseText = """
                %s
                
                📚 *Question %d/%d*
                
                What is the Russian translation of:
                🔤 *%s*
                """
                .formatted(
                    isCorrect ? "✅ Correct!" : "❌ Incorrect. The answer was: " + currentQ.wrdAnswer(),
                    session.getCurrentQuestionIndex() + 1,
                    session.getTotalQuestions(),
                    nextQ.wrdQuesion()
                );

            OutgoingTextMessage msg = new OutgoingTextMessage();
            msg.setText(responseText);
            msg.setParseMode("Markdown");
            exchange.getMessage().setBody(msg);
        } else {
            // SHOW FINAL RESULTS
            String resultText = """
                🏁 *Quiz Complete!*
                
                %s Great job, %s!
                
                📊 *Final Results:*
                -------------------
                📚 Topic: %s
                ✅ Correct: %d
                ❌ Incorrect: %d
                📈 Score: %d%%
                
                Type /start to try another quiz!
                """
                .formatted(
                    isCorrect ? "✅ Correct!" : "❌ Incorrect. The answer was: " + currentQ.wrdAnswer(),
                    session.getUserName(),
                    session.getTopicName(),
                    session.getCorrectAnswersCount(),
                    session.getTotalQuestions() - session.getCorrectAnswersCount(),
                    session.getScore()
                );
            
            OutgoingTextMessage msg = new OutgoingTextMessage();
            msg.setText(resultText);
            msg.setParseMode("Markdown");
            exchange.getMessage().setBody(msg);
            
            // Clean up session
            sessionService.clearSession(chatId);
        }
    }
}
```

### 4.3 TelegramBotRoute
Finally, wire everything together in the Camel Route.

`src/main/java/org/acme/route/TelegramBotRoute.java`
```java
package org.acme.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.processor.QuizAnswerProcessor;
import org.acme.processor.TopicCallbackProcessor;
import org.acme.service.KeyboardBuilderService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.IncomingCallbackQuery;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

@ApplicationScoped
public class TelegramBotRoute extends RouteBuilder {

    @Inject
    KeyboardBuilderService keyboardBuilderService;
    
    @Inject
    TopicCallbackProcessor topicCallbackProcessor;
    
    @Inject
    QuizAnswerProcessor quizAnswerProcessor;

    @Override
    public void configure() throws Exception {
        
        from("telegram:bots")
            .routeId("telegram-main-route")
            .log("Received: ${body}")
            
            .choice()
                // 1. Handle /start command
                .when(simple("${body} == '/start'"))
                    .process(exchange -> {
                        OutgoingTextMessage msg = new OutgoingTextMessage();
                        msg.setText("🤖 *Welcome to the Language Quiz Bot!*\n\nPlease select a topic to start:");
                        msg.setParseMode("Markdown");
                        msg.setReplyMarkup(keyboardBuilderService.buildTopicKeyboard());
                        exchange.getMessage().setBody(msg);
                    })
                
                // 2. Handle Callback Queries (Button Clicks)
                .when(body().isInstanceOf(IncomingCallbackQuery.class))
                    .process(topicCallbackProcessor)
                
                // 3. Handle Regular Text (Potential Answers)
                .otherwise()
                    .process(quizAnswerProcessor)
            .end()
            
            // Send response back to Telegram
            // We need to ensure the Chat ID is set for the response
            .setHeader(TelegramConstants.TELEGRAM_CHAT_ID, simple("${header.CamelTelegramChatId}"))
            .to("telegram:bots");
    }
}
```

## Running the Bot

1.  Start your Quarkus application:
    ```bash
    ./mvnw quarkus:dev
    ```
2.  Open Telegram and find your bot.
3.  Send `/start`.
4.  You should see the inline keyboard with topics.
5.  Click a topic (e.g., "Numbers").
6.  The bot will ask the first question.
7.  Type an answer. The bot will validate it and show the next question or the final score.

Happy Coding! 🚀
