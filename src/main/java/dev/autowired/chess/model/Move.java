package dev.autowired.chess.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Move {
    private Position from;
    private Position to;
    private PieceType pieceType;
    private PieceColor color;
    private LocalDateTime timestamp;
    private ChessPiece capturedPiece;
}