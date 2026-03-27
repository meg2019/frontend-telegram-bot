# Creating a Telegram Bot with Quarkus and Apache Camel

This comprehensive guide will walk you through building a Telegram bot using **Quarkus** and **Apache Camel**. The bot will stream words from a reactive `Multi<String>` source and display them in the Telegram chat.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Understanding the Architecture](#understanding-the-architecture)
4. [Step 1: Create a Quarkus Project](#step-1-create-a-quarkus-project)
5. [Step 2: Add Required Dependencies](#step-2-add-required-dependencies)
6. [Step 3: Create a Telegram Bot](#step-3-create-a-telegram-bot)
7. [Step 4: Configure Application Properties](#step-4-configure-application-properties)
8. [Step 5: Create the Word Provider Service](#step-5-create-the-word-provider-service)
9. [Step 6: Create the Bot State Manager](#step-6-create-the-bot-state-manager)
10. [Step 7: Create the Camel Route](#step-7-create-the-camel-route)
11. [Step 8: Running the Application](#step-8-running-the-application)
12. [Step 9: Testing the Bot](#step-9-testing-the-bot)
13. [Complete Project Structure](#complete-project-structure)
14. [Troubleshooting](#troubleshooting)
15. [Additional Resources](#additional-resources)

---

## Overview

In this guide, we'll build a Telegram bot that:

1. **Shows a menu** with one option: `/start to run bot`
2. **Receives `/start` command** to begin processing
3. **Gets words** from a reactive `Multi<String>` stream via the `provideWords()` function
4. **Displays each word** sequentially in Telegram
5. **Shows "stream now empty"** when all words are processed and displays the main menu again

### Technologies Used

| Technology | Purpose |
|------------|---------|
| **Quarkus** | Supersonic, subatomic Java framework |
| **Apache Camel** | Integration framework with Telegram component |
| **Mutiny** | Reactive programming library for handling `Multi<String>` streams |
| **SmallRye Reactive** | Reactive messaging support |

---

## Prerequisites

Before starting, ensure you have:

- **Java 17+** installed (`java -version`)
- **Maven 3.8+** or **Gradle** installed
- **A Telegram account**
- **Internet connection** for Telegram Bot API access
- **IDE** (IntelliJ IDEA, VS Code, or Eclipse recommended)

---

## Understanding the Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Quarkus Application                          │
│                                                                 │
│  ┌──────────────────┐    ┌─────────────────┐   ┌─────────────┐ │
│  │  WordProvider    │───▶│  BotStateManager │───▶│ Camel Route │ │
│  │  provideWords()  │    │                 │   │             │ │
│  │  Multi<String>   │    │  Session State  │   │ Telegram    │ │
│  └──────────────────┘    └─────────────────┘   │ Component   │ │
│                                                 └──────┬──────┘ │
│                                                        │        │
└────────────────────────────────────────────────────────┼────────┘
                                                         │
                                                         ▼
                                              ┌──────────────────┐
                                              │  Telegram Bot    │
                                              │  API             │
                                              └──────────────────┘
```

---

## Step 1: Create a Quarkus Project

### Option A: Using Quarkus CLI

```bash
quarkus create app com.example:telegram-word-bot \
    --extension='camel-quarkus-telegram,quarkus-arc' \
    --no-code
```

### Option B: Using Maven

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.17.0:create \
    -DprojectGroupId=com.example \
    -DprojectArtifactId=telegram-word-bot \
    -Dextensions="camel-quarkus-telegram,quarkus-arc" \
    -DnoCode
```

### Option C: Using code.quarkus.io

1. Go to [https://code.quarkus.io/](https://code.quarkus.io/)
2. Enter project details:
   - **Group**: `com.example`
   - **Artifact**: `telegram-word-bot`
3. Add extensions:
   - `camel-quarkus-telegram`
   - `quarkus-arc`
4. Click **Generate your application** and download

Navigate to the project directory:

```bash
cd telegram-word-bot
```

---

## Step 2: Add Required Dependencies

Open your `pom.xml` and ensure the following dependencies are present:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.example</groupId>
    <artifactId>telegram-word-bot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    
    <properties>
        <compiler-plugin.version>3.13.0</compiler-plugin.version>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <quarkus.platform.version>3.17.0</quarkus.platform.version>
        <camel-quarkus.version>3.17.0</camel-quarkus.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- Quarkus BOM -->
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Camel Quarkus BOM -->
            <dependency>
                <groupId>org.apache.camel.quarkus</groupId>
                <artifactId>camel-quarkus-bom</artifactId>
                <version>${camel-quarkus.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <dependencies>
        <!-- Quarkus Core -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        
        <!-- Camel Telegram Component for Quarkus -->
        <dependency>
            <groupId>org.apache.camel.quarkus</groupId>
            <artifactId>camel-quarkus-telegram</artifactId>
        </dependency>
        
        <!-- Camel Core (for RouteBuilder) -->
        <dependency>
            <groupId>org.apache.camel.quarkus</groupId>
            <artifactId>camel-quarkus-core</artifactId>
        </dependency>
        
        <!-- Camel Direct Component (for triggering routes) -->
        <dependency>
            <groupId>org.apache.camel.quarkus</groupId>
            <artifactId>camel-quarkus-direct</artifactId>
        </dependency>
        
        <!-- Mutiny (for Multi<String> reactive streams) -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-mutiny</artifactId>
        </dependency>
        
        <!-- SmallRye Reactive Messaging -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-reactive-messaging</artifactId>
        </dependency>
        
        <!-- Testing Dependencies -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                            <goal>native-image-agent</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${compiler-plugin.version}</version>
                <configuration>
                    <release>${maven.compiler.release}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Install Dependencies

```bash
mvn clean install -DskipTests
```

---

## Step 3: Create a Telegram Bot

### 3.1 Talk to BotFather

1. Open Telegram and search for **@BotFather**
2. Start a chat and send `/newbot`
3. Follow the prompts:
   - **Name**: `Word Stream Bot` (display name)
   - **Username**: `word_stream_bot` (must end with `bot`)

4. BotFather will provide you with an **Authorization Token** like:
   ```
   123456789:ABCdefGHIjklMNOpqrsTUVwxyz
   ```

> ⚠️ **Important**: Keep your token secret! Never commit it to version control.

### 3.2 Set Bot Commands (Optional but Recommended)

Send to BotFather:
```
/setcommands
```

Then select your bot and send:
```
start - Start the word stream bot
```

This makes `/start` appear in the Telegram command menu.

### 3.3 Get Your Chat ID

To test your bot, you need your chat ID. Here's how to get it:

1. Start a conversation with your bot in Telegram
2. Send any message to your bot
3. Open this URL in your browser (replace with your token):
   ```
   https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates
   ```
4. Look for the `"chat":{"id":XXXXXXXXX}` value in the response

---

## Step 4: Configure Application Properties

Create or edit `src/main/resources/application.properties`:

```properties
# ===========================================
# Telegram Bot Configuration
# ===========================================

# Telegram Bot Authorization Token
# IMPORTANT: Use environment variables in production!
telegram.authorization-token=${TELEGRAM_BOT_TOKEN:your-bot-token-here}

# Default Chat ID (for testing - optional)
telegram.default-chat-id=${TELEGRAM_CHAT_ID:}

# ===========================================
# Camel Configuration
# ===========================================

# Enable Camel route tracing for debugging (set to false in production)
camel.main.tracing=false

# Camel component configuration
camel.component.telegram.authorization-token=${telegram.authorization-token}

# ===========================================
# Quarkus Configuration
# ===========================================

# Quarkus HTTP port (optional, if you want REST endpoints)
quarkus.http.port=8080

# Logging configuration
quarkus.log.category."org.apache.camel".level=INFO
quarkus.log.category."com.example".level=DEBUG

# ===========================================
# Application Settings
# ===========================================

# Word stream delay in milliseconds (delay between sending words)
bot.word-delay-ms=1000
```

### Using Environment Variables (Recommended)

For security, set your token via environment variables:

```bash
export TELEGRAM_BOT_TOKEN="123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
export TELEGRAM_CHAT_ID="your-chat-id"
```

---

## Step 5: Create the Word Provider Service

This service provides the `Multi<String>` stream of words.

Create `src/main/java/com/example/service/WordProviderService.java`:

```java
package com.example.service;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Service that provides a stream of words using Mutiny's Multi.
 * This service simulates a reactive data source that emits words one by one.
 */
@ApplicationScoped
public class WordProviderService {

    private static final Logger LOG = Logger.getLogger(WordProviderService.class);

    @ConfigProperty(name = "bot.word-delay-ms", defaultValue = "1000")
    long wordDelayMs;

    /**
     * Default list of words to stream.
     * You can modify this or load from an external source.
     */
    private static final List<String> DEFAULT_WORDS = Arrays.asList(
            "Hello",
            "Welcome",
            "to",
            "Quarkus",
            "Telegram",
            "Bot",
            "powered",
            "by",
            "Apache",
            "Camel!"
    );

    /**
     * Provides a reactive stream of words.
     * Each word is emitted with a configurable delay.
     *
     * @return Multi<String> - A reactive stream of words
     */
    public Multi<String> provideWords() {
        LOG.info("Starting to provide words stream...");
        
        return Multi.createFrom()
                .iterable(DEFAULT_WORDS)
                .onItem()
                .call(word -> {
                    LOG.debugf("Emitting word: %s", word);
                    // Add a delay between words for a nice streaming effect
                    return Multi.createFrom()
                            .item(word)
                            .onItem()
                            .delayIt()
                            .by(Duration.ofMillis(wordDelayMs));
                });
    }

    /**
     * Provides words with a custom delay between emissions.
     *
     * @param delayMs delay in milliseconds between each word
     * @return Multi<String> - A reactive stream of words with delays
     */
    public Multi<String> provideWordsWithDelay(long delayMs) {
        LOG.infof("Starting to provide words stream with %dms delay...", delayMs);
        
        return Multi.createFrom()
                .iterable(DEFAULT_WORDS)
                .onItem()
                .transformToUniAndConcatenate(word -> {
                    return Multi.createFrom()
                            .item(word)
                            .onItem()
                            .delayIt()
                            .by(Duration.ofMillis(delayMs))
                            .toUni();
                });
    }

    /**
     * Provides words from a custom list.
     *
     * @param words custom list of words to stream
     * @return Multi<String> - A reactive stream of custom words
     */
    public Multi<String> provideCustomWords(List<String> words) {
        LOG.infof("Starting to provide custom words stream with %d words...", words.size());
        
        return Multi.createFrom()
                .iterable(words);
    }

    /**
     * Returns the count of words in the default stream.
     *
     * @return number of words
     */
    public int getWordCount() {
        return DEFAULT_WORDS.size();
    }
}
```

---

## Step 6: Create the Bot State Manager

This service manages the state of conversations with users.

Create `src/main/java/com/example/service/BotStateManager.java`:

```java
package com.example.service;

import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the state of bot sessions per chat.
 * Tracks whether the bot is currently streaming words to each chat.
 */
@ApplicationScoped
public class BotStateManager {

    private static final Logger LOG = Logger.getLogger(BotStateManager.class);

    /**
     * Represents the state of a chat session.
     */
    public static class ChatState {
        private final AtomicBoolean isStreaming = new AtomicBoolean(false);
        private Cancellable currentSubscription;

        public boolean isStreaming() {
            return isStreaming.get();
        }

        public void setStreaming(boolean streaming) {
            isStreaming.set(streaming);
        }

        public Cancellable getCurrentSubscription() {
            return currentSubscription;
        }

        public void setCurrentSubscription(Cancellable subscription) {
            this.currentSubscription = subscription;
        }

        public void cancelIfActive() {
            if (currentSubscription != null) {
                currentSubscription.cancel();
                currentSubscription = null;
            }
            isStreaming.set(false);
        }
    }

    /**
     * Map of chat ID to chat state.
     */
    private final Map<String, ChatState> chatStates = new ConcurrentHashMap<>();

    /**
     * Gets or creates the state for a chat.
     *
     * @param chatId the chat ID
     * @return the chat state
     */
    public ChatState getOrCreateState(String chatId) {
        return chatStates.computeIfAbsent(chatId, k -> {
            LOG.debugf("Creating new state for chat: %s", chatId);
            return new ChatState();
        });
    }

    /**
     * Checks if a chat is currently streaming.
     *
     * @param chatId the chat ID
     * @return true if streaming, false otherwise
     */
    public boolean isStreaming(String chatId) {
        ChatState state = chatStates.get(chatId);
        return state != null && state.isStreaming();
    }

    /**
     * Starts streaming for a chat.
     *
     * @param chatId the chat ID
     * @param subscription the subscription to track
     */
    public void startStreaming(String chatId, Cancellable subscription) {
        ChatState state = getOrCreateState(chatId);
        state.cancelIfActive(); // Cancel any existing stream
        state.setStreaming(true);
        state.setCurrentSubscription(subscription);
        LOG.infof("Started streaming for chat: %s", chatId);
    }

    /**
     * Stops streaming for a chat.
     *
     * @param chatId the chat ID
     */
    public void stopStreaming(String chatId) {
        ChatState state = chatStates.get(chatId);
        if (state != null) {
            state.cancelIfActive();
            LOG.infof("Stopped streaming for chat: %s", chatId);
        }
    }

    /**
     * Removes the state for a chat.
     *
     * @param chatId the chat ID
     */
    public void removeState(String chatId) {
        ChatState state = chatStates.remove(chatId);
        if (state != null) {
            state.cancelIfActive();
            LOG.debugf("Removed state for chat: %s", chatId);
        }
    }

    /**
     * Gets the main menu text.
     *
     * @return the menu text
     */
    public String getMenuText() {
        return """
                🤖 *Word Stream Bot*
                
                Welcome! This bot streams words from a reactive Multi<String> source.
                
                📋 *Available Commands:*
                /start - Start the word stream
                
                Tap the command or type it to begin!
                """;
    }

    /**
     * Gets the streaming started message.
     *
     * @param wordCount the number of words to stream
     * @return the message
     */
    public String getStreamStartedMessage(int wordCount) {
        return String.format("""
                ▶️ *Stream Started!*
                
                Streaming %d words...
                
                Each word will appear one by one.
                """, wordCount);
    }

    /**
     * Gets the stream completed message.
     *
     * @return the message
     */
    public String getStreamCompletedMessage() {
        return """
                ✅ *Stream Completed!*
                
                stream now empty
                
                The word stream has finished.
                """;
    }
}
```

---

## Step 7: Create the Camel Route

This is the main integration logic using Apache Camel.

Create `src/main/java/com/example/route/TelegramBotRoute.java`:

```java
package com.example.route;

import com.example.service.BotStateManager;
import com.example.service.WordProviderService;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Apache Camel Route that handles Telegram Bot interactions.
 * 
 * Flow:
 * 1. User sends /start command
 * 2. Bot receives command and starts streaming words
 * 3. Words from Multi<String> are sent one by one
 * 4. When stream completes, "stream now empty" is sent
 * 5. Main menu is displayed again
 */
@ApplicationScoped
public class TelegramBotRoute extends RouteBuilder {

    private static final Logger LOG = Logger.getLogger(TelegramBotRoute.class);

    @Inject
    WordProviderService wordProviderService;

    @Inject
    BotStateManager stateManager;

    @Inject
    ProducerTemplate producerTemplate;

    @ConfigProperty(name = "telegram.authorization-token")
    String authorizationToken;

    @ConfigProperty(name = "bot.word-delay-ms", defaultValue = "1000")
    long wordDelayMs;

    @Override
    public void configure() throws Exception {
        
        // ===========================================
        // Error Handling
        // ===========================================
        onException(Exception.class)
                .handled(true)
                .log("Error in Telegram route: ${exception.message}")
                .process(exchange -> {
                    String chatId = getChatId(exchange);
                    if (chatId != null) {
                        stateManager.stopStreaming(chatId);
                    }
                });

        // ===========================================
        // Main Route: Receive messages from Telegram
        // ===========================================
        from("telegram:bots?authorizationToken=" + authorizationToken)
                .routeId("telegram-receiver")
                .log("Received message from Telegram: ${body}")
                .process(this::handleIncomingMessage);

        // ===========================================
        // Route: Send message to Telegram
        // ===========================================
        from("direct:sendToTelegram")
                .routeId("telegram-sender")
                .log("Sending message to Telegram chat ${header.CamelTelegramChatId}: ${body}")
                .to("telegram:bots?authorizationToken=" + authorizationToken);

        // ===========================================
        // Route: Send main menu
        // ===========================================
        from("direct:sendMenu")
                .routeId("send-menu")
                .process(exchange -> {
                    String chatId = exchange.getIn().getHeader("CamelTelegramChatId", String.class);
                    exchange.getIn().setBody(stateManager.getMenuText());
                    exchange.getIn().setHeader("CamelTelegramChatId", chatId);
                    exchange.getIn().setHeader("CamelTelegramParseMode", "Markdown");
                })
                .to("direct:sendToTelegram");

        // ===========================================
        // Route: Start word streaming
        // ===========================================
        from("direct:startStream")
                .routeId("start-stream")
                .process(this::startWordStream);
    }

    /**
     * Handles incoming messages from Telegram.
     *
     * @param exchange the Camel exchange
     */
    private void handleIncomingMessage(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        String chatId = getChatId(exchange);
        String messageText = "";

        LOG.debugf("Processing message for chat %s: %s", chatId, body);

        // Extract message text
        if (body instanceof IncomingMessage) {
            IncomingMessage incomingMessage = (IncomingMessage) body;
            messageText = incomingMessage.getText();
            if (chatId == null) {
                chatId = String.valueOf(incomingMessage.getChat().getId());
            }
        } else if (body instanceof String) {
            messageText = (String) body;
        }

        if (chatId == null) {
            LOG.warn("Could not determine chat ID from message");
            return;
        }

        // Store chatId in header for downstream processing
        exchange.getIn().setHeader("CamelTelegramChatId", chatId);

        // Handle commands
        if (messageText != null) {
            messageText = messageText.trim().toLowerCase();

            if (messageText.equals("/start")) {
                handleStartCommand(chatId);
            } else {
                // For any other message, show the menu
                sendMenu(chatId);
            }
        } else {
            // For non-text messages, show the menu
            sendMenu(chatId);
        }
    }

    /**
     * Handles the /start command.
     *
     * @param chatId the chat ID
     */
    private void handleStartCommand(String chatId) {
        LOG.infof("Processing /start command for chat: %s", chatId);

        // Check if already streaming
        if (stateManager.isStreaming(chatId)) {
            sendMessage(chatId, "⚠️ Stream is already running! Please wait for it to complete.");
            return;
        }

        // Send "stream started" message
        int wordCount = wordProviderService.getWordCount();
        sendMessage(chatId, stateManager.getStreamStartedMessage(wordCount));

        // Start streaming words
        startWordStreamForChat(chatId);
    }

    /**
     * Starts the word stream for a specific chat.
     *
     * @param chatId the chat ID
     */
    private void startWordStreamForChat(String chatId) {
        LOG.infof("Starting word stream for chat: %s", chatId);

        // Subscribe to the word stream
        Cancellable subscription = wordProviderService.provideWords()
                .onItem()
                .invoke(word -> {
                    // Send each word to Telegram
                    LOG.debugf("Sending word '%s' to chat %s", word, chatId);
                    sendMessage(chatId, "📝 " + word);
                    
                    // Add delay between words
                    try {
                        Thread.sleep(wordDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                })
                .onCompletion()
                .invoke(() -> {
                    LOG.infof("Word stream completed for chat: %s", chatId);
                    
                    // Mark streaming as complete
                    stateManager.stopStreaming(chatId);
                    
                    // Send completion message
                    sendMessage(chatId, stateManager.getStreamCompletedMessage());
                    
                    // Show menu again
                    sendMenu(chatId);
                })
                .onFailure()
                .invoke(throwable -> {
                    LOG.errorf("Error in word stream for chat %s: %s", chatId, throwable.getMessage());
                    stateManager.stopStreaming(chatId);
                    sendMessage(chatId, "❌ Error occurred: " + throwable.getMessage());
                    sendMenu(chatId);
                })
                .subscribe()
                .with(
                        item -> LOG.debugf("Processed word: %s", item),
                        throwable -> LOG.errorf("Subscription error: %s", throwable.getMessage())
                );

        // Track the subscription
        stateManager.startStreaming(chatId, subscription);
    }

    /**
     * Starts the word stream process.
     *
     * @param exchange the Camel exchange
     */
    private void startWordStream(Exchange exchange) {
        String chatId = exchange.getIn().getHeader("CamelTelegramChatId", String.class);
        if (chatId != null) {
            startWordStreamForChat(chatId);
        }
    }

    /**
     * Sends a message to a specific chat.
     *
     * @param chatId the chat ID
     * @param message the message text
     */
    private void sendMessage(String chatId, String message) {
        try {
            producerTemplate.sendBodyAndHeaders(
                    "direct:sendToTelegram",
                    message,
                    Map.of(
                            "CamelTelegramChatId", chatId,
                            "CamelTelegramParseMode", "Markdown"
                    )
            );
        } catch (Exception e) {
            LOG.errorf("Failed to send message to chat %s: %s", chatId, e.getMessage());
        }
    }

    /**
     * Sends the main menu to a specific chat.
     *
     * @param chatId the chat ID
     */
    private void sendMenu(String chatId) {
        try {
            producerTemplate.sendBodyAndHeaders(
                    "direct:sendMenu",
                    null,
                    Map.of("CamelTelegramChatId", chatId)
            );
        } catch (Exception e) {
            LOG.errorf("Failed to send menu to chat %s: %s", chatId, e.getMessage());
        }
    }

    /**
     * Extracts the chat ID from the exchange.
     *
     * @param exchange the Camel exchange
     * @return the chat ID or null
     */
    private String getChatId(Exchange exchange) {
        // Try header first
        String chatId = exchange.getIn().getHeader("CamelTelegramChatId", String.class);
        if (chatId != null) {
            return chatId;
        }

        // Try from message body
        Object body = exchange.getIn().getBody();
        if (body instanceof IncomingMessage) {
            IncomingMessage msg = (IncomingMessage) body;
            if (msg.getChat() != null) {
                return String.valueOf(msg.getChat().getId());
            }
        }

        return null;
    }

    // Import for Map utility
    private static class Map {
        static <K, V> java.util.Map<K, V> of(K k1, V v1, K k2, V v2) {
            java.util.Map<K, V> map = new java.util.HashMap<>();
            map.put(k1, v1);
            map.put(k2, v2);
            return map;
        }

        static <K, V> java.util.Map<K, V> of(K k1, V v1) {
            java.util.Map<K, V> map = new java.util.HashMap<>();
            map.put(k1, v1);
            return map;
        }
    }
}
```

---

## Step 8: Running the Application

### 8.1 Set Environment Variables

Before running, set your Telegram bot token:

```bash
# Linux/Mac
export TELEGRAM_BOT_TOKEN="your-bot-token-here"

# Windows (PowerShell)
$env:TELEGRAM_BOT_TOKEN="your-bot-token-here"

# Windows (CMD)
set TELEGRAM_BOT_TOKEN=your-bot-token-here
```

### 8.2 Run in Development Mode

```bash
mvn quarkus:dev
```

Or using the Quarkus CLI:

```bash
quarkus dev
```

You should see output like:
```
__  ____  __  _____   ___  __ ____  ______ 
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
2024-XX-XX INFO  [org.apa.cam.imp.eng.AbstractCamelContext] Apache Camel started
2024-XX-XX INFO  [org.apa.cam.com.tel.TelegramConsumer] Telegram bot is now listening...
```

### 8.3 Build for Production

```bash
# Build JAR
mvn package -DskipTests

# Run the JAR
java -jar target/quarkus-app/quarkus-run.jar
```

### 8.4 Build Native Executable (Optional)

```bash
# Build native executable (requires GraalVM)
mvn package -Dnative -DskipTests

# Run native executable
./target/telegram-word-bot-1.0.0-SNAPSHOT-runner
```

---

## Step 9: Testing the Bot

### 9.1 Manual Testing

1. Open Telegram and find your bot
2. Send `/start` or tap the start button
3. You should see:
   - "Stream Started!" message
   - Words appearing one by one with emoji prefix
   - "stream now empty" when complete
   - Main menu displayed again

### 9.2 Expected Flow

```
User: /start

Bot: ▶️ Stream Started!
     Streaming 10 words...
     Each word will appear one by one.

Bot: 📝 Hello
Bot: 📝 Welcome
Bot: 📝 to
Bot: 📝 Quarkus
Bot: 📝 Telegram
Bot: 📝 Bot
Bot: 📝 powered
Bot: 📝 by
Bot: 📝 Apache
Bot: 📝 Camel!

Bot: ✅ Stream Completed!
     stream now empty
     The word stream has finished.

Bot: 🤖 Word Stream Bot
     Welcome! This bot streams words...
     /start - Start the word stream
```

### 9.3 Unit Testing

Create `src/test/java/com/example/service/WordProviderServiceTest.java`:

```java
package com.example.service;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WordProviderServiceTest {

    @Inject
    WordProviderService wordProviderService;

    @Test
    void testProvideWords() {
        // Given
        Multi<String> words = wordProviderService.provideWords();

        // When
        AssertSubscriber<String> subscriber = words
                .subscribe()
                .withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        // Then
        subscriber.awaitCompletion();
        List<String> items = subscriber.getItems();
        
        assertFalse(items.isEmpty(), "Should have words");
        assertEquals(10, items.size(), "Should have 10 words");
        assertTrue(items.contains("Quarkus"), "Should contain 'Quarkus'");
    }

    @Test
    void testProvideCustomWords() {
        // Given
        List<String> customWords = List.of("Test", "Custom", "Words");

        // When
        Multi<String> words = wordProviderService.provideCustomWords(customWords);
        AssertSubscriber<String> subscriber = words
                .subscribe()
                .withSubscriber(AssertSubscriber.create(Long.MAX_VALUE));

        // Then
        subscriber.awaitCompletion();
        assertEquals(customWords, subscriber.getItems());
    }

    @Test
    void testGetWordCount() {
        // When
        int count = wordProviderService.getWordCount();

        // Then
        assertEquals(10, count, "Should return correct word count");
    }
}
```

Run tests:

```bash
mvn test
```

---

## Complete Project Structure

```
telegram-word-bot/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           ├── route/
│   │   │           │   └── TelegramBotRoute.java
│   │   │           └── service/
│   │   │               ├── BotStateManager.java
│   │   │               └── WordProviderService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/
│               └── example/
│                   └── service/
│                       └── WordProviderServiceTest.java
├── pom.xml
└── README.md
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Bot Not Receiving Messages

**Problem**: The bot doesn't respond to commands.

**Solutions**:
- Verify your `TELEGRAM_BOT_TOKEN` is correct
- Check if the bot is running (look for Camel startup logs)
- Ensure you've started a conversation with the bot first (send `/start`)
- Check network/firewall settings

```bash
# Test your token
curl https://api.telegram.org/bot<YOUR_TOKEN>/getMe
```

#### 2. Authorization Token Error

**Problem**: `401 Unauthorized` errors in logs.

**Solution**:
- Verify the token format: `123456789:ABCdefGHI...`
- Regenerate token with BotFather using `/revoke` then `/newbot`

#### 3. Chat ID Issues

**Problem**: Messages not reaching specific chats.

**Solution**:
- Get the correct chat ID from `/getUpdates` API
- For groups, the chat ID is negative (e.g., `-123456789`)
- Ensure the bot is a member of the group

#### 4. Dependency Conflicts

**Problem**: ClassNotFoundException or NoSuchMethodError.

**Solution**:
```bash
# Check for dependency conflicts
mvn dependency:tree

# Force update dependencies
mvn clean install -U
```

#### 5. Rate Limiting

**Problem**: Telegram API returns `429 Too Many Requests`.

**Solution**:
- Increase `bot.word-delay-ms` in `application.properties`
- Telegram limits: ~30 messages/second to different users, ~20 messages/minute to same group

---

## Additional Resources

### Official Documentation

- 📚 [Quarkus Guides](https://quarkus.io/guides/)
- 📚 [Apache Camel Quarkus](https://camel.apache.org/camel-quarkus/latest/)
- 📚 [Apache Camel Telegram Component](https://camel.apache.org/components/latest/telegram-component.html)
- 📚 [Mutiny Documentation](https://smallrye.io/smallrye-mutiny/)
- 📚 [Telegram Bot API](https://core.telegram.org/bots/api)

### Useful Links

- 🔗 [Quarkus Extension Hub](https://quarkus.io/extensions/)
- 🔗 [Camel Quarkus Extensions](https://camel.apache.org/camel-quarkus/latest/reference/index.html)
- 🔗 [Smallrye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging/)

### Related Tutorials

- 📖 [Getting Started with Quarkus](https://quarkus.io/get-started/)
- 📖 [Camel Quarkus First Steps](https://camel.apache.org/camel-quarkus/latest/user-guide/first-steps.html)
- 📖 [Reactive Programming with Mutiny](https://quarkus.io/guides/mutiny-primer)

---

## Summary

In this guide, you learned how to:

1. ✅ Create a Quarkus project with Apache Camel and Telegram integration
2. ✅ Configure a Telegram bot and obtain the authorization token
3. ✅ Implement a reactive word provider using `Multi<String>`
4. ✅ Manage bot state across multiple chat sessions
5. ✅ Create Camel routes for receiving and sending Telegram messages
6. ✅ Stream words one by one to Telegram chats
7. ✅ Handle stream completion and display the menu

The combination of Quarkus, Apache Camel, and Mutiny provides a powerful, reactive, and cloud-native solution for building Telegram bots!

---

*Happy Coding! 🚀*
