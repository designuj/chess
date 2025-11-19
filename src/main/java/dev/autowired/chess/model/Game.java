package dev.autowired.chess.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "games")
public class Game {
    @Id
    private String id;

    private String whitePlayerId;
    private String whitePlayerName;
    private String blackPlayerId;
    private String blackPlayerName;

    private GameStatus status;
    private String currentTurn;

    private List<Move> moves;
    private String[][] board;

    private String winner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Game() {
        this.moves = new ArrayList<>();
        this.status = GameStatus.WAITING;
        this.currentTurn = "WHITE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.board = initializeBoard();
    }

    private String[][] initializeBoard() {
        String[][] board = new String[8][8];

        // Black pieces (top)
        board[0] = new String[]{"r", "n", "b", "q", "k", "b", "n", "r"};
        board[1] = new String[]{"p", "p", "p", "p", "p", "p", "p", "p"};

        // Empty squares
        for (int i = 2; i < 6; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = null;
            }
        }

        // White pieces (bottom)
        board[6] = new String[]{"P", "P", "P", "P", "P", "P", "P", "P"};
        board[7] = new String[]{"R", "N", "B", "Q", "K", "B", "N", "R"};

        return board;
    }

    public void addMove(Move move) {
        this.moves.add(move);
        this.updatedAt = LocalDateTime.now();
    }

    public void switchTurn() {
        this.currentTurn = this.currentTurn.equals("WHITE") ? "BLACK" : "WHITE";
        this.updatedAt = LocalDateTime.now();
    }
}