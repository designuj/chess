package dev.autowired.chess.service;

import dev.autowired.chess.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced chess rules engine with proper validation and game state checking.
 */
@Slf4j
@Service
public class ChessRulesEngine {

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    /**
     * Validates a chess move according to chess rules.
     */
    public ValidationResult validateMove(Game game, Position from, Position to, String playerId) {
        // 1. Basic validation
        if (!from.isValid() || !to.isValid()) {
            return ValidationResult.invalid("Invalid position");
        }

        if (from.equals(to)) {
            return ValidationResult.invalid("Cannot move to the same position");
        }

        // 2. Check if player can move
        PieceColor playerColor = getPlayerColor(game, playerId);
        if (playerColor == null) {
            return ValidationResult.invalid("Player not in this game");
        }

        if (game.getCurrentTurn() != playerColor) {
            return ValidationResult.invalid("Not your turn");
        }

        // 3. Check piece exists and belongs to player
        ChessPiece piece = game.getPieceAt(from);
        if (piece == null) {
            return ValidationResult.invalid("No piece at source position");
        }

        if (piece.getColor() != playerColor) {
            return ValidationResult.invalid("Cannot move opponent's piece");
        }

        // 4. Check destination doesn't have friendly piece
        ChessPiece destinationPiece = game.getPieceAt(to);
        if (destinationPiece != null && destinationPiece.getColor() == playerColor) {
            return ValidationResult.invalid("Cannot capture your own piece");
        }

        // 5. Check if piece can make this move
        if (!isPieceMoveValid(game, from, to, piece)) {
            return ValidationResult.invalid("Invalid move for this piece");
        }

        // 6. Check if path is clear (except for knights)
        if (piece.getType() != PieceType.KNIGHT && !isPathClear(game, from, to)) {
            return ValidationResult.invalid("Path is blocked");
        }

        // 7. Check if move would leave king in check
        if (wouldLeaveKingInCheck(game, from, to, playerColor)) {
            return ValidationResult.invalid("Move would leave king in check");
        }

        return ValidationResult.valid();
    }

    /**
     * Check the game status (in progress, check, checkmate, stalemate).
     */
    public GameStatus checkGameStatus(Game game, PieceColor currentTurn) {
        boolean isInCheck = isKingInCheck(game, currentTurn);
        List<Move> validMoves = getAllValidMoves(game, currentTurn);

        if (validMoves.isEmpty()) {
            return isInCheck ? GameStatus.COMPLETED : GameStatus.COMPLETED; // Checkmate or Stalemate
        }

        return GameStatus.IN_PROGRESS; // Continue playing
    }

    /**
     * Get all valid moves for a player.
     */
    public List<Move> getAllValidMoves(Game game, PieceColor color) {
        List<Move> validMoves = new ArrayList<>();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Position from = new Position(row, col);
                ChessPiece piece = game.getPieceAt(from);

                if (piece != null && piece.getColor() == color) {
                    for (int toRow = 0; toRow < 8; toRow++) {
                        for (int toCol = 0; toCol < 8; toCol++) {
                            Position to = new Position(toRow, toCol);

                            if (isPieceMoveValid(game, from, to, piece) &&
                                (piece.getType() == PieceType.KNIGHT || isPathClear(game, from, to)) &&
                                !wouldLeaveKingInCheck(game, from, to, color)) {

                                validMoves.add(new Move(from, to, piece.getType(), color, null, game.getPieceAt(to)));
                            }
                        }
                    }
                }
            }
        }

        return validMoves;
    }

    /**
     * Check if a specific piece can make a move (piece-specific rules).
     */
    private boolean isPieceMoveValid(Game game, Position from, Position to, ChessPiece piece) {
        int rowDiff = to.getRow() - from.getRow();
        int colDiff = to.getCol() - from.getCol();
        int absRowDiff = Math.abs(rowDiff);
        int absColDiff = Math.abs(colDiff);

        return switch (piece.getType()) {
            case PAWN -> isValidPawnMove(game, from, to, piece, rowDiff, colDiff);
            case ROOK -> isValidRookMove(absRowDiff, absColDiff);
            case KNIGHT -> isValidKnightMove(absRowDiff, absColDiff);
            case BISHOP -> isValidBishopMove(absRowDiff, absColDiff);
            case QUEEN -> isValidQueenMove(absRowDiff, absColDiff);
            case KING -> isValidKingMove(absRowDiff, absColDiff);
        };
    }

    private boolean isValidPawnMove(Game game, Position from, Position to, ChessPiece piece, int rowDiff, int colDiff) {
        int direction = piece.getColor() == PieceColor.WHITE ? -1 : 1;
        ChessPiece destinationPiece = game.getPieceAt(to);

        // Forward move
        if (colDiff == 0 && destinationPiece == null) {
            if (rowDiff == direction) {
                return true;
            }
            // Initial two-square move
            int startRow = piece.getColor() == PieceColor.WHITE ? 6 : 1;
            return from.getRow() == startRow && rowDiff == 2 * direction;
        }

        // Diagonal capture
        if (Math.abs(colDiff) == 1 && rowDiff == direction && destinationPiece != null) {
            return destinationPiece.getColor() != piece.getColor();
        }

        return false;
    }

    private boolean isValidRookMove(int absRowDiff, int absColDiff) {
        return (absRowDiff == 0 && absColDiff > 0) || (absRowDiff > 0 && absColDiff == 0);
    }

    private boolean isValidKnightMove(int absRowDiff, int absColDiff) {
        return (absRowDiff == 2 && absColDiff == 1) || (absRowDiff == 1 && absColDiff == 2);
    }

    private boolean isValidBishopMove(int absRowDiff, int absColDiff) {
        return absRowDiff == absColDiff && absRowDiff > 0;
    }

    private boolean isValidQueenMove(int absRowDiff, int absColDiff) {
        return isValidRookMove(absRowDiff, absColDiff) || isValidBishopMove(absRowDiff, absColDiff);
    }

    private boolean isValidKingMove(int absRowDiff, int absColDiff) {
        return absRowDiff <= 1 && absColDiff <= 1 && (absRowDiff > 0 || absColDiff > 0);
    }

    /**
     * Check if the path between two positions is clear.
     */
    private boolean isPathClear(Game game, Position from, Position to) {
        int rowStep = Integer.compare(to.getRow(), from.getRow());
        int colStep = Integer.compare(to.getCol(), from.getCol());

        int currentRow = from.getRow() + rowStep;
        int currentCol = from.getCol() + colStep;

        while (currentRow != to.getRow() || currentCol != to.getCol()) {
            if (game.getPieceAt(new Position(currentRow, currentCol)) != null) {
                return false;
            }
            currentRow += rowStep;
            currentCol += colStep;
        }

        return true;
    }

    /**
     * Check if the king is in check.
     */
    private boolean isKingInCheck(Game game, PieceColor color) {
        Position kingPosition = findKing(game, color);
        if (kingPosition == null) {
            log.error("King not found for color {}", color);
            return false; // This should never happen in a valid game
        }

        PieceColor opponentColor = color == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;

        // Check if any opponent piece can attack the king
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Position from = new Position(row, col);
                ChessPiece piece = game.getPieceAt(from);

                if (piece != null && piece.getColor() == opponentColor) {
                    if (isPieceMoveValid(game, from, kingPosition, piece) &&
                        (piece.getType() == PieceType.KNIGHT || isPathClear(game, from, kingPosition))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if a move would leave the king in check.
     */
    private boolean wouldLeaveKingInCheck(Game game, Position from, Position to, PieceColor color) {
        // Simulate the move
        ChessPiece piece = game.getPieceAt(from);
        ChessPiece capturedPiece = game.getPieceAt(to);

        game.setPieceAt(to, piece);
        game.setPieceAt(from, null);

        boolean kingInCheck = isKingInCheck(game, color);

        // Restore the board
        game.setPieceAt(from, piece);
        game.setPieceAt(to, capturedPiece);

        return kingInCheck;
    }

    /**
     * Find the king position for a given color.
     */
    private Position findKing(Game game, PieceColor color) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Position position = new Position(row, col);
                ChessPiece piece = game.getPieceAt(position);

                if (piece != null && piece.getType() == PieceType.KING && piece.getColor() == color) {
                    return position;
                }
            }
        }
        return null;
    }

    private PieceColor getPlayerColor(Game game, String playerId) {
        if (playerId.equals(game.getWhitePlayerId())) {
            return PieceColor.WHITE;
        } else if (playerId.equals(game.getBlackPlayerId())) {
            return PieceColor.BLACK;
        }
        return null;
    }
}
