package dev.autowired.chess.service;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.GameStatus;
import dev.autowired.chess.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChessGameService {

    private final GameRepository gameRepository;
    private final ChessEngine chessEngine;

    public Mono<Game> createGame(String whitePlayerId, String whitePlayerName) {
        Game game = new Game();
        game.setWhitePlayerId(whitePlayerId);
        game.setWhitePlayerName(whitePlayerName);
        game.setStatus(GameStatus.WAITING);

        return gameRepository.save(game)
                .doOnSuccess(g -> log.info("Game created: {} by player {}", g.getId(), whitePlayerName));
    }

    public Mono<Game> joinGame(String gameId, String blackPlayerId, String blackPlayerName) {
        return gameRepository.findById(gameId)
                .flatMap(game -> {
                    if (game.getStatus() != GameStatus.WAITING) {
                        return Mono.error(new IllegalStateException("Game is not waiting for players"));
                    }

                    game.setBlackPlayerId(blackPlayerId);
                    game.setBlackPlayerName(blackPlayerName);
                    game.setStatus(GameStatus.IN_PROGRESS);

                    return gameRepository.save(game)
                            .doOnSuccess(g -> log.info("Player {} joined game {}", blackPlayerName, gameId));
                });
    }

    public Mono<Game> getGame(String gameId) {
        return gameRepository.findById(gameId);
    }

    public Mono<java.util.List<String>> getPossibleMoves(String gameId, String from, String playerId) {
        return gameRepository.findById(gameId)
                .map(game -> {
                    if (game.getStatus() != GameStatus.IN_PROGRESS) {
                        return java.util.Collections.emptyList();
                    }
                    return chessEngine.getPossibleMoves(game, from, playerId);
                });
    }

    public Mono<Game> makeMove(String gameId, String playerId, String from, String to) {
        return gameRepository.findById(gameId)
                .flatMap(game -> {
                    if (game.getStatus() != GameStatus.IN_PROGRESS) {
                        return Mono.error(new IllegalStateException("Game is not in progress"));
                    }

                    boolean success = chessEngine.makeMove(game, from, to, playerId);

                    if (!success) {
                        return Mono.error(new IllegalStateException("Invalid move"));
                    }

                    return gameRepository.save(game)
                            .doOnSuccess(g -> log.info("Move made in game {}: {} to {}", gameId, from, to));
                });
    }

    public Mono<Void> deleteGame(String gameId) {
        return gameRepository.deleteById(gameId)
                .doOnSuccess(v -> log.info("Game deleted: {}", gameId));
    }
}