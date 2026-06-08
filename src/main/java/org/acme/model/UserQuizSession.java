package org.acme.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Thread-safe quiz session for a user.
 * Uses AtomicInteger for lock-free thread-safe mutable state.
 */
@Getter
public final class UserQuizSession {

    // Immutable data
    private final long chatId;
    private final String userName;
    private final String topicName;
    private final List<QuizPair> questions;

    // Mutable state - thread-safe using AtomicInteger
    private final AtomicInteger currentQuestionIndex;
    private final AtomicInteger correctAnswers;

    /**
     * Creates a new quiz session.
     *
     * @param chatId               Telegram chat ID
     * @param userName             Username - must not be null or empty
     * @param topicName            Quiz topic name - must not be null or empty
     * @param questions            List of quiz questions - must not be null or empty
     * @param currentQuestionIndex Starting question index - defaults to 0
     * @param correctAnswers       Starting correct answers count - defaults to 0
     */
    @Builder
    public UserQuizSession(
            long chatId,
            String userName,
            String topicName,
            List<QuizPair> questions,
            int currentQuestionIndex,
            int correctAnswers) {
        this.chatId = chatId;
        this.userName = requireNonEmpty(userName, "userName");
        this.topicName = requireNonEmpty(topicName, "topicName");
        this.questions = List.copyOf(requireNonEmpty(questions, "questions"));
        this.currentQuestionIndex = new AtomicInteger(currentQuestionIndex);
        this.correctAnswers = new AtomicInteger(correctAnswers);
    }

    /**
     * Factory method for common use case.
     */
    public static UserQuizSession create(
            long chatId,
            String userName,
            String topicName,
            List<QuizPair> questions) {
        return UserQuizSession.builder()
                .chatId(chatId)
                .userName(userName)
                .topicName(topicName)
                .questions(questions)
                .build();
    }

    // Business methods - all thread-safe

    /**
     * Gets the current question if available.
     * Thread-safe: reads atomic index.
     */
    public Optional<QuizPair> getCurrentQuestion() {
        int index = currentQuestionIndex.get();
        return index < questions.size()
                ? Optional.of(questions.get(index))
                : Optional.empty();
    }

    /**
     * Checks if there are more questions.
     * Thread-safe: reads atomic index.
     */
    public boolean hasNextQuestion() {
        return currentQuestionIndex.get() < questions.size();
    }

    /**
     * Moves to the next question.
     * Thread-safe: uses atomic increment with boundary check.
     */
    public void moveToNextQuestion() {
        currentQuestionIndex.getAndUpdate(current ->
                current < questions.size() ? current + 1 : current
        );
    }

    /**
     * Increments the correct answers counter.
     * Thread-safe: uses atomic increment.
     */
    public void incrementCorrectAnswers() {
        correctAnswers.incrementAndGet();
    }

    /**
     * Gets the current question index.
     * Thread-safe: reads atomic value.
     */
    public int getCurrentQuestionIndex() {
        return currentQuestionIndex.get();
    }

    /**
     * Gets the correct answers count.
     * Thread-safe: reads atomic value.
     */
    public int getCorrectAnswersCount() {
        return correctAnswers.get();
    }

    /**
     * Calculates the score as a percentage.
     */
    public int getScore() {
        int total = questions.size();
        return total > 0 ? (getCorrectAnswersCount() * 100) / total : 0;
    }

    /**
     * Checks if the quiz is completed.
     */
    public boolean isCompleted() {
        return !hasNextQuestion();
    }

    /**
     * Gets the total number of questions.
     */
    public int getTotalQuestions() {
        return questions.size();
    }

    // Validation helpers - kept as they provide useful error messages

    private static String requireNonEmpty(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null or empty");
        }
        return value;
    }

    private static <T, C extends Collection<? extends T>> C requireNonEmpty(C collection, String name) {
        checkArgument(collection != null && !collection.isEmpty(),
                "Collection %s cannot be null or empty", name);
        return collection;
    }

    private static <T extends Comparable<? super T>> Optional<T> max(Collection<T> collection) {
        return collection.stream().max(Comparator.naturalOrder());
    }
}
