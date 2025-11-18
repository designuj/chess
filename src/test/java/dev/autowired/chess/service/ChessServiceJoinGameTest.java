package dev.autowired.chess.service;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.GameStatus;
import dev.autowired.chess.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChessServiceJoinGameTest {

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private ChessService chessService;

    @Test
    void shouldAllowPlayerToJoinGameSuccessfully() {
        // Given
        String gameId = "game123";
        String playerId = "player456";
        String playerName = "Player Two";

        Game existingGame = new Game("player123", "Player One");
        existingGame.setId(gameId);
        existingGame.setStatus(GameStatus.WAITING);

        Game savedGame = new Game("player123", "Player One");
        savedGame.setId(gameId);
        savedGame.setBlackPlayerId(playerId);
        savedGame.setBlackPlayerName(playerName);
        savedGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(gameId)).thenReturn(Mono.just(existingGame));
        when(gameRepository.save(any(Game.class))).thenReturn(Mono.just(savedGame));

        // When
        Mono<Game> result = chessService.joinGame(gameId, playerId, playerName);

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getBlackPlayerId()).isEqualTo(playerId);
                    assertThat(game.getBlackPlayerName()).isEqualTo(playerName);
                    assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectJoinWhenPlayerIsAlreadyWhitePlayer() {
        // Given
        String gameId = "game123";
        String playerId = "player123"; // Same as white player
        String playerName = "Player One";

        Game existingGame = new Game(playerId, "Player One");
        existingGame.setId(gameId);
        existingGame.setStatus(GameStatus.WAITING);

        when(gameRepository.findById(gameId)).thenReturn(Mono.just(existingGame));

        // When
        Mono<Game> result = chessService.joinGame(gameId, playerId, playerName);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().equals("You cannot join your own game"))
                .verify();
    }

    @Test
    void shouldRejectJoinWhenGameIsFull() {
        // Given
        String gameId = "game123";
        String playerId = "player789";
        String playerName = "Player Three";

        Game existingGame = new Game("player123", "Player One");
        existingGame.setId(gameId);
        existingGame.setBlackPlayerId("player456");
        existingGame.setBlackPlayerName("Player Two");
        existingGame.setStatus(GameStatus.WAITING);

        when(gameRepository.findById(gameId)).thenReturn(Mono.just(existingGame));

        // When
        Mono<Game> result = chessService.joinGame(gameId, playerId, playerName);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().equals("Game is full"))
                .verify();
    }

    @Test
    void shouldRejectJoinWhenGameNotInWaitingStatus() {
        // Given
        String gameId = "game123";
        String playerId = "player456";
        String playerName = "Player Two";

        Game existingGame = new Game("player123", "Player One");
        existingGame.setId(gameId);
        existingGame.setStatus(GameStatus.IN_PROGRESS); // Not waiting

        when(gameRepository.findById(gameId)).thenReturn(Mono.just(existingGame));

        // When
        Mono<Game> result = chessService.joinGame(gameId, playerId, playerName);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().equals("Game is not accepting players"))
                .verify();
    }

    @Test
    void shouldRejectJoinWhenGameNotFound() {
        // Given
        String gameId = "nonexistent";
        String playerId = "player456";
        String playerName = "Player Two";

        when(gameRepository.findById(gameId)).thenReturn(Mono.empty());

        // When
        Mono<Game> result = chessService.joinGame(gameId, playerId, playerName);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().equals("Game not found"))
                .verify();
    }

    @Test
    void shouldHandleNullPlayerIdGracefully() {
        // Given
        String gameId = "game123";
        String playerId = null; // Null player ID
        String playerName = "Player Two";

        Game existingGame = new Game("player123", "Player One");
        existingGame.setId(gameId);
        existingGame.setStatus(GameStatus.WAITING);

        Game savedGame = new Game("player123", "Player One");
        savedGame.setId(gameId);
        savedGame.setBlackPlayerId(null);
        savedGame.setBlackPlayerName(playerName);
        savedGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(gameId)).thenReturn(Mono.just(existingGame));
        when(gameRepository.save(any(Game.class))).thenReturn(Mono.just(savedGame));

        // When
        Mono<Game> result = chessService.joinGame(gameId, playerId, playerName);

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getBlackPlayerId()).isNull();
                    assertThat(game.getBlackPlayerName()).isEqualTo(playerName);
                    assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
                })
                .verifyComplete();
    }
}
