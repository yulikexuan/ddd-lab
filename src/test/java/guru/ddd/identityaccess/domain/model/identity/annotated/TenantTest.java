//: guru.ddd.identityaccess.domain.model.identity.annotated.TenantTest.java

package guru.ddd.identityaccess.domain.model.identity.annotated;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


@DisplayName("Tenant Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TenantTest {

    private static final String HUNDRED_CHARS = "a".repeat(100);

    private static TenantId newTenantId() {
        return TenantId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("@AggregateRoot contract: equality by @Identity only - ")
    class Equality_By_Identity {

        @Test
        void Exposes_The_Identity_It_Was_Created_With() {

            // Given
            TenantId id = newTenantId();

            // When
            Tenant tenant = new Tenant(id, "Acme", true);

            // Then
            assertThat(tenant.tenantId()).isSameAs(id);
        }

        @Test
        void Same_Identity_Means_Same_Tenant_Even_When_State_Differs() {

            // Given
            TenantId id = newTenantId();

            // When
            Tenant one = new Tenant(id, "Acme", true);
            Tenant other = new Tenant(id, "Acme Renamed", false);

            // Then
            assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        }

        @Test
        void Different_Identity_Means_Different_Tenant_Even_When_State_Matches() {

            // When
            Tenant one = new Tenant(newTenantId(), "Acme", true);
            Tenant other = new Tenant(newTenantId(), "Acme", true);

            // Then
            assertThat(one).isNotEqualTo(other).doesNotHaveSameHashCodeAs(other);
        }

        @Test
        void Identity_And_Equality_Survive_State_Changes() {

            // Given
            Tenant tenant = new Tenant(newTenantId(), "Acme", true);
            Tenant snapshot = new Tenant(tenant.tenantId(), tenant.name(), tenant.isActive());
            int hashBefore = tenant.hashCode();

            // When
            tenant.deactivate();

            // Then
            assertThat(tenant).isEqualTo(snapshot);
            assertThat(tenant.hashCode()).isEqualTo(hashBefore);
        }

    }

    @Nested
    @DisplayName("@AggregateRoot contract: state changes only through behavior - ")
    class Lifecycle {

        @Test
        void Activating_An_Inactive_Tenant_Makes_It_Active() {

            // Given
            Tenant tenant = new Tenant(newTenantId(), "Acme", false);

            // When
            tenant.activate();

            // Then
            assertThat(tenant.isActive()).isTrue();
        }

        @Test
        void Deactivating_An_Active_Tenant_Makes_It_Inactive() {

            // Given
            Tenant tenant = new Tenant(newTenantId(), "Acme", true);

            // When
            tenant.deactivate();

            // Then
            assertThat(tenant.isActive()).isFalse();
        }

        @Test
        void Activating_Twice_Is_Idempotent() {

            // Given
            Tenant tenant = new Tenant(newTenantId(), "Acme", true);

            // When
            tenant.activate();

            // Then
            assertThat(tenant.isActive()).isTrue();
        }

        @Test
        void Deactivating_Twice_Is_Idempotent() {

            // Given
            Tenant tenant = new Tenant(newTenantId(), "Acme", false);

            // When
            tenant.deactivate();

            // Then
            assertThat(tenant.isActive()).isFalse();
        }

    }

    @Nested
    @DisplayName("@AggregateRoot contract: guards its invariants - ")
    class Invariants {

        @Test
        void Trims_The_Name() {

            // When
            Tenant tenant = new Tenant(newTenantId(), "  Acme  ", true);

            // Then
            assertThat(tenant.name()).isEqualTo("Acme");
        }

        @Test
        void Accepts_A_Name_Of_Exactly_100_Characters() {

            // When
            Tenant tenant = new Tenant(newTenantId(), " " + HUNDRED_CHARS + " ", true);

            // Then
            assertThat(tenant.name()).isEqualTo(HUNDRED_CHARS);
        }

        @Test
        void Rejects_A_Name_Longer_Than_100_Characters() {

            // When / Then
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Tenant(newTenantId(), HUNDRED_CHARS + "a", true))
                    .withMessage("The name must be 100 characters or less.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t\n"})
        void Rejects_A_Blank_Name(String blank) {

            // When / Then
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Tenant(newTenantId(), blank, true))
                    .withMessage("The tenant name is required.");
        }

        @Test
        void Rejects_A_Missing_Identity() {

            // When / Then
            assertThatNullPointerException().isThrownBy(
                    () -> new Tenant(null, "Acme", true));
        }

        @Test
        void Rejects_A_Null_Name() {

            // When / Then
            assertThatNullPointerException().isThrownBy(
                    () -> new Tenant(newTenantId(), null, true));
        }

    }

}
