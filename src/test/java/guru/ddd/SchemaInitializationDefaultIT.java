//: guru.ddd.SchemaInitializationDefaultIT.java

package guru.ddd;


import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;


@DisplayName("Modulith schema-initialization default - ")
@DisplayNameGeneration(ReplaceUnderscores.class)
class SchemaInitializationDefaultIT {

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startPostgres() throws IOException {
        postgres = EmbeddedPostgres.start();
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        postgres.close();
    }

    // ApplicationContextRunner loads no application.yml, so the property is truly absent
    // and spring.sql.init stays at its non-embedded default (off) - only Modulith can create the table.
    @Test
    void absent_Property_Lets_Modulith_Create_The_Table() {

        // Given
        DataSource dataSource = postgres.getPostgresDatabase();
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(DomainDrivenDesignApplication.class)
                .withBean(DataSource.class, () -> dataSource);

        // When / Then
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasBean("databaseSchemaInitializer");
            List<String> tables = new JdbcTemplate(dataSource).queryForList("""
                    SELECT table_name FROM information_schema.tables
                     WHERE table_name = 'event_publication'
                    """, String.class);
            assertThat(tables).containsExactly("event_publication");
        });
    }

} /// :~
