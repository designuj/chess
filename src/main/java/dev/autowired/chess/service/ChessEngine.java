package dev.autowired.chess.service;

import dev.autowired.chess.model.Game;
import dev.autowired.chess.model.Move;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
class ChessEngine {

    boolean makeMove(Game game, String from, String to, String playerId) {
        // Verify it's the player's turn
        String playerColor = getPlayerColor(game, playerId);
        if (playerColor == null || !playerColor.equals(game.getCurrentTurn())) {
            log.warn("Not player's turn: {} vs {}", playerColor, game.getCurrentTurn());
            return false;
        }

        // Parse positions
        int[] fromPos = parsePosition(from);
        int[] toPos = parsePosition(to);

        if (fromPos == null || toPos == null) {
            log.warn("Invalid position format: {} to {}", from, to);
            return false;
        }

        String[][] board = game.getBoard();
        String piece = board[fromPos[0]][fromPos[1]];

        if (piece == null) {
            log.warn("No piece at position: {}", from);
            return false;
        }

        // Verify piece belongs to current player
        boolean isWhitePiece = Character.isUpperCase(piece.charAt(0));
        if ((playerColor.equals("WHITE") && !isWhitePiece) ||
            (playerColor.equals("BLACK") && isWhitePiece)) {
            log.warn("Piece doesn't belong to player");
            return false;
        }

        // Validate move based on piece type
        if (!isValidMove(board, piece, fromPos, toPos)) {
            log.warn("Invalid move for piece {}: {} to {}", piece, from, to);
            return false;
        }

        // Execute move
        board[toPos[0]][toPos[1]] = piece;
        board[fromPos[0]][fromPos[1]] = null;

        // Record move
        Move move = new Move(from, to, piece, playerColor, System.currentTimeMillis());
        game.addMove(move);
        game.switchTurn();

        log.info("Move executed: {} from {} to {}", piece, from, to);
        return true;
    }

    private String getPlayerColor(Game game, String playerId) {
        if (playerId.equals(game.getWhitePlayerId())) {
            return "WHITE";
        } else if (playerId.equals(game.getBlackPlayerId())) {
            return "BLACK";
        }
        return null;
    }

    private int[] parsePosition(String pos) {
        if (pos == null || pos.length() != 2) {
            return null;
        }

        char file = pos.charAt(0); // a-h
        char rank = pos.charAt(1); // 1-8

        if (file < 'a' || file > 'h' || rank < '1' || rank > '8') {
            return null;
        }

        int col = file - 'a';
        int row = 8 - (rank - '0'); // Convert to 0-7 (row 0 is rank 8)

        return new int[]{row, col};
    }

    /**
     * Validate move based on chess rules
     */
    private boolean isValidMove(String[][] board, String piece, int[] from, int[] to) {
        char pieceType = Character.toUpperCase(piece.charAt(0));

        return switch (pieceType) {
            case 'P' -> isValidPawnMove(board, piece, from, to);
            case 'R' -> isValidRookMove(board, from, to);
            case 'N' -> isValidKnightMove(board, from, to);
            case 'B' -> isValidBishopMove(board, from, to);
            case 'Q' -> isValidQueenMove(board, from, to);
            case 'K' -> isValidKingMove(board, from, to);
            default -> false;
        };
    }

    private boolean isValidPawnMove(String[][] board, String piece, int[] from, int[] to) {
        boolean isWhite = Character.isUpperCase(piece.charAt(0));
        int direction = isWhite ? -1 : 1; // White moves up (-), Black moves down (+)

        int rowDiff = to[0] - from[0];
        int colDiff = Math.abs(to[1] - from[1]);

        // Forward move
        if (colDiff == 0) {
            // One square forward
            if (rowDiff == direction && board[to[0]][to[1]] == null) {
                return true;
            }
            // Two squares forward from starting position
            int startRow = isWhite ? 6 : 1;
            if (from[0] == startRow && rowDiff == 2 * direction &&
                board[to[0]][to[1]] == null &&
                board[from[0] + direction][from[1]] == null) {
                return true;
            }
        }

        // Diagonal capture
        if (colDiff == 1 && rowDiff == direction) {
            String targetPiece = board[to[0]][to[1]];
            if (targetPiece != null &&
                Character.isUpperCase(targetPiece.charAt(0)) != isWhite) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidRookMove(String[][] board, int[] from, int[] to) {
        // Rook moves horizontally or vertically
        if (from[0] != to[0] && from[1] != to[1]) {
            return false;
        }

        return isPathClear(board, from, to);
    }

    private boolean isValidKnightMove(String[][] board, int[] from, int[] to) {
        int rowDiff = Math.abs(to[0] - from[0]);
        int colDiff = Math.abs(to[1] - from[1]);

        // L-shape: 2+1 or 1+2
        return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
    }

    private boolean isValidBishopMove(String[][] board, int[] from, int[] to) {
        // Bishop moves diagonally
        if (Math.abs(to[0] - from[0]) != Math.abs(to[1] - from[1])) {
            return false;
        }

        return isPathClear(board, from, to);
    }

    private boolean isValidQueenMove(String[][] board, int[] from, int[] to) {
        // Queen moves like rook or bishop
        return isValidRookMove(board, from, to) || isValidBishopMove(board, from, to);
    }

    private boolean isValidKingMove(String[][] board, int[] from, int[] to) {
        int rowDiff = Math.abs(to[0] - from[0]);
        int colDiff = Math.abs(to[1] - from[1]);

        // King moves one square in any direction
        return rowDiff <= 1 && colDiff <= 1;
    }

    /**
     * Check if path is clear (no pieces blocking)
     */
    private boolean isPathClear(String[][] board, int[] from, int[] to) {
        int rowStep = Integer.compare(to[0], from[0]);
        int colStep = Integer.compare(to[1], from[1]);

        int currentRow = from[0] + rowStep;
        int currentCol = from[1] + colStep;

        while (currentRow != to[0] || currentCol != to[1]) {
            if (board[currentRow][currentCol] != null) {
                return false;
            }
            currentRow += rowStep;
            currentCol += colStep;
        }

        // Check destination square
        String targetPiece = board[to[0]][to[1]];
        if (targetPiece != null) {
            // Can only capture opponent's piece
            String movingPiece = board[from[0]][from[1]];
            return Character.isUpperCase(movingPiece.charAt(0)) !=
                   Character.isUpperCase(targetPiece.charAt(0));
        }

        return true;
    }
}