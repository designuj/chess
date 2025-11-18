package dev.autowired.chess.service;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.GameStatus;
import dev.autowired.chess.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChessGameServiceTest {

    @Mock
    private GameRepository gameRepository;

    private final ChessEngine chessEngine = new ChessEngine();
    private ChessGameService chessGameService;

    private Game testGame;

    @BeforeEach
    void setUp() {
        chessGameService = new ChessGameService(gameRepository, chessEngine);

        testGame = new Game();
        testGame.setId("test-game-123");
        testGame.setWhitePlayerId("player1");
        testGame.setWhitePlayerName("Alice");
        testGame.setStatus(GameStatus.WAITING);
    }

    @Test
    void createGame_shouldCreateNewGame() {
        // Given
        when(gameRepository.save(any(Game.class))).thenReturn(Mono.just(testGame));

        // When
        Mono<Game> result = chessGameService.createGame("player1", "Alice");

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getId()).isEqualTo("test-game-123");
                    assertThat(game.getWhitePlayerId()).isEqualTo("player1");
                    assertThat(game.getWhitePlayerName()).isEqualTo("Alice");
                    assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
                })
                .verifyComplete();
    }

    @Test
    void joinGame_shouldAddBlackPlayerAndStartGame() {
        // Given
        when(gameRepository.findById("test-game-123")).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game savedGame = invocation.getArgument(0);
            return Mono.just(savedGame);
        });

        // When
        Mono<Game> result = chessGameService.joinGame("test-game-123", "player2", "Bob");

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getBlackPlayerId()).isEqualTo("player2");
                    assertThat(game.getBlackPlayerName()).isEqualTo("Bob");
                    assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
                })
                .verifyComplete();
    }

    @Test
    void joinGame_shouldFailIfGameNotWaiting() {
        // Given
        testGame.setStatus(GameStatus.IN_PROGRESS);
        when(gameRepository.findById("test-game-123")).thenReturn(Mono.just(testGame));

        // When
        Mono<Game> result = chessGameService.joinGame("test-game-123", "player2", "Bob");

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalStateException &&
                        throwable.getMessage().equals("Game is not waiting for players")
                )
                .verify();
    }

    @Test
    void getGame_shouldReturnGame() {
        // Given
        when(gameRepository.findById("test-game-123")).thenReturn(Mono.just(testGame));

        // When
        Mono<Game> result = chessGameService.getGame("test-game-123");

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getId()).isEqualTo("test-game-123");
                    assertThat(game.getWhitePlayerId()).isEqualTo("player1");
                })
                .verifyComplete();
    }

    @Test
    void makeMove_shouldExecuteValidMove() {
        // Given
        testGame.setStatus(GameStatus.IN_PROGRESS);
        testGame.setBlackPlayerId("player2");
        testGame.setBlackPlayerName("Bob");

        when(gameRepository.findById("test-game-123")).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game savedGame = invocation.getArgument(0);
            return Mono.just(savedGame);
        });

        // When - valid pawn move e2 to e4
        Mono<Game> result = chessGameService.makeMove("test-game-123", "player1", "e2", "e4");

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getId()).isEqualTo("test-game-123");
                    assertThat(game.getMoves()).hasSize(1);
                    assertThat(game.getMoves().get(0).getFrom()).isEqualTo("e2");
                    assertThat(game.getMoves().get(0).getTo()).isEqualTo("e4");
                    assertThat(game.getCurrentTurn()).isEqualTo("BLACK");
                })
                .verifyComplete();
    }

    @Test
    void makeMove_shouldFailIfGameNotInProgress() {
        // Given
        testGame.setStatus(GameStatus.COMPLETED);
        when(gameRepository.findById("test-game-123")).thenReturn(Mono.just(testGame));

        // When
        Mono<Game> result = chessGameService.makeMove("test-game-123", "player1", "e2", "e4");

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalStateException &&
                        throwable.getMessage().equals("Game is not in progress")
                )
                .verify();
    }

    @Test
    void makeMove_shouldFailIfMoveIsInvalid() {
        // Given
        testGame.setStatus(GameStatus.IN_PROGRESS);
        testGame.setBlackPlayerId("player2");

        when(gameRepository.findById("test-game-123")).thenReturn(Mono.just(testGame));

        // When - invalid pawn move (pawn can't move 3 squares)
        Mono<Game> result = chessGameService.makeMove("test-game-123", "player1", "e2", "e5");

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalStateException &&
                        throwable.getMessage().equals("Invalid move")
                )
                .verify();
    }

    @Test
    void deleteGame_shouldDeleteGame() {
        // Given
        when(gameRepository.deleteById("test-game-123")).thenReturn(Mono.empty());

        // When
        Mono<Void> result = chessGameService.deleteGame("test-game-123");

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }
}