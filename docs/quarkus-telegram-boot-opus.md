# Building a Telegram Bot with Quarkus Framework

## Complete Step-by-Step Guide for Beginners

---

## Table of Contents

1. [Introduction](#introduction)
2. [Prerequisites](#prerequisites)
3. [Creating Your Telegram Bot](#creating-your-telegram-bot)
4. [Project Setup](#project-setup)
5. [Approach 1: Using Apache Camel Quarkus Telegram](#approach-1-using-apache-camel-quarkus-telegram)
6. [Approach 2: Using TelegramBots Library with Quarkus](#approach-2-using-telegrambots-library-with-quarkus)
7. [Approach 3: Direct REST API Integration](#approach-3-direct-rest-api-integration)
8. [Advanced Features](#advanced-features)
9. [Testing Your Bot](#testing-your-bot)
10. [Deployment](#deployment)
11. [Troubleshooting](#troubleshooting)
12. [Useful Resources](#useful-resources)

---

## Introduction

This guide will walk you through creating a fully functional Telegram bot using the **Quarkus** framework. Quarkus is a Kubernetes-native Java framework designed for fast startup times and low memory footprint, making it ideal for microservices and cloud-native applications.

We'll cover three different approaches:
- **Apache Camel Quarkus Telegram** - Best for integration-heavy applications
- **TelegramBots Java Library** - Popular community library with rich features
- **Direct REST API** - Lightweight approach using Quarkus REST client

---

## Prerequisites

Before starting, ensure you have:

| Requirement | Version | Description |
|-------------|---------|-------------|
| JDK | 17+ | Java Development Kit |
| Maven | 3.9+ | Build tool |
| Quarkus CLI | Latest | Optional but recommended |
| IDE | Any | IntelliJ IDEA, VS Code, Eclipse |
| Telegram Account | - | To create and test your bot |

### Install Quarkus CLI (Optional)

```bash
# Using SDKMAN
sdk install quarkus

# Using Homebrew (macOS)
brew install quarkusio/tap/quarkus

# Verify installation
quarkus --version
```

---

## Creating Your Telegram Bot

### Step 1: Talk to BotFather

1. Open Telegram and search for `@BotFather`
2. Start a conversation with `/start`
3. Create a new bot with `/newbot`
4. Follow the prompts:
   - Enter a **display name** (e.g., "My Quarkus Bot")
   - Enter a **username** (must end with `bot`, e.g., `my_quarkus_bot`)

### Step 2: Save Your Token

BotFather will provide an **API Token** like:
```
123456789:ABCdefGHIjklMNOpqrsTUVwxyz
```

> ⚠️ **Important**: Keep this token secure! Never commit it to version control.

### Step 3: Get Your Chat ID (For Testing)

1. Start a conversation with your bot
2. Send any message to your bot
3. Visit: `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates`
4. Find your `chat.id` in the JSON response

---

## Project Setup

### Create a New Quarkus Project

```bash
# Using Quarkus CLI
quarkus create app com.example:telegram-bot \
    --extension='rest,rest-client-reactive-jackson,scheduler' \
    --no-code

cd telegram-bot
```

Or using Maven:

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.17.0:create \
    -DprojectGroupId=com.example \
    -DprojectArtifactId=telegram-bot \
    -Dextensions="rest,rest-client-reactive-jackson,scheduler"

cd telegram-bot
```

### Project Structure

```
telegram-bot/
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── bot/
│   │   │   │   ├── TelegramBotService.java
│   │   │   │   ├── MessageHandler.java
│   │   │   │   └── commands/
│   │   │   ├── client/
│   │   │   │   └── TelegramApiClient.java
│   │   │   └── model/
│   │   │       ├── Update.java
│   │   │       └── Message.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
└── pom.xml
```

---

## Approach 1: Using Apache Camel Quarkus Telegram

This is the recommended approach for enterprise integration scenarios.

### Step 1: Add Dependencies

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- Camel Quarkus Telegram -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-telegram</artifactId>
    </dependency>
    
    <!-- Camel Quarkus Core -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-core</artifactId>
    </dependency>
    
    <!-- Camel Quarkus Direct (for internal routing) -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-direct</artifactId>
    </dependency>
    
    <!-- Camel Quarkus Log -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-log</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Configure Application Properties

Create `src/main/resources/application.properties`:

```properties
# Telegram Bot Configuration
telegram.bot.token=${TELEGRAM_BOT_TOKEN:your-bot-token-here}
telegram.chat.id=${TELEGRAM_CHAT_ID:your-chat-id-here}

# Camel Configuration
camel.component.telegram.authorization-token=${telegram.bot.token}

# Application Configuration
quarkus.application.name=telegram-bot
quarkus.http.port=8080

# Logging
quarkus.log.level=INFO
quarkus.log.category."org.apache.camel".level=DEBUG
```

### Step 3: Create the Camel Route

Create `src/main/java/com/example/bot/TelegramRoute.java`:

```java
package com.example.bot;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class TelegramRoute extends RouteBuilder {

    @ConfigProperty(name = "telegram.bot.token")
    String botToken;

    @ConfigProperty(name = "telegram.chat.id")
    String chatId;

    @Override
    public void configure() throws Exception {
        // Error handling
        onException(Exception.class)
            .log("Error processing message: ${exception.message}")
            .handled(true);

        // Route to receive messages from Telegram
        from("telegram:bots?authorizationToken=" + botToken)
            .routeId("telegram-receiver")
            .log("Received message: ${body}")
            .choice()
                .when(simple("${body} starts with '/start'"))
                    .setBody(constant("👋 Welcome to the Quarkus Telegram Bot!\n\n" +
                        "Available commands:\n" +
                        "/start - Show this message\n" +
                        "/help - Get help\n" +
                        "/echo <text> - Echo your message\n" +
                        "/time - Get current server time"))
                .when(simple("${body} starts with '/help'"))
                    .setBody(constant("🆘 Help Menu:\n\n" +
                        "This bot is built with Quarkus and Apache Camel.\n" +
                        "Use /start to see available commands."))
                .when(simple("${body} starts with '/echo'"))
                    .process(exchange -> {
                        String message = exchange.getIn().getBody(String.class);
                        String echoText = message.length() > 6 
                            ? message.substring(6).trim() 
                            : "Please provide text to echo!";
                        exchange.getIn().setBody("🔊 Echo: " + echoText);
                    })
                .when(simple("${body} starts with '/time'"))
                    .process(exchange -> {
                        String time = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        exchange.getIn().setBody("🕐 Current server time: " + time);
                    })
                .otherwise()
                    .process(exchange -> {
                        String message = exchange.getIn().getBody(String.class);
                        exchange.getIn().setBody("🤔 I don't understand: " + message + 
                            "\n\nTry /help for available commands.");
                    })
            .end()
            .to("telegram:bots?authorizationToken=" + botToken);

        // Route to send notifications (can be triggered from other parts of the app)
        from("direct:sendNotification")
            .routeId("telegram-sender")
            .log("Sending notification: ${body}")
            .to("telegram:bots?authorizationToken=" + botToken + "&chatId=" + chatId);
    }
}
```

### Step 4: Create a Notification Service

Create `src/main/java/com/example/bot/NotificationService.java`:

```java
package com.example.bot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;

@ApplicationScoped
public class NotificationService {

    @Inject
    ProducerTemplate producerTemplate;

    public void sendNotification(String message) {
        producerTemplate.sendBody("direct:sendNotification", message);
    }
}
```

### Step 5: Create a REST Endpoint to Send Messages

Create `src/main/java/com/example/bot/BotResource.java`:

```java
package com.example.bot;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/bot")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BotResource {

    @Inject
    NotificationService notificationService;

    @POST
    @Path("/send")
    public Response sendMessage(MessageRequest request) {
        try {
            notificationService.sendNotification(request.message);
            return Response.ok(new MessageResponse("Message sent successfully!")).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(new MessageResponse("Failed to send: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(new MessageResponse("Bot is running!")).build();
    }

    public static class MessageRequest {
        public String message;
    }

    public static class MessageResponse {
        public String status;
        public MessageResponse(String status) {
            this.status = status;
        }
    }
}
```

---

## Approach 2: Using TelegramBots Library with Quarkus

This approach uses the popular `telegrambots` library with Quarkus.

### Step 1: Add Dependencies

```xml
<dependencies>
    <!-- Quarkus REST -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest</artifactId>
    </dependency>
    
    <!-- Quarkus Scheduler -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-scheduler</artifactId>
    </dependency>

    <!-- TelegramBots Library -->
    <dependency>
        <groupId>org.telegram</groupId>
        <artifactId>telegrambots-longpolling</artifactId>
        <version>8.0.0</version>
    </dependency>
    
    <dependency>
        <groupId>org.telegram</groupId>
        <artifactId>telegrambots-client</artifactId>
        <version>8.0.0</version>
    </dependency>
</dependencies>
```

### Step 2: Create the Bot Implementation

Create `src/main/java/com/example/bot/QuarkusTelegramBot.java`:

```java
package com.example.bot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

@ApplicationScoped
public class QuarkusTelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(QuarkusTelegramBot.class);

    private final TelegramClient telegramClient;
    private final Map<String, BiConsumer<Long, String>> commandHandlers;

    @ConfigProperty(name = "telegram.bot.token")
    String botToken;

    @Inject
    public QuarkusTelegramBot(@ConfigProperty(name = "telegram.bot.token") String botToken) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.commandHandlers = new HashMap<>();
        registerCommands();
    }

    private void registerCommands() {
        commandHandlers.put("/start", this::handleStart);
        commandHandlers.put("/help", this::handleHelp);
        commandHandlers.put("/echo", this::handleEcho);
        commandHandlers.put("/time", this::handleTime);
        commandHandlers.put("/weather", this::handleWeather);
        commandHandlers.put("/quote", this::handleQuote);
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            LOG.info("Received message from @{}: {}", username, messageText);

            processMessage(chatId, messageText);
        }
    }

    private void processMessage(Long chatId, String messageText) {
        String command = messageText.split(" ")[0].toLowerCase();
        
        BiConsumer<Long, String> handler = commandHandlers.get(command);
        if (handler != null) {
            handler.accept(chatId, messageText);
        } else {
            sendMessage(chatId, "❓ Unknown command. Use /help to see available commands.");
        }
    }

    private void handleStart(Long chatId, String message) {
        String welcomeMessage = """
            👋 *Welcome to Quarkus Telegram Bot!*
            
            I'm a bot built with Quarkus framework.
            
            🚀 *Features:*
            • Fast startup time
            • Low memory footprint
            • Cloud-native ready
            
            Use /help to see all available commands.
            """;
        sendMarkdownMessage(chatId, welcomeMessage);
    }

    private void handleHelp(Long chatId, String message) {
        String helpMessage = """
            📚 *Available Commands:*
            
            /start - Welcome message
            /help - Show this help
            /echo <text> - Echo your message
            /time - Current server time
            /weather - Sample weather info
            /quote - Get an inspiring quote
            """;
        sendMarkdownMessage(chatId, helpMessage);
    }

    private void handleEcho(Long chatId, String message) {
        String echoText = message.length() > 6 ? message.substring(6).trim() : "";
        if (echoText.isEmpty()) {
            sendMessage(chatId, "📝 Usage: /echo <your message>");
        } else {
            sendMessage(chatId, "🔊 " + echoText);
        }
    }

    private void handleTime(Long chatId, String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sendMessage(chatId, "🕐 Server time: " + time);
    }

    private void handleWeather(Long chatId, String message) {
        // Sample weather response (in real app, call weather API)
        String weather = """
            🌤️ *Weather Report*
            
            📍 Location: Sample City
            🌡️ Temperature: 22°C
            💧 Humidity: 65%
            💨 Wind: 12 km/h
            
            _This is sample data. Integrate with a real API!_
            """;
        sendMarkdownMessage(chatId, weather);
    }

    private void handleQuote(Long chatId, String message) {
        String[] quotes = {
            "The only way to do great work is to love what you do. - Steve Jobs",
            "Innovation distinguishes between a leader and a follower. - Steve Jobs",
            "Stay hungry, stay foolish. - Steve Jobs",
            "Code is like humor. When you have to explain it, it's bad. - Cory House",
            "First, solve the problem. Then, write the code. - John Johnson"
        };
        String randomQuote = quotes[(int) (Math.random() * quotes.length)];
        sendMessage(chatId, "💡 " + randomQuote);
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send message: {}", e.getMessage());
        }
    }

    public void sendMarkdownMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .parseMode("Markdown")
            .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send markdown message: {}", e.getMessage());
        }
    }
}
```

### Step 3: Create Bot Startup Service

Create `src/main/java/com/example/bot/BotStartupService.java`:

```java
package com.example.bot;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@ApplicationScoped
public class BotStartupService {

    private static final Logger LOG = LoggerFactory.getLogger(BotStartupService.class);

    @Inject
    QuarkusTelegramBot bot;

    @ConfigProperty(name = "telegram.bot.token")
    String botToken;

    private TelegramBotsLongPollingApplication botsApplication;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Starting Telegram Bot...");
        try {
            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, bot);
            LOG.info("Telegram Bot started successfully!");
        } catch (Exception e) {
            LOG.error("Failed to start Telegram Bot: {}", e.getMessage(), e);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        LOG.info("Stopping Telegram Bot...");
        if (botsApplication != null) {
            try {
                botsApplication.close();
                LOG.info("Telegram Bot stopped.");
            } catch (Exception e) {
                LOG.error("Error stopping bot: {}", e.getMessage());
            }
        }
    }
}
```

---

## Approach 3: Direct REST API Integration

This lightweight approach uses Quarkus REST client to interact directly with Telegram API.

### Step 1: Add Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest-client-reactive-jackson</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest-jackson</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-scheduler</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Create DTOs

Create `src/main/java/com/example/model/TelegramModels.java`:

```java
package com.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TelegramModels {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateResponse {
        public boolean ok;
        public List<Update> result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Update {
        @JsonProperty("update_id")
        public Long updateId;
        public Message message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("message_id")
        public Long messageId;
        public Chat chat;
        public User from;
        public String text;
        public Long date;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chat {
        public Long id;
        public String type;
        @JsonProperty("first_name")
        public String firstName;
        public String username;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        public Long id;
        @JsonProperty("first_name")
        public String firstName;
        public String username;
        @JsonProperty("is_bot")
        public boolean isBot;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SendMessageRequest {
        @JsonProperty("chat_id")
        public Long chatId;
        public String text;
        @JsonProperty("parse_mode")
        public String parseMode;

        public SendMessageRequest() {}

        public SendMessageRequest(Long chatId, String text) {
            this.chatId = chatId;
            this.text = text;
        }

        public SendMessageRequest(Long chatId, String text, String parseMode) {
            this.chatId = chatId;
            this.text = text;
            this.parseMode = parseMode;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse {
        public boolean ok;
        public Object result;
        public String description;
    }
}
```

### Step 3: Create REST Client Interface

Create `src/main/java/com/example/client/TelegramApiClient.java`:

```java
package com.example.client;

import com.example.model.TelegramModels.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/bot{token}")
@RegisterRestClient(configKey = "telegram-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TelegramApiClient {

    @GET
    @Path("/getUpdates")
    UpdateResponse getUpdates(
        @PathParam("token") String token,
        @QueryParam("offset") Long offset,
        @QueryParam("timeout") Integer timeout
    );

    @POST
    @Path("/sendMessage")
    ApiResponse sendMessage(
        @PathParam("token") String token,
        SendMessageRequest request
    );

    @GET
    @Path("/getMe")
    ApiResponse getMe(@PathParam("token") String token);
}
```

### Step 4: Create Bot Service

Create `src/main/java/com/example/bot/DirectApiBotService.java`:

```java
package com.example.bot;

import com.example.client.TelegramApiClient;
import com.example.model.TelegramModels.*;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class DirectApiBotService {

    private static final Logger LOG = LoggerFactory.getLogger(DirectApiBotService.class);

    @Inject
    @RestClient
    TelegramApiClient telegramApi;

    @ConfigProperty(name = "telegram.bot.token")
    String botToken;

    private final AtomicLong lastUpdateId = new AtomicLong(0);

    @Scheduled(every = "2s")
    void pollForUpdates() {
        try {
            Long offset = lastUpdateId.get() > 0 ? lastUpdateId.get() + 1 : null;
            UpdateResponse response = telegramApi.getUpdates(botToken, offset, 30);

            if (response.ok && response.result != null) {
                for (Update update : response.result) {
                    processUpdate(update);
                    lastUpdateId.set(update.updateId);
                }
            }
        } catch (Exception e) {
            LOG.error("Error polling updates: {}", e.getMessage());
        }
    }

    private void processUpdate(Update update) {
        if (update.message != null && update.message.text != null) {
            Long chatId = update.message.chat.id;
            String text = update.message.text;
            String username = update.message.from != null ? update.message.from.username : "unknown";

            LOG.info("Message from @{}: {}", username, text);

            String response = generateResponse(text);
            sendMessage(chatId, response);
        }
    }

    private String generateResponse(String text) {
        String command = text.split(" ")[0].toLowerCase();

        return switch (command) {
            case "/start" -> """
                👋 Welcome to Direct API Bot!
                
                This bot uses Quarkus REST Client to communicate with Telegram API.
                
                Commands: /help, /time, /echo <text>
                """;
            case "/help" -> """
                📚 Available Commands:
                /start - Welcome message
                /help - This help
                /time - Server time
                /echo <text> - Echo message
                """;
            case "/time" -> "🕐 Time: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            case "/echo" -> {
                String echo = text.length() > 6 ? text.substring(6).trim() : "";
                yield echo.isEmpty() ? "Usage: /echo <text>" : "🔊 " + echo;
            }
            default -> "❓ Unknown command. Try /help";
        };
    }

    public void sendMessage(Long chatId, String text) {
        try {
            SendMessageRequest request = new SendMessageRequest(chatId, text);
            ApiResponse response = telegramApi.sendMessage(botToken, request);
            if (!response.ok) {
                LOG.error("Failed to send message: {}", response.description);
            }
        } catch (Exception e) {
            LOG.error("Error sending message: {}", e.getMessage());
        }
    }

    public void sendNotification(Long chatId, String message) {
        sendMessage(chatId, "📢 " + message);
    }
}
```

### Step 5: Configure REST Client

Add to `application.properties`:

```properties
# Telegram Configuration
telegram.bot.token=${TELEGRAM_BOT_TOKEN}

# REST Client Configuration
quarkus.rest-client.telegram-api.url=https://api.telegram.org
quarkus.rest-client.telegram-api.scope=jakarta.inject.Singleton

# Timeouts
quarkus.rest-client.telegram-api.connect-timeout=5000
quarkus.rest-client.telegram-api.read-timeout=35000
```

---

## Advanced Features

### Inline Keyboards

```java
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

public void sendInlineKeyboard(Long chatId) {
    InlineKeyboardButton button1 = InlineKeyboardButton.builder()
        .text("Option 1")
        .callbackData("option_1")
        .build();
    
    InlineKeyboardButton button2 = InlineKeyboardButton.builder()
        .text("Option 2")
        .callbackData("option_2")
        .build();
    
    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboardRow(new InlineKeyboardRow(button1, button2))
        .build();
    
    SendMessage message = SendMessage.builder()
        .chatId(chatId.toString())
        .text("Choose an option:")
        .replyMarkup(keyboard)
        .build();
    
    telegramClient.execute(message);
}
```

### Scheduled Notifications

```java
@ApplicationScoped
public class ScheduledNotifications {

    @Inject
    QuarkusTelegramBot bot;

    @ConfigProperty(name = "telegram.chat.id")
    Long adminChatId;

    @Scheduled(cron = "0 0 9 * * ?") // Every day at 9 AM
    void dailyReminder() {
        bot.sendMessage(adminChatId, "☀️ Good morning! Daily reminder from your Quarkus bot.");
    }

    @Scheduled(every = "1h")
    void hourlyHealthCheck() {
        // Perform health check
        bot.sendMessage(adminChatId, "✅ Hourly health check: Bot is running!");
    }
}
```

### Webhook Configuration (Production)

For production, use webhooks instead of long polling:

```java
@Path("/webhook")
@ApplicationScoped
public class TelegramWebhook {

    @Inject
    QuarkusTelegramBot bot;

    @POST
    @Path("/telegram")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleUpdate(Update update) {
        bot.consume(update);
        return Response.ok().build();
    }
}
```

Set webhook via API:
```bash
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
     -d "url=https://your-domain.com/webhook/telegram"
```

---

## Testing Your Bot

### Unit Testing

```java
@QuarkusTest
public class BotServiceTest {

    @Inject
    DirectApiBotService botService;

    @Test
    void testGenerateResponse() {
        // Use reflection or make method package-private for testing
        String response = botService.generateResponse("/start");
        assertTrue(response.contains("Welcome"));
    }

    @Test
    void testEchoCommand() {
        String response = botService.generateResponse("/echo Hello World");
        assertEquals("🔊 Hello World", response);
    }
}
```

### Integration Testing with WireMock

```java
@QuarkusTest
@QuarkusTestResource(WireMockTelegramResource.class)
public class TelegramIntegrationTest {

    @InjectWireMock
    WireMockServer wireMock;

    @Test
    void testSendMessage() {
        wireMock.stubFor(post(urlPathMatching("/bot.*/sendMessage"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"ok\":true,\"result\":{}}")));

        // Test your bot service
    }
}
```

---

## Deployment

### Build Native Executable

```bash
# Build native executable
./mvnw package -Pnative

# Run native executable
./target/telegram-bot-1.0.0-SNAPSHOT-runner
```

### Docker Deployment

Create `Dockerfile.jvm`:

```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17:1.18

ENV LANGUAGE='en_US:en'

COPY target/quarkus-app/lib/ /deployments/lib/
COPY target/quarkus-app/*.jar /deployments/
COPY target/quarkus-app/app/ /deployments/app/
COPY target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
```

Build and run:

```bash
./mvnw package
docker build -f Dockerfile.jvm -t telegram-bot .
docker run -e TELEGRAM_BOT_TOKEN=your-token -p 8080:8080 telegram-bot
```

### Kubernetes Deployment

Create `kubernetes.yml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: telegram-bot
spec:
  replicas: 1
  selector:
    matchLabels:
      app: telegram-bot
  template:
    metadata:
      labels:
        app: telegram-bot
    spec:
      containers:
        - name: telegram-bot
          image: your-registry/telegram-bot:latest
          ports:
            - containerPort: 8080
          env:
            - name: TELEGRAM_BOT_TOKEN
              valueFrom:
                secretKeyRef:
                  name: telegram-secrets
                  key: bot-token
          resources:
            limits:
              memory: "256Mi"
              cpu: "500m"
---
apiVersion: v1
kind: Secret
metadata:
  name: telegram-secrets
type: Opaque
stringData:
  bot-token: "your-bot-token-here"
```

---

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| Bot not responding | Check token is correct and bot is started |
| Connection timeout | Increase REST client timeout settings |
| Duplicate messages | Ensure proper offset handling in getUpdates |
| Native build fails | Check GraalVM configuration for reflection |

### Logging Configuration

```properties
quarkus.log.level=INFO
quarkus.log.category."com.example".level=DEBUG
quarkus.log.category."org.telegram".level=DEBUG
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] %s%e%n
```

---

## Useful Resources

### Official Documentation
- [Quarkus Guides](https://quarkus.io/guides/)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Apache Camel Telegram](https://camel.apache.org/components/latest/telegram-component.html)
- [Camel Quarkus Extensions](https://camel.apache.org/camel-quarkus/latest/)

### Libraries
- [TelegramBots Java Library](https://github.com/rubenlagus/TelegramBots)
- [Quarkus REST Client](https://quarkus.io/guides/rest-client-reactive)
- [Quarkus Scheduler](https://quarkus.io/guides/scheduler)

### Tutorials & Examples
- [Quarkus Getting Started](https://quarkus.io/get-started/)
- [Camel Quarkus Examples](https://github.com/apache/camel-quarkus-examples)

---

## Summary

This guide covered three approaches to building Telegram bots with Quarkus:

1. **Apache Camel Quarkus** - Best for complex integrations with routing capabilities
2. **TelegramBots Library** - Feature-rich with extensive Telegram API support
3. **Direct REST API** - Lightweight and full control over API calls

Choose the approach that best fits your use case:
- Use **Camel** for enterprise integration patterns
- Use **TelegramBots** for feature-rich bots with less boilerplate
- Use **Direct API** for lightweight bots or learning purposes

Happy coding! 🚀
