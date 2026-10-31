//: guru.ddd.EventPublicationRegistryIT.java

package guru.ddd;


import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureEmbeddedDatabase(type = POSTGRES, provider = ZONKY)
@Import(EventPublicationRegistryIT.Listeners.class)
@DisplayName("Event Publication Registry on schema.sql-owned tables - ")
@DisplayNameGeneration(ReplaceUnderscores.class)
class EventPublicationRegistryIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    record Succeeding(UUID id) {}

    record Failing(UUID id) {}

    @TestConfiguration
    static class Listeners {

        @ApplicationModuleListener
        void on(Succeeding event) {}

        @ApplicationModuleListener
        void on(Failing event) {
            throw new IllegalStateException("boom");
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        this.jdbc.update("DELETE FROM event_publication");
        this.jdbc.update("DELETE FROM event_publication_archive");
    }

    @Test
    void tables_Are_Created_By_Schema_Sql_Not_By_Modulith() {

        // Given
        String sql = """
                SELECT table_name FROM information_schema.tables
                  WHERE table_schema = current_schema()
                    AND table_name LIKE 'event_publication%'
                """;

        // When
        List<String> tables = this.jdbc.queryForList(sql, String.class);

        // Then
        assertSoftly(softly -> {
            softly.assertThat(tables).containsExactlyInAnyOrder(
                    "event_publication", "event_publication_archive");
            softly.assertThat(this.context.containsBean(
                    "databaseSchemaInitializer")).isFalse();
        });
    }

    @Test
    void successful_Listener_Moves_Publication_To_Archive_As_Completed() {

        // Given
        String type = Succeeding.class.getName();

        // When
        this.tx.executeWithoutResult(_ -> this.publisher.publishEvent(new Succeeding(UUID.randomUUID())));

        // Then
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(rows("event_publication_archive", type)).singleElement().satisfies(row -> {
                assertThat(row.get("status")).isEqualTo("COMPLETED");
                assertThat(row.get("completion_date")).isNotNull();
            });
        });
        assertThat(rows("event_publication", type)).isEmpty();
    }

    @Test
    void failing_Listener_Leaves_Publication_Failed_Without_Completion_Date() {

        // Given
        String type = Failing.class.getName();

        // When
        this.tx.executeWithoutResult(_ -> this.publisher.publishEvent(new Failing(UUID.randomUUID())));

        // Then
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(rows("event_publication", type)).singleElement().satisfies(row -> {
                assertThat(row.get("status")).isEqualTo("FAILED");
                assertThat(row.get("completion_date")).isNull();
            });
        });
        assertThat(rows("event_publication_archive", type)).isEmpty();
    }

    private List<Map<String, Object>> rows(String table, String type) {
        return this.jdbc.queryForList(
                "SELECT status, completion_date FROM " + table + " WHERE event_type = ?", type);
    }

} /// :~
