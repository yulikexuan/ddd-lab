//: guru.ddd.helpdesk.domain.model.Height.java

package guru.ddd.helpdesk.domain.model;


public sealed interface Height permits Height.Metric, Height.Imperial {

    record Metric(int cm) implements Height {

        public Metric {
            requireNonNegative(cm, "cm");
        }

        @Override
        public String toString() {
            return cm + "cm";
        }

    } //: End of record Metric

    record Imperial(int feet, int inches) implements Height {

        private static final String TO_STRING_TEMPLATE = "%d feet %d inches";

        public Imperial {
            requireNonNegative(feet, "feet");
            requireNonNegative(inches, "inches");
        }

        @Override
        public String toString() {
            return TO_STRING_TEMPLATE.formatted(feet, inches);
        }

    } //: End of record Imperial

    private static void requireNonNegative(final int value, final String name) {

        if (value < 0) {
            throw new IllegalArgumentException(
                    "%s must not be negative but was %d".formatted(name, value));
        }
    }

} /// :~
