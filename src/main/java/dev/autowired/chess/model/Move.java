package dev.autowired.chess.model;

public record Move (
    String from,
    String to,
    String piece,
    String player,
    long timestamp
) {
}