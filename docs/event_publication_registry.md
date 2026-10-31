# The Event Publication Registry — Owning the `event_publication` Table

How to create the table that backs `@ApplicationModuleListener` **yourself**, and configure Spring Modulith to use it.

Companion to [event_listener_patterns.md](./event_listener_patterns.md) §5 
- that document explains *why* the registry exists; 
- this one is the hands-on "Create it, Wire it, Run it" Tutorial.

> Everything below is read from the `spring-modulith-events-jdbc-2.1.0` sources jar  
> - (`JdbcRepositorySettings`, `JdbcEventPublicationRepositoryV2`, `JdbcEventPublicationAutoConfiguration`, `schemas/v2/*.sql`). 
> - Items marked **⚠️ unverified** are source-derived but not yet proven by a test in this repo.

---

## 0. The One-Screen Answer

**Yes, you may create the table manually.** 
- Modulith does not care who created it 
- Modulith only issues plain SQL against a table with a fixed name and fixed columns.

```
1. Create table  event_publication  (exact V2-structure columns — schemas/v2, §1.2/§2)   ── psql / schema.sql / Flyway
2. Turn Modulith's own DDL off: spring.modulith.events.jdbc.schema-initialization.enabled: false
3. (optional) spring.modulith.events.jdbc.schema: <schema>   ── if the table is not on the search_path
4. Use @ApplicationModuleListener — nothing else to wire
```

There is no entity, no mapping, no repository to write. 

The contract is **name + columns**, nothing more.

---

## 1. The Contract — What Modulith Expects

### 1.1 Table Name

Hard-coded in `JdbcRepositorySettings`:

``` 
String getTable() {
    return ObjectUtils.isEmpty(schema) ? "EVENT_PUBLICATION" : schema + ".EVENT_PUBLICATION";
}
String getArchiveTable() {
    return isArchiveCompletion() ? getTable() + "_ARCHIVE" : getTable();
}
```

- **Not configurable.** You cannot rename the table; you can only choose its schema.
- It is emitted **unquoted**, so Postgres folds it to `event_publication`. 
  - ⛔ **Never create it as `"EVENT_PUBLICATION"` (quoted, upper-case)** 
    - Postgres would then treat it as a different identifier, and Modulith would not find it.
- Without `spring.modulith.events.jdbc.schema`, the table is resolved through the connection's `search_path` 
  - here: `currentSchema=public` in `spring.datasource.url`.

### 1.2 Structure Version — V1 vs V2

| Property                                          | Repository used                    | DDL                   |
|---------------------------------------------------|------------------------------------|-----------------------|
| `use-legacy-structure: false` (**default**)       | `JdbcEventPublicationRepositoryV2` | `schemas/v2/*.sql`    |
| `use-legacy-structure: true`                      | `JdbcEventPublicationRepository`   | `schemas/v1/*.sql`    |

V2 adds some new columns, and 
- `status` 
- `completion_attempts` 
- `last_resubmission_date` 
 
The new columns are required for the 2.x state machine 
- `PUBLISHED ➡️ PROCESSING ➡️ COMPLETED / FAILED ➡️ RESUBMITTED` 
- and the staleness monitor. 

**Greenfield ➡️ always V2.** 
- V1 exists only for databases created by Modulith 1.x.

### 1.3 Columns (V2) and Who writes them

| Column                   | Type          | Written by                                                     | Meaning                                                              |
|--------------------------|---------------|----------------------------------------------------------------|----------------------------------------------------------------------|
| `id`                     | `UUID` PK     | `INSERT` at `publishEvent`                                     | publication id — one row **per event × listener**                    |
| `listener_id`            | `TEXT`        | `INSERT`                                                       | the listener's id (§7.2)                                             |
| `event_type`             | `TEXT`        | `INSERT`                                                       | `event.getClass().getName()` — used to deserialize on resubmission   |
| `serialized_event`       | `TEXT`        | `INSERT`                                                       | the event as JSON (`spring-modulith-events-jackson`)                 |
| `publication_date`       | `TIMESTAMPTZ` | `INSERT`                                                       | when `publishEvent` ran                                              |
| `completion_date`        | `TIMESTAMPTZ` | completion `UPDATE`                                            | `NULL` ⇔ not completed                                               |
| `status`                 | `TEXT`        | `INSERT` / every transition                                    | `PUBLISHED`, `PROCESSING`, `COMPLETED`, `FAILED`, `RESUBMITTED`      |
| `completion_attempts`    | `INT`         | `INSERT` (=`1`), resubmission (`+1`)                           | delivery attempts                                                    |
| `last_resubmission_date` | `TIMESTAMPTZ` | `INSERT` (= publication date), resubmission                    | input to `ResubmissionOptions#withMinAge` and staleness checks       |

---

## 2. The DDL

Copied verbatim from `spring-modulith-events-jdbc-2.1.0.jar!/org/springframework/modulith/events/jdbc/schemas/v2/`.

### 2.1 `event_publication` — always required

```sql
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
```

### 2.2 `event_publication_archive` — only for `completion-mode: archive`

```sql
CREATE TABLE IF NOT EXISTS event_publication_archive
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_archive_serialized_event_hash_idx ON event_publication_archive USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_archive_by_completion_date_idx ON event_publication_archive (completion_date);
```

### 2.3 Why the two indexes — **Do NOT Drop Them**

- **`serialized_event` hash index** — the normal completion path matches rows **by event JSON + listener id**, not by `id`:

  ```sql
  UPDATE event_publication SET STATUS = 'COMPLETED', COMPLETION_DATE = ?
  WHERE LISTENER_ID = ? AND COMPLETION_DATE IS NULL AND SERIALIZED_EVENT = ?
  ```

A `TEXT` equality on a potentially large JSON payload — presumably `hash` because a B-tree entry is capped (~2.7 KB in Postgres). Without it every completion is a sequential scan.
- **`completion_date` index** — every "incomplete" / "completed" / purge query filters on it.

### 2.4 What you may add

Additive changes are safe — every `INSERT` names its columns, and the row mapper reads by column name (`rs.getString("EVENT_TYPE")`, …), so extra columns are never seen.

- ✅ extra indexes (e.g. `(status)`, `(listener_id, status)` for ops queries)
- ✅ a `CHECK (status IN ('PUBLISHED','PROCESSING','COMPLETED','FAILED','RESUBMITTED'))`
- ✅ extra nullable columns (Modulith ignores them)
- ⛔ renaming / retyping columns, `NOT NULL` on `status`/`completion_date`, a unique constraint on `(listener_id, serialized_event)` 
  - the same event may legitimately be published twice.

---

## 3. Three Ways to Create It

| Option                      | Who runs the DDL          | Versioned | Fits when                                    |
|-----------------------------|---------------------------|-----------|----------------------------------------------|
| **A. By hand (psql / DBA)** | a human, once per env     | ❌        | DBA-owned schemas, app user has no DDL grant |
| **B. `spring.sql.init`**    | Spring Boot at startup    | ❌        | **this repo** — already `mode: always`       |
| **C. Flyway / Liquibase**   | migration tool at startup | ✅        | production with evolving schema              |

In all three: set `schema-initialization.enabled: false`, so Modulith does not also run its own copy.

### A. By hand

```bash
psql -h localhost -p 54316 -U postgres -d postgres -f event_publication.sql
```

The file is §2.1 (plus §2.2 for archive mode). Then grant the application role:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication TO <app_role>;
-- archive mode:
GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication_archive TO <app_role>;
```

`DELETE` is needed even in `update` mode — `CompletedEventPublications#deletePublicationsOlderThan` and friends issue it.

### B. `spring.sql.init` — `src/main/resources/schema.sql`

Put §2.1 into `src/main/resources/schema.sql`. This repo already has:

```yaml
spring:
  sql:
    init:
      mode: always      # run schema.sql / data.sql on every startup, embedded or not
```

- The `IF NOT EXISTS` clauses make it idempotent — **required**, since it runs on every boot.
- It is not versioned: a future Modulith DDL change (a new column) must be applied by hand to existing databases — `IF NOT EXISTS` will not alter an existing table.
- Vendor-specific file: `schema-postgresql.sql` + `spring.sql.init.platform: postgresql` if more than one DB is ever targeted.
- Tests: the same `schema.sql` runs against the Zonky embedded Postgres, so tests exercise the exact production DDL.

### C. Flyway

```
src/main/resources/db/migration/V1__event_publication.sql   ← §2.1
```

Add `spring-boot-starter-flyway` + `flyway-database-postgresql`, and **drop** `spring.sql.init.mode: always` (two DDL runners on one schema is a race you don't want). 
Modulith's own initializer is explicitly ordered **after** Flyway/Liquibase (`@AutoConfiguration(afterName = {...FlywayAutoConfiguration, ...LiquibaseAutoConfiguration})`), but disabling it is still cleaner — one owner per table.

---

## 4. Configuration

### 4.1 Minimum for a manually created table

```yaml
spring:
  modulith:
    events:
      jdbc:
        schema-initialization:
          enabled: false          # we own the DDL
```

That is the whole integration. `spring-modulith-starter-jdbc` (already in `pom.xml`) auto-configures:

```
JdbcEventPublicationAutoConfiguration
  DatabaseType                  ← from DataSource metadata ("PostgreSQL")
  JdbcRepositorySettings        ← DatabaseType + completion-mode + jdbc.* properties
  EventPublicationRepository    ← JdbcEventPublicationRepositoryV2 (JdbcTemplate + Jackson EventSerializer)
  DatabaseSchemaInitializer     ← only when schema-initialization.enabled — the thing you are turning off
```

> **✅ verified — with the property absent, Modulith runs its DDL.** 
> Configuration metadata and `JdbcConfigurationProperties` (`@DefaultValue("false")`) say `false`, 
> but the initializer is gated by `@ConditionalOnProperty(..., havingValue = "true", matchIfMissing = true)` — that condition wins. 
> Proven by `SchemaInitializationDefaultIT`: property absent ➡️ `databaseSchemaInitializer` bean present and `event_publication` created. 
> Still **always set it explicitly.**

### 4.2 All registry-relevant properties

| Property                                                                | Default      | Effect                                                                            |
|-------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------------------|
| `spring.modulith.events.jdbc.schema-initialization.enabled`             | effectively `true` (§4.1) | Modulith runs `schemas/v2/schema-<db>.sql` itself                                 |
| `spring.modulith.events.jdbc.schema`                                    | —            | qualifies the table: `<schema>.EVENT_PUBLICATION`                                 |
| `spring.modulith.events.jdbc.use-legacy-structure`                      | `false`      | `true` ➡️ V1 table + V1 repository                                                |
| `spring.modulith.events.completion-mode`                                | `update`     | `update` / `delete` / `archive` (§6)                                              |
| `spring.modulith.events.republish-outstanding-events-on-restart`        | `false`      | resubmit incomplete rows once at startup; `false` ≠ lost (§4.2.3)                 |
| `spring.modulith.events.staleness.published / processing / resubmitted` | unset ➡️ off | `Duration`: flip rows stuck in that state to `FAILED` after it (§4.2.1)           |
| `spring.modulith.events.staleness.check-intervall`                      | `1m`         | `Duration`: monitor fixed delay (the name really is spelled `intervall`) (§4.2.1) |
| `spring.modulith.events.registry-trigger-annotation`                    | unset        | 2.1+; narrow which `AFTER_COMMIT` listeners get a row (§4.2.2)                    |

One more example: 
```yaml
spring:
  modulith:
    events:
      completion-mode: archive                          # update (default) | delete | archive
      republish-outstanding-events-on-restart: false   # see §4.2.3
      registry-trigger-annotation: org.springframework.modulith.events.ApplicationModuleListener
      jdbc:
        schema-initialization:
          enabled: false
      staleness:                                       # 2.0+; all zero ➡️ monitor not registered
        published: 0
        processing: 0
        resubmitted: 0
```

#### 4.2.1 Staleness monitor — units, default, activation

From `StalenessProperties` / `StalenessMonitorConfiguration` (`spring-modulith-events-core-2.1.0`).

**Units.** All four staleness properties are `java.time.Duration` with no `@DurationUnit`, so Spring Boot's rule applies:
- bare number ➡️ **milliseconds** (`5000` = 5 s)
- prefer explicit: `30s`, `5m`, `PT30S` (suffixes `ns us ms s m h d`)
- `0` = `Duration.ZERO` = threshold off

**Default.** `check-intervall` absent ➡️ `Duration.ofMinutes(1)` (constructor fallback). 

The thresholds default to `ZERO`.

**Activation — both must hold, else the interval is irrelevant:**
1. `monitorStaleness()` — at least one of `published` / `processing` / `resubmitted` is non-zero; otherwise `configureTasks` registers nothing.
2. Scheduling is enabled. `StalenessMonitorConfiguration` is a `SchedulingConfigurer`; its `configureTasks` is invoked only by the post-processor that `@EnableScheduling` registers. 
   - Modulith's auto-config enables async, not scheduling, and this repo has no `@EnableScheduling`.

| Thresholds               | `@EnableScheduling` | Effect                                            |
|--------------------------|---------------------|---------------------------------------------------|
| all 0 / unset (repo now) | any                 | no monitor; `check-intervall` ignored             |
| ≥1 set                   | missing (repo now)  | likely no monitor — **⚠️ unverified**             |
| ≥1 set                   | present             | runs every `check-intervall` (1 min), fixed delay |

> **⚠️ unverified — point 2** is inferred from Spring's scheduling mechanics, not proven here. 
> Check: set `staleness.processing: 5m`, boot, and look for the log line `Checking for stale event publications every …`; 
> absent ➡️ add `@EnableScheduling` (e.g. on a `guru.ddd.config` class) and re-check.

#### 4.2.2 `registry-trigger-annotation` — which listeners get a row

From `PersistentApplicationEventMulticaster.TransactionalEventListeners#byAnnotationFilter` (`spring-modulith-events-core-2.1.0`, `@since 2.1`).

**Unset (default)** ➡️ **every** `AFTER_COMMIT` `TransactionalApplicationListener` gets a row — a plain `@TransactionalEventListener` too, not only `@ApplicationModuleListener`.

**Set** ➡️ value is an annotation **FQCN**; only `AFTER_COMMIT` listener methods carrying it (directly or meta-annotated) are recorded:

```yaml
spring.modulith.events.registry-trigger-annotation: org.springframework.modulith.events.ApplicationModuleListener
```

**Why:** `AFTER_COMMIT` listeners that need no delivery guarantee (cache eviction, metrics, logging) otherwise each cost an `INSERT` in the business transaction (§5 step 1) plus a row to purge.

**Edge cases (from source):**
- programmatic listeners (not a `TransactionalApplicationListenerMethodAdapter`, i.e. not an annotated method) are **excluded** whenever the property is set.
- non-annotation type or unknown class ➡️ `IllegalStateException` — evaluated at **publish time**, not startup.
- the Javadoc names it `spring.modulith.events.annotation`; the code reads `registry-trigger-annotation`.

> **⚠️ unverified —** filtered-out listeners are presumably still invoked after commit (the filter only selects what is persisted), 
> but without a row they get **no retry / resubmission** on failure.

#### 4.2.3 `republish-outstanding-events-on-restart` — what `false` does and doesn't mean

From `PersistentApplicationEventMulticaster#afterSingletonsInstantiated` (`spring-modulith-events-core-2.1.0`) and `spring-modulith-events-api-2.1.0`.

**The flag only controls the one-shot resubmission at boot.** 
- `true` ➡️ once per startup: `resubmitIncompletePublications(__ -> true)`.
- `false` / unset ➡️ early return; nothing else changes.
- Legacy key `spring.modulith.republish-outstanding-events-on-restart` is still read as fallback.

**`false` ≠ lost.** Incomplete rows stay in the table and can be resubmitted **manually** via injectable beans:

| Bean                          | Method                                                        |
|-------------------------------|---------------------------------------------------------------|
| `IncompleteEventPublications` | `resubmitIncompletePublications(Predicate<EventPublication>)` |
|                               | `resubmitIncompletePublicationsOlderThan(Duration)`           |
|                               | `resubmitIncompletePublications(ResubmissionOptions)`         |
| `FailedEventPublications`     | `resubmit(ResubmissionOptions)`                               |

Typical: call one from your own `@Scheduled` job or an admin endpoint.

**No other automatic retry.** The staleness monitor (§4.2.1) only flips stuck rows to `FAILED`; it never republishes.

➡️ `false` + no code calling these APIs ➡️ incomplete rows are **never redelivered**, but persist until resubmitted or purged (§7.1).

### 4.3 A dedicated schema

Keep infrastructure tables out of `public`:

```sql
CREATE SCHEMA IF NOT EXISTS modulith;
CREATE TABLE IF NOT EXISTS modulith.event_publication ( ... §2.1 ... );
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON modulith.event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx   ON modulith.event_publication (completion_date);
```

```yaml
spring:
  modulith:
    events:
      jdbc:
        schema: modulith
        schema-initialization:
          enabled: false
```

Every statement is then emitted against `modulith.EVENT_PUBLICATION` (and `modulith.EVENT_PUBLICATION_ARCHIVE`). 
The schema name is concatenated unquoted — use a lower-case, unquoted name.

---

## 5. What Actually Hits the Table — One Event, One Listener

```java
// module A — publisher
@Transactional
void open(TicketId id) {
    tickets.save(...);
    events.publishEvent(new TicketOpened(id));
}

// module B — listener
@ApplicationModuleListener(id = "helpdesk.ticket-opened.notify")
void on(TicketOpened event) { ... }
```

| #  | Thread / tx                                 | SQL (abridged, from `JdbcEventPublicationRepositoryV2`)                                                                                                                              | Row after             |
|----|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------|
| 1  | publisher, **business tx**                  | `INSERT INTO EVENT_PUBLICATION (ID, EVENT_TYPE, LISTENER_ID, PUBLICATION_DATE, SERIALIZED_EVENT, STATUS, COMPLETION_ATTEMPTS, LAST_RESUBMISSION_DATE) VALUES (…, 'PUBLISHED', 1, …)` | `PUBLISHED`           |
| –  | commit ➡️ `AFTER_COMMIT` ➡️ `@Async` submit |                                                                                                                                                                                      |                       |
| 2  | executor, `REQUIRES_NEW`                    | `UPDATE … SET STATUS = 'PROCESSING' WHERE ID = ? AND STATUS != 'PROCESSING'`                                                                                                         | `PROCESSING`          |
| 3  | executor, `REQUIRES_NEW`                    | *your listener body*                                                                                                                                                                 |                       |
| 4a | success, `REQUIRES_NEW`                     | `UPDATE … SET STATUS = 'COMPLETED', COMPLETION_DATE = ? WHERE LISTENER_ID = ? AND COMPLETION_DATE IS NULL AND SERIALIZED_EVENT = ?`                                                  | `COMPLETED`, dated    |
| 4b | exception, `REQUIRES_NEW`                   | `UPDATE … SET STATUS = 'FAILED' WHERE ID = ? AND STATUS != 'FAILED'`                                                                                                                 | `FAILED`, date `NULL` |

Key point for a manual table: 
- **step 1 runs inside the publisher's transaction.** 
- If the table is missing, has the wrong columns, or the role lacks `INSERT`, the **business transaction rolls back** — the registry is not a side-channel, it is on the critical path.

Resubmission (manual, restart, or `IncompleteEventPublications` / `FailedEventPublications`) claims a row with:

```sql
UPDATE EVENT_PUBLICATION
SET STATUS = 'RESUBMITTED', COMPLETION_ATTEMPTS = COMPLETION_ATTEMPTS + 1, LAST_RESUBMISSION_DATE = ?
WHERE ID = ? AND STATUS != 'RESUBMITTED'
```

---

## 6. Completion Modes and Their DDL Needs

| Mode               | On success                                                     | Tables needed                        | Trade-off                                     |
|--------------------|----------------------------------------------------------------|--------------------------------------|-----------------------------------------------|
| `update` (default) | row kept, `status = COMPLETED`, `completion_date` set          | `event_publication`                  | full audit trail; table grows ➡️ purge (§7.1) |
| `delete`           | `DELETE … WHERE LISTENER_ID = ? AND SERIALIZED_EVENT = ?`      | `event_publication`                  | table holds only open work; no history        |
| `archive`          | `INSERT INTO …_ARCHIVE SELECT …` then `DELETE` from main table | `event_publication` **+** `_archive` | hot table stays small, history kept elsewhere |

Switching **to** `archive` with a manual table: 
- create §2.2 **before** restarting — the first completion otherwise fails with "relation does not exist", leaving rows un-completed (they will be re-delivered).

---

## 7. Operating It

### 7.1 Useful queries

```sql
-- open work per listener
SELECT listener_id, status, count(*)
FROM event_publication
WHERE completion_date IS NULL
GROUP BY listener_id, status;

-- stuck (e.g. crashed mid-processing) for > 10 min
SELECT id, listener_id, status, publication_date, completion_attempts
FROM event_publication
WHERE completion_date IS NULL AND publication_date < now() - interval '10 minutes'
ORDER BY publication_date;
```

Purging in `update` mode — prefer the API over raw SQL:

```java
completedEventPublications.deletePublicationsOlderThan(Duration.ofDays(7));
```

(`CompletedEventPublications` is an injectable bean; schedule it with `@Scheduled`.)

---

### 7.2 Things that silently orphan rows

- **Renaming the event class / package** — `event_type` stores the FQCN; resubmission of old rows can no longer deserialize.
- **Renaming or moving the listener method, class, or parameter type** — the default listener id is derived from the method signature; old rows then match no listener. 
  - Default (`ApplicationListenerMethodAdapter#getDefaultListenerId`, spring-context 7.0.9): `ClassUtils.getQualifiedMethodName(method)` + `(` FQCN param types `)`, e.g. 
    `guru.ddd.helpdesk.app.TicketNotifier.on(guru.ddd.helpdesk.TicketOpened)`
  - Where the id is used: 
    - `INSERT` writes it to `listener_id` (§5 step 1); 
    - completion matches `LISTENER_ID = ? AND SERIALIZED_EVENT = ?` (§5 step 4a); 
    - resubmission (`invokeTargetListener`) invokes only live listeners whose `getListenerId()` equals the stored id — no match ➡️ nothing is invoked.
  - ✅ pin it: `@ApplicationModuleListener(id = "…")` — `@AliasFor(annotation = EventListener.class, attribute = "id")`, since 1.1. A pinned id survives refactors.
  - Keep ids **unique** — **⚠️ unverified**: two listeners sharing an id would presumably both be invoked on resubmission.
- **Changing the event's JSON shape** — completion matches on `SERIALIZED_EVENT = ?`; the payload re-serialized in step 4a must equal the one stored in step 1. 
  - Stable for records; 
  - watch for non-deterministic fields (maps with unordered keys, custom serializers).

---

## 8. Checklist — Manual Table, This Repo

1. ✅ §2.1 + §2.2 DDL in `src/main/resources/schema.sql` (runs via existing `spring.sql.init.mode: always`).
2. ✅ `spring.modulith.events.jdbc.schema-initialization.enabled: false` in `application.yml`.
3. ✅ `completion-mode: archive` ➡️ archive table created by `schema.sql`.
4. ⬜ Pin listener ids with `@ApplicationModuleListener(id = …)`.
5. ✅ `EventPublicationRegistryIT` (Zonky): tables created by `schema.sql` (not Modulith), success ➡️ archive row `COMPLETED` + date, exception ➡️ `FAILED` + `NULL` date.
6. ✅ §4.1 resolved by `SchemaInitializationDefaultIT`.
7. ⬜ If staleness thresholds are used: resolve §4.2.1 ⚠️ (does the monitor run without `@EnableScheduling`?).

Tracked in `docs/dev/finished/2026-09-23-22-42_plan-manual-event-publication-table.md`.

---

##### Version Notes

- Spring Modulith 2.1.0 
- Spring Boot 4.1.1 
- PostgreSQL 
- DDL and SQL quoted from `spring-modulith-events-jdbc-2.1.0` sources; re-diff `schemas/v2/schema-postgresql.sql` on every Modulith upgrade, since a manually owned table will not pick up upstream changes.
