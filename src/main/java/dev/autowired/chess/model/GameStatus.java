package dev.autowired.chess.model;

public enum GameStatus {
    WAITING,      // Waiting for second player
    IN_PROGRESS,  // Game is active
    COMPLETED,    // Game finished normally
    ABANDONED     // Game abandoned by player
}