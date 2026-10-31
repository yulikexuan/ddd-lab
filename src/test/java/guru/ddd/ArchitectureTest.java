//: com.tecsys.elite.coordinator.ArchitectureTest.java

package guru.ddd;


import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;


@AnalyzeClasses(
    packages = "guru.ddd",
    importOptions = {
        ImportOption.DoNotIncludeTests.class,
        ImportOption.DoNotIncludeJars.class,
        ImportOption.DoNotIncludeArchives.class
    })
@DisplayName("Dependency Rules Test - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests DOMAIN_RULES = ArchTests.in(DomainRules.class);

    @ArchTest
    static final ArchTests CODING_RULES = ArchTests.in(CodingRules.class);

    @ArchTest
    static final ArchTests CYCLE_CHECK = ArchTests.in(CycleChecks.class);

} /// :~
