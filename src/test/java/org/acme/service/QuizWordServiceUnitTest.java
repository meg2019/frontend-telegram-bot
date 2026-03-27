package org.acme.service;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.logging.Log;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.backend.model.*;
import org.acme.exception.WordServiceException;
import org.acme.model.QuizPair;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class QuizWordServiceUnitTest {

    private static final Logger LOG = Logger.getLogger(QuizWordService.class);

    @InjectMock
    @GrpcClient("word-service")
    WordService wordService;

    @Inject
    private QuizWordService quizWordService;

    @BeforeEach
    void setUp() {
        Mockito.reset(wordService);
    }

    @Test
    @DisplayName("Should return the correct number of topics from the word service")
    @RunOnVertxContext
    void wordService_VerifyTopicsCount(UniAsserter asserter) {

        long expectedCount = 10L;
        TopicCount expectedTopicCount = TopicCount.newBuilder()
                .setCount(expectedCount)
                .build();

        when(wordService.getTopicCount(Empty.getDefaultInstance()))
                .thenReturn(Uni.createFrom().item(expectedTopicCount));

        asserter.assertEquals(
                () -> quizWordService.getAllTopicsNumber(),
                expectedCount
        );
    }

    @Test
    @DisplayName("Should return the correct topics name from the word service")
    @RunOnVertxContext
    void wordService_VerifyReturnedTopics(UniAsserter asserter) {
        List<String> expectedTopics = List.of("ATopic", "CTopic", "ZTopic", "FTopic");
        LOG.infof("✅ This we got from client: %s", expectedTopics);
        List<String> expectedTopicsSorted = List.of("ATopic", "CTopic", "FTopic", "ZTopic");

        when(wordService.getTopics(Empty.getDefaultInstance()))
                .thenReturn(Multi.createFrom().items(
                        expectedTopics.stream()
                                .map(el -> Topic.newBuilder().setName(el).build())
                ));

        asserter.assertThat(
                () -> quizWordService.getAllTopicsNameSorted().collect().asList(),
                actualResult -> {
                    Log.infof("✅ We got this as result: %s", actualResult);
                    assertIterableEquals(expectedTopicsSorted, actualResult);
                }
        );
    }

    @Test
    @DisplayName("Should return the correct word pairs from the word service")
    @RunOnVertxContext
    void wordService_VerifyWordPairs(UniAsserter asserter) {
        WordPair wordPair_1 = WordPair.newBuilder()
                .setSourceWord("מזכירות")
                .setSourceWordDesc("Source Word: Register Office description")
                .setTargetWord("Регистратура")
                .setTargetWordDesc("Target Word: Register Office description")
                .build();
        WordPair wordPair_2 = WordPair.newBuilder()
                .setSourceWord("הפניה")
                .setSourceWordDesc("Source Word: Referral description")
                .setTargetWord("Направление")
                .setTargetWordDesc("Target Word: Referral description")
                .build();

        List<WordPair> expectedWordPairs = List.of(wordPair_1, wordPair_2);

        QuizPair quizPair1 = QuizPair.builder()
                .wrdQuestion("מזכירות")
                .wrdQuestionDesc("Source Word: Register Office description")
                .wrdAnswer("Регистратура")
                .wrdAnswerDesc("Target Word: Register Office description")
                .build();
        QuizPair quizPair2 = QuizPair.builder()
                .wrdQuestion("הפניה")
                .wrdQuestionDesc("Source Word: Referral description")
                .wrdAnswer("Направление")
                .wrdAnswerDesc("Target Word: Referral description")
                .build();

        List<QuizPair> quizPairList = List.of(quizPair1, quizPair2);

        WordPairRequest wordPairRequest = WordPairRequest.newBuilder()
                .setTopicName("Test topicName")
                .setSourceLanguage("HE")
                .setTargetLanguage("RU").build();

        when(wordService.getWordPairs(wordPairRequest))
                .thenReturn(Multi.createFrom().iterable(expectedWordPairs));

        asserter.assertThat(
                () -> quizWordService.getWordPairs("Test topicName", "HE", "RU")
                        .collect().asList(),
                actualResult -> {
                    LOG.infof("✅ We got this as result: %s", actualResult);
                    assertEquals(2, actualResult.size());
                    assertIterableEquals(quizPairList, actualResult);
                    verify(wordService, times(1)).getWordPairs(wordPairRequest);
                }
        );
    }
}