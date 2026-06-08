package org.acme.processor;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.service.MenuService;
import org.acme.service.QuizWordService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;

import java.lang.management.ManagementFactory;
import java.time.Duration;

/**
 * A Camel {@link Processor} implementation that handles the {@code /status} command
 * in the Telegram bot integration.
 * <p>
 * This processor is a Quarkus CDI-managed bean ({@link ApplicationScoped}) and is
 * invoked by a Camel route when a user requests the bot's status information.
 * It collects runtime diagnostics — including the Java version, application uptime,
 * and the total number of quiz topics — and formats them into a Markdown-rendered
 * {@link OutgoingTextMessage} suitable for delivery via the Telegram Bot API.
 * <p>
 * <strong>Dependencies:</strong>
 * <ul>
 *   <li>{@link QuizWordService} — provides the total count of available quiz topics.</li>
 *   <li>{@link MenuService} — formats the collected metrics into a human-readable status string.</li>
 * </ul>
 * <p>
 * <strong>Processing flow:</strong>
 * <ol>
 *   <li>Retrieve the current Java version from the system property.</li>
 *   <li>Compute the application uptime using the JVM runtime MXBean.</li>
 *   <li>Asynchronously fetch the total topic count from {@link QuizWordService}.</li>
 *   <li>Delegate to {@link MenuService} to build the formatted status message.</li>
 *   <li>Set the resulting {@link OutgoingTextMessage} as the outgoing Camel exchange body.</li>
 * </ol>
 *
 * @see Processor
 * @see OutgoingTextMessage
 * @see MenuService
 * @see QuizWordService
 */
@ApplicationScoped
@RequiredArgsConstructor
public class StatusCommandProcessor implements Processor {

    private final QuizWordService quizWordService;
    private final MenuService menuService;


    @Override
    public void process(Exchange exchange) throws Exception {
        OutgoingTextMessage outgoingTextMessage = OutgoingTextMessage.builder()
                .text(menuService.getStatusMessage(
                        System.getProperty("java.version"),
                        getUpTime(),
                        getAllTopicsNumber()
                ))
                .parseMode("Markdown")
                .build();

        exchange.getMessage().setBody(outgoingTextMessage);
    }

    private Long getAllTopicsNumber() {
        return quizWordService.getAllTopicsNumber().await().atMost(Duration.ofSeconds(10L));
    }

    private static String getUpTime() {
        long uptime = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        long sec = uptime / 1000;
        long min = sec / 60;
        long hours = min / 60;
        return String.format("%02d:%02d:%02d", hours, min % 60, sec % 60);
    }
}
