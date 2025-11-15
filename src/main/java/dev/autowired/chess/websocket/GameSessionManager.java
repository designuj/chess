package dev.autowired.chess.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service to manage WebSocket sessions for chess games.
 * Handles session lifecycle, cleanup, and broadcasting.
 */
@Slf4j
@Service
public class GameSessionManager {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> gameSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Cleanup closed sessions every 30 seconds
        cleanup.scheduleAtFixedRate(this::cleanupClosedSessions, 30, 30, TimeUnit.SECONDS);
        log.info("GameSessionManager initialized with cleanup scheduled");
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down GameSessionManager");
        cleanup.shutdown();
        try {
            if (!cleanup.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanup.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanup.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Add a WebSocket session to a game room.
     */
    public synchronized void addSession(String gameId, WebSocketSession session) {
        if (gameId == null || session == null) {
            log.warn("Attempt to add null gameId or session");
            return;
        }

        Set<WebSocketSession> sessions = gameSessions.computeIfAbsent(gameId,
            k -> ConcurrentHashMap.newKeySet());
        sessions.add(session);

        log.debug("Added session to game {}, total sessions: {}", gameId, sessions.size());
    }

    /**
     * Remove a WebSocket session from a game room.
     */
    public synchronized void removeSession(String gameId, WebSocketSession session) {
        if (gameId == null || session == null) {
            return;
        }

        Set<WebSocketSession> sessions = gameSessions.get(gameId);
        if (sessions != null) {
            sessions.remove(session);
            log.debug("Removed session from game {}, remaining sessions: {}", gameId, sessions.size());

            if (sessions.isEmpty()) {
                gameSessions.remove(gameId);
                log.info("Game {} has no active sessions, removed from manager", gameId);
            }
        }
    }

    /**
     * Get all active sessions for a game.
     */
    public Set<WebSocketSession> getGameSessions(String gameId) {
        return gameSessions.getOrDefault(gameId, Set.of());
    }

    /**
     * Get the number of active games.
     */
    public int getActiveGamesCount() {
        return gameSessions.size();
    }

    /**
     * Get total number of active sessions across all games.
     */
    public int getTotalSessionsCount() {
        return gameSessions.values().stream()
                          .mapToInt(Set::size)
                          .sum();
    }

    /**
     * Cleanup closed WebSocket sessions.
     */
    private void cleanupClosedSessions() {
        int removedSessions = 0;
        int removedGames = 0;

        for (var entry : gameSessions.entrySet()) {
            String gameId = entry.getKey();
            Set<WebSocketSession> sessions = entry.getValue();

            int initialSize = sessions.size();
            sessions.removeIf(session -> !session.isOpen());
            removedSessions += (initialSize - sessions.size());

            if (sessions.isEmpty()) {
                gameSessions.remove(gameId);
                removedGames++;
            }
        }

        if (removedSessions > 0 || removedGames > 0) {
            log.info("Cleanup completed: removed {} sessions, {} games. Active games: {}, total sessions: {}",
                    removedSessions, removedGames, getActiveGamesCount(), getTotalSessionsCount());
        }
    }
}
