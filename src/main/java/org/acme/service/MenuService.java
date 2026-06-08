package org.acme.service;

import io.quarkus.runtime.util.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Service responsible for generating formatted text messages for the Telegram bot interface.
 * All menus are already implemented and ready to use. <p>
 * This CDI-managed bean provides all user-facing messages including main menu text,
 * quiz prompts, status updates, and result summaries. All messages use Telegram
 * Markdown formatting for rich display.
 * </p>
 */
@ApplicationScoped
public class MenuService {

    private static final NavigableMap<Integer, String> ENCOURAGEMENT_MAP = new TreeMap<>() {{
        put(100, "🏆");
        put(80, "🌟");
        put(60, "👍");
        put(40, "💪");
    }};

    /**
     * Gets the main menu text.
     * Uses Telegram Markdown formatting for rich display.
     *
     * @return the menu text with Markdown formatting
     */
    public String getMainMenuText() {
        return """
                 🤖 *Word Telegram Bot*
                
                 Welcome! This test bot streams words from
                 a reactive Multi<String> source.
                
                 📋 *Available Commands:*
                 /start - Start the word stream
                 /status - Check the bot status
                
                 Tap the command or type it to begin!
                """;
    }

    public String getTopicKeyboardMenuText() {
        return """
                🎓*Welcome to the Language Quiz Bot!*
                Choose a topic below to start your quiz:
                
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
                
                """, topicName);
    }

    public String getStatusMessage(String javaVersion, String uptime, Long topicsNumber) {
        return """
                ✅ Bot is running smoothly!
                
                🚀 Java Version: *%s*
                ⏰ Uptime: *%s*
                📘 Topics number: *%s*
                """.formatted(javaVersion, uptime, topicsNumber);
    }

    public String getResultMessage(String userName,
                                   String topicName,
                                   int correctAnswers,
                                   int incorrectAnswers,
                                   int score) {
        return """
                🏁 *Quiz Complete!*
                %s Great job, %s!
                -------------------
                📊 *Final Results:*
                📚 Topic: %s
                ✅ Correct: %d
                ❌ Incorrect: %d
                📈 Score: %d%%
                
                Type /start to try another quiz!
                """.formatted(getEncouragement(score),
                userName,
                topicName,
                correctAnswers,
                incorrectAnswers,
                score);
    }

    /**
     * Returns an encouraging emoji based on the score percentage.
     */
    private static String getEncouragement(int score) {
        Map.Entry<Integer, String> entry = ENCOURAGEMENT_MAP.floorEntry(score);
        return entry != null ? entry.getValue() : "📖";
    }

    public String getQuestionMessage(int questionNumber, int totalQuestions, String wrdQuestion) {
        return """
                📚 *Question %d/%d*
                What is the Russian translation of:
                🔤 *%s*
                Type your answer below:
                """.formatted(questionNumber, totalQuestions, wrdQuestion);
    }

    /**
     * Gets the message prompting user to select quiz length.
     *
     * @param topicName the name of the selected topic
     * @param totalWords the total number of word pairs available
     * @return formatted prompt text
     */
    public String getQuizLengthMenuText(String topicName, int totalWords) {
        return String.format("""
                📖 *Choose Quiz Length*
                
                You selected: *%s*
                Total words available: *%d*
                
                How many words would you like to practice?
                """, topicName, totalWords);
    }

    public String getIncorrectAnswerMessage(String rightAnswer, String rightAnswerExplanation) {
        return """
                ❌ *Incorrect.* The correct answer is: *%s*%n*Note:* %s
                """.formatted(rightAnswer, rightAnswerExplanation);
    }

    public static String[] longStringFormatter(String longString) {
        if (StringUtil.isNullOrEmpty(longString)) {
            return new String[0];
        }
        if (!longString.contains(" ")) {
            return new String[]{longString};
        }
        int mid = longString.length() / 2;
        // Find the space closest to the center
        int splitIdx = longString.lastIndexOf(' ', mid);

        // If no space is found before the middle, look after it
        if (splitIdx == -1) splitIdx = longString.indexOf(' ', mid);

        // If still no space, just return the original
        if (splitIdx == -1) return new String[]{longString};

        return new String[]{
                longString.substring(0, splitIdx),
                longString.substring(splitIdx + 1)
        };
    }
}
