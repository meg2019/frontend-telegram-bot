package org.acme.processor;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.service.KeyboardBuilderService;
import org.acme.service.MenuService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

/**
 * Handles the /start command.
 * Sends a welcome message with an InlineKeyboard listing available quiz topics.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class StartCommandProcessor implements Processor {

    private final KeyboardBuilderService keyboardService;
    private final MenuService menuService;


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
        OutgoingTextMessage message = OutgoingTextMessage.builder()
                .parseMode("Markdown")
                .text(menuService.getTopicKeyboardMenuText())
                .replyMarkup(keyboardService.buildTopicKeyboard())
                .build();
        // Set the message as the exchange body — Camel Telegram will send it
        exchange.getIn().setBody(message);
    }
}
