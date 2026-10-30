# IDDD Study Roadmap

Vaughn Vernon, *Implementing Domain-Driven Design* (2013): 14 chapters + Appendix A.
This repo has one weekly lab per chapter. The labs rebuild the book's **SaaSOvation** case on Spring Boot 4.1 / Spring Modulith 2.1 / Java 25.

| SaaSOvation Bounded Context | Module                    | Subdomain type (book) |
|-----------------------------|---------------------------|-----------------------|
| Identity and Access         | `guru.ddd.identityaccess` | Generic               |
| Collaboration               | `guru.ddd.collaboration`  | Supporting            |
| Agile Project Management    | `guru.ddd.agilepm`        | Core                  |

Reference code: [`VaughnVernon/IDDD_Samples`](https://github.com/VaughnVernon/IDDD_Samples). It is Java 7 with Hibernate/MySQL, LevelDB and RabbitMQ. Read it for the model; don't copy its tech choices.

---

## 1. How to Study

**Before week 0:** finish the [jMolecules](../jmolecules/README.md) and [hkj](../hkj/README.md) tracks. The labs below use both.

**Week 0 (primer):** *Domain-Driven Design Distilled* (Vernon, ~150 pp), ch 1–5. It gives you the whole picture before IDDD's detail.

**Every week:**

1. **Skim** (30 min): headings, diagrams, the chapter's wrap-up.
2. **Read** (2–3 h): follow the SaaSOvation storyline. Note each "aha" and each disagreement.
3. **Lab** (2–4 h): ask Claude to *"scaffold week N"*. You get a spec, failing tests and TODO stubs. You make the tests green.
4. **Review:** ask Claude to *"review week N"*. Claude checks your code against the book, then you address the findings.
5. **Note:** copy the [template](#4-lab-note-template) to `docs/iddd/labs/NN-<topic>.md` and add new terms to the [glossary](./glossary.md).
6. **Commit:** `DDD-00NN <Chapter> - <topic>`.

**Done when:** `mvn verify` is green (incl. `ModuleStructureTest` and `ArchitectureTest`), the note is written and the review is addressed.

---

## 2. Weekly Plan

### Part I — Strategic Design

#### Week 1 · Ch 1 Getting Started with DDD — `agilepm`
- **Look for:** the DDD scorecard (is DDD worth it here?), Ubiquitous Language, symptoms of an anemic domain model.
- **Lab:** refactor an anemic `BacklogItem` (getters/setters) into a rich one with intention-revealing behaviour, e.g. `commitTo(Sprint)`. Seed the glossary.
- **Boot 4 / Java:** records vs classes; tests as the executable spec.

#### Week 2 · Ch 2 Domains, Subdomains, and Bounded Contexts — all
- **Look for:** problem space vs solution space; Core, Supporting and Generic subdomains; a BC as a *linguistic* boundary; why SaaSOvation pulled users and permissions out of Collaboration.
- **Lab:** in `labs/02`, classify each subdomain and justify each BC boundary. Name one term that means different things in two BCs.
- **Boot 4 / Modulith:** one Modulith module = one BC; `ModuleStructureTest`.

#### Week 3 · Ch 3 Context Maps — all
- **Look for:** Partnership, Shared Kernel, Customer–Supplier, Conformist, Anticorruption Layer (ACL), Open Host Service (OHS), Published Language (PL), Separate Ways; upstream vs downstream.
- **Lab:** IdentityAccess is upstream (OHS + PL). Collaboration and AgilePM are downstream (ACL). Enforce these arrows in code; any other dependency must fail `verify()`.
- **Boot 4 / Modulith:** `@NamedInterface`, `@ApplicationModule(allowedDependencies = …)`, a `Documenter` C4 diagram.

#### Week 4 · Ch 4 Architecture — all
- **Look for:** Layers + DIP, Hexagonal (Ports and Adapters), REST, CQRS, Event-Driven, Event Sourcing. Quality requirements should pick the architecture, not fashion.
- **Lab:** a hexagonal package layout per module (`domain.model`, `application`, `port.adapter`); the first port and adapter. Only read CQRS and Event-Driven now; they come back in weeks 13–15.
- **Boot 4 / jMolecules:** `@PrimaryPort` / `@SecondaryAdapter` etc.; `JMoleculesArchitectureRules.ensureHexagonal()` added to `verify()`.

### Part II — Tactical Design

#### Week 5 · Ch 5 Entities — `identityaccess`
- **Look for:** identity-generation strategies and their timing; surrogate vs domain identity; stable identity; validating attributes, whole objects and compositions.
- **Lab:** `Tenant` and `User` with typed ids (`TenantId`, `UserId`) and self-validation.
- **Boot 4 / jMolecules:** `@Entity`, `@Identity`; Spring Data JDBC converters for typed ids.

#### Week 6 · Ch 6 Value Objects — `identityaccess`
- **Look for:** measures/describes, immutability, conceptual whole, replaceability, value equality, side-effect-free functions; preferring VOs at integration points.
- **Lab:** `FullName`, `EmailAddress`, `ContactInformation`, `EncryptedPassword`.
- **Boot 4 / Java:** records and sealed types; `@Embedded` VOs in Spring Data JDBC.

#### Week 7 · Ch 7 Services — `identityaccess`
- **Look for:** when an operation belongs to no Entity or VO; domain service vs application service; Separated Interface; how overusing services leads to an anemic model.
- **Lab:** `AuthenticationService` (domain service), `EncryptionService` (port in the domain, adapter in infrastructure), `PasswordService`.
- **Boot 4 / jMolecules:** jMolecules `@Service` (a DDD concept) vs Spring `@Service` (a bean).

#### Week 8 · Ch 8 Domain Events — `identityaccess`
- **Look for:** "When…" / "If that happens…" phrases from domain experts; past-tense names; event payload (ids, timestamp); event store; forwarding events; idempotent receivers.
- **Lab:** `TenantProvisioned` and `UserRegistered`, published by the aggregate.
- **Boot 4 / Modulith:** `@DomainEvents` / `AbstractAggregateRoot`, `@ApplicationModuleListener`, the Event Publication Registry ([docs](../event_publication_registry.md)).

#### Week 9 · Ch 9 Modules — `agilepm`
- **Look for:** Module names come from the Ubiquitous Language, not from patterns (no `entities/` or `services/` packages); low coupling; reach for a Module before a new BC.
- **Lab:** split `agilepm.domain.model` into `product`, `team` and `discussion`.
- **Boot 4 / Modulith:** a DDD *Module* is a package **inside** a BC; a Modulith *module* is the BC itself. Generate a module canvas (`writeModuleCanvases`).

#### Week 10 · Ch 10 Aggregates — `agilepm`
- **Read first:** Vernon's essays *Effective Aggregate Design* I–III.
- **Look for:** the 4 rules (model true invariants in consistency boundaries, design small aggregates, reference other aggregates by identity, use eventual consistency outside the boundary); when to break them; Tell-Don't-Ask; optimistic concurrency.
- **Lab:** `Product`, `BacklogItem`, `Release`, `Sprint`. Start from the book's large-cluster mistake and split it.
- **Boot 4 / Spring Data JDBC:** `AggregateReference`, `@Version`, a concurrency test.

#### Week 11 · Ch 11 Factories — `agilepm`, `collaboration`
- **Look for:** factory methods on an aggregate root; factories in a domain service that crosses a BC.
- **Lab:** `Product.planBacklogItem(..)`, `Tenant.registerUser(..)`; `CollaboratorService` builds a `Collaborator` from IdentityAccess data.
- **Boot 4 / Java:** static and instance factory methods. Keep Spring out of the domain.

#### Week 12 · Ch 12 Repositories — `agilepm`, `identityaccess`
- **Look for:** collection-oriented vs persistence-oriented repositories; one repository per aggregate root; repository vs DAO; transactions belong in the application layer.
- **Lab:** repositories for the aggregates so far, plus the DB schema.
- **Boot 4 / Spring Data JDBC:** persistence-oriented `save()`, `schema.sql`, `@DataJdbcTest` + Zonky embedded Postgres.

### Part III — Integration and Application

#### Week 13 · Ch 13 Integrating Bounded Contexts — `collaboration`, `agilepm`
- **Look for:** fallacies of distributed computing; REST + ACL; messaging; long-running processes; idempotency and message order.
- **Lab:** consume IdentityAccess events. ACL translators map `User` → `Collaborator` and `User` → `ProductOwner` / `TeamMember`.
- **Boot 4 / Modulith:** Published Language = event records in a `@NamedInterface` package; `Scenario` tests; optional `@Externalized`.

#### Week 14 · Ch 14 Application — all
- **Look for:** thin application services; transactions and security; DTOs vs Domain Payload Objects; use-case-optimal queries; composing several BCs.
- **Lab:** application services and REST endpoints. End-to-end flow: provision tenant → register user → a collaborator and a team member appear.
- **Boot 4 / Modulith:** `@Transactional`, WebMvc, `@ApplicationModuleTest`.

#### Week 15 · Appendix A Aggregates and Event Sourcing (A+ES) — `collaboration`
- **Look for:** state rebuilt from history; `apply`/`when` methods; concurrency through the expected version; snapshots; given-events / when-command / then-events tests.
- **Lab:** event-sourced `Forum` / `Discussion` over an append-only Postgres event table.
- **Compare:** [Event-Sourced Domain Model Pattern](../event_sourced_domain_model_pattern.md).

---

## 3. 2013 → 2026 Cheat-Sheet

| Book (2013)                                        | This repo (Boot 4.1 / Modulith 2.1 / Java 25)                              |
|----------------------------------------------------|----------------------------------------------------------------------------|
| Mutable classes, self-encapsulating setters        | Records, sealed interfaces, compact-constructor validation                 |
| `AssertionConcern`                                 | Compact-ctor checks / Guava `Preconditions` / jakarta.validation           |
| Hibernate + MySQL                                  | Spring Data JDBC + Postgres: aggregate = unit of persistence, no lazy load |
| Thread-local `DomainEventPublisher`                | `@DomainEvents` / `ApplicationEventPublisher`                              |
| `EventStore` + `NotificationLog` (REST) + RabbitMQ | Event Publication Registry (outbox) + optional `@Externalized`             |
| LevelDB key-value repositories                     | Postgres                                                                   |
| BC = separate deployable                           | BC = Modulith application module, boundaries checked by `verify()`         |

---

## 4. Lab Note Template

```markdown
# Week NN · Ch NN <Chapter> — <topic>

## In my own words
<½ page: the chapter's core idea>

## New terms
<term — meaning (also added to glossary.md)>

## Book vs this repo
<where Boot 4 / Modulith / Java 25 changed the implementation, and why>

## Open questions
<what is still unclear or disputed>
```
