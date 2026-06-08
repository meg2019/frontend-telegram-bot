package org.acme.predicate;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.service.SessionManagerService;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.commons.lang3.StringUtils;

import static org.apache.camel.component.telegram.TelegramConstants.TELEGRAM_CHAT_ID;


/**
 * Apache Camel {@link Predicate} that determines whether a Telegram user
 * currently has an active quiz session.
 *
 * <p>This predicate inspects the incoming {@link Exchange} to extract the
 * Telegram chat identifier from the {@code TELEGRAM_CHAT_ID} header. It then
 * delegates to {@link SessionManagerService} to verify that an active session
 * exists for the corresponding user.</p>
 *
 * <p>The predicate returns {@code true} only when:</p>
 * <ul>
 *   <li>The chat ID header is present and non-blank</li>
 *   <li>The parsed chat ID maps to an active session in {@link SessionManagerService}</li>
 * </ul>
 *
 * <p>Typical usage within a Camel route:</p>
 * <pre>{@code
 * from("direct:telegram.update")
 *     .filter(new isSessionActive())
 *         .to("direct:quiz.answer.processor");
 * }</pre>
 *
 * @see Predicate
 * @see SessionManagerService
 * @see org.apache.camel.component.telegram.TelegramConstants#TELEGRAM_CHAT_ID
 */
@ApplicationScoped
@RequiredArgsConstructor
public class isSessionActive implements Predicate {

    private final SessionManagerService sessionManagerService;

    @Override
    public boolean matches(Exchange exchange) {

        String chatId = exchange.getIn().getHeader(TELEGRAM_CHAT_ID, String.class);
        return StringUtils.isNotBlank(chatId)
                && sessionManagerService.hasActiveSession(
                        Long.parseLong(chatId));
    }
}
