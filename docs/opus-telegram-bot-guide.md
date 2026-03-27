# 🤖 Building a Telegram Bot with Quarkus & Apache Camel

> A comprehensive step-by-step guide for creating a **Word Telegram Bot** that displays a main menu,
> shows a one-time keyboard with topic names from a reactive `Multi<String>` source,
> echoes the user's selection, and loops back to the menu.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Create the BotFather Telegram Bot](#2-create-the-botfather-telegram-bot)
3. [Scaffold the Quarkus Project](#3-scaffold-the-quarkus-project)
4. [Project Structure Overview](#4-project-structure-overview)
5. [Configure Application Properties](#5-configure-application-properties)
6. [Create the Topic Service (Reactive Data Source)](#6-create-the-topic-service-reactive-data-source)
7. [Create the Menu Service](#7-create-the-menu-service)
8. [Create the Keyboard Builder Service](#8-create-the-keyboard-builder-service)
9. [Create the Telegram Bot Route (Apache Camel)](#9-create-the-telegram-bot-route-apache-camel)
10. [Run and Test the Bot](#10-run-and-test-the-bot)
11. [How It Works — Full Flow Diagram](#11-how-it-works--full-flow-diagram)
12. [Common Pitfalls & Troubleshooting](#12-common-pitfalls--troubleshooting)
13. [Next Steps & Enhancements](#13-next-steps--enhancements)

---

## 1. Prerequisites

Before you begin, make sure you have the following installed and ready:

| Tool             | Minimum Version | Purpose                           |
|------------------|-----------------|-----------------------------------|
| **JDK**          | 17+             | Java runtime                      |
| **Maven**        | 3.9+            | Build tool                        |
| **Quarkus CLI**  | 3.x (optional)  | Scaffolding projects              |
| **Telegram App** | Any             | To interact with BotFather & bot  |
| **IDE**          | Any             | IntelliJ IDEA / VS Code recommended |

> [!TIP]
> You can install the Quarkus CLI via SDKMAN: `sdk install quarkus` or use Maven directly.

---

## 2. Create the BotFather Telegram Bot

Before writing any code, you need a **Telegram Bot Token**.

### Steps:

1. Open Telegram and search for **@BotFather**.
2. Start a chat and send the command `/newbot`.
3. Follow the prompts:
   - **Name**: `Word Bot` (or any display name you prefer)
   - **Username**: `your_word_bot` (must end with `bot`)
4. BotFather will respond with your **HTTP API Token**:
   ```
   123456789:ABCDefGhIJklMNoPQrsTUvWxYz
   ```
5. **Save this token securely** — you'll need it in Step 5.

> [!CAUTION]
> Never commit your bot token to version control. Always use environment variables or Quarkus configuration profiles.

---

## 3. Scaffold the Quarkus Project

### Option A: Using Quarkus CLI

```bash
quarkus create app com.example:word-telegram-bot \
    --extension='camel-quarkus-telegram,camel-quarkus-direct,camel-quarkus-log' \
    --no-code
cd word-telegram-bot
```

### Option B: Using Maven

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.17.7:create \
    -DprojectGroupId=com.example \
    -DprojectArtifactId=word-telegram-bot \
    -Dextensions="camel-quarkus-telegram,camel-quarkus-direct,camel-quarkus-log" \
    -DnoCode
cd word-telegram-bot
```

### Option C: Using [code.quarkus.io](https://code.quarkus.io)

1. Go to **https://code.quarkus.io**
2. Search for and add these extensions:
   - `camel-quarkus-telegram`
   - `camel-quarkus-direct`
   - `camel-quarkus-log`
3. Set Group: `com.example`, Artifact: `word-telegram-bot`
4. Click **Generate your application** and download the ZIP.

### Verify the Maven dependencies

After scaffolding, open `pom.xml` and ensure these dependencies are present:

```xml
<dependencies>
    <!-- Apache Camel Quarkus – Telegram Component -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-telegram</artifactId>
    </dependency>

    <!-- Apache Camel Quarkus – Direct Component (in-memory routing) -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-direct</artifactId>
    </dependency>

    <!-- Apache Camel Quarkus – Log Component -->
    <dependency>
        <groupId>org.apache.camel.quarkus</groupId>
        <artifactId>camel-quarkus-log</artifactId>
    </dependency>

    <!-- Mutiny (comes with Quarkus, but explicitly listed for clarity) -->
    <dependency>
        <groupId>io.smallrye.reactive</groupId>
        <artifactId>mutiny</artifactId>
    </dependency>

    <!-- Quarkus JUnit 5 (testing) -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> [!NOTE]
> The Quarkus BOM manages dependency versions automatically. You do **not** need to specify version numbers for these artifacts.

---

## 4. Project Structure Overview

After completing all steps in this guide, your project structure will look like:

```
word-telegram-bot/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── service/
│   │   │   │   ├── TopicService.java          ← Reactive data source
│   │   │   │   ├── MenuService.java           ← Menu text provider
│   │   │   │   └── KeyboardBuilderService.java ← One-time keyboard builder
│   │   │   └── route/
│   │   │       └── TelegramBotRoute.java      ← Apache Camel route
│   │   └── resources/
│   │       └── application.properties         ← Bot configuration
│   └── test/
│       └── java/com/example/
│           └── TelegramBotRouteTest.java
```

---

## 5. Configure Application Properties

Create or edit `src/main/resources/application.properties`:

```properties
# ============================================
# Telegram Bot Configuration
# ============================================

# Bot authorization token from @BotFather
# IMPORTANT: Use environment variable in production!
telegram.bot.token=${TELEGRAM_BOT_TOKEN:YOUR_BOT_TOKEN_HERE}

# Camel Telegram component configuration
camel.component.telegram.authorization-token=${telegram.bot.token}

# ============================================
# Quarkus Configuration
# ============================================
quarkus.application.name=word-telegram-bot
quarkus.log.category."org.apache.camel".level=INFO
quarkus.log.category."com.example".level=DEBUG
```

### Setting the Token via Environment Variable

```bash
# Linux / macOS
export TELEGRAM_BOT_TOKEN="123456789:ABCDefGhIJklMNoPQrsTUvWxYz"

# Or pass it directly when running
mvn quarkus:dev -Dtelegram.bot.token="123456789:ABCDefGhIJklMNoPQrsTUvWxYz"
```

> [!IMPORTANT]
> Never hardcode your token in `application.properties` for production. Always use
> `${TELEGRAM_BOT_TOKEN}` and set the environment variable on your deployment target.

---

## 6. Create the Topic Service (Reactive Data Source)

This service provides the **topic names** as a reactive `Multi<String>` stream. The topics are
what the user will see as keyboard buttons.

### File: `src/main/java/com/example/service/TopicService.java`

```java
package com.example.service;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Service that provides topic names as a reactive Multi<String> stream.
 *
 * <p>In a real application, these topics could come from a database,
 * an external API, or any other reactive data source.</p>
 */
@ApplicationScoped
public class TopicService {

    /**
     * Static list of available topics.
     * Replace this with your own data source (e.g., database, REST call).
     */
    private static final List<String> TOPICS = List.of(
            "Java Basics",
            "Quarkus Framework",
            "Apache Camel",
            "Mutiny Reactive",
            "CDI & Dependency Injection",
            "RESTEasy Reactive",
            "Hibernate ORM",
            "MicroProfile Config",
            "Docker & Kubernetes",
            "GraalVM Native"
    );

    /**
     * Returns all topic names as a reactive Multi<String>, sorted alphabetically.
     *
     * @return a Multi<String> emitting sorted topic names
     */
    public Multi<String> getAllTopicsNameSorted() {
        return Multi.createFrom().iterable(TOPICS)
                .select().distinct()
                .collect().asList()
                .onItem().transformToMulti(list -> {
                    list.sort(String::compareTo);
                    return Multi.createFrom().iterable(list);
                });
    }

    /**
     * Collects all topic names into a sorted List<String>.
     * This is a convenience blocking method used inside Camel processors.
     *
     * @return sorted list of topic names
     */
    public List<String> getAllTopicsNameSortedAsList() {
        return getAllTopicsNameSorted()
                .collect().asList()
                .await().indefinitely();
    }
}
```

### Key Concepts Explained:

| Concept | Explanation |
|---------|-------------|
| `@ApplicationScoped` | CDI scope — a single instance shared across the application |
| `Multi<String>` | Mutiny reactive type representing a stream of 0..N items |
| `Multi.createFrom().iterable(...)` | Creates a Multi from a Java `Iterable` |
| `.select().distinct()` | Filters out duplicate items |
| `.collect().asList()` | Collects all items into a `Uni<List<String>>` |
| `.await().indefinitely()` | Blocks and waits for the result (use in non-reactive contexts) |

> [!NOTE]
> We provide both the reactive `Multi<String>` method and a blocking convenience method
> `getAllTopicsNameSortedAsList()` because Apache Camel processors run in a synchronous context.

---

## 7. Create the Menu Service

This service generates the **main menu text** shown to users when they first interact with the bot
or after each completed flow cycle.

### File: `src/main/java/com/example/service/MenuService.java`

```java
package com.example.service;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service responsible for generating bot menu messages.
 */
@ApplicationScoped
public class MenuService {

    /**
     * Gets the main menu text.
     * Uses Telegram Markdown formatting for rich display.
     *
     * @return the menu text with Markdown formatting
     */
    public String getMainMenuText() {
        return """
                🤖 *Word Telegram Bot*
                
                Welcome! This test bot streams words from a reactive Multi<String> source.
                
                📋 *Available Commands:*
                /start - Start the word stream
                
                Tap the command or type it to begin!
                """;
    }

    /**
     * Gets the response message for a selected topic.
     *
     * @param topicName the name of the selected topic
     * @return formatted response text
     */
    public String getTopicSelectedText(String topicName) {
        return String.format("""
                ✅ *Topic Selected!*
                
                You chose: *%s*
                
                Great choice! 🎉
                
                Returning to main menu...
                """, topicName);
    }
}
```

### Key Concepts Explained:

- **Text Blocks (Java 15+)**: The `"""..."""` syntax defines multi-line strings cleanly.
- **Telegram Markdown**: Using `*bold*` syntax for Telegram's Markdown mode.
- The service is a simple CDI bean (`@ApplicationScoped`) injected into the Camel route.

---

## 8. Create the Keyboard Builder Service

This service builds the **one-time reply keyboard** from the topic list. The keyboard
is built using Apache Camel's Telegram model classes.

### File: `src/main/java/com/example/service/KeyboardBuilderService.java`

```java
package com.example.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.apache.camel.component.telegram.model.ReplyKeyboardMarkup;

import java.util.Collections;
import java.util.List;

/**
 * Service that builds Telegram reply keyboards from topic names.
 */
@ApplicationScoped
public class KeyboardBuilderService {

    @Inject
    TopicService topicService;

    @Inject
    MenuService menuService;

    /**
     * Builds an OutgoingTextMessage with a one-time reply keyboard
     * containing all available topics as buttons.
     *
     * <p>Each topic gets its own row with a single button.
     * The keyboard is configured as one-time, meaning it disappears
     * after the user taps a button.</p>
     *
     * @return OutgoingTextMessage with the reply keyboard attached
     */
    public OutgoingTextMessage buildTopicKeyboard() {
        // 1. Collect topics from the reactive stream
        List<String> topics = topicService.getAllTopicsNameSortedAsList();

        // 2. Create the outgoing message
        OutgoingTextMessage message = new OutgoingTextMessage();
        message.setText("📚 *Choose a Topic:*\n\nSelect one of the topics below:");
        message.setParseMode("Markdown");

        // 3. Build keyboard rows — one button per row
        ReplyKeyboardMarkup.Builder keyboardBuilder = ReplyKeyboardMarkup.builder()
                .keyboard();

        for (String topic : topics) {
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(topic)
                    .build();
            keyboardBuilder.addRow(Collections.singletonList(button));
        }

        ReplyKeyboardMarkup replyMarkup = keyboardBuilder
                .close()
                .oneTimeKeyboard(true)       // Keyboard disappears after selection
                .resizeKeyboard(true)        // Resize keyboard to fit buttons
                .build();

        message.setReplyMarkup(replyMarkup);

        return message;
    }

    /**
     * Builds an OutgoingTextMessage that removes the custom keyboard
     * and displays the given text.
     *
     * @param text the message text
     * @return OutgoingTextMessage with keyboard removed
     */
    public OutgoingTextMessage buildMessageWithKeyboardRemoval(String text) {
        OutgoingTextMessage message = new OutgoingTextMessage();
        message.setText(text);
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup replyMarkup = ReplyKeyboardMarkup.builder()
                .removeKeyboard(true)
                .build();

        message.setReplyKeyboardMarkup(replyMarkup);

        return message;
    }
}
```

### Key Concepts Explained:

| Concept | Explanation |
|---------|-------------|
| `InlineKeyboardButton` | Represents a single button on the Telegram keyboard |
| `ReplyKeyboardMarkup` | Defines the keyboard layout, rows, and behavior |
| `.oneTimeKeyboard(true)` | Keyboard hides after user taps a button |
| `.resizeKeyboard(true)` | Adjusts keyboard size to fit button text |
| `.removeKeyboard(true)` | Removes the custom keyboard (used after selection) |
| `.addRow(...)` | Adds a row of buttons; each row is a `List<InlineKeyboardButton>` |

> [!TIP]
> To place multiple buttons on the same row, pass a list with multiple `InlineKeyboardButton`
> objects to `.addRow()`. Each call to `.addRow()` creates a new horizontal row.

---

## 9. Create the Telegram Bot Route (Apache Camel)

This is the **heart of the application** — the Apache Camel route that orchestrates
the entire conversation flow.

### File: `src/main/java/com/example/route/TelegramBotRoute.java`

```java
package com.example.route;

import com.example.service.KeyboardBuilderService;
import com.example.service.MenuService;
import com.example.service.TopicService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import java.util.List;

/**
 * Apache Camel route that handles Telegram bot interactions.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>User sends any message → Show main menu</li>
 *   <li>User sends /start → Show one-time keyboard with topics</li>
 *   <li>User selects a topic → Echo selection, then show menu again</li>
 * </ol>
 */
@ApplicationScoped
public class TelegramBotRoute extends RouteBuilder {

    @Inject
    MenuService menuService;

    @Inject
    KeyboardBuilderService keyboardBuilderService;

    @Inject
    TopicService topicService;

    @Override
    public void configure() throws Exception {

        // ───────────────────────────────────────────────
        // MAIN ROUTE: Receive all incoming Telegram messages
        // ───────────────────────────────────────────────
        from("telegram:bots")
            .routeId("telegram-bot-main")
            .log("Received message: ${body} from chat: ${header.CamelTelegramChatId}")
            .choice()
                // ── Handle "/start" command ──
                .when(body().startsWith("/start"))
                    .log("User requested /start — showing topic keyboard")
                    .to("direct:showTopicKeyboard")

                // ── Handle topic selection (any other text) ──
                .otherwise()
                    .log("User sent: ${body} — checking if it's a topic selection")
                    .to("direct:handleUserInput")
            .end();

        // ───────────────────────────────────────────────
        // ROUTE: Show the one-time keyboard with topics
        // ───────────────────────────────────────────────
        from("direct:showTopicKeyboard")
            .routeId("show-topic-keyboard")
            .log("Building topic keyboard...")
            .process(exchange -> {
                OutgoingTextMessage keyboardMessage =
                        keyboardBuilderService.buildTopicKeyboard();
                exchange.getIn().setBody(keyboardMessage);
            })
            .to("telegram:bots");

        // ───────────────────────────────────────────────
        // ROUTE: Handle user input (topic selection or unknown)
        // ───────────────────────────────────────────────
        from("direct:handleUserInput")
            .routeId("handle-user-input")
            .process(exchange -> {
                String userText = exchange.getIn().getBody(String.class);

                // Check if the user's text matches a known topic
                List<String> topics = topicService.getAllTopicsNameSortedAsList();
                boolean isValidTopic = topics.contains(userText);

                exchange.setProperty("isValidTopic", isValidTopic);
                exchange.setProperty("userText", userText);
            })
            .choice()
                // ── Valid topic selected ──
                .when(exchangeProperty("isValidTopic").isEqualTo(true))
                    .log("Valid topic selected: ${exchangeProperty.userText}")
                    .to("direct:showTopicSelection")

                // ── Unknown input → show main menu ──
                .otherwise()
                    .log("Unknown input — showing main menu")
                    .to("direct:showMainMenu")
            .end();

        // ───────────────────────────────────────────────
        // ROUTE: Show the selected topic, then return to menu
        // ───────────────────────────────────────────────
        from("direct:showTopicSelection")
            .routeId("show-topic-selection")
            .process(exchange -> {
                String topic = exchange.getProperty("userText", String.class);
                String responseText = menuService.getTopicSelectedText(topic);

                // Build message that also removes the keyboard
                OutgoingTextMessage message =
                        keyboardBuilderService.buildMessageWithKeyboardRemoval(responseText);
                exchange.getIn().setBody(message);
            })
            .to("telegram:bots")
            // After showing the selection, pause briefly then show the menu again
            .delay(1500)
            .to("direct:showMainMenu");

        // ───────────────────────────────────────────────
        // ROUTE: Show the main menu
        // ───────────────────────────────────────────────
        from("direct:showMainMenu")
            .routeId("show-main-menu")
            .process(exchange -> {
                OutgoingTextMessage message = new OutgoingTextMessage();
                message.setText(menuService.getMainMenuText());
                message.setParseMode("Markdown");
                exchange.getIn().setBody(message);
            })
            .to("telegram:bots");
    }
}
```

### Route Flow Breakdown

```
┌─────────────────────────────────────────────┐
│          telegram:bots (Consumer)           │
│     Listens for ALL incoming messages       │
└──────────────────┬──────────────────────────┘
                   │
              ┌────▼────┐
              │ choice() │
              └────┬────┘
         ┌─────────┼──────────┐
         │                    │
    "/start"             otherwise
         │                    │
         ▼                    ▼
┌────────────────┐   ┌──────────────────┐
│ direct:show    │   │ direct:handle    │
│ TopicKeyboard  │   │ UserInput        │
│                │   │                  │
│ Builds reply   │   │ Checks if text   │
│ keyboard with  │   │ matches a topic  │
│ topic buttons  │   │                  │
│ (one-time)     │   └───────┬──────────┘
│                │      ┌────▼────┐
└───────┬────────┘      │ choice() │
        │               └────┬────┘
        │          ┌─────────┼──────────┐
        │          │                    │
        │     valid topic          unknown
        │          │                    │
        │          ▼                    ▼
        │  ┌───────────────┐   ┌──────────────┐
        │  │ direct:show   │   │ direct:show  │
        │  │ TopicSelection│   │ MainMenu     │
        │  │               │   │              │
        │  │ Echo choice,  │   │ Show welcome │
        │  │ remove kbd,   │   │ message      │
        │  │ delay 1.5s,   │   │              │
        │  │ show menu     │   │              │
        │  └───────────────┘   └──────────────┘
        │
        ▼
  telegram:bots (Producer)
  Sends message to user
```

### Key Camel Concepts Explained:

| Concept | Explanation |
|---------|-------------|
| `from("telegram:bots")` | **Consumer** — receives all messages sent to your bot |
| `.to("telegram:bots")` | **Producer** — sends a message back to the user's chat |
| `.choice().when().otherwise()` | **Content-Based Router** — EIP pattern for conditional routing |
| `from("direct:...")` | **Direct component** — in-memory synchronous messaging between routes |
| `.process(exchange -> {...})` | **Processor** — custom Java logic within a route |
| `.routeId("...")` | Labels the route for logging and management |
| `exchange.getIn().setBody(...)` | Sets the outgoing message body |
| `exchange.setProperty(...)` | Stores data on the exchange for downstream use |
| `.delay(1500)` | Pauses processing for 1500ms before continuing |
| `${header.CamelTelegramChatId}` | Camel header containing the Telegram Chat ID |

> [!IMPORTANT]
> The `camel.component.telegram.authorization-token` property in `application.properties`
> is automatically used by both `telegram:bots` consumer and producer endpoints.
> You do **not** need to specify `authorizationToken` in the URI when using this approach.

---

## 10. Run and Test the Bot

### Step 1: Start in Dev Mode

```bash
# Set your bot token
export TELEGRAM_BOT_TOKEN="123456789:ABCDefGhIJklMNoPQrsTUvWxYz"

# Run the application in dev mode
mvn quarkus:dev
```

You should see log output similar to:

```
__  ____  __  _____   ___  __ ____  ______
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/

INFO  [io.quarkus] word-telegram-bot 1.0.0 on JVM started in 2.345s.
INFO  [org.apa.cam.imp.eng.AbstractCamelContext] Routes startup:
INFO  [org.apa.cam.imp.eng.AbstractCamelContext]     Started telegram-bot-main (telegram://bots)
INFO  [org.apa.cam.imp.eng.AbstractCamelContext]     Started show-topic-keyboard (direct://showTopicKeyboard)
INFO  [org.apa.cam.imp.eng.AbstractCamelContext]     Started handle-user-input (direct://handleUserInput)
INFO  [org.apa.cam.imp.eng.AbstractCamelContext]     Started show-topic-selection (direct://showTopicSelection)
INFO  [org.apa.cam.imp.eng.AbstractCamelContext]     Started show-main-menu (direct://showMainMenu)
INFO  [org.apa.cam.imp.eng.AbstractCamelContext] Total 5 routes, of which 5 are started
```

### Step 2: Test on Telegram

1. Open Telegram and find your bot by its username (e.g., `@your_word_bot`).
2. Send any message → You should see the **main menu** with the welcome message.
3. Send `/start` → A **one-time keyboard** appears with all topic names as buttons.
4. Tap a topic button (e.g., *"Quarkus Framework"*) → The bot confirms your selection.
5. After 1.5 seconds → The **main menu** appears again, ready for the next cycle.

### Expected Interaction Flow:

```
YOU:  /hello
BOT:  🤖 Word Telegram Bot
      Welcome! This test bot streams words from a reactive Multi<String> source.
      📋 Available Commands:
      /start - Start the word stream
      Tap the command or type it to begin!

YOU:  /start
BOT:  📚 Choose a Topic:
      Select one of the topics below:
      ┌──────────────────────────┐
      │ Apache Camel             │
      ├──────────────────────────┤
      │ CDI & Dependency Inject. │
      ├──────────────────────────┤
      │ Docker & Kubernetes      │
      ├──────────────────────────┤
      │ GraalVM Native           │
      ├──────────────────────────┤
      │ Hibernate ORM            │
      ├──────────────────────────┤
      │ ...                      │
      └──────────────────────────┘

YOU:  [taps "Quarkus Framework"]
BOT:  ✅ Topic Selected!
      You chose: Quarkus Framework
      Great choice! 🎉
      Returning to main menu...

BOT:  🤖 Word Telegram Bot
      Welcome! This test bot streams words ...
```

---

## 11. How It Works — Full Flow Diagram

```
┌──────────┐                    ┌───────────────────┐
│ Telegram │  HTTP Long Poll    │   Quarkus App     │
│  User    │◄──────────────────►│                   │
└─────┬────┘                    │  ┌─────────────┐  │
      │                         │  │ Camel Route │  │
      │  1. Any message         │  │  (Consumer) │  │
      │ ──────────────────────► │  └──────┬──────┘  │
      │                         │         │         │
      │                         │    ┌────▼────┐    │
      │                         │    │ /start? │    │
      │                         │    └────┬────┘    │
      │                         │    NO   │   YES   │
      │                         │    ▼    │    ▼    │
      │                         │ ┌─────┐ │┌──────┐ │
      │                         │ │Menu │ ││Topic │ │
      │                         │ │Svc  │ ││Svc   │ │
      │                         │ └──┬──┘ │└──┬───┘ │
      │                         │    │    │   │     │
      │                         │    │    │┌──▼───┐ │
      │                         │    │    ││KBD   │ │
      │                         │    │    ││Build │ │
      │                         │    │    │└──┬───┘ │
      │                         │    │    │   │     │
      │  2. Response message    │  ┌─▼────▼───▼──┐  │
      │ ◄────────────────────── │  │ Camel Route │  │
      │                         │  │ (Producer)  │  │
      │  3. User taps topic     │  └─────────────┘  │
      │ ──────────────────────► │       ...         │
      │                         │                   │
      │  4. Echo + Menu again   │                   │
      │ ◄────────────────────── │                   │
      └────────────────────────►└───────────────────┘
```

---

## 12. Common Pitfalls & Troubleshooting

### ❌ Bot doesn't receive messages

| Possible Cause | Solution |
|----------------|----------|
| Invalid token | Double-check your `TELEGRAM_BOT_TOKEN` env variable |
| Another instance running | Only **one** consumer can poll a bot at a time. Stop other instances |
| Firewall blocking | Ensure outbound HTTPS (port 443) is allowed |

### ❌ Keyboard doesn't appear

| Possible Cause | Solution |
|----------------|----------|
| Empty topic list | Verify `TopicService.getAllTopicsNameSortedAsList()` returns data |
| Wrong markup type | Use `ReplyKeyboardMarkup`, not `InlineKeyboardMarkup` for one-time keyboards |

### ❌ `ClassNotFoundException` for Telegram model classes

```bash
# Make sure camel-quarkus-telegram is in your pom.xml
mvn dependency:tree | grep telegram
```

### ❌ Dev mode hot reload issues

```bash
# If routes don't reload, try forcing a restart
# Press 's' in the Quarkus dev terminal to force restart
```

> [!TIP]
> Use `quarkus dev` logs extensively during development. The `log` component in each route
> will help trace the exact flow of messages through your Camel routes.

---

## 13. Next Steps & Enhancements

Once you have the basic bot working, consider these improvements:

### 🔄 Dynamic Topics from a Database
Replace the static `List<String>` in `TopicService` with Hibernate Reactive + Panache:

```java
@ApplicationScoped
public class TopicService {
    public Multi<String> getAllTopicsNameSorted() {
        return Topic.listAll()
                .onItem().transformToMulti(list ->
                    Multi.createFrom().iterable(list))
                .map(Topic::getName)
                .collect().asList()
                .onItem().transformToMulti(list -> {
                    list.sort(String::compareTo);
                    return Multi.createFrom().iterable(list);
                });
    }
}
```

### 🧪 Unit Testing with Camel Quarkus Test Support

Add the test dependency:

```xml
<dependency>
    <groupId>org.apache.camel.quarkus</groupId>
    <artifactId>camel-quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

### 📝 Multi-step Conversations
Use Camel's **aggregation** patterns or maintain conversation state in a `ConcurrentHashMap<Long, ConversationState>` keyed by `CamelTelegramChatId`.

### 🐳 Build a Native Executable

```bash
mvn package -Dnative -Dquarkus.native.container-build=true
```

### 🚀 Deploy to Kubernetes

```bash
# Add the Kubernetes extension
quarkus extension add kubernetes

# Build and deploy
mvn package -Dquarkus.kubernetes.deploy=true
```

---

## Summary

In this guide, you learned how to:

1. ✅ **Create a Telegram bot** via BotFather and obtain an API token
2. ✅ **Scaffold a Quarkus project** with Apache Camel Telegram extension
3. ✅ **Define a reactive data source** using Mutiny `Multi<String>`
4. ✅ **Build one-time reply keyboards** with `ReplyKeyboardMarkup`
5. ✅ **Create Camel routes** using the Content-Based Router EIP pattern
6. ✅ **Wire everything together** with CDI dependency injection
7. ✅ **Run and test** the bot in Quarkus dev mode

### Technology Stack Used:

| Technology | Role |
|------------|------|
| **Quarkus 3.x** | Application framework |
| **Apache Camel 4.x** | Integration framework & Telegram adapter |
| **Camel Telegram Component** | Telegram Bot API abstraction |
| **Mutiny** | Reactive programming (Multi/Uni) |
| **CDI (Jakarta)** | Dependency injection |
| **MicroProfile Config** | Externalized configuration |

> [!NOTE]
> This guide uses Apache Camel's **Java DSL** for route definitions. Camel also supports
> XML DSL and YAML DSL, but Java DSL is the recommended approach for Quarkus applications
> as it benefits from compile-time checking and CDI integration.

---

*Happy coding! 🚀*
