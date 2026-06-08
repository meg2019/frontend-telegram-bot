package org.acme;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.acme.model.QuizPair;
import org.acme.server.TestWordServiceServer;
import org.acme.service.QuizWordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@QuarkusTest
public class QuizWordServiceIT {

    @Inject
    QuizWordService quizWordService;

    @Inject
    @GrpcService
    TestWordServiceServer testServer;

    @BeforeEach
    void setup() {
        testServer.reset();
    }


    @Test
    @DisplayName("Test topic count IT")
    @RunOnVertxContext
    void testQuizIT_TopicCountTest(UniAsserter asserter) {

        asserter.assertEquals(
                () -> quizWordService.getAllTopicsNumber(),
                5L
        );
    }

    @Test
    @DisplayName("Test topics list IT")
    @RunOnVertxContext
    void testQuizIT_TopicListTest(UniAsserter asserter) {
        List<String> expectedTopics = List.of("ATopic", "BTopic");
        asserter.assertThat(
                () -> quizWordService.getAllTopicsNameSorted().collect().asList(),
                actualResult -> assertIterableEquals(expectedTopics, actualResult)
        );
    }

    @Test
    @DisplayName("Test word pairs IT")
    @RunOnVertxContext
    void testQuizIT_WordPairsTest(UniAsserter asserter) {
        QuizPair quizPair1 = new QuizPair("Hello", "A english greeting", "Hola", "A spanish greeting");
        QuizPair quizPair2 = new QuizPair("Goodbye", "A english farewell", "Adiós", "A spanish farewell");

        asserter.assertThat(
                () -> quizWordService.getWordPairs("TestTopic", "SL", "TL")
                        .collect().asList(),
                actualResult -> assertIterableEquals(List.of(quizPair1, quizPair2), actualResult)
        );
    }

    @Test
    @DisplayName("UNAVAILABLE error - Fallback method return zero topic count")
    @RunOnVertxContext
    void testQuizIT_ZeroTopicCountIfServerUnavailable(UniAsserter asserter) {
        // Configure server to simulate UNAVAILABLE error
        asserter.execute(() -> testServer.setSimulateError(true, Status.UNAVAILABLE));

        // Assert that the call fails and return zero Topic count from fallback method
        asserter.assertEquals(
                () -> quizWordService.getAllTopicsNumber(),
                0L
        );
    }

}
