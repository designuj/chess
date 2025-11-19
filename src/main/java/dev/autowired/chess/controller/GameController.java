package dev.autowired.chess.controller;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.service.ChessGameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
class GameController {

    private final ChessGameService chessGameService;

    @GetMapping("/{gameId}")
    Mono<Game> getGame(@PathVariable String gameId) {
        log.info("Fetching game: {}", gameId);
        return chessGameService.getGame(gameId);
    }
}