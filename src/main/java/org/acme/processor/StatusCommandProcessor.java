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
