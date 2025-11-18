package dev.autowired.chess.controller;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.GameListDTO;
import dev.autowired.chess.model.GameStatus;
import dev.autowired.chess.service.ChessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameControllerFilterTest {

    @Mock
    private ChessService chessService;

    @InjectMocks
    private GameController gameController;

    @Test
    void shouldReturnEmptyFluxWhenNoPlayerIdProvided() {
        // When
        Flux<GameListDTO> result = gameController.getActiveGames(null);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyFluxWhenEmptyPlayerIdProvided() {
        // When
        Flux<GameListDTO> result = gameController.getActiveGames("");

        // Then
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldReturnFilteredGamesForValidPlayerId() {
        // Given
        String playerId = "player123";

        // Create a game where player is the white player (should be shown as own waiting game)
        Game ownGame = new Game(playerId, "Player Name");
        ownGame.setStatus(GameStatus.WAITING);

        // Create a game where player is the black player (should be shown in IN_PROGRESS)
        Game gameAsBlack = new Game("whitePlayer", "White Player");
        gameAsBlack.setStatus(GameStatus.IN_PROGRESS);
        gameAsBlack.setBlackPlayerId(playerId);

        when(chessService.getActiveGamesForPlayer(playerId))
                .thenReturn(Flux.just(ownGame, gameAsBlack));

        // When
        Flux<GameListDTO> result = gameController.getActiveGames(playerId);

        // Then
        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldHandleJoinGameWhenPlayerIsAlreadyWhitePlayer() {
        // Given
        String gameId = "game123";
        String playerId = "player123";
        String playerName = "Player Name";
        Map<String, String> request = Map.of("playerId", playerId, "playerName", playerName);

        when(chessService.joinGame(gameId, playerId, playerName))
                .thenReturn(Mono.error(new IllegalStateException("You are already in this game")));

        // When
        Mono<ResponseEntity<?>> result = gameController.joinGame(gameId, request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response -> response.getStatusCode().is4xxClientError())
                .verifyComplete();
    }

    @Test
    void shouldHandleJoinGameWhenGameIsFull() {
        // Given
        String gameId = "game123";
        String playerId = "player123";
        String playerName = "Player Name";
        Map<String, String> request = Map.of("playerId", playerId, "playerName", playerName);

        when(chessService.joinGame(gameId, playerId, playerName))
                .thenReturn(Mono.error(new IllegalStateException("Game is full")));

        // When
        Mono<ResponseEntity<?>> result = gameController.joinGame(gameId, request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response -> response.getStatusCode().is4xxClientError())
                .verifyComplete();
    }
}
