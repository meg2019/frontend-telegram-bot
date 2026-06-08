package org.acme.server;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.acme.backend.model.*;

import java.util.stream.Stream;

// an in-process gRPC server for integration tests
@Slf4j
@GrpcService
@Singleton
public class TestWordServiceServer implements WordService {

    private boolean simulateError = false;
    private Status errorStatus = Status.UNAVAILABLE;

    @Override
    public Uni<TopicCount> getTopicCount(Empty request) {
        log.info("Test gRPC Server: getTopicCount endpoint called...");

        if (simulateError) {
            log.info("Simulating error: {}", errorStatus);
            return Uni.createFrom().failure(
                    new StatusRuntimeException(
                            errorStatus.withDescription("Simulated error from test server"))
            );
        }
        TopicCount topicCount = TopicCount.newBuilder()
                .setCount(5L)
                .build();

        return Uni.createFrom().item(topicCount);
    }

    @Override
    public Multi<Topic> getTopics(Empty request) {

        log.info("Test gRPC Server: getTopics endpoint called...");

        if (simulateError) {
            log.info("Simulating error: {}", errorStatus);
            return Multi.createFrom().failure(
                    new StatusRuntimeException(errorStatus.withDescription("Simulated error from test server"))
            );
        }

        Topic bTopic = Topic.newBuilder()
                .setName("BTopic")
                .setDescription("Description for Topic B")
                .build();

        Topic aTopic = Topic.newBuilder()
                .setName("ATopic")
                .setDescription("Description for Topic A")
                .build();

        return Multi.createFrom().items(Stream.of(bTopic, aTopic));
    }

    @Override
    public Multi<WordPair> getWordPairs(WordPairRequest request) {

        log.info("Test gRPC Server: getWordPairs endpoint called with topicName: %s, %s x %s..."
                .formatted(request.getTopicName(), request.getSourceLanguage(), request.getTargetLanguage()));

        if (simulateError) {
            log.info("Simulating error: {}", errorStatus);
            return Multi.createFrom().failure(
                    new StatusRuntimeException(errorStatus.withDescription("Simulated error from test server"))
            );
        }

        WordPair wordPair1 = WordPair.newBuilder()
                .setSourceWord("Hello")
                .setSourceWordDesc("A english greeting")
                .setTargetWord("Hola")
                .setTargetWordDesc("A spanish greeting")
                .build();

        WordPair wordPair2 = WordPair.newBuilder()
                .setSourceWord("Goodbye")
                .setSourceWordDesc("A english farewell")
                .setTargetWord("Adiós")
                .setTargetWordDesc("A spanish farewell")
                .build();

        return Multi.createFrom().items(Stream.of(wordPair1, wordPair2));
    }

    /**
     * Configure the mock to simulate error conditions for testing.
     */
    public void setSimulateError(boolean simulateError, Status errorStatus) {
        this.simulateError = simulateError;
        this.errorStatus = errorStatus;
    }

    /**
     * Reset the mock to normal operation.
     */
    public void reset() {
        this.simulateError = false;
        this.errorStatus = Status.UNAVAILABLE;
    }
}

