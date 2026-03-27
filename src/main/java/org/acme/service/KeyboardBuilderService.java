package org.acme.service;

import com.google.common.collect.Lists;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.apache.camel.component.telegram.model.InlineKeyboardButton;
import org.apache.camel.component.telegram.model.InlineKeyboardMarkup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
public class KeyboardBuilderService {

    private final CachedQuizWordService cachedQuizWordService;

    /**
     * Prefix for callback data to identify topic selections.
     * When Telegram sends back a callback, the data will be "topic: Animals", etc.
     */
    public static final String CALLBACK_PREFIX = "topic:";

    /**
     * Prefix for callback data to identify quiz length selections.
     * When Telegram sends back a callback, the data will be "length: 1", "length: 2", etc.
     * representing 1/3, 2/3, 3/3 of available word pairs.
     */
    public static final String LENGTH_CALLBACK_PREFIX = "length:";

    /**
     * Builds an InlineKeyboardMarkup with one button per topic.
     * Each button's callback data is prefixed with "topic: " followed by the topic name.
     *
     * @return InlineKeyboardMarkup ready to attach to an OutgoingTextMessage
     */
    public InlineKeyboardMarkup buildTopicKeyboard() {

        List<String> topicsName = getAllTopicsNameSortedAsList();

        List<List<InlineKeyboardButton>> rows = Lists.partition(
                topicsName.stream()
                        .map(topic -> InlineKeyboardButton.builder()
                                .text(topic)
                                .callbackData(("%s %s").formatted(CALLBACK_PREFIX, topic))
                                .build())
                        .toList(), 2);

        var kbBuilder = InlineKeyboardMarkup.builder();
        rows.forEach(kbBuilder::addRow);
        return kbBuilder.build();
    }

    private List<String> getAllTopicsNameSortedAsList() {
        return cachedQuizWordService.getAllTopicNameSorted()
                .await().atMost(Duration.ofSeconds(10L));
    }

    /**
     * Builds an InlineKeyboardMarkup with quiz length options (1/3, 2/3, 3/3).
     * Each button's callback data is prefixed with "length: " followed by the portion number and topic name.
     * Format: "length: X|TopicName"
     *
     * @param topicName  the selected topic name (included in callback data)
     * @param totalWords the total number of word pairs available for the topic
     * @return InlineKeyboardMarkup with length options
     */
    public InlineKeyboardMarkup buildQuizLengthKeyboard(String topicName, int totalWords) {
        // Determine which options to show based on total word count
        List<String> options = new ArrayList<>();
        
        if (totalWords >= 4) {
            options.add(String.valueOf(totalWords / 4));
        }
        if (totalWords >= 8) {
            options.add(String.valueOf(totalWords / 4 * 2));
        }
        if (totalWords >= 12) {
            options.add(String.valueOf(totalWords / 4 * 3));
        }
        options.add(String.valueOf(totalWords));

        List<List<InlineKeyboardButton>> rows = Lists.partition(options.stream()
                .map(option -> {
                    int portion = options.indexOf(option) + 1;
                    return InlineKeyboardButton.builder()
                            .text(option)
                            .callbackData(("%s %d|%s").formatted(LENGTH_CALLBACK_PREFIX, portion, topicName))
                            .build();
                })
                .toList(), 4);

        var kbBuilder = InlineKeyboardMarkup.builder();
        rows.forEach(kbBuilder::addRow);
        return kbBuilder.build();
    }
}
