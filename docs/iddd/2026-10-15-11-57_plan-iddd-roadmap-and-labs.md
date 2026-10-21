# Plan — IDDD study roadmap + SaaSOvation labs on Boot 4

Book: Vaughn Vernon, *Implementing Domain-Driven Design* (2013), ch 1–14 + App. A (A+ES).
Decisions: new to DDD · SaaSOvation domain · ~1 chapter/week · you code, Claude scaffolds + reviews · delete `helpdesk` · jMolecules annotations + archunit only (no ByteBuddy).

Reference code: `VaughnVernon/IDDD_Samples`. Gradle build, Java 7, Hibernate/MySQL, LevelDB, RabbitMQ. Read it for the ideas only; don't copy its tech choices.

## Step 0 — Setup (Claude, after plan approval) ✅ 2026-10-15

Done. Deviations: no empty sub-packages (git can't track them; labs create them). `allowEmptyShould(true)` on `DOMAIN_ACCESSING_RULE` + `NO_CLASSES_SHOULD_USE_FIELD_INJECTION` (no classes yet). jMolecules pinned via `jmolecules-bom` 2025.0.2 → integrations 0.33.0.

1. `README.md`: the roadmap. Reading method, the weekly table below, the 2013→2026 cheat-sheet, and a lab-note template. Link it from the root `README.md`.
2. Delete `helpdesk` (main + tests). Drop its scan from `DomainDrivenDesignCfg`.
3. Module skeletons `guru.ddd.{identityaccess,collaboration,agilepm}`. Each gets `config/<X>Cfg` (package-private), `package-info` with `@ApplicationModule`, and empty `domain.model` / `application` / `port.adapter` packages. Add each config package to the `DomainDrivenDesignCfg` scan and update `ModuleStructureTest`.
4. jMolecules: add `jmolecules-ddd`, `jmolecules-events` and `jmolecules-hexagonal-architecture` (compile scope), plus `jmolecules-archunit` (test scope). Pin versions via javadocs MCP at execution time, Boot 4–compatible line (integrations ≥ 0.32). Modulith `verify()` then runs the jMolecules DDD rules automatically.
5. `glossary.md`: the Ubiquitous Language glossary. It grows every week.
6. Verify: `mvn verify` is green.

## Reading method (per chapter, per week)

1. **Skim** (30 min): headings, diagrams, the chapter's "Wrap-Up".
2. **Read** (2–3 h): follow the SaaSOvation storyline. Note each "aha" and each disagreement.
3. **Lab** (2–4 h): Claude scaffolds it (spec, failing tests, TODO stubs). You make the tests green. Claude reviews against the book.
4. **Note** `docs/iddd/labs/NN-<topic>.md`: a ½-page summary in your own words, new glossary terms, how Boot 4 differs from the book, open questions.
5. **Commit** `DDD-00NN <Chapter> - <topic>`.

Primer (week 0): *DDD Distilled* (Vernon, ~150 pp) ch 1–5. Before ch 10, also read Vernon's essay series *Effective Aggregate Design* (parts I–III).

## Weekly labs

| Wk | Ch                 | Module(s)         | Lab (you implement)                                                                                                                    | Boot 4 / Modulith / Java focus                                                                      |
|----|--------------------|-------------------|----------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| 1  | 1 Getting Started  | agilepm           | Anemic `BacklogItem` (setters) → rich behaviour (`commitTo(Sprint)`). Seed the glossary.                                               | Records vs classes, tests as spec                                                                   |
| 2  | 2 Subdomains & BCs | all               | Classify subdomains (core / supporting / generic) and justify each BC boundary in `docs/iddd/labs/02`                                  | Modulith module = BC; `ModuleStructureTest`                                                         |
| 3  | 3 Context Maps     | all               | Map IdentityAccess (OHS/PL, upstream) to Collaboration and AgilePM (ACL, downstream). Enforce the arrows in code.                      | `@NamedInterface`, `allowedDependencies`, `Documenter` C4 diagram                                   |
| 4  | 4 Architecture     | all               | Hexagonal layout per module; first port + adapter. Read CQRS/EDA only and revisit in wk 13–15.                                         | `JMoleculesArchitectureRules.ensureHexagonal` in `verify()`                                         |
| 5  | 5 Entities         | identityaccess    | `Tenant`, `User` with typed ids (`TenantId`, `UserId`); identity generation; self-validation                                           | `@Identity`, `@Entity` (jMolecules); Spring Data JDBC typed-id converters                           |
| 6  | 6 Value Objects    | identityaccess    | `FullName`, `EmailAddress`, `ContactInformation`, `EncryptedPassword`; side-effect-free functions                                      | Records + sealed types; `@Embedded` VOs in JDBC                                                     |
| 7  | 7 Services         | identityaccess    | `AuthenticationService` (domain), `EncryptionService` (port + adapter), `PasswordService`; domain service vs application service       | `@Service` (jMolecules) vs Spring `@Service`                                                        |
| 8  | 8 Domain Events    | identityaccess    | `TenantProvisioned`, `UserRegistered`; publish from the aggregate                                                                      | `@DomainEvents` / `AbstractAggregateRoot`, `@ApplicationModuleListener`, Event Publication Registry |
| 9  | 9 Modules          | agilepm           | Name packages in the Ubiquitous Language (`product`, `team`, `discussion`). A DDD Module is a package *inside* a BC, not a BC.         | Modulith module canvas (`writeModuleCanvases`)                                                      |
| 10 | 10 Aggregates      | agilepm           | `Product`, `BacklogItem`, `Release`, `Sprint` under the 4 rules: true invariants, small, reference by id, eventual consistency outside | `AggregateReference`, `@Version` optimistic lock, concurrency test                                  |
| 11 | 11 Factories       | agilepm, collab   | `Product.planBacklogItem(..)`, `Tenant.registerUser(..)`; a factory in a domain service (`CollaboratorService`)                        | Static/instance factory methods; no Spring here                                                     |
| 12 | 12 Repositories    | agilepm, identity | One repository per aggregate root, persistence-oriented (`save`); schema per module                                                    | Spring Data JDBC, `schema.sql`, `@DataJdbcTest` + Zonky                                             |
| 13 | 13 Integrating BCs | collab, agilepm   | Consume IdentityAccess events. ACL translators: `User` → `Collaborator`, `User` → `ProductOwner` / `TeamMember`                        | Published Language as event records in a `@NamedInterface` package; `Scenario` tests                |
| 14 | 14 Application     | all               | Application services, transactions, REST endpoints; end-to-end: provision tenant → register user → collaborator + team member appear   | `@Transactional`, WebMvc, `@ApplicationModuleTest`                                                  |
| 15 | App. A A+ES        | collaboration     | Event-sourced `Forum` / `Discussion`: append-only event table, rehydration, optional snapshot                                          | Hand-rolled event store on Postgres; compare with `../event_sourced_domain_model_pattern.md`      |

Each lab is done when `mvn verify` is green (tests incl. `ModuleStructureTest` + `ArchitectureTest`), the lab note is written and the review is addressed.

## 2013 → 2026 cheat-sheet (goes in roadmap)

| Book (2013)                                          | This repo (Boot 4.1 / Modulith 2.1 / Java 25)                                |
|------------------------------------------------------|------------------------------------------------------------------------------|
| Mutable classes, self-encapsulating setters          | Records, sealed interfaces, compact-constructor validation                   |
| `AssertionConcern`                                   | Compact-ctor checks / Guava `Preconditions` / jakarta.validation             |
| Hibernate + MySQL                                    | Spring Data JDBC + Postgres: aggregate = unit of persistence, no lazy load   |
| Thread-local `DomainEventPublisher`                  | `@DomainEvents` / `ApplicationEventPublisher`                                |
| `EventStore` + `NotificationLog` (REST) + RabbitMQ   | Event Publication Registry (outbox) + optional `@Externalized`               |
| LevelDB key-value repos                              | Postgres                                                                     |
| BC = separate deployable                             | BC = Modulith application module, boundaries checked by `verify()`           |

## Risks

- jMolecules integrations versions run separately from `jmolecules-bom`. Confirm Boot 4.1 compatibility before pinning.
- Typed ids in Spring Data JDBC need converters (wk 5). Look them up in the Spring Data JDBC 4 docs when we get there.
- Some IDDD_Samples class names were confirmed only from the package lists. Verify them per lab.
