package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.model.QuizPair;
import org.acme.model.UserQuizSession;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active quiz sessions for users.
 * <p>
 * Sessions are stored in a ConcurrentHashMap keyed by chatId.
 * Thread-safe for concurrent access from multiple Camel routes.
 */
@ApplicationScoped
public class SessionManagerService {

    private static final Logger LOG = Logger.getLogger(SessionManagerService.class);

    // chatId x UserSession
    private final Map<Long, UserQuizSession> activeSessions = new ConcurrentHashMap<>();

    // Temporary storage for word pairs between topic and length selection
    // Map<chatId, List<QuizPair>>
    private final Map<Long, List<QuizPair>> pendingWordPairs = new ConcurrentHashMap<>();

    /**
     * Creates and stores a new quiz session for the given chat.
     * If a session already exists for this chatId, it is replaced.
     *
     * @param chatId    Telegram chat ID
     * @param userName  user's display name
     * @param topicName selected topic name
     * @param questions list of quiz pairs
     * @return the newly created session
     */
    public UserQuizSession createSession(long chatId,
                                         String userName,
                                         String topicName,
                                         List<QuizPair> questions) {
        var session = UserQuizSession.create(chatId, userName, topicName, questions);
        activeSessions.put(chatId, session);
        LOG.infof("✅ Created new session for chatId: %d, user: %s", chatId, userName);
        return session;
    }

    /**
     * Retrieves the active session for a given chat, if one exists.
     *
     * @param chatId Telegram chat ID
     * @return Optional containing the session, or empty if no active session
     */
    public Optional<UserQuizSession> getSession(long chatId) {
        return Optional.ofNullable(activeSessions.get(chatId));
    }

    /**
     * Removes the session for a given chat (e.g., after quiz completion).
     *
     * @param chatId Telegram chat ID
     */
    public void removeSession(long chatId) {
        activeSessions.remove(chatId);
    }

    /**
     * Checks if a user currently has an active quiz session.
     *
     * @param chatId Telegram chat ID
     * @return true if an active session exists
     */
    public boolean hasActiveSession(long chatId) {
        return activeSessions.containsKey(chatId);
    }

    public int getActiveSessionsCount() {
        return activeSessions.size();
    }

    /**
     * Temporarily stores word pairs for a user while they choose quiz length.
     * This is needed because Telegram requires a second callback for length selection.
     *
     * @param chatId    Telegram chat ID
     * @param wordPairs all word pairs for the topic
     */
    public void storePendingWordPairs(long chatId, List<QuizPair> wordPairs) {
        pendingWordPairs.put(chatId, new ArrayList<>(wordPairs));
        LOG.infof("📝 Stored %d pending word pairs for chatId: %d", wordPairs.size(), chatId);
    }

    /**
     * Retrieves and removes the pending word pairs for a user.
     *
     * @param chatId Telegram chat ID
     * @return Optional containing the pending word pairs, or empty if none exist
     */
    public Optional<List<QuizPair>> getPendingWordPairs(long chatId) {
        return Optional.ofNullable(pendingWordPairs.remove(chatId));
    }

    /**
     * Clears pending word pairs for a user (e.g., after quiz starts or timeout).
     *
     * @param chatId Telegram chat ID
     */
    public void clearPendingWordPairs(long chatId) {
        pendingWordPairs.remove(chatId);
        LOG.infof("🧹 Cleared pending word pairs for chatId: %d", chatId);
    }
}
