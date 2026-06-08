package org.acme.model;

import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class UserQuizSessionTest {

    private static final QuizPair TEST_QUIZ_PAIR =
            new QuizPair("Q1", "Q1 Description", "A1", "A1 Description");

    public static Stream<Arguments> invalidArgumentsProvider() {
        return Stream.of(
                Arguments.of(null, "topic", Collections.singletonList(TEST_QUIZ_PAIR)),
                Arguments.of("user", null, Collections.singletonList(TEST_QUIZ_PAIR)),
                Arguments.of("user", "topic", null),
                Arguments.of("user", "topic", Collections.emptyList())
        );
    }

    @Test
    @SneakyThrows
    void testConcurrentIncrementCorrectAnswers() {
        var session = UserQuizSession.create(1L, "user", "topic",
                Collections.singletonList(TEST_QUIZ_PAIR));

        assertAll(
                () -> assertEquals(0, session.getCurrentQuestionIndex()),
                () -> assertEquals(0, session.getCorrectAnswersCount())
        );

        int threads = 10;
        int incrementsPerThread = 100;
        CountDownLatch latch;
        try (var executor = Executors.newFixedThreadPool(threads)) {
            latch = new CountDownLatch(threads * incrementsPerThread);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        session.incrementCorrectAnswers();
                        latch.countDown();
                    }
                });
            }
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(threads * incrementsPerThread, session.getCorrectAnswersCount());
    }

    @Test
    @SneakyThrows
    void testConcurrentMoveToNextQuestion() {
        var questions = IntStream.range(0, 100)
                .mapToObj(i -> new QuizPair("Q " + i, "QD " + i,
                        "A " + i, "AD " + i))
                .toList();
        var session = UserQuizSession.create(1L, "user", "topic", questions);
        assertAll(
                () -> assertEquals(0, session.getCurrentQuestionIndex()),
                () -> assertEquals(0, session.getCorrectAnswersCount())
        );

        CountDownLatch latch;
        try (var executor = Executors.newFixedThreadPool(10)) {
            latch = new CountDownLatch(100);

            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    session.moveToNextQuestion();
                    latch.countDown();
                });
            }
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(100, session.getCurrentQuestionIndex());
    }

    @ParameterizedTest
    @MethodSource("invalidArgumentsProvider")
    @DisplayName("Should throw IllegalArgumentException when invalid arguments are provided")
    void userQuizSessionValidationOnCreateTest(String username, String topic, List<QuizPair> questions) {

        var exception = assertThrows(IllegalArgumentException.class,
                () -> UserQuizSession.create(1L, username, topic, questions));
        Log.infof("Got this exception message: %s", exception.getMessage());
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }
}