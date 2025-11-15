package dev.autowired.chess.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.Position;
import dev.autowired.chess.service.ChessService;
import dev.autowired.chess.service.ChessRulesEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChessWebSocketHandler implements WebSocketHandler {

    private final ChessService chessService;
    private final GameSessionManager sessionManager;
    private final ChessRulesEngine rulesEngine;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<String, Sinks.Many<String>> gameSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        String gameId = extractGameId(path);

        if (gameId == null) {
            log.warn("Invalid game ID in WebSocket path: {}", path);
            return session.close();
        }

        log.info("WebSocket connection established for game: {}", gameId);

        // Add session to manager
        sessionManager.addSession(gameId, session);

        // Create or get the broadcast sink for this game
        Sinks.Many<String> broadcastSink = gameSinks.computeIfAbsent(gameId,
                k -> Sinks.many().multicast().onBackpressureBuffer());

        // Create a personal sink for this session (for error messages)
        Sinks.Many<String> personalSink = Sinks.many().unicast().onBackpressureBuffer();

        // Merge broadcast and personal messages
        Mono<Void> output = session.send(
                broadcastSink.asFlux()
                        .mergeWith(personalSink.asFlux())
                        .map(session::textMessage)
        );

        // Receive messages from client
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(payload -> log.debug("Received WebSocket message for game {}: {}", gameId, payload))
                .flatMap(payload -> handleMessage(gameId, payload, broadcastSink, personalSink))
                .doFinally(signalType -> {
                    log.info("WebSocket connection closed for game {} with signal: {}", gameId, signalType);
                    sessionManager.removeSession(gameId, session);

                    // Cleanup sink if no more sessions
                    if (sessionManager.getGameSessions(gameId).isEmpty()) {
                        gameSinks.remove(gameId);
                        log.info("Removed broadcast sink for game: {}", gameId);
                    }
                })
                .then();

        return Mono.zip(input, output).then();
    }

    /**
     * Extract and validate game ID from WebSocket path.
     */
    private String extractGameId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String[] parts = path.split("/");
        if (parts.length == 0) {
            return null;
        }

        String gameId = parts[parts.length - 1];

        // Validate gameId format (alphanumeric plus hyphens/underscores, 3-50 characters)
        if (!gameId.matches("^[a-zA-Z0-9_-]{3,50}$")) {
            log.warn("Invalid game ID format: {}", gameId);
            return null;
        }

        return gameId;
    }

    private Mono<Void> handleMessage(String gameId, String payload, Sinks.Many<String> broadcastSink, Sinks.Many<String> personalSink) {
        try {
            Map<String, Object> message = objectMapper.readValue(payload, Map.class);
            String type = (String) message.get("type");

            return switch (type) {
                case "JOIN" -> handleJoin(gameId, (String) message.get("playerId"), broadcastSink, personalSink);
                case "MOVE" -> handleMove(gameId, message, broadcastSink, personalSink);
                case "GET_STATE" -> handleGetState(gameId, broadcastSink, personalSink);
                default -> {
                    log.warn("Unknown message type: {}", type);
                    sendError(personalSink, "Unknown message type");
                    yield Mono.empty();
                }
            };
        } catch (Exception e) {
            log.error("Error handling message for game {}", gameId, e);
            sendError(personalSink, "Invalid message format");
            return Mono.empty();
        }
    }

    private Mono<Void> handleJoin(String gameId, String playerId, Sinks.Many<String> broadcastSink, Sinks.Many<String> personalSink) {
        if (playerId == null || playerId.isBlank()) {
            sendError(personalSink, "Player ID is required");
            return Mono.empty();
        }

        // For WebSocket JOIN, we use a default name since the player should have already joined via REST API
        // This is mainly for getting the current game state
        String playerName = "Player";
        return chessService.joinGame(gameId, playerId, playerName)
                .flatMap(game -> broadcastGameState(game, broadcastSink, "PLAYER_JOINED"))
                .onErrorResume(e -> {
                    log.warn("Failed to join game {} for player {}: {}", gameId, playerId, e.getMessage());
                    sendError(personalSink, sanitizeErrorMessage(e.getMessage()));
                    return Mono.empty();
                });
    }

    private Mono<Void> handleMove(String gameId, Map<String, Object> message, Sinks.Many<String> broadcastSink, Sinks.Many<String> personalSink) {
        try {
            String playerId = (String) message.get("playerId");
            if (playerId == null || playerId.isBlank()) {
                sendError(personalSink, "Player ID is required");
                return Mono.empty();
            }

            Map<String, Integer> fromMap = (Map<String, Integer>) message.get("from");
            Map<String, Integer> toMap = (Map<String, Integer>) message.get("to");

            if (fromMap == null || toMap == null) {
                sendError(personalSink, "Move positions are required");
                return Mono.empty();
            }

            Position from = new Position(fromMap.get("row"), fromMap.get("col"));
            Position to = new Position(toMap.get("row"), toMap.get("col"));

            // Validate move format
            if (!from.isValid() || !to.isValid()) {
                sendError(personalSink, "Invalid move positions");
                return Mono.empty();
            }

            return chessService.makeMove(gameId, playerId, from, to)
                    .flatMap(game -> broadcastGameState(game, broadcastSink, "MOVE_MADE"))
                    .onErrorResume(e -> {
                        log.warn("Failed to make move in game {} for player {}: {}", gameId, playerId, e.getMessage());
                        sendError(personalSink, sanitizeErrorMessage(e.getMessage()));
                        return Mono.empty();
                    });
        } catch (ClassCastException e) {
            log.warn("Invalid move message format for game {}: {}", gameId, e.getMessage());
            sendError(personalSink, "Invalid move format");
            return Mono.empty();
        } catch (Exception e) {
            log.error("Unexpected error handling move for game {}", gameId, e);
            sendError(personalSink, "Failed to process move");
            return Mono.empty();
        }
    }

    private Mono<Void> handleGetState(String gameId, Sinks.Many<String> broadcastSink, Sinks.Many<String> personalSink) {
        return chessService.getGame(gameId)
                .flatMap(game -> broadcastGameState(game, broadcastSink, "STATE_UPDATE"))
                .onErrorResume(e -> {
                    log.warn("Failed to get game state for {}: {}", gameId, e.getMessage());
                    sendError(personalSink, "Game not found");
                    return Mono.empty();
                });
    }

    private Mono<Void> broadcastGameState(Game game, Sinks.Many<String> sink, String messageType) {
        try {
            Map<String, Object> response = Map.of(
                    "type", messageType,
                    "game", game,
                    "timestamp", java.time.Instant.now()
            );
            String json = objectMapper.writeValueAsString(response);

            Sinks.EmitResult result = sink.tryEmitNext(json);
            if (result.isFailure()) {
                log.warn("Failed to broadcast game state: {}", result);
            }

            return Mono.empty();
        } catch (Exception e) {
            log.error("Error broadcasting game state", e);
            return Mono.empty();
        }
    }

    private void sendError(Sinks.Many<String> sink, String error) {
        try {
            Map<String, Object> response = Map.of(
                    "type", "ERROR",
                    "message", error,
                    "timestamp", java.time.Instant.now()
            );
            String json = objectMapper.writeValueAsString(response);

            Sinks.EmitResult result = sink.tryEmitNext(json);
            if (result.isFailure()) {
                log.warn("Failed to send error message: {}", result);
            }
        } catch (Exception e) {
            log.error("Error sending error message", e);
        }
    }

    /**
     * Sanitize error messages to avoid exposing sensitive information.
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "An error occurred";
        }

        // Limit message length and remove potentially sensitive info
        String sanitized = message.replaceAll("(?i)(password|token|secret|key)", "[REDACTED]");
        return sanitized.length() > 100 ? sanitized.substring(0, 100) + "..." : sanitized;
    }
}
