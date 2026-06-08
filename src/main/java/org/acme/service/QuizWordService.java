package org.acme.service;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.backend.model.*;
import org.acme.exception.WordServiceException;
import org.acme.model.QuizPair;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Resilient gRPC client for consuming the WordService.
 * Features:
 * - Circuit breaker to prevent cascading failures
 * - Retry mechanism for transient failures
 * - Timeout protection
 * - Comprehensive error handling
 * - Load balancing via Stork (when multiple instances available)
 */
@ApplicationScoped
public class QuizWordService {

    private static final Logger LOG = Logger.getLogger(QuizWordService.class);

    @GrpcClient("word-service")
    WordService wordServiceGrpcClient;


    /**
     * Retrieves the total number of topics available.
     *
     * @return the Uni<Long> count of topics
     * @throws WordServiceException if the gRPC call fails after retries
     */
    @CircuitBreaker(
            requestVolumeThreshold = 10, // Open circuit after 10 failures
            failureRatio = 0.5,         // 50% failure rate triggers circuit
            delay = 10000L,             // Wait 10s before trying again
            successThreshold = 3        // Need 3 successes to close circuit
    )
    @Retry(
            maxRetries = 5, // Retry up to 5 times
            delay = 200L,    // Base delay 200ms
            jitter = 100L,   // Add random jitter up to 100ms
            retryOn = {StatusRuntimeException.class, WordServiceException.class}
    )
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "getTopicCountFallback")
    public Uni<Long> getAllTopicsNumber() {
        LOG.debug("✅ Fetching topic count from word service");
        return wordServiceGrpcClient.getTopicCount(Empty.getDefaultInstance())
                .onItem().transform(TopicCount::getCount)
                .onFailure(StatusRuntimeException.class)
                .transform(this::transformGrpcException)
                .invoke(count -> LOG.debugf("✅ Retrieved topic count: %d", count));
    }

    /**
     * Fallback method for getAllTopicsNumber when circuit breaker is open.
     *
     * @return Uni containing fallback value (0)
     */
    public Uni<Long> getTopicCountFallback() {
        LOG.warn("Circuit breaker open, returning fallback zero topic count");
        return Uni.createFrom().item(0L);
    }

    /**
     * Retrieves all topic names available in alphabetical order.
     * Protected by circuit breaker and retry mechanisms.
     *
     * @return a list of topic names
     * @throws WordServiceException if the gRPC call fails after retries
     */
    @CircuitBreaker(
            requestVolumeThreshold = 10, // Open circuit after 10 requests
            failureRatio = 0.5,         // 50% failure rate triggers circuit
            delay = 10000L,             // Wait 10s before trying again
            successThreshold = 3        // Need 3 successes to close circuit
    )
    @Retry(
            maxRetries = 3,             // Retry up to 3 times
            delay = 200L,               // Base delay 200ms
            jitter = 100L,              // Add random jitter up to 100ms
            retryOn = {StatusRuntimeException.class, WordServiceException.class}
    )
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "getTopicsFallback")
    public Multi<String> getAllTopicsNameSorted() {
        LOG.debug("✅ Fetching topics from word service");
        return wordServiceGrpcClient.getTopics(Empty.getDefaultInstance())
                .map(Topic::getName)
                .collect().asList()
                .onItem().transform(list -> {
                    List<String> sortedList = new ArrayList<>(list);
                    sortedList.sort(String.CASE_INSENSITIVE_ORDER);
                    LOG.debugf("✅ Retrieved and sorted %d topics", sortedList.size());
                    return sortedList;
                })
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onFailure(StatusRuntimeException.class)
                .transform(this::transformGrpcException)
                .invoke(() -> LOG.debug("✅ Successfully retrieved and transformed topics"));
    }


    /**
     * Fallback method for getAllTopicsNameSorted when circuit breaker is open.
     *
     * @return Multi containing empty stream
     */
    public Multi<String> getTopicsFallback() {
        LOG.warn("Circuit breaker open, returning empty topics stream");
        return Multi.createFrom().empty();
    }


    /**
     * Retrieves shuffled word pairs for a given topic and language combination.
     * Protected by circuit breaker and retry mechanisms.
     *
     * @param topicName  the name of the topic
     * @param sourceLang the source language code
     * @param targetLang the target language code
     * @return a map where keys are source words and values are target words
     * @throws IllegalArgumentException if any parameter is null or blank
     * @throws WordServiceException     if the gRPC call fails after retries
     */
    @CircuitBreaker(
            requestVolumeThreshold = 10, // Open circuit after 10 requests
            failureRatio = 0.5,         // 50% failure rate triggers circuit
            delay = 10000L,             // Wait 10s before trying again
            successThreshold = 3        // Need 3 successes to close circuit
    )
    @Retry(
            maxRetries = 3,             // Retry up to 3 times
            delay = 200L,               // Base delay 200ms
            jitter = 100L,              // Add random jitter up to 100ms
            retryOn = {StatusRuntimeException.class, WordServiceException.class}
    )
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    @Fallback(fallbackMethod = "getWordPairsFallback")
    public Multi<QuizPair> getWordPairs(String topicName, String sourceLang, String targetLang) {

        // Input validation
        validateParameters(topicName, sourceLang, targetLang);
        LOG.debugf("✅ Fetching word pairs for topic: %s, %s x %s", topicName, sourceLang, targetLang);

        WordPairRequest request = WordPairRequest.newBuilder()
                .setTopicName(topicName)
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build();

        return wordServiceGrpcClient.getWordPairs(request)
                .onItem().transform(this::mapToQuizPair)
                .onFailure(StatusRuntimeException.class)
                .transform(this::transformGrpcException)
                .invoke(() -> LOG.debugf("✅ Successfully fetched word pairs for topic: %s", topicName));
    }

    /**
     * Fallback method for getWordPairs when circuit breaker is open.
     *
     * @return Multi containing empty stream
     */
    public Multi<QuizPair> getWordPairsFallback(String topicName, String sourceLang, String targetLang) {
        LOG.warn("Circuit breaker open, returning empty word pairs stream");
        return Multi.createFrom().empty();
    }

    /**
     * Maps a WordPair protobuf message to a QuizPair domain object.
     *
     * @param wordPair the protobuf WordPair to map
     * @return the mapped QuizPair
     */
    private QuizPair mapToQuizPair(WordPair wordPair) {

        return QuizPair.builder()
                .wrdQuestion(wordPair.getSourceWord())
                .wrdQuestionDesc(wordPair.getSourceWordDesc())
                .wrdAnswer(wordPair.getTargetWord())
                .wrdAnswerDesc(wordPair.getTargetWordDesc())
                .build();
    }

    private void validateParameters(String topicName, String sourceLang, String targetLang) {
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalArgumentException("Topic name must not be null or blank");
        }
        if (sourceLang == null || sourceLang.isBlank()) {
            throw new IllegalArgumentException("Source language must not be null or blank");
        }
        if (targetLang == null || targetLang.isBlank()) {
            throw new IllegalArgumentException("Target language must not be null or blank");
        }
    }

    /**
     * Validates that a string parameter is not null or blank.
     *
     * @param value     the value to validate
     * @param paramName the name of the parameter (for error messages)
     * @throws IllegalArgumentException if the value is null or blank
     */
    private void validateNotBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s: cannot be null or blank".formatted(paramName));
        }
    }

    /**
     * Transforms a Throwable into a WordServiceException.
     * Provides more detailed error messages based on the gRPC status code.
     *
     * @param e the Throwable to transform
     * @return a WordServiceException with appropriate message
     */
    private WordServiceException transformGrpcException(Throwable e) {
        if (e instanceof StatusRuntimeException sre) {
            Status status = sre.getStatus();
            String message = switch (status.getCode()) {
                case NOT_FOUND -> "Resource not found: " + status.getDescription();
                case UNAVAILABLE -> "Word service unavailable: " + status.getDescription();
                case DEADLINE_EXCEEDED -> "Request timeout: " + status.getDescription();
                case PERMISSION_DENIED -> "Permission denied: " + status.getDescription();
                case INVALID_ARGUMENT -> "Invalid argument: " + status.getDescription();
                default -> "gRPC error: " + status.getCode() + " - " + status.getDescription();
            };
            LOG.errorf("gRPC error occurred: %s", message);
            return new WordServiceException(message, e);
        }
        LOG.errorf("Unexpected error occurred: %s", e.getMessage());
        return new WordServiceException("Unexpected error: " + e.getMessage(), e);
    }
}
