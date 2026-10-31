//: com.tecsys.elite.coordinator.CycleChecks.java

package guru.ddd;


import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;


class CycleChecks {

    @ArchTest
    public static final ArchRule CYCLE_CHECKS = slices()
        .matching("guru.ddd.(*)..")
        .as("Modules within guru.ddd")
        .should()
        .beFreeOfCycles()
        .ignoreDependency(
            resideInAPackage("guru.ddd.*.config"),
            alwaysTrue())
        .because("Cyclic dependencies within modules should be avoided");

} /// :~
