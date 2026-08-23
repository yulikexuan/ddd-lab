//: guru.ddd.helpdesk.domain.model.ColorTest.java

package guru.ddd.helpdesk.domain.model;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import guru.ddd.helpdesk.domain.model.Color.RGB;


@DisplayName("Color.RGB Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ColorTest {

    @Test
    void able_To_Hold_Every_Channel_Value_Of_The_Full_Range() {

        // Given
        final RGB white = new RGB(255, 255, 255);
        final RGB black = new RGB(0, 0, 0);

        // Then
        assertThat(white.red()).isEqualTo(255);
        assertThat(white.green()).isEqualTo(255);
        assertThat(white.blue()).isEqualTo(255);
        assertThat(black.red()).isZero();
        assertThat(black.green()).isZero();
        assertThat(black.blue()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "-1,   0,   0, red",
            "256,  0,   0, red",
            "0,   -1,   0, green",
            "0,  256,   0, green",
            "0,    0,  -1, blue",
            "0,    0, 256, blue"})
    void unable_To_Be_Created_With_A_Channel_Outside_0_To_255(
            final int red, final int green, final int blue, final String channelName) {

        // When & Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RGB(red, green, blue))
                .withMessageStartingWith(channelName + " must be between 0 and 255");
    }

    @ParameterizedTest
    @CsvSource({
            "  0,   0,   0, #000000",
            "255, 255, 255, #FFFFFF",
            " 10,   0,   0, #0A0000",
            "200,  16,   5, #C81005"})
    void able_To_Be_Rendered_As_A_Zero_Padded_Six_Digit_Hex_Triplet(
            final int red, final int green, final int blue, final String expected) {

        // Given
        final RGB color = new RGB(red, green, blue);

        // Then
        assertThat(color).hasToString(expected);
    }

    @Test
    void able_To_Mix_With_Another_Color_Into_The_Channel_Wise_Average() {

        // Given
        final RGB red = new RGB(255, 0, 0);
        final RGB blue = new RGB(0, 0, 255);

        // When
        final RGB mixed = red.mixWith(blue);

        // Then
        assertThat(mixed).isEqualTo(new RGB(127, 0, 127));
    }

    @Test
    void able_To_Mix_Two_Bright_Colors_Without_Overflowing() {

        // Given
        final RGB bright = new RGB(200, 220, 240);

        // When
        final RGB mixed = bright.mixWith(new RGB(240, 220, 200));

        // Then
        assertThat(mixed).isEqualTo(new RGB(220, 220, 220));
    }

    @Test
    void identified_By_The_Composition_Of_Its_Channel_Values() {

        // Given
        final RGB color = new RGB(12, 34, 56);

        // Then
        assertThat(color).isEqualTo(new RGB(12, 34, 56))
                .hasSameHashCodeAs(new RGB(12, 34, 56))
                .isNotEqualTo(new RGB(12, 34, 57));
    }

} /// :~
