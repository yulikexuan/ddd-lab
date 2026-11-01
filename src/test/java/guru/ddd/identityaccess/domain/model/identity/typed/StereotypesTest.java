//: guru.ddd.identityaccess.domain.model.identity.typed.StereotypesTest.java

package guru.ddd.identityaccess.domain.model.identity.typed;


import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.ARRAY;
import static org.jmolecules.archunit.JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation;
import static org.jmolecules.archunit.JMoleculesDddRules.annotatedEntitiesAndAggregatesNeedToHaveAnIdentifier;
import static org.jmolecules.archunit.JMoleculesDddRules.entitiesShouldBeDeclaredForUseInSameAggregate;
import static org.jmolecules.archunit.JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.ddd.types.ValueObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;


@DisplayName("Stereotypes Test (type model) - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StereotypesTest {

    private static final JavaClasses TYPED_MODEL = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackagesOf(Tenant.class);

    @Nested
    @DisplayName("Stereotypes are declared through the type system - ")
    class Type_System {

        @Test
        void Tenant_Is_An_Aggregate_Root_Identified_By_TenantId() {

            // When
            final Type[] genericInterfaces = Tenant.class.getGenericInterfaces();
            Stream<ParameterizedType> declared = Arrays.stream(genericInterfaces)
                    .filter(ParameterizedType.class::isInstance)
                    .map(ParameterizedType.class::cast);

            // Then
            assertThat(declared)
                    .filteredOn(type -> type.getRawType() == AggregateRoot.class)
                    .singleElement()
                    .extracting(ParameterizedType::getActualTypeArguments, ARRAY)
                    .containsExactly(Tenant.class, TenantId.class);
        }

        @Test
        void TenantId_Is_An_Identifier_But_Not_A_Value_Object() {

            // When / Then
            assertThat(Identifier.class.isAssignableFrom(TenantId.class)).isTrue();
            assertThat(ValueObject.class.isAssignableFrom(TenantId.class)).isFalse();
        }

        @Test
        void FullName_Is_A_Value_Object() {

            // When / Then
            assertThat(ValueObject.class.isAssignableFrom(FullName.class)).isTrue();
        }

        @Test
        void Type_Model_Carries_No_Ddd_Annotation() {

            // Given
            ArchRule noDddAnnotations = noClasses().should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.jmolecules.ddd.annotation..");

            // When / Then
            noDddAnnotations.check(TYPED_MODEL);
        }

    }

    @Nested
    @DisplayName("jMolecules DDD rules (ArchUnit) accept the model - ")
    class Ddd_Rules {

        @ParameterizedTest
        @MethodSource
        void Model_Satisfies(ArchRule rule) {

            // When / Then
            rule.check(TYPED_MODEL);
        }

        static Stream<Arguments> Model_Satisfies() {
            return Stream.of(
                    argumentSet("annotated aggregates need an identifier",
                            annotatedEntitiesAndAggregatesNeedToHaveAnIdentifier()),
                    argumentSet("value objects must not refer to identifiables",
                            valueObjectsMustNotReferToIdentifiables()),
                    argumentSet("aggregate references via id or association",
                            aggregateReferencesShouldBeViaIdOrAssociation()),
                    argumentSet("entities declared for use in same aggregate",
                            entitiesShouldBeDeclaredForUseInSameAggregate()));
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Spring Modulith detects the stereotypes - ")
    class Modulith {

        private ApplicationModule identityAccess;

        @BeforeAll
        void Detect_Modules() {
            identityAccess = ApplicationModules.of(
                            "guru.ddd",
                            resideInAPackage("guru.ddd.config.."))
                    .getModuleByName("identityaccess")
                    .orElseThrow();
        }

        @Test
        void Lists_Tenant_As_Aggregate_Root_Of_Identity_Access() {

            // When
            List<String> aggregateRoots = names(identityAccess.getAggregateRoots());

            // Then
            assertThat(aggregateRoots).contains(Tenant.class.getName())
                    .doesNotContain(FullName.class.getName(), TenantId.class.getName());
        }

        @Test
        void Lists_Only_FullName_As_Value_Type_Because_Identifier_Is_Not_A_ValueObject() {

            // When
            List<String> valueTypes = names(identityAccess.getValueTypes());

            // Then
            assertThat(valueTypes).contains(FullName.class.getName())
                    .doesNotContain(Tenant.class.getName(), TenantId.class.getName());
        }

        private static List<String> names(List<JavaClass> types) {
            return types.stream().map(JavaClass::getName).toList();
        }

    }

}
