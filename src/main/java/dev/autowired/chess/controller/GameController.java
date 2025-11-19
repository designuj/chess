package dev.autowired.chess.controller;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.service.ChessGameService;
import dev.autowired.chess.websocket.UserWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
class GameController {

    private final ChessGameService chessGameService;
    private final UserWebSocketHandler webSocketHandler;

    @GetMapping("/{gameId}")
    Mono<Game> getGame(@PathVariable String gameId) {
        log.info("Fetching game: {}", gameId);
        return chessGameService.getGame(gameId);
    }

    @GetMapping("/{gameId}/possible-moves")
    Mono<java.util.List<String>> getPossibleMoves(
            @PathVariable String gameId,
            @RequestParam String from,
            @RequestParam String playerId) {
        log.info("Fetching possible moves for game {}, from {}, player {}", gameId, from, playerId);
        return chessGameService.getPossibleMoves(gameId, from, playerId);
    }

    @PostMapping("/{gameId}/move")
    Mono<Game> makeMove(
            @PathVariable String gameId,
            @RequestBody MoveRequest request) {
        log.info("Making move in game {}: {} to {}", gameId, request.from(), request.to());
        return chessGameService.makeMove(gameId, request.playerId(), request.from(), request.to())
                .doOnSuccess(game -> webSocketHandler.broadcastGameUpdate(game));
    }

    record MoveRequest(String playerId, String from, String to) {}
}