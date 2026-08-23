//: guru.ddd.helpdesk.domain.model.HeightTest.java

package guru.ddd.helpdesk.domain.model;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import guru.ddd.helpdesk.domain.model.Height.Imperial;
import guru.ddd.helpdesk.domain.model.Height.Metric;


@DisplayName("Height Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HeightTest {

    @Test
    void metric_Able_To_Be_Created_From_Zero_Upwards() {

        // Given
        final Metric zero = new Metric(0);
        final Metric tall = new Metric(183);

        // Then
        assertThat(zero.cm()).isZero();
        assertThat(tall).hasToString("183cm");
    }

    @Test
    void metric_Unable_To_Be_Created_With_A_Negative_Value() {

        // When & Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Metric(-1))
                .withMessage("cm must not be negative but was -1");
    }

    @Test
    void imperial_Able_To_Be_Created_From_Zero_Upwards() {

        // Given
        final Imperial height = new Imperial(6, 1);

        // Then
        assertThat(height).hasToString("6 feet 1 inches");
    }

    @Test
    void imperial_Unable_To_Be_Created_With_A_Negative_Feet_Or_Inches() {

        // When & Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Imperial(-1, 0))
                .withMessage("feet must not be negative but was -1");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Imperial(0, -2))
                .withMessage("inches must not be negative but was -2");
    }

    @Test
    void identified_By_The_Composition_Of_Its_Values() {

        // Given
        final Imperial height = new Imperial(5, 11);

        // Then
        assertThat(height).isEqualTo(new Imperial(5, 11))
                .hasSameHashCodeAs(new Imperial(5, 11))
                .isNotEqualTo(new Imperial(5, 10));
    }

} /// :~
