//: guru.ddd.identityaccess.domain.model.identity.hybrid.StereotypesTest.java

package guru.ddd.identityaccess.domain.model.identity.hybrid;


import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jmolecules.archunit.JMoleculesDddRules.aggregateReferencesShouldBeViaIdOrAssociation;
import static org.jmolecules.archunit.JMoleculesDddRules.annotatedEntitiesAndAggregatesNeedToHaveAnIdentifier;
import static org.jmolecules.archunit.JMoleculesDddRules.entitiesShouldBeDeclaredForUseInSameAggregate;
import static org.jmolecules.archunit.JMoleculesDddRules.valueObjectsMustNotReferToIdentifiables;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jmolecules.ddd.types.Identifier;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;


@DisplayName("Stereotypes Test (hybrid model) - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StereotypesTest {

    private static final JavaClasses HYBRID_MODEL = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackagesOf(Tenant.class);

    @Nested
    @DisplayName("Annotations on aggregate and value object, a type for the id - ")
    class Stereotypes {

        @Test
        void Tenant_Is_Declared_An_Aggregate_Root() {

            // When / Then
            assertThat(Tenant.class.isAnnotationPresent(AggregateRoot.class)).isTrue();
        }

        @Test
        void Tenant_Marks_Exactly_One_Identity_Field_Of_Type_TenantId() {

            // When
            List<Field> identityFields = Arrays.stream(Tenant.class.getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(Identity.class))
                    .toList();

            // Then
            assertThat(identityFields).singleElement()
                    .extracting(Field::getType)
                    .isEqualTo(TenantId.class);
        }

        @Test
        void TenantId_Is_An_Identifier_Not_An_Annotated_Value_Object() {

            // When / Then
            assertThat(Identifier.class.isAssignableFrom(TenantId.class)).isTrue();
            assertThat(TenantId.class.isAnnotationPresent(ValueObject.class)).isFalse();
        }

        @Test
        void FullName_Is_Declared_A_Value_Object() {

            // When / Then
            assertThat(FullName.class.isAnnotationPresent(ValueObject.class)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(classes = {Tenant.class, TenantId.class, FullName.class})
        void Hybrid_Model_Implements_No_Aggregate_Entity_Or_Value_Object_Interface(Class<?> type) {

            // Given
            List<Class<?>> forbidden = List.of(
                    org.jmolecules.ddd.types.AggregateRoot.class,
                    org.jmolecules.ddd.types.Entity.class,
                    org.jmolecules.ddd.types.ValueObject.class);

            // When / Then
            assertThat(forbidden).noneMatch(contract -> contract.isAssignableFrom(type));
        }

    }

    @Nested
    @DisplayName("jMolecules DDD rules (ArchUnit) accept the model - ")
    class Ddd_Rules {

        @ParameterizedTest
        @MethodSource
        void Model_Satisfies(ArchRule rule) {

            // When / Then
            rule.check(HYBRID_MODEL);
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
