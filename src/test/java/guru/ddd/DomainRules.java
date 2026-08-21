//: guru.ddd.DomainRules.java

package guru.ddd;


import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;


@AnalyzeClasses(packages = "guru.ddd")
class DomainRules {

    @ArchTest
    public static final ArchRule DOMAIN_ACCESSING_RULE = noClasses()
            .that()
            .resideInAnyPackage("guru.ddd.*.domain.model..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "guru.ddd.*.domain.repository..",
                    "guru.ddd.*.domain.service..")
            .because("Domain model should always be the core of the whole application.");

} /// :~