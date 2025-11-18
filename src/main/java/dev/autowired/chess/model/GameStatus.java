package dev.autowired.chess.model;

public enum GameStatus {
    WAITING,      // Waiting for players
    IN_PROGRESS,  // Game is active
    COMPLETED,    // Game finished
    ABANDONED     // Game abandoned
}