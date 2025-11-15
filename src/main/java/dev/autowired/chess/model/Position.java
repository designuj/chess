package dev.autowired.chess.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private int row;
    private int col;

    public boolean isValid() {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    public static Position fromNotation(String notation) {
        if (notation == null || notation.length() != 2) {
            throw new IllegalArgumentException("Invalid notation: " + notation);
        }
        char colChar = notation.charAt(0);
        char rowChar = notation.charAt(1);

        int col = colChar - 'a';
        int row = 8 - (rowChar - '0');

        return new Position(row, col);
    }

    public String toNotation() {
        char colChar = (char) ('a' + col);
        char rowChar = (char) ('0' + (8 - row));
        return "" + colChar + rowChar;
    }
}