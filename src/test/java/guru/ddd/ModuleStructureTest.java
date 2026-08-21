//: guru.ddd.ModuleStructureTest.java

package guru.ddd;


import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;


@DisplayName("Module Structure Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ModuleStructureTest {

    private static final String BASE_PACKAGE = "guru.ddd";

    // verify() covers host, statistic and pool: no cycles and no illegal access
    // to non-exposed internals. pool may reference statistic (events / dto) and
    // host (readiness) only through their exposed @NamedInterface packages.
    @Test
    void ddd_Modules_Form_A_Valid_Structure_With_Only_Exposed_Cross_Module_Access() {

        // Given
        final ApplicationModules modules = ApplicationModules.of(
                BASE_PACKAGE,
                resideInAPackage("guru.ddd.config.."));

        // When
        List<String> moduleNames = modules.stream()
                .map(m -> m.getIdentifier().toString())
                .toList();

        // Then
        modules.verify();
        assertThat(moduleNames).hasSize(1)
                .containsExactlyInAnyOrder("helpdesk");
    }

} /// :~