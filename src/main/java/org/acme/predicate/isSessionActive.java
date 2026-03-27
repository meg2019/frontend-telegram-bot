package org.acme.predicate;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.service.SessionManagerService;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import static org.apache.camel.component.telegram.TelegramConstants.TELEGRAM_CHAT_ID;


@ApplicationScoped
@RequiredArgsConstructor
public class isSessionActive implements Predicate {

    private final SessionManagerService sessionManagerService;

    @Override
    public boolean matches(Exchange exchange) {

        String chatId = exchange.getIn().getHeader(TELEGRAM_CHAT_ID, String.class);
        return chatId != null && sessionManagerService.hasActiveSession(Long.parseLong(chatId));
    }
}
