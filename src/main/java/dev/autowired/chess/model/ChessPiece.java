package dev.autowired.chess.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChessPiece {
    private PieceType type;
    private PieceColor color;
    private Position position;

    public String getSymbol() {
        String symbol = switch (type) {
            case KING -> "♔";
            case QUEEN -> "♕";
            case ROOK -> "♖";
            case BISHOP -> "♗";
            case KNIGHT -> "♘";
            case PAWN -> "♙";
        };

        // Unicode for black pieces
        if (color == PieceColor.BLACK) {
            symbol = switch (type) {
                case KING -> "♚";
                case QUEEN -> "♛";
                case ROOK -> "♜";
                case BISHOP -> "♝";
                case KNIGHT -> "♞";
                case PAWN -> "♟";
            };
        }

        return symbol;
    }
}