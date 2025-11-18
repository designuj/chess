package dev.autowired.chess.controller;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.GameListDTO;
import dev.autowired.chess.service.ChessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final ChessService chessService;

    @PostMapping
    public Mono<ResponseEntity<?>> createGame(@RequestBody Map<String, String> request) {
        String playerId = request.get("playerId");
        String playerName = request.get("playerName");
        return chessService.createGame(playerId, playerName)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest()
                                .body(Map.of("error", e.getMessage()))
                ));
    }

    @GetMapping
    public Flux<GameListDTO> getActiveGames(@RequestParam(required = false) String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            // Return empty flux if no player ID provided
            return Flux.empty();
        }
        return chessService.getActiveGamesForPlayer(playerId)
                .map(GameListDTO::fromGame);
    }

    @GetMapping("/my-game")
    public Mono<ResponseEntity<Game>> getMyActiveGame(@RequestParam String playerId) {
        return chessService.getActiveGameForPlayer(playerId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{gameId}")
    public Mono<ResponseEntity<Game>> getGame(@PathVariable String gameId) {
        return chessService.getGame(gameId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/{gameId}/join")
    public Mono<ResponseEntity<?>> joinGame(@PathVariable String gameId, @RequestBody Map<String, String> request) {
        String playerId = request.get("playerId");
        String playerName = request.get("playerName");
        return chessService.joinGame(gameId, playerId, playerName)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest()
                                .body(Map.of("error", e.getMessage()))
                ));
    }

    @PostMapping("/cleanup")
    public Mono<ResponseEntity<Void>> cleanupCompletedGames() {
        return chessService.cleanupCompletedGames()
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }
}