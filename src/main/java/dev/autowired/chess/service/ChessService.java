package dev.autowired.chess.service;

import dev.autowired.chess.model.*;
import dev.autowired.chess.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChessService {

    private final GameRepository gameRepository;
    private static final int MAX_ACTIVE_GAMES = 10;

    public Mono<Game> createGame(String playerId, String playerName) {
        return getActiveGamesCount()
                .flatMap(count -> {
                    if (count >= MAX_ACTIVE_GAMES) {
                        return Mono.error(new IllegalStateException("Maximum number of active games reached"));
                    }
                    Game game = new Game(playerId, playerName);
                    return gameRepository.save(game);
                });
    }

    public Mono<Game> joinGame(String gameId, String playerId, String playerName) {
        return gameRepository.findById(gameId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Game not found")))
                .flatMap(game -> {
                    if (game.getStatus() != GameStatus.WAITING) {
                        return Mono.error(new IllegalStateException("Game is not accepting players"));
                    }
                    if (game.getWhitePlayerId().equals(playerId)) {
                        return Mono.error(new IllegalStateException("You are already in this game"));
                    }
                    game.setBlackPlayerId(playerId);
                    game.setBlackPlayerName(playerName);
                    game.setStatus(GameStatus.IN_PROGRESS);
                    game.setUpdatedAt(LocalDateTime.now());
                    return gameRepository.save(game);
                });
    }

    public Mono<Game> getGame(String gameId) {
        return gameRepository.findById(gameId);
    }

    public Flux<Game> getActiveGames() {
        return gameRepository.findAll()
                .filter(game -> game.getStatus() == GameStatus.WAITING ||
                               game.getStatus() == GameStatus.IN_PROGRESS)
                .sort((g1, g2) -> g2.getCreatedAt().compareTo(g1.getCreatedAt()))
                .take(MAX_ACTIVE_GAMES);
    }

    public Mono<Long> getActiveGamesCount() {
        return gameRepository.findAll()
                .filter(game -> game.getStatus() == GameStatus.WAITING ||
                               game.getStatus() == GameStatus.IN_PROGRESS)
                .count();
    }

    public Mono<Void> cleanupCompletedGames() {
        return gameRepository.findAll()
                .filter(game -> game.getStatus() == GameStatus.COMPLETED)
                .flatMap(game -> gameRepository.deleteById(game.getId()))
                .then();
    }

    public Mono<Game> makeMove(String gameId, String playerId, Position from, Position to) {
        return gameRepository.findById(gameId)
                .flatMap(game -> {
                    if (game.getStatus() != GameStatus.IN_PROGRESS) {
                        return Mono.error(new IllegalStateException("Game is not in progress"));
                    }

                    // Verify it's the player's turn
                    PieceColor playerColor = getPlayerColor(game, playerId);
                    if (playerColor == null) {
                        return Mono.error(new IllegalStateException("You are not in this game"));
                    }
                    if (game.getCurrentTurn() != playerColor) {
                        return Mono.error(new IllegalStateException("It's not your turn"));
                    }

                    // Validate and execute move
                    ChessPiece piece = game.getPieceAt(from);
                    if (piece == null || piece.getColor() != playerColor) {
                        return Mono.error(new IllegalStateException("Invalid piece selection"));
                    }

                    if (!isValidMove(game, from, to, piece)) {
                        return Mono.error(new IllegalStateException("Invalid move"));
                    }

                    // Execute move
                    ChessPiece capturedPiece = game.getPieceAt(to);
                    game.setPieceAt(to, piece);
                    game.setPieceAt(from, null);

                    // Record move
                    Move move = new Move(from, to, piece.getType(), piece.getColor(), LocalDateTime.now(), capturedPiece);
                    game.getMoveHistory().add(move);

                    // Switch turn
                    game.setCurrentTurn(game.getCurrentTurn() == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE);
                    game.setUpdatedAt(LocalDateTime.now());

                    // Check for game end (simplified - just check if king is captured)
                    if (capturedPiece != null && capturedPiece.getType() == PieceType.KING) {
                        game.setStatus(GameStatus.COMPLETED);
                        game.setWinner(playerId);
                    }

                    return gameRepository.save(game);
                });
    }

    private PieceColor getPlayerColor(Game game, String playerId) {
        if (game.getWhitePlayerId().equals(playerId)) {
            return PieceColor.WHITE;
        } else if (playerId.equals(game.getBlackPlayerId())) {
            return PieceColor.BLACK;
        }
        return null;
    }

    private boolean isValidMove(Game game, Position from, Position to, ChessPiece piece) {
        if (!to.isValid()) {
            return false;
        }

        // Check if destination has friendly piece
        ChessPiece destPiece = game.getPieceAt(to);
        if (destPiece != null && destPiece.getColor() == piece.getColor()) {
            return false;
        }

        int rowDiff = to.getRow() - from.getRow();
        int colDiff = to.getCol() - from.getCol();
        int absRowDiff = Math.abs(rowDiff);
        int absColDiff = Math.abs(colDiff);

        return switch (piece.getType()) {
            case PAWN -> isValidPawnMove(game, from, to, piece, rowDiff, colDiff, absRowDiff, absColDiff);
            case ROOK -> isValidRookMove(game, from, to, absRowDiff, absColDiff);
            case KNIGHT -> isValidKnightMove(absRowDiff, absColDiff);
            case BISHOP -> isValidBishopMove(game, from, to, absRowDiff, absColDiff);
            case QUEEN -> isValidQueenMove(game, from, to, absRowDiff, absColDiff);
            case KING -> isValidKingMove(absRowDiff, absColDiff);
        };
    }

    private boolean isValidPawnMove(Game game, Position from, Position to, ChessPiece piece,
                                     int rowDiff, int colDiff, int absRowDiff, int absColDiff) {
        int direction = piece.getColor() == PieceColor.WHITE ? -1 : 1;
        ChessPiece destPiece = game.getPieceAt(to);

        // Forward move
        if (colDiff == 0 && destPiece == null) {
            if (rowDiff == direction) {
                return true;
            }
            // Initial two-square move
            int startRow = piece.getColor() == PieceColor.WHITE ? 6 : 1;
            if (from.getRow() == startRow && rowDiff == 2 * direction) {
                Position middle = new Position(from.getRow() + direction, from.getCol());
                return game.getPieceAt(middle) == null;
            }
        }

        // Capture
        if (absColDiff == 1 && rowDiff == direction && destPiece != null) {
            return true;
        }

        return false;
    }

    private boolean isValidRookMove(Game game, Position from, Position to, int absRowDiff, int absColDiff) {
        if (absRowDiff > 0 && absColDiff > 0) {
            return false;
        }
        return isPathClear(game, from, to);
    }

    private boolean isValidKnightMove(int absRowDiff, int absColDiff) {
        return (absRowDiff == 2 && absColDiff == 1) || (absRowDiff == 1 && absColDiff == 2);
    }

    private boolean isValidBishopMove(Game game, Position from, Position to, int absRowDiff, int absColDiff) {
        if (absRowDiff != absColDiff) {
            return false;
        }
        return isPathClear(game, from, to);
    }

    private boolean isValidQueenMove(Game game, Position from, Position to, int absRowDiff, int absColDiff) {
        if (absRowDiff == absColDiff || absRowDiff == 0 || absColDiff == 0) {
            return isPathClear(game, from, to);
        }
        return false;
    }

    private boolean isValidKingMove(int absRowDiff, int absColDiff) {
        return absRowDiff <= 1 && absColDiff <= 1;
    }

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
}