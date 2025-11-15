package dev.autowired.chess.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "games")
public class Game {
    @Id
    private String id;
    private String whitePlayerId;
    private String whitePlayerName;
    private String blackPlayerId;
    private String blackPlayerName;
    private ChessPiece[][] board;
    private PieceColor currentTurn;
    private GameStatus status;
    private List<Move> moveHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String winner;

    public Game(String whitePlayerId, String whitePlayerName) {
        this.id = generateShortId();
        this.whitePlayerId = whitePlayerId;
        this.whitePlayerName = whitePlayerName;
        this.board = initializeBoard();
        this.currentTurn = PieceColor.WHITE;
        this.status = GameStatus.WAITING;
        this.moveHistory = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private String generateShortId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder id = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            id.append(chars.charAt(random.nextInt(chars.length())));
        }
        return id.toString();
    }

    private ChessPiece[][] initializeBoard() {
        ChessPiece[][] board = new ChessPiece[8][8];

        // Black pieces (row 0 and 1)
        board[0][0] = new ChessPiece(PieceType.ROOK, PieceColor.BLACK, new Position(0, 0));
        board[0][1] = new ChessPiece(PieceType.KNIGHT, PieceColor.BLACK, new Position(0, 1));
        board[0][2] = new ChessPiece(PieceType.BISHOP, PieceColor.BLACK, new Position(0, 2));
        board[0][3] = new ChessPiece(PieceType.QUEEN, PieceColor.BLACK, new Position(0, 3));
        board[0][4] = new ChessPiece(PieceType.KING, PieceColor.BLACK, new Position(0, 4));
        board[0][5] = new ChessPiece(PieceType.BISHOP, PieceColor.BLACK, new Position(0, 5));
        board[0][6] = new ChessPiece(PieceType.KNIGHT, PieceColor.BLACK, new Position(0, 6));
        board[0][7] = new ChessPiece(PieceType.ROOK, PieceColor.BLACK, new Position(0, 7));

        for (int col = 0; col < 8; col++) {
            board[1][col] = new ChessPiece(PieceType.PAWN, PieceColor.BLACK, new Position(1, col));
        }

        // White pieces (row 6 and 7)
        for (int col = 0; col < 8; col++) {
            board[6][col] = new ChessPiece(PieceType.PAWN, PieceColor.WHITE, new Position(6, col));
        }

        board[7][0] = new ChessPiece(PieceType.ROOK, PieceColor.WHITE, new Position(7, 0));
        board[7][1] = new ChessPiece(PieceType.KNIGHT, PieceColor.WHITE, new Position(7, 1));
        board[7][2] = new ChessPiece(PieceType.BISHOP, PieceColor.WHITE, new Position(7, 2));
        board[7][3] = new ChessPiece(PieceType.QUEEN, PieceColor.WHITE, new Position(7, 3));
        board[7][4] = new ChessPiece(PieceType.KING, PieceColor.WHITE, new Position(7, 4));
        board[7][5] = new ChessPiece(PieceType.BISHOP, PieceColor.WHITE, new Position(7, 5));
        board[7][6] = new ChessPiece(PieceType.KNIGHT, PieceColor.WHITE, new Position(7, 6));
        board[7][7] = new ChessPiece(PieceType.ROOK, PieceColor.WHITE, new Position(7, 7));

        return board;
    }

    public ChessPiece getPieceAt(Position position) {
        if (!position.isValid()) {
            return null;
        }
        return board[position.getRow()][position.getCol()];
    }

    public void setPieceAt(Position position, ChessPiece piece) {
        if (position.isValid()) {
            board[position.getRow()][position.getCol()] = piece;
            if (piece != null) {
                piece.setPosition(position);
            }
        }
    }
}