//: guru.ddd.identityaccess.domain.model.identity.hybrid.FullNameTest.java

package guru.ddd.identityaccess.domain.model.identity.hybrid;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;


@DisplayName("FullName Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class FullNameTest {

    private static final String FIFTY_CHARS = "a".repeat(50);

    @Nested
    @DisplayName("@ValueObject contract: equality by value - ")
    class Equality_By_Value {

        @Test
        void Two_Instances_With_Same_Values_Are_Equal_And_Interchangeable() {

            // Given
            FullName one = new FullName("Ada", "Lovelace");
            FullName other = new FullName("Ada", "Lovelace");

            // Then
            assertThat(one).isNotSameAs(other)
                    .isEqualTo(other)
                    .hasSameHashCodeAs(other);
        }

        @Test
        void Input_Is_Normalized_So_Padded_Values_Equal_Clean_Values() {

            // When
            FullName padded = new FullName("  Ada ", " Lovelace  ");

            // Then
            assertThat(padded).isEqualTo(new FullName("Ada", "Lovelace"));
        }

        @ParameterizedTest
        @MethodSource
        void Any_Different_Value_Makes_A_Different_Name(FullName other) {

            // Given
            FullName name = new FullName("Ada", "Lovelace");

            // Then
            assertThat(name).isNotEqualTo(other);
        }

        static Stream<Arguments> Any_Different_Value_Makes_A_Different_Name() {
            return Stream.of(
                    argumentSet("other first name", new FullName("Augusta", "Lovelace")),
                    argumentSet("other last name", new FullName("Ada", "Byron")),
                    argumentSet("different case", new FullName("ada", "lovelace")));
        }

    }

    @Nested
    @DisplayName("@ValueObject contract: immutable, side-effect-free behavior - ")
    class Immutability {

        @Test
        void Formatted_Name_Joins_First_And_Last_Name() {

            // Given
            FullName name = new FullName("Ada", "Lovelace");

            // When / Then
            assertThat(name.formattedName()).isEqualTo("Ada Lovelace");
        }

        @Test
        void Changing_First_Name_Returns_A_New_Value_And_Keeps_The_Original() {

            // Given
            FullName original = new FullName("Ada", "Lovelace");

            // When
            FullName changed = original.withChangedFirstName("Augusta");

            // Then
            assertThat(changed).isNotSameAs(original)
                    .isEqualTo(new FullName("Augusta", "Lovelace"));
            assertThat(original).isEqualTo(new FullName("Ada", "Lovelace"));
        }

        @Test
        void Changing_Last_Name_Returns_A_New_Value_And_Keeps_The_Original() {

            // Given
            FullName original = new FullName("Ada", "Byron");

            // When
            FullName changed = original.withChangedLastName("Lovelace");

            // Then
            assertThat(changed).isNotSameAs(original)
                    .isEqualTo(new FullName("Ada", "Lovelace"));
            assertThat(original).isEqualTo(new FullName("Ada", "Byron"));
        }

    }

    @Nested
    @DisplayName("@ValueObject contract: always valid once created - ")
    class Self_Validation {

        @Test
        void Accepts_Names_Of_Exactly_50_Characters() {

            // When
            FullName name = new FullName(FIFTY_CHARS, FIFTY_CHARS);

            // Then
            assertThat(name.firstName()).hasSize(50);
            assertThat(name.lastName()).hasSize(50);
        }

        @Test
        void Measures_Length_After_Trimming() {

            // When
            FullName name = new FullName(" " + FIFTY_CHARS + " ", FIFTY_CHARS);

            // Then
            assertThat(name.firstName()).isEqualTo(FIFTY_CHARS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t\n"})
        void Rejects_Blank_First_Name(String blank) {

            // When / Then
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FullName(blank, "Lovelace"))
                    .withMessage("First name is required.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t\n"})
        void Rejects_Blank_Last_Name(String blank) {

            // When / Then
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FullName("Ada", blank))
                    .withMessage("Last name is required.");
        }

        @Test
        void Rejects_First_Name_Longer_Than_50_Characters() {

            // When / Then
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FullName(FIFTY_CHARS + "a", "Lovelace"))
                    .withMessage("The first name must be 50 characters or less.");
        }

        @Test
        void Rejects_Last_Name_Longer_Than_50_Characters() {

            // When / Then
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FullName("Ada", FIFTY_CHARS + "a"))
                    .withMessage("The last name must be 50 characters or less.");
        }

        @Test
        void Rejects_Null_Parts() {

            // When / Then
            assertThatNullPointerException().isThrownBy(
                    () -> new FullName(null, "Lovelace"));
            assertThatNullPointerException().isThrownBy(
                    () -> new FullName("Ada", null));
        }

        @Test
        void Wither_Methods_Validate_Like_The_Constructor() {

            // Given
            FullName name = new FullName("Ada", "Lovelace");

            // When / Then
            assertThatIllegalArgumentException().isThrownBy(
                    () -> name.withChangedFirstName(" "));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> name.withChangedLastName(" "));
        }

    }

}
