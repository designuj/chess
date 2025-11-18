package dev.autowired.chess.service;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.GameStatus;
import dev.autowired.chess.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChessServiceFilteringTest {

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private ChessService chessService;

    @Test
    void shouldIncludeAllWaitingGamesAndUserInProgressGames() {
        // Given
        String playerId = "player123";

        // Game where player is NOT the white player (should be INCLUDED in WAITING games)
        Game otherGame = new Game("otherPlayer", "Other Player");
        otherGame.setStatus(GameStatus.WAITING);

        // Game where player is participating as black (should be INCLUDED in IN_PROGRESS games)
        Game inProgressGame = new Game("whitePlayer", "White Player");
        inProgressGame.setStatus(GameStatus.IN_PROGRESS);
        inProgressGame.setBlackPlayerId(playerId);

        when(gameRepository.findAll()).thenReturn(Flux.just(otherGame, inProgressGame));

        // When
        Flux<Game> result = chessService.getActiveGamesForPlayer(playerId);

        // Then
        StepVerifier.create(result)
                .expectNextCount(2) // Should get inProgressGame + otherGame (not own waiting game)
                .verifyComplete();
    }

    @Test
    void shouldIncludeGamesWherePlayerIsWhiteInInProgressGames() {
        // Given
        String playerId = "player123";

        // Game where player is the white player and game is in progress
        Game whitePlayerGame = new Game(playerId, "Player Name");
        whitePlayerGame.setStatus(GameStatus.IN_PROGRESS);
        whitePlayerGame.setBlackPlayerId("opponent");

        when(gameRepository.findAll()).thenReturn(Flux.just(whitePlayerGame));

        // When
        Flux<Game> result = chessService.getActiveGamesForPlayer(playerId);

        // Then
        StepVerifier.create(result)
                .assertNext(game -> {
                    assertThat(game.getWhitePlayerId()).isEqualTo(playerId);
                    assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyForCompletedGames() {
        // Given
        String playerId = "player123";

        Game completedGame = new Game(playerId, "Player Name");
        completedGame.setStatus(GameStatus.COMPLETED);

        when(gameRepository.findAll()).thenReturn(Flux.just(completedGame));

        // When
        Flux<Game> result = chessService.getActiveGamesForPlayer(playerId);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldHandleNullPlayerIdGracefully() {
        // Given
        String playerId = null;

        // When - null player ID should return empty result without calling repository
        Flux<Game> result = chessService.getActiveGamesForPlayer(playerId);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }
}
