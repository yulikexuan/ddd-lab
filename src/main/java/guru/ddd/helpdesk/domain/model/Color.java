//: guru.ddd.helpdesk.domain.model.Color.java

package guru.ddd.helpdesk.domain.model;


/**
 * Value Object
 * A value object is an object that can be identified by the composition of its values.
 *
 * The composition of the values of the three fields red, green, and blue defines a color.
 * Changing the value of one of the fields will result in a new color.
 * No two colors can have the same values.
 * Also, two instances of the same color must have the same values. T
 * Therefore, no explicit identification field is needed to identify colors.
 *
 */
public sealed interface Color permits Color.RGB, Color.Hex {

    record RGB(int red, int green, int blue) implements Color {

        private static final int MIN_CHANNEL = 0;
        private static final int MAX_CHANNEL = 255;

        private static final String TO_STRING_TEMPLATE = "#%02X%02X%02X";

        public RGB {
            requireChannel(red, "red");
            requireChannel(green, "green");
            requireChannel(blue, "blue");
        }

        public RGB mixWith(final RGB color) {
            return new RGB(
                    (red + color.red) / 2,
                    (green + color.green) / 2,
                    (blue + color.blue) / 2);
        }

        @Override
        public String toString() {
            return TO_STRING_TEMPLATE.formatted(red, green, blue);
        }

        private static void requireChannel(final int channel, final String name) {

            if (channel < MIN_CHANNEL || channel > MAX_CHANNEL) {

                String errMsg = "%s must be between %d and %d but was %d"
                        .formatted(name, MIN_CHANNEL, MAX_CHANNEL, channel);

                throw new IllegalArgumentException(errMsg);
            }
        }

    } //: End of record RGB

    record Hex(String hex) implements Color {

    }

} /// :~
