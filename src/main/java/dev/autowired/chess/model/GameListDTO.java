package dev.autowired.chess.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameListDTO {
    private String id;
    private String whitePlayerId;
    private String whitePlayerName;
    private String blackPlayerId;
    private String blackPlayerName;
    private GameStatus status;

    public static GameListDTO fromGame(Game game) {
        return new GameListDTO(
                game.getId(),
                game.getWhitePlayerId(),
                game.getWhitePlayerName(),
                game.getBlackPlayerId(),
                game.getBlackPlayerName(),
                game.getStatus()
        );
    }
}