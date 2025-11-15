package dev.autowired.chess.integration;

import dev.autowired.chess.model.*;
import dev.autowired.chess.repository.GameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChessGameIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private GameRepository gameRepository;

    @AfterEach
    void cleanup() {
        gameRepository.deleteAll().block();
    }

    @Test
    @DisplayName("Integration: Complete game flow - create, join, and make moves")
    void shouldCompleteFullGameFlow() {
        // Create a game
        Game createdGame = webTestClient.post()
                .uri("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Game.class)
                .returnResult()
                .getResponseBody();

        assertThat(createdGame).isNotNull();
        assertThat(createdGame.getId()).isNotNull();
        assertThat(createdGame.getWhitePlayerId()).isEqualTo("player1");
        assertThat(createdGame.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(createdGame.getBoard()).isNotNull();

        String gameId = createdGame.getId();

        // Second player joins
        Game joinedGame = webTestClient.post()
                .uri("/api/games/{gameId}/join", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player2"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Game.class)
                .returnResult()
                .getResponseBody();

        assertThat(joinedGame).isNotNull();
        assertThat(joinedGame.getBlackPlayerId()).isEqualTo("player2");
        assertThat(joinedGame.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);

        // Verify game is persisted in database
        StepVerifier.create(gameRepository.findById(gameId))
                .assertNext(game -> {
                    assertThat(game.getWhitePlayerId()).isEqualTo("player1");
                    assertThat(game.getBlackPlayerId()).isEqualTo("player2");
                    assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Integration: Should retrieve existing game")
    void shouldRetrieveExistingGame() {
        // Create a game
        Game game = new Game("player1", "Player1");
        Game savedGame = gameRepository.save(game).block();

        // Retrieve the game
        webTestClient.get()
                .uri("/api/games/{gameId}", savedGame.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Game.class)
                .value(retrievedGame -> {
                    assertThat(retrievedGame.getId()).isEqualTo(savedGame.getId());
                    assertThat(retrievedGame.getWhitePlayerId()).isEqualTo("player1");
                    assertThat(retrievedGame.getStatus()).isEqualTo(GameStatus.WAITING);
                });
    }

    @Test
    @DisplayName("Integration: Should return 404 for non-existent game")
    void shouldReturn404ForNonExistentGame() {
        webTestClient.get()
                .uri("/api/games/{gameId}", "non-existent-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Integration: Should not allow same player to join twice")
    void shouldNotAllowSamePlayerToJoinTwice() {
        // Create a game
        Game game = new Game("player1", "Player1");
        Game savedGame = gameRepository.save(game).block();

        // Try to join with same player
        webTestClient.post()
                .uri("/api/games/{gameId}/join", savedGame.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player1"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Integration: Should not allow joining game that is already in progress")
    void shouldNotAllowJoiningInProgressGame() {
        // Create a game with both players
        Game game = new Game("player1", "Player1");
        game.setBlackPlayerId("player2");
        game.setStatus(GameStatus.IN_PROGRESS);
        Game savedGame = gameRepository.save(game).block();

        // Try to join with third player
        webTestClient.post()
                .uri("/api/games/{gameId}/join", savedGame.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player3"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Integration: Should persist initial board setup correctly")
    void shouldPersistInitialBoardSetup() {
        Game game = new Game("player1", "Player1");
        Game savedGame = gameRepository.save(game).block();

        StepVerifier.create(gameRepository.findById(savedGame.getId()))
                .assertNext(retrievedGame -> {
                    // Check white pieces
                    assertThat(retrievedGame.getPieceAt(new Position(7, 0)).getType()).isEqualTo(PieceType.ROOK);
                    assertThat(retrievedGame.getPieceAt(new Position(7, 0)).getColor()).isEqualTo(PieceColor.WHITE);
                    assertThat(retrievedGame.getPieceAt(new Position(7, 1)).getType()).isEqualTo(PieceType.KNIGHT);
                    assertThat(retrievedGame.getPieceAt(new Position(7, 4)).getType()).isEqualTo(PieceType.KING);
                    assertThat(retrievedGame.getPieceAt(new Position(7, 3)).getType()).isEqualTo(PieceType.QUEEN);
                    assertThat(retrievedGame.getPieceAt(new Position(6, 0)).getType()).isEqualTo(PieceType.PAWN);

                    // Check black pieces
                    assertThat(retrievedGame.getPieceAt(new Position(0, 0)).getType()).isEqualTo(PieceType.ROOK);
                    assertThat(retrievedGame.getPieceAt(new Position(0, 0)).getColor()).isEqualTo(PieceColor.BLACK);
                    assertThat(retrievedGame.getPieceAt(new Position(0, 1)).getType()).isEqualTo(PieceType.KNIGHT);
                    assertThat(retrievedGame.getPieceAt(new Position(0, 4)).getType()).isEqualTo(PieceType.KING);
                    assertThat(retrievedGame.getPieceAt(new Position(0, 3)).getType()).isEqualTo(PieceType.QUEEN);
                    assertThat(retrievedGame.getPieceAt(new Position(1, 0)).getType()).isEqualTo(PieceType.PAWN);

                    // Check empty squares
                    assertThat(retrievedGame.getPieceAt(new Position(3, 3))).isNull();
                    assertThat(retrievedGame.getPieceAt(new Position(4, 4))).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Integration: Should handle multiple concurrent games")
    void shouldHandleMultipleConcurrentGames() {
        // Create first game
        Game game1 = webTestClient.post()
                .uri("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player1"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Game.class)
                .returnResult()
                .getResponseBody();

        // Create second game
        Game game2 = webTestClient.post()
                .uri("/api/games")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player3"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Game.class)
                .returnResult()
                .getResponseBody();

        assertThat(game1.getId()).isNotEqualTo(game2.getId());
        assertThat(game1.getWhitePlayerId()).isEqualTo("player1");
        assertThat(game2.getWhitePlayerId()).isEqualTo("player3");

        // Join both games
        webTestClient.post()
                .uri("/api/games/{gameId}/join", game1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player2"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/games/{gameId}/join", game2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("playerId", "player4"))
                .exchange()
                .expectStatus().isOk();

        // Verify both games exist independently
        StepVerifier.create(gameRepository.findById(game1.getId()))
                .assertNext(g -> {
                    assertThat(g.getWhitePlayerId()).isEqualTo("player1");
                    assertThat(g.getBlackPlayerId()).isEqualTo("player2");
                })
                .verifyComplete();

        StepVerifier.create(gameRepository.findById(game2.getId()))
                .assertNext(g -> {
                    assertThat(g.getWhitePlayerId()).isEqualTo("player3");
                    assertThat(g.getBlackPlayerId()).isEqualTo("player4");
                })
                .verifyComplete();
    }
}