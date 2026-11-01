//: guru.ddd.identityaccess.domain.model.identity.typed.TenantIdTest.java

package guru.ddd.identityaccess.domain.model.identity.typed;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


@DisplayName("TenantId Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TenantIdTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "123e4567-e89b-12d3-a456-426614174000",
            "123E4567-E89B-12D3-A456-426614174000"})
    void Accepts_Canonical_Uuid_In_Any_Case(String value) {

        // When
        TenantId tenantId = new TenantId(value);

        // Then
        assertThat(tenantId.id()).isEqualTo(value);
    }

    @Test
    void Accepts_Random_Uuid() {

        // Given
        String value = UUID.randomUUID().toString();

        // When
        TenantId tenantId = new TenantId(value);

        // Then
        assertThat(tenantId.id()).isEqualTo(value);
    }

    @Test
    void Of_Keeps_The_Uuid_As_Its_Id() {

        // Given
        UUID uuid = UUID.randomUUID();

        // When
        TenantId tenantId = TenantId.of(uuid);

        // Then
        assertThat(tenantId.id()).isEqualTo(uuid.toString());
    }

    @Test
    void Two_Ids_With_The_Same_Value_Are_Equal() {

        // Given
        UUID uuid = UUID.randomUUID();

        // When
        TenantId one = TenantId.of(uuid);
        TenantId other = new TenantId(uuid.toString());

        // Then
        assertThat(one).isNotSameAs(other)
                .isEqualTo(other)
                .hasSameHashCodeAs(other);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "1-1-1-1-1",
            "not-a-uuid",
            "123e4567e89b12d3a456426614174000",
            "123e4567-e89b-12d3-a456-4266141740000",
            "123e4567-e89b-12d3-a456-42661417400g",
            " 123e4567-e89b-12d3-a456-426614174000"})
    void Rejects_Non_Canonical_Uuid(String value) {

        // When / Then
        assertThatIllegalArgumentException().isThrownBy(() -> new TenantId(value));
    }

    @Test
    void Rejects_Null() {

        // When / Then
        assertThatNullPointerException().isThrownBy(() -> new TenantId(null));
        assertThatNullPointerException().isThrownBy(() -> TenantId.of(null));
    }

}
