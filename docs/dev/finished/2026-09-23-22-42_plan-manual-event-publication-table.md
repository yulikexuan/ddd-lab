# Plan — Manually managed `event_publication` table

Source of truth: `spring-modulith-events-jdbc-2.1.0` (sources jar) — V2 schema, `JdbcRepositorySettings`, `JdbcEventPublicationRepositoryV2`.

## Steps

1. `src/main/resources/schema.sql` 
   - V2 Postgres DDL for `event_publication` and `event_publication_archive` (`IF NOT EXISTS`, incl. both indexes each), run by existing `spring.sql.init.mode: always`.
   - If tables exist, do not recreate them again. 
2. `application.yml` — replace current `modulith.events` block (drop wrong "defaults to false" comment):
   ```yaml
   spring:
     modulith:
       events:
         completion-mode: archive # update (default) | delete | archive
         republish-outstanding-events-on-restart: false # see event_publication_registry.md §4.2.3
         registry-trigger-annotation: org.springframework.modulith.events.ApplicationModuleListener
         jdbc:
           schema-initialization:
             enabled: false
   ```
3. `src/test/java/guru/ddd/EventPublicationRegistryIT.java` (Zonky `@AutoConfigureEmbeddedDatabase`, Failsafe):
   - test event/listeners/publisher `@Import`ed (app scans only `guru.ddd.config`); publish inside a transaction; Awaitility for async listeners
   - tables created by `schema.sql`, `DatabaseSchemaInitializer` bean absent
   - successful `@ApplicationModuleListener` → row in `event_publication_archive` with `status = COMPLETED` + `completion_date`; none left in `event_publication`
   - throwing listener → `event_publication` row `status = FAILED`, `completion_date` null; nothing archived
4. Verify default of `schema-initialization.enabled` when absent: source says initializer runs (`@ConditionalOnProperty(matchIfMissing = true)`); `@DefaultValue("false")` on `JdbcConfigurationProperties` does not gate it. Prove with a context test (property absent → `DatabaseSchemaInitializer` bean present); fix `event_listener_patterns.md` §5.2 and resolve ⚠️ in `event_publication_registry.md` §4.1.
5. `docs/event_publication_registry.md` (exists) — update: §4.1 verified default, §8 checklist ticked, IT reference.
