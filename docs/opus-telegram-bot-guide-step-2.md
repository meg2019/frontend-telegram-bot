# 📖 Opus Telegram Bot Guide — Step 2: Inline Keyboard & Callback Queries

> **Goal**: Build a Telegram bot using **Quarkus** and **Apache Camel** that responds to the `/start`
> command with an inline keyboard whose button labels come from a `List<String>`, and then displays a
> menu showing the pressed button text when a user taps any button.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Project Setup](#2-project-setup)
3. [Application Configuration](#3-application-configuration)
4. [Understanding the Flow](#4-understanding-the-flow)
5. [Step 1 — Define the Topics List](#5-step-1--define-the-topics-list)
6. [Step 2 — Create the Inline Keyboard Builder](#6-step-2--create-the-inline-keyboard-builder)
7. [Step 3 — Handle the `/start` Command](#7-step-3--handle-the-start-command)
8. [Step 4 — Handle Callback Queries (Button Press)](#8-step-4--handle-callback-queries-button-press)
9. [Step 5 — Build the Camel Route](#9-step-5--build-the-camel-route)
10. [Full Source Code](#10-full-source-code)
11. [Running the Application](#11-running-the-application)
12. [Testing the Bot](#12-testing-the-bot)
13. [Summary](#13-summary)

---

## 1. Prerequisites

Before you begin, ensure you have:

| Requirement              | Version / Details                                                                      |
|--------------------------|----------------------------------------------------------------------------------------|
| **JDK**                  | 17 or later                                                                            |
| **Maven**                | 3.9+                                                                                   |
| **Quarkus**              | 3.x (latest stable)                                                                    |
| **Telegram Bot Token**   | Obtained from [@BotFather](https://t.me/BotFather)                                     |
| **Telegram Chat ID**     | Your personal chat ID (get it from [@userinfobot](https://t.me/userinfobot))           |
| **IDE**                  | IntelliJ IDEA, VS Code, or any Java IDE                                                |

### Creating a Bot with BotFather

If you haven't yet created your Telegram bot:

1. Open Telegram and search for **@BotFather**.
2. Send `/newbot` and follow the prompts to choose a name and username.
3. Copy the **authorization token** — you'll need it in the configuration step.

---

## 2. Project Setup

### 2.1 Generate the Quarkus Project

Use the Quarkus CLI or Maven plugin to create a new project:

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.17.8:create \
    -DprojectGroupId=com.opus \
    -DprojectArtifactId=opus-telegram-bot \
    -Dextensions="camel-quarkus-telegram" \
    -DnoCode
```

> **Tip:** The `camel-quarkus-telegram` extension automatically pulls in the Apache Camel Telegram
> component with all necessary dependencies.

### 2.2 Verify the Dependency in `pom.xml`

After project generation, confirm that your `pom.xml` contains:

```xml
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-telegram</artifactId>
</dependency>
```

### 2.3 Additional Dependencies (if needed)

If your project doesn't already include the Camel core CDI support, add:

```xml
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-core</artifactId>
</dependency>
```

Your final `pom.xml` dependencies section should look similar to:

```xml
<dependencies>
    <!-- Quarkus & Camel -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-telegram</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-core</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 3. Application Configuration

### 3.1 Configure `application.properties`

Open `src/main/resources/application.properties` and add your Telegram bot token:

```properties
# Telegram Bot Configuration
telegram.authorization-token=YOUR_BOT_TOKEN_HERE

# Camel Telegram Component Configuration
camel.component.telegram.authorization-token=${telegram.authorization-token}
```

> ⚠️ **Security Warning**: Never commit your actual bot token to version control. Use environment
> variables or Quarkus config profiles for sensitive values.

### 3.2 Using Environment Variables (Recommended)

Instead of hardcoding the token, set it via an environment variable:

```bash
export TELEGRAM_AUTHORIZATION_TOKEN=123456789:AABBccDDeeFFggHHiiJJkkLLmmNNooPPqqR
```

Then reference it in `application.properties`:

```properties
telegram.authorization-token=${TELEGRAM_AUTHORIZATION_TOKEN}
camel.component.telegram.authorization-token=${telegram.authorization-token}
```

---

## 4. Understanding the Flow

Here's the complete interaction flow we're building:

```
┌──────────────────────────────────────────────────────────────┐
│                        USER                                  │
│                                                              │
│  1. Sends "/start"                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌─────────────────────────────────────────────────────┐     │
│  │              BOT RECEIVES MESSAGE                   │     │
│  │  - Checks if text equals "/start"                   │     │
│  │  - Builds OutgoingTextMessage with:                 │     │
│  │    • Text: "📚 *Choose a Topic:*..."                │     │
│  │    • ParseMode: Markdown                            │     │
│  │    • InlineKeyboardMarkup from List<String>         │     │
│  └─────────────────────────────────────────────────────┘     │
│         │                                                    │
│         ▼                                                    │
│  2. Bot displays message with inline keyboard buttons        │
│     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │
│     │  Topic 1    │ │  Topic 2    │ │  Topic 3    │        │
│     └─────────────┘ └─────────────┘ └─────────────┘        │
│                                                              │
│  3. User presses a button (e.g., "Topic 2")                 │
│         │                                                    │
│         ▼                                                    │
│  ┌─────────────────────────────────────────────────────┐     │
│  │           BOT RECEIVES CALLBACK QUERY               │     │
│  │  - Extracts callback data (pressed button text)     │     │
│  │  - Sends new message: "📋 Menu for: Topic 2"       │     │
│  └─────────────────────────────────────────────────────┘     │
│         │                                                    │
│         ▼                                                    │
│  4. Bot shows menu with the pressed button text              │
└──────────────────────────────────────────────────────────────┘
```

---

## 5. Step 1 — Define the Topics List

Create a configuration class that provides the list of topics. These topics will become the text
labels on the inline keyboard buttons.

### File: `src/main/java/com/opus/config/BotConfig.java`

```java
package com.opus.config;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
public class BotConfig {

    /**
     * Produces a list of topic names that will be displayed
     * as InlineKeyboardButton labels in the Telegram bot.
     *
     * You can modify this list to add/remove topics dynamically.
     */
    @Produces
    @Named("topicList")
    public List<String> topicList() {
        return List.of(
                "☕ Java Core",
                "🚀 Quarkus Framework",
                "🐪 Apache Camel",
                "🗄️ Databases & JPA",
                "🧪 Testing",
                "📦 Microservices"
        );
    }
}
```

> **Key Concept**: We use CDI `@Produces` and `@Named` to make the topic list injectable
> anywhere in the application. This keeps the configuration centralized and easy to modify.

---

## 6. Step 2 — Create the Inline Keyboard Builder

Create a utility service that converts a `List<String>` into a Telegram `InlineKeyboardMarkup`.

### File: `src/main/java/com/opus/service/KeyboardService.java`

```java
package com.opus.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;

@ApplicationScoped
public class KeyboardService {

    @Inject
    @Named("topicList")
    List<String> topics;

    /**
     * Builds an InlineKeyboardMarkup from the topics list.
     *
     * Each topic becomes a button on its own row.
     * The button text is the topic name, and the callback data
     * is also set to the topic name so we can identify which
     * button was pressed in the callback handler.
     *
     * @return InlineKeyboardMarkup ready to attach to an OutgoingTextMessage
     */
    public InlineKeyboardMarkup buildTopicsKeyboard() {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();

        for (String topic : topics) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(topic)
                    .callbackData(topic)
                    .build();
            // Each button on its own row for better readability
            rows.add(new InlineKeyboardButton[]{button});
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setInlineKeyboard(rows);
        return markup;
    }

    /**
     * Gets the topic list for reference.
     */
    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }
}
```

### Understanding `InlineKeyboardButton`

The `InlineKeyboardButton` is a key class from the Apache Camel Telegram component:

| Property         | Type     | Description                                                       |
|------------------|----------|-------------------------------------------------------------------|
| `text`           | `String` | Label text displayed on the button                                |
| `callbackData`   | `String` | Data sent back to the bot when the button is pressed              |
| `url`            | `String` | Optional URL to open when button is pressed                       |

> **Important**: When a user presses an inline keyboard button, Telegram sends a **CallbackQuery**
> back to the bot containing the `callbackData` value. This is how we know which button was pressed.

### Understanding `InlineKeyboardMarkup`

`InlineKeyboardMarkup` represents the entire inline keyboard attached to a message:

- It contains a `List<InlineKeyboardButton[]>` where each array represents **one row** of buttons.
- Each row can have multiple buttons side by side.
- In our case, we put one button per row for a clean vertical list.

---

## 7. Step 3 — Handle the `/start` Command

Create a processor that handles the `/start` command and sends back an `OutgoingTextMessage`
with the inline keyboard.

### File: `src/main/java/com/opus/processor/StartCommandProcessor.java`

```java
package com.opus.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import com.opus.service.KeyboardService;

@ApplicationScoped
public class StartCommandProcessor implements Processor {

    @Inject
    KeyboardService keyboardService;

    /**
     * Processes the /start command by sending an OutgoingTextMessage
     * with Markdown-formatted text and an inline keyboard.
     *
     * The OutgoingTextMessage is set as the exchange body,
     * which Apache Camel's Telegram component will then send
     * to the user's chat.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        OutgoingTextMessage message = new OutgoingTextMessage();

        // Set the message text with Markdown formatting
        message.setText("📚 *Choose a Topic:*\n\nSelect one of the topics below:");

        // Set parse mode to Markdown so Telegram renders *bold* etc.
        message.setParseMode("Markdown");

        // Attach the inline keyboard built from our List<String>
        message.setReplyMarkup(keyboardService.buildTopicsKeyboard());

        // Set the message as the exchange body
        exchange.getIn().setBody(message);
    }
}
```

### Understanding `OutgoingTextMessage`

`OutgoingTextMessage` is the Apache Camel model class for sending text messages via Telegram:

| Method               | Description                                                        |
|----------------------|--------------------------------------------------------------------|
| `setText(String)`    | Sets the message text content                                      |
| `setParseMode(String)` | Sets parsing mode: `"Markdown"`, `"MarkdownV2"`, or `"HTML"`   |
| `setReplyMarkup(...)` | Attaches a keyboard (inline or reply) to the message              |

> **Parse Modes**:
> - `Markdown` — supports `*bold*`, `_italic_`, `` `code` ``, `[link](url)`
> - `MarkdownV2` — extended Markdown with more escape rules
> - `HTML` — supports `<b>`, `<i>`, `<code>`, `<a href="">` tags

---

## 8. Step 4 — Handle Callback Queries (Button Press)

When a user presses an inline keyboard button, Telegram sends a **CallbackQuery** to the bot.
We need to process it and display a menu with the pressed button text.

### File: `src/main/java/com/opus/processor/CallbackQueryProcessor.java`

```java
package com.opus.processor;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

@ApplicationScoped
public class CallbackQueryProcessor implements Processor {

    /**
     * Processes a callback query triggered by an inline keyboard button press.
     *
     * Extracts the callback data (which contains the topic name)
     * and sends back a menu message displaying the selected topic.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // The callback data is available via the CamelTelegramCallbackQueryData header
        // It contains the value we set in InlineKeyboardButton.callbackData
        String callbackData = exchange.getIn()
                .getHeader("CamelTelegramCallbackQueryData", String.class);

        // Build the menu message showing the selected topic
        OutgoingTextMessage menuMessage = new OutgoingTextMessage();
        menuMessage.setText(buildMenuText(callbackData));
        menuMessage.setParseMode("Markdown");

        exchange.getIn().setBody(menuMessage);
    }

    /**
     * Builds a formatted menu text for the selected topic.
     *
     * @param topicName the name of the selected topic
     * @return formatted menu string
     */
    private String buildMenuText(String topicName) {
        return String.format(
                """
                📋 *Menu for:* %s
                
                ━━━━━━━━━━━━━━━━━━━━
                
                You selected the topic:
                👉 *%s*
                
                _Here you can add topic-specific options,_
                _lessons, quizzes, or other interactions._
                
                ━━━━━━━━━━━━━━━━━━━━
                
                Send /start to return to the main menu.
                """,
                topicName, topicName
        );
    }
}
```

### How Callback Queries Work in Apache Camel

When a user presses an inline keyboard button:

1. **Telegram** sends a `CallbackQuery` object to your bot. It contains:
   - `id` — Unique identifier for the callback query
   - `from` — User who pressed the button
   - `data` — The `callbackData` value from the pressed `InlineKeyboardButton`
   - `message` — The original message containing the keyboard

2. **Apache Camel** receives this and sets special **headers** on the exchange:
   - `CamelTelegramCallbackQueryData` — contains the callback `data` string
   - `CamelTelegramCallbackQueryId` — contains the callback query `id`

3. **Your processor** reads these headers to determine what button was pressed and responds
   accordingly.

> **Note from Telegram API**: After receiving a callback query, the bot should call
> `answerCallbackQuery` to dismiss the loading indicator on the client. Apache Camel handles this
> automatically when you send a response.

---

## 9. Step 5 — Build the Camel Route

Now wire everything together in a Camel `RouteBuilder`. This is where we define the message flow.

### File: `src/main/java/com/opus/route/TelegramBotRoute.java`

```java
package com.opus.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.builder.RouteBuilder;

import com.opus.processor.CallbackQueryProcessor;
import com.opus.processor.StartCommandProcessor;

@ApplicationScoped
public class TelegramBotRoute extends RouteBuilder {

    @Inject
    StartCommandProcessor startCommandProcessor;

    @Inject
    CallbackQueryProcessor callbackQueryProcessor;

    @Override
    public void configure() throws Exception {

        // ──────────────────────────────────────────────
        // Route 1: Receive messages from Telegram
        // ──────────────────────────────────────────────
        from("telegram:bots")
                .routeId("telegram-incoming")
                .log("Received message: ${body}")
                .choice()
                    // Check if the incoming message text is "/start"
                    .when(simple("${body} == '/start'"))
                        .log("Processing /start command")
                        .process(startCommandProcessor)
                        .to("telegram:bots")

                    // Check if this is a callback query (button press)
                    .when(header("CamelTelegramCallbackQueryData").isNotNull())
                        .log("Processing callback query: ${header.CamelTelegramCallbackQueryData}")
                        .process(callbackQueryProcessor)
                        .to("telegram:bots")

                    // Handle any other message
                    .otherwise()
                        .log("Unknown command received: ${body}")
                        .setBody(simple("❓ Unknown command. Send /start to begin."))
                        .to("telegram:bots")
                .end();
    }
}
```

### Route Breakdown

Let's analyze each part of the route:

#### 1. The `from("telegram:bots")` Consumer

```java
from("telegram:bots")
```

- This creates a **Telegram consumer** that polls for incoming messages.
- The `telegram:bots` URI uses the token from `camel.component.telegram.authorization-token`.
- It receives all types of updates: text messages, callback queries, etc.

#### 2. The Content-Based Router (`choice()`)

```java
.choice()
    .when(simple("${body} == '/start'"))
        // handle /start
    .when(header("CamelTelegramCallbackQueryData").isNotNull())
        // handle callback queries
    .otherwise()
        // handle unknown messages
.end();
```

- **`choice()`** — Implements the Content-Based Router Enterprise Integration Pattern (EIP).
- **`when(simple("${body} == '/start'"))`** — Matches when the message body is exactly `/start`.
- **`when(header("CamelTelegramCallbackQueryData").isNotNull())`** — Matches when a callback
  query is received (the header exists).
- **`otherwise()`** — Catches all other messages.

#### 3. The `to("telegram:bots")` Producer

```java
.to("telegram:bots")
```

- Sends the exchange body (our `OutgoingTextMessage`) back to the user via Telegram.
- Automatically sends to the correct chat based on the incoming message's chat ID.

---

## 10. Full Source Code

### Project Structure

```
opus-telegram-bot/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── opus/
        │           ├── config/
        │           │   └── BotConfig.java
        │           ├── processor/
        │           │   ├── StartCommandProcessor.java
        │           │   └── CallbackQueryProcessor.java
        │           ├── route/
        │           │   └── TelegramBotRoute.java
        │           └── service/
        │               └── KeyboardService.java
        └── resources/
            └── application.properties
```

### Complete `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.opus</groupId>
    <artifactId>opus-telegram-bot</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <quarkus.platform.version>3.17.8</quarkus.platform.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-camel-bom</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.apache.camel.quarkus</groupId>
            <artifactId>camel-quarkus-telegram</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.camel.quarkus</groupId>
            <artifactId>camel-quarkus-core</artifactId>
        </dependency>
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
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Complete `application.properties`

```properties
# ================================
# Telegram Bot Configuration
# ================================
telegram.authorization-token=${TELEGRAM_AUTHORIZATION_TOKEN:your-bot-token-here}

# Camel Telegram Component
camel.component.telegram.authorization-token=${telegram.authorization-token}

# ================================
# Quarkus Configuration
# ================================
quarkus.log.category."org.apache.camel".level=INFO
quarkus.log.category."com.opus".level=DEBUG
```

### Complete `BotConfig.java`

```java
package com.opus.config;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
public class BotConfig {

    @Produces
    @Named("topicList")
    public List<String> topicList() {
        return List.of(
                "☕ Java Core",
                "🚀 Quarkus Framework",
                "🐪 Apache Camel",
                "🗄️ Databases & JPA",
                "🧪 Testing",
                "📦 Microservices"
        );
    }
}
```

### Complete `KeyboardService.java`

```java
package com.opus.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;

@ApplicationScoped
public class KeyboardService {

    @Inject
    @Named("topicList")
    List<String> topics;

    public InlineKeyboardMarkup buildTopicsKeyboard() {
        List<InlineKeyboardButton[]> rows = new ArrayList<>();

        for (String topic : topics) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(topic)
                    .callbackData(topic)
                    .build();
            rows.add(new InlineKeyboardButton[]{button});
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setInlineKeyboard(rows);
        return markup;
    }

    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }
}
```

### Complete `StartCommandProcessor.java`

```java
package com.opus.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import com.opus.service.KeyboardService;

@ApplicationScoped
public class StartCommandProcessor implements Processor {

    @Inject
    KeyboardService keyboardService;

    @Override
    public void process(Exchange exchange) throws Exception {
        OutgoingTextMessage message = new OutgoingTextMessage();
        message.setText("📚 *Choose a Topic:*\n\nSelect one of the topics below:");
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboardService.buildTopicsKeyboard());
        exchange.getIn().setBody(message);
    }
}
```

### Complete `CallbackQueryProcessor.java`

```java
package com.opus.processor;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

@ApplicationScoped
public class CallbackQueryProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String callbackData = exchange.getIn()
                .getHeader("CamelTelegramCallbackQueryData", String.class);

        OutgoingTextMessage menuMessage = new OutgoingTextMessage();
        menuMessage.setText(buildMenuText(callbackData));
        menuMessage.setParseMode("Markdown");

        exchange.getIn().setBody(menuMessage);
    }

    private String buildMenuText(String topicName) {
        return String.format(
                """
                📋 *Menu for:* %s
                
                ━━━━━━━━━━━━━━━━━━━━
                
                You selected the topic:
                👉 *%s*
                
                _Here you can add topic-specific options,_
                _lessons, quizzes, or other interactions._
                
                ━━━━━━━━━━━━━━━━━━━━
                
                Send /start to return to the main menu.
                """,
                topicName, topicName
        );
    }
}
```

### Complete `TelegramBotRoute.java`

```java
package com.opus.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.builder.RouteBuilder;

import com.opus.processor.CallbackQueryProcessor;
import com.opus.processor.StartCommandProcessor;

@ApplicationScoped
public class TelegramBotRoute extends RouteBuilder {

    @Inject
    StartCommandProcessor startCommandProcessor;

    @Inject
    CallbackQueryProcessor callbackQueryProcessor;

    @Override
    public void configure() throws Exception {

        from("telegram:bots")
                .routeId("telegram-incoming")
                .log("Received message: ${body}")
                .choice()
                    .when(simple("${body} == '/start'"))
                        .log("Processing /start command")
                        .process(startCommandProcessor)
                        .to("telegram:bots")
                    .when(header("CamelTelegramCallbackQueryData").isNotNull())
                        .log("Processing callback query: ${header.CamelTelegramCallbackQueryData}")
                        .process(callbackQueryProcessor)
                        .to("telegram:bots")
                    .otherwise()
                        .log("Unknown command received: ${body}")
                        .setBody(simple("❓ Unknown command. Send /start to begin."))
                        .to("telegram:bots")
                .end();
    }
}
```

---

## 11. Running the Application

### Development Mode

Start the application in Quarkus dev mode for live reload:

```bash
# Set your bot token
export TELEGRAM_AUTHORIZATION_TOKEN=123456789:AABBccDDeeFFggHHiiJJkkLLmmNNooPPqqR

# Run in dev mode
./mvnw quarkus:dev
```

You should see output similar to:

```
__  ____  __  _____   ___  __ ____  ______
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/
2026-02-13 19:15:00,000 INFO  [org.apa.cam.imp.eng.AbstractCamelContext] (main)
    Routes total: 1 started: 1
2026-02-13 19:15:00,001 INFO  [io.quarkus] (main)
    opus-telegram-bot 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.17.8) started
```

### Production Mode

To build and run a production JAR:

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native Build (Optional)

For a GraalVM native executable:

```bash
./mvnw package -Dnative
./target/opus-telegram-bot-1.0.0-SNAPSHOT-runner
```

---

## 12. Testing the Bot

### Manual Testing Steps

1. **Open Telegram** and find your bot by username.

2. **Send `/start`** to the bot:
   - ✅ You should see the message: **📚 *Choose a Topic:*** followed by "Select one of the topics below:"
   - ✅ Below the message, an **inline keyboard** appears with buttons:
     - ☕ Java Core
     - 🚀 Quarkus Framework
     - 🐪 Apache Camel
     - 🗄️ Databases & JPA
     - 🧪 Testing
     - 📦 Microservices

3. **Press any button** (e.g., "🚀 Quarkus Framework"):
   - ✅ The bot responds with: **📋 *Menu for:* 🚀 Quarkus Framework**
   - ✅ Shows the selected topic details
   - ✅ Includes instruction to send `/start` to return

4. **Send `/start` again**:
   - ✅ The topic selection keyboard appears again

5. **Send any other text** (e.g., "hello"):
   - ✅ Bot responds with: "❓ Unknown command. Send /start to begin."

### Verifying in Quarkus Dev Console

While running in dev mode, you can check:

- **Camel Routes**: Visit `http://localhost:8080/q/dev-ui` → Camel → Routes
- **Logs**: Check the terminal for routing logs showing message processing

---

## 13. Summary

In this guide, you've learned how to:

| Step | What You Built                         | Key Concepts                                              |
|------|----------------------------------------|-----------------------------------------------------------|
| 1    | Topics configuration                  | CDI `@Produces`, `@Named`, `List<String>`                 |
| 2    | Inline keyboard builder               | `InlineKeyboardButton`, `InlineKeyboardMarkup`            |
| 3    | `/start` command handler              | `OutgoingTextMessage`, `parseMode`, `setReplyMarkup()`    |
| 4    | Callback query handler                | `CamelTelegramCallbackQueryData` header, callback routing |
| 5    | Camel route with content-based router | `choice()`, `when()`, `otherwise()`, EIP patterns         |

### Key Takeaways

- **InlineKeyboardButton** vs **ReplyKeyboardButton**: Inline keyboards are attached to messages
  and send callback queries; reply keyboards replace the device keyboard and send regular messages.
- **Callback Data Flow**: `InlineKeyboardButton.callbackData` → Telegram → `CamelTelegramCallbackQueryData` header.
- **OutgoingTextMessage** is the primary model class for sending formatted text messages with
  keyboards via Apache Camel's Telegram component.
- **Content-Based Router** (`choice()`) is a fundamental EIP that routes messages based on their
  content — here we use it to distinguish between `/start` commands, callback queries, and other
  inputs.

### Next Steps

- Add sub-menus for each topic with more inline keyboard interactions
- Implement a "Back" button to navigate between menus
- Store user progress in a database using Quarkus Panache
- Add quiz functionality for each topic
- Implement webhook mode instead of polling for production deployments

---

> 📌 **Reference**: This guide uses the
> [Apache Camel Telegram Component](https://camel.apache.org/components/latest/telegram-component.html)
> and the [Camel Quarkus Telegram Extension](https://camel.apache.org/camel-quarkus/latest/reference/extensions/telegram.html)
> documentation as primary sources.
