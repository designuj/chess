package dev.autowired.chess.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    @Test
    @DisplayName("Should create valid position")
    void shouldCreateValidPosition() {
        Position position = new Position(3, 4);
        assertThat(position.getRow()).isEqualTo(3);
        assertThat(position.getCol()).isEqualTo(4);
        assertThat(position.isValid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, true",
            "7, 7, true",
            "3, 4, true",
            "-1, 0, false",
            "0, -1, false",
            "8, 0, false",
            "0, 8, false",
            "-1, -1, false",
            "8, 8, false"
    })
    @DisplayName("Should validate position boundaries")
    void shouldValidatePositionBoundaries(int row, int col, boolean expectedValid) {
        Position position = new Position(row, col);
        assertThat(position.isValid()).isEqualTo(expectedValid);
    }

    @ParameterizedTest
    @CsvSource({
            "a1, 7, 0",
            "a8, 0, 0",
            "h1, 7, 7",
            "h8, 0, 7",
            "e4, 4, 4",
            "d2, 6, 3",
            "c7, 1, 2"
    })
    @DisplayName("Should convert from chess notation correctly")
    void shouldConvertFromNotation(String notation, int expectedRow, int expectedCol) {
        Position position = Position.fromNotation(notation);
        assertThat(position.getRow()).isEqualTo(expectedRow);
        assertThat(position.getCol()).isEqualTo(expectedCol);
    }

    @ParameterizedTest
    @CsvSource({
            "7, 0, a1",
            "0, 0, a8",
            "7, 7, h1",
            "0, 7, h8",
            "4, 4, e4",
            "6, 3, d2",
            "1, 2, c7"
    })
    @DisplayName("Should convert to chess notation correctly")
    void shouldConvertToNotation(int row, int col, String expectedNotation) {
        Position position = new Position(row, col);
        assertThat(position.toNotation()).isEqualTo(expectedNotation);
    }

    @Test
    @DisplayName("Should throw exception for invalid notation - null")
    void shouldThrowExceptionForNullNotation() {
        assertThatThrownBy(() -> Position.fromNotation(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid notation");
    }

    @Test
    @DisplayName("Should throw exception for invalid notation - wrong length")
    void shouldThrowExceptionForWrongLengthNotation() {
        assertThatThrownBy(() -> Position.fromNotation("a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid notation");

        assertThatThrownBy(() -> Position.fromNotation("a12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid notation");
    }

    @Test
    @DisplayName("Should have proper equals and hashCode")
    void shouldHaveProperEqualsAndHashCode() {
        Position pos1 = new Position(3, 4);
        Position pos2 = new Position(3, 4);
        Position pos3 = new Position(4, 3);

        assertThat(pos1).isEqualTo(pos2);
        assertThat(pos1).isNotEqualTo(pos3);
        assertThat(pos1.hashCode()).isEqualTo(pos2.hashCode());
    }

    @Test
    @DisplayName("Should convert back and forth from notation")
    void shouldConvertBackAndForthFromNotation() {
        String originalNotation = "e4";
        Position position = Position.fromNotation(originalNotation);
        String convertedNotation = position.toNotation();
        assertThat(convertedNotation).isEqualTo(originalNotation);
    }
}