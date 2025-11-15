package dev.autowired.chess.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.autowired.chess.model.*;
import dev.autowired.chess.service.ChessService;
import dev.autowired.chess.service.ChessRulesEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChessWebSocketHandlerTest {

    @Mock
    private ChessService chessService;

    @Mock
    private GameSessionManager sessionManager;

    @Mock
    private ChessRulesEngine rulesEngine;

    private ChessWebSocketHandler handler;
    private ObjectMapper objectMapper;

    private static final String GAME_ID = "test-game-id";
    private static final String PLAYER_ID = "player-1";

    @BeforeEach
    void setUp() {
        handler = new ChessWebSocketHandler(chessService, sessionManager, rulesEngine);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should handle GET_STATE message and return game state")
    void shouldHandleGetStateMessage() throws Exception {
        Game testGame = new Game("player1", "Player1");
        testGame.setId(GAME_ID);

        when(chessService.getGame(GAME_ID)).thenReturn(Mono.just(testGame));

        // Use reflection to access private method for testing
        Method handleGetStateMethod = ChessWebSocketHandler.class.getDeclaredMethod(
                "handleGetState", String.class, reactor.core.publisher.Sinks.Many.class, reactor.core.publisher.Sinks.Many.class);
        handleGetStateMethod.setAccessible(true);

        reactor.core.publisher.Sinks.Many<String> broadcastSink = reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        reactor.core.publisher.Sinks.Many<String> personalSink = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> result = (Mono<Void>) handleGetStateMethod.invoke(handler, GAME_ID, broadcastSink, personalSink);

        StepVerifier.create(result)
                .verifyComplete();

        // Verify a message was emitted to the broadcast sink
        StepVerifier.create(broadcastSink.asFlux().next())
                .assertNext(message -> {
                    try {
                        Map<String, Object> parsedMessage = objectMapper.readValue(message, Map.class);
                        assertThat(parsedMessage.get("type")).isEqualTo("STATE_UPDATE");
                        assertThat(parsedMessage.get("game")).isNotNull();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle JOIN message successfully")
    void shouldHandleJoinMessage() throws Exception {
        Game testGame = new Game("player1", "Player1");
        testGame.setId(GAME_ID);
        testGame.setBlackPlayerId(PLAYER_ID);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(chessService.joinGame(eq(GAME_ID), eq(PLAYER_ID), anyString())).thenReturn(Mono.just(testGame));

        Method handleJoinMethod = ChessWebSocketHandler.class.getDeclaredMethod(
                "handleJoin", String.class, String.class, reactor.core.publisher.Sinks.Many.class, reactor.core.publisher.Sinks.Many.class);
        handleJoinMethod.setAccessible(true);

        reactor.core.publisher.Sinks.Many<String> broadcastSink = reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        reactor.core.publisher.Sinks.Many<String> personalSink = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> result = (Mono<Void>) handleJoinMethod.invoke(handler, GAME_ID, PLAYER_ID, broadcastSink, personalSink);

        StepVerifier.create(result)
                .verifyComplete();

        StepVerifier.create(broadcastSink.asFlux().next())
                .assertNext(message -> {
                    try {
                        Map<String, Object> parsedMessage = objectMapper.readValue(message, Map.class);
                        assertThat(parsedMessage.get("type")).isEqualTo("PLAYER_JOINED");
                        assertThat(parsedMessage.get("game")).isNotNull();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle MOVE message successfully")
    void shouldHandleMoveMessage() throws Exception {
        Game testGame = new Game("player1", "Player1");
        testGame.setId(GAME_ID);
        testGame.setBlackPlayerId("player2");
        testGame.setStatus(GameStatus.IN_PROGRESS);

        Position from = new Position(6, 4);
        Position to = new Position(5, 4);

        when(chessService.makeMove(eq(GAME_ID), eq(PLAYER_ID), any(Position.class), any(Position.class)))
                .thenReturn(Mono.just(testGame));

        Method handleMoveMethod = ChessWebSocketHandler.class.getDeclaredMethod(
                "handleMove", String.class, Map.class, reactor.core.publisher.Sinks.Many.class, reactor.core.publisher.Sinks.Many.class);
        handleMoveMethod.setAccessible(true);

        reactor.core.publisher.Sinks.Many<String> broadcastSink = reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        reactor.core.publisher.Sinks.Many<String> personalSink = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> moveMessage = Map.of(
                "type", "MOVE",
                "playerId", PLAYER_ID,
                "from", Map.of("row", from.getRow(), "col", from.getCol()),
                "to", Map.of("row", to.getRow(), "col", to.getCol())
        );

        Mono<Void> result = (Mono<Void>) handleMoveMethod.invoke(handler, GAME_ID, moveMessage, broadcastSink, personalSink);

        StepVerifier.create(result)
                .verifyComplete();

        StepVerifier.create(broadcastSink.asFlux().next())
                .assertNext(message -> {
                    try {
                        Map<String, Object> parsedMessage = objectMapper.readValue(message, Map.class);
                        assertThat(parsedMessage.get("type")).isEqualTo("MOVE_MADE");
                        assertThat(parsedMessage.get("game")).isNotNull();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should send error message when join fails")
    void shouldSendErrorWhenJoinFails() throws Exception {
        when(chessService.joinGame(eq(GAME_ID), eq(PLAYER_ID), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Game is full")));

        Method handleJoinMethod = ChessWebSocketHandler.class.getDeclaredMethod(
                "handleJoin", String.class, String.class, reactor.core.publisher.Sinks.Many.class, reactor.core.publisher.Sinks.Many.class);
        handleJoinMethod.setAccessible(true);

        reactor.core.publisher.Sinks.Many<String> broadcastSink = reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        reactor.core.publisher.Sinks.Many<String> personalSink = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> result = (Mono<Void>) handleJoinMethod.invoke(handler, GAME_ID, PLAYER_ID, broadcastSink, personalSink);

        StepVerifier.create(result)
                .verifyComplete();

        // Error should go to personal sink, not broadcast
        StepVerifier.create(personalSink.asFlux().next())
                .assertNext(message -> {
                    try {
                        Map<String, Object> parsedMessage = objectMapper.readValue(message, Map.class);
                        assertThat(parsedMessage.get("type")).isEqualTo("ERROR");
                        assertThat(parsedMessage.get("message")).isEqualTo("Game is full");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should send error message when move is invalid")
    void shouldSendErrorWhenMoveIsInvalid() throws Exception {
        when(chessService.makeMove(eq(GAME_ID), eq(PLAYER_ID), any(Position.class), any(Position.class)))
                .thenReturn(Mono.error(new IllegalStateException("Invalid move")));

        Method handleMoveMethod = ChessWebSocketHandler.class.getDeclaredMethod(
                "handleMove", String.class, Map.class, reactor.core.publisher.Sinks.Many.class, reactor.core.publisher.Sinks.Many.class);
        handleMoveMethod.setAccessible(true);

        reactor.core.publisher.Sinks.Many<String> broadcastSink = reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        reactor.core.publisher.Sinks.Many<String> personalSink = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> moveMessage = Map.of(
                "type", "MOVE",
                "playerId", PLAYER_ID,
                "from", Map.of("row", 6, "col", 4),
                "to", Map.of("row", 5, "col", 4)
        );

        Mono<Void> result = (Mono<Void>) handleMoveMethod.invoke(handler, GAME_ID, moveMessage, broadcastSink, personalSink);

        StepVerifier.create(result)
                .verifyComplete();

        // Error should go to personal sink, not broadcast
        StepVerifier.create(personalSink.asFlux().next())
                .assertNext(message -> {
                    try {
                        Map<String, Object> parsedMessage = objectMapper.readValue(message, Map.class);
                        assertThat(parsedMessage.get("type")).isEqualTo("ERROR");
                        assertThat(parsedMessage.get("message")).isEqualTo("Invalid move");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should correctly extract game ID from WebSocket path")
    void shouldExtractGameIdFromPath() throws Exception {
        Method extractGameIdMethod = ChessWebSocketHandler.class.getDeclaredMethod("extractGameId", String.class);
        extractGameIdMethod.setAccessible(true);

        String path = "/ws/game/test-game-123";
        String extractedId = (String) extractGameIdMethod.invoke(handler, path);

        assertThat(extractedId).isEqualTo("test-game-123");
    }

    @Test
    @DisplayName("Should serialize game state with LocalDateTime correctly")
    void shouldSerializeGameStateWithLocalDateTime() throws Exception {
        Game testGame = new Game("player1", "Player1");
        testGame.setId(GAME_ID);

        when(chessService.getGame(GAME_ID)).thenReturn(Mono.just(testGame));

        Method handleGetStateMethod = ChessWebSocketHandler.class.getDeclaredMethod(
                "handleGetState", String.class, reactor.core.publisher.Sinks.Many.class, reactor.core.publisher.Sinks.Many.class);
        handleGetStateMethod.setAccessible(true);

        reactor.core.publisher.Sinks.Many<String> broadcastSink = reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();
        reactor.core.publisher.Sinks.Many<String> personalSink = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> result = (Mono<Void>) handleGetStateMethod.invoke(handler, GAME_ID, broadcastSink, personalSink);

        StepVerifier.create(result).verifyComplete();

        // Verify the message is valid JSON and contains LocalDateTime
        StepVerifier.create(broadcastSink.asFlux().next())
                .assertNext(message -> {
                    try {
                        Map<String, Object> parsedMessage = objectMapper.readValue(message, Map.class);
                        Map<String, Object> game = (Map<String, Object>) parsedMessage.get("game");
                        assertThat(game.get("createdAt")).isNotNull();
                        assertThat(game.get("updatedAt")).isNotNull();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse JSON with LocalDateTime: " + e.getMessage());
                    }
                })
                .verifyComplete();
    }
}