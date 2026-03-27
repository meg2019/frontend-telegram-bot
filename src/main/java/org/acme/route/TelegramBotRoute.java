package org.acme.route;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.predicate.isSessionActive;
import org.acme.processor.AnswerProcessor;
import org.acme.processor.CallbackQueryProcessor;
import org.acme.processor.StartCommandProcessor;
import org.acme.processor.StatusCommandProcessor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.IncomingCallbackQuery;

/**
 * Main Apache Camel route for the Telegram Quiz Bot.
 *
 * <p>
 * Message routing logic:
 * </p>
 * <ol>
 *   <li>"/start" command → StartCommandProcessor → show topic keyboard</li>
 *   <li>Callback query (button press) → CallbackQueryProcessor → start quiz</li>
 *   <li>Text during active session → AnswerProcessor → check answer</li>
 *   <li>Any other message → "Unknown command" response</li>
 * </ol>
 *
 * <p>
 * All business logic is delegated to dedicated processors — this class
 * only handles routing decisions.
 * </p>
 */
@ApplicationScoped
@RequiredArgsConstructor
public class TelegramBotRoute extends RouteBuilder {

    private final StartCommandProcessor startCommandProcessor;
    private final CallbackQueryProcessor callbackQueryProcessor;
    private final AnswerProcessor answerProcessor;
    private final StatusCommandProcessor statusCommandProcessor;
    private final isSessionActive isSessionActive;


    @Override
    public void configure() throws Exception {
        // Global error handling for this route
        onException(Exception.class)
                .handled(true)
                .log("Error processing message: ${exception.message}")
                .setBody(constant("❌ An error occurred. Please try again later."))
                .to("telegram:bots");
        // ─────────────────────────────────────────────────────────
        // Main Route: Receive and dispatch Telegram messages
        // ─────────────────────────────────────────────────────────
        from("telegram:bots")
            .routeId("telegram-quiz-bot")
            .log("Received from ${header.CamelTelegramChatId}: ${body}")
            .choice()
                // ── 1. Handle /start command ──
                .when(simple("${body} =~ '/start'"))
                    .log("Processing /start command")
                    .process(startCommandProcessor)
                    .to("telegram:bots")
                // ── 1.5 Handle /status command ──
                .when(simple("${body} =~ '/status'"))
                    .log("Processing /status command")
                    .process(statusCommandProcessor)
                    .to("telegram:bots")
                // ── 2. Handle InlineKeyboard callback (topic selection) ──
                .when(body().isInstanceOf(IncomingCallbackQuery.class))
                    .log("Processing callback query (topic selection)...")
                    .process(callbackQueryProcessor)
                    .to("telegram:bots")
                // ── 3. Handle text answers during active quiz ──
                .when(isSessionActive)
                    .log("Processing quiz answer...")
                    .process(answerProcessor)
                    .to("telegram:bots")
                // ── 4. Handle unknown messages ──
                .otherwise()
                    .log("Unknown command received: ${body}")
                    .setBody(simple(
                        "❓ Unknown command.\n\nSend /start to begin a quiz!"))
                    .to("telegram:bots")
                .end();
    }
}
