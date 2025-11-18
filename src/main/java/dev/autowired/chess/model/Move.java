package dev.autowired.chess.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Move {
    private String from;  // e.g., "e2"
    private String to;    // e.g., "e4"
    private String piece; // e.g., "P" (Pawn), "N" (Knight), etc.
    private String player; // WHITE or BLACK
    private long timestamp;

    public Move(String from, String to, String piece, String player) {
        this.from = from;
        this.to = to;
        this.piece = piece;
        this.player = player;
        this.timestamp = System.currentTimeMillis();
    }
}