package org.acme.service;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.model.QuizPair;

import java.util.List;

/**
 * Caching intermediary service for QuizWordService.
 * <p>
 * This service wraps the original QuizWordService and adds caching
 * capabilities without modifying the original service.
 * <p>
 * Cache Strategy:
 * - topics-cache: Single entry for all topics list, TTL 10 minutes
 * - word-pairs-cache: Per topic/language combination, TTL 30 minutes, max 100 entries
 */
@ApplicationScoped
@RequiredArgsConstructor
public class CachedQuizWordService {

    private final QuizWordService delegate;

    /**
     * Retrieves all topic names sorted alphabetically (cached).
     * Cache key: none (single entry)
     * TTL: 10 minutes
     */
    @CacheResult(cacheName = "topics-cache")
    public Uni<List<String>> getAllTopicNameSorted() {
        Log.info("Cache miss: Fetching topics from delegate");
        return delegate.getAllTopicsNameSorted()
                .collect().asList()
                .invoke(list ->
                        Log.infof("Cached %d topics", list.size()));
    }

    /**
     * Retrieves word pairs for a topic/language combination (cached).
     * Cache key: composite of (topicName, sourceLang, targetLang)
     * TTL: 30 minutes
     * Max entries: 100
     */
    @CacheResult(cacheName = "word-pairs-cache")
    public Uni<List<QuizPair>> getWordPairs(String topicName, String sourceLang, String targetLang) {
        Log.infof("Cache miss: Fetching word pairs for topic=%s, source=%s, target=%s",
                topicName, sourceLang, targetLang);
        return delegate.getWordPairs(topicName, sourceLang, targetLang)
                .collect().asList()
                .invoke(list -> Log.infof("Cached %d word pairs", list.size()));
    }

    /**
     * Invalidates the topics cache.
     * Call this when topics are known to have changed.
     */
    @CacheInvalidate(cacheName = "topics-cache")
    public void invalidateTopicsCache() {
        Log.info("Invalidated topics cache");
    }

    /**
     * Invalidates word pairs cache for a specific combination.
     * Call this when word pairs for a specific topic/language are updated.
     */
    @CacheInvalidate(cacheName = "word-pairs-cache")
    public void invalidateWordPairs(String topicName, String sourceLang, String targetLang) {
        Log.infof("Invalidated word pairs cache for %s/%s/%s", topicName, sourceLang, targetLang);
    }

    /**
     * Invalidates all word pairs cache entries.
     * Call this when a bulk update occurs.
     */
    @CacheInvalidateAll(cacheName = "word-pairs-cache")
    public void invalidateAllWordPairs() {
        Log.info("Invalidated all word pairs cache");
    }

    /**
     * Invalidates all caches.
     * Useful for admin operations or complete refresh.
     */
    public void invalidateAllCaches() {
        invalidateTopicsCache();
        invalidateAllWordPairs();
        Log.info("All caches invalidated");
    }
}
