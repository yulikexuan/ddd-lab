# jMolecules Study Roadmap

jMolecules puts DDD and architecture concepts into code, as annotations or as interfaces. Tools then check them (ArchUnit, Spring Modulith) or act on them (Spring, Jackson, ByteBuddy).
The track has 5 weekly labs (`J1`–`J5`). Do it **before** [hkj](../hkj/README.md) and the [IDDD roadmap](../iddd/README.md).

| Artifact in `pom.xml`               | Brings in                                                                                                              |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `jmolecules-starter-ddd`            | `jmolecules-ddd`, `jmolecules-events`, `jmolecules-apt`, `jmolecules-ddd-integration`, `jmolecules-spring` (runtime)   |
| `jmolecules-hexagonal-architecture` | `@Application`, `@Port`, `@PrimaryPort`, `@SecondaryPort`, `@Adapter`, `@PrimaryAdapter`, `@SecondaryAdapter`          |
| `jmolecules-starter-test`           | `jmolecules-archunit`, `archunit-junit5`, `spring-boot-starter-test`                                                   |

Versions come from `jmolecules-bom` 2025.0.2: core 2.0.1, integrations 0.33.0.

Sources (more talks and slides in [modeling/jmolecules.md](../iddd/modeling/jmolecules.md)):
- [Architecturally evident code with jMolecules](https://odrotbohm.github.io/2021/02/Architecturally-evident-code-with-jMolecules): the author's blog post on why the library exists. Read it before J1.
- [`odrotbohm/tactical-ddd-workshop`](https://github.com/odrotbohm/tactical-ddd-workshop): the author's workshop on Spring Boot and jMolecules, the closest match to these labs.
- [`xmolecules/jmolecules`](https://github.com/xmolecules/jmolecules): the core README, plus the annotation and type sources. The source is small, and its Javadoc is the spec.
- [`xmolecules/jmolecules-integrations`](https://github.com/xmolecules/jmolecules-integrations): one readme per module (`jmolecules-spring`, `jmolecules-jackson`, `jmolecules-bytebuddy`, `jmolecules-archunit`).
- [`xmolecules/jmolecules-examples`](https://github.com/xmolecules/jmolecules-examples): last updated around 2023. Read it for ideas only.
- [`odrotbohm/spring-restbucks`](https://github.com/odrotbohm/spring-restbucks): a full app built with jMolecules by its author.
- Spring Modulith reference: [Verification](https://docs.spring.io/spring-modulith/reference/verification.html), [Events](https://docs.spring.io/spring-modulith/reference/events.html), [Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html).

---

## 1. How to Study

**Every week:**

1. **Read** (1–2 h): the week's sources. Open the jMolecules type itself, not just the README.
2. **Lab** (2–3 h): ask Claude to *"scaffold week J<N>"*. You get a spec, failing tests and TODO stubs. You make the tests green.
3. **Review:** ask Claude to *"review week J<N>"*, then address the findings.
4. **Note:** copy the [template](#4-lab-note-template) to `docs/jmolecules/labs/0N-<topic>.md`.
5. **Commit:** `JMOL-000N <topic>`.

**Done when:** `mvn verify` is green (incl. `ModuleStructureTest` and `ArchitectureTest`), the note is written and the review is addressed.

---

## 2. Weekly Plan

#### Week J1 · Stereotypes as code — `identityaccess`
- **Read:** the core README ("annotation-based" vs "type-based" models); `org.jmolecules.ddd.annotation` and `org.jmolecules.ddd.types`.
- **Look for:** the claim each stereotype makes (`@AggregateRoot`, `@Entity`, `@ValueObject`, `@Identity`, `@Association`, `@Repository`, `@Service`, `@Factory`, `@Module`, `@BoundedContext`). Note what the type model's generics let the compiler enforce, e.g. `AggregateRoot<T extends AggregateRoot<T, ID>, ID extends Identifier>` and `Entity<T extends AggregateRoot<T, ?>, ID>`.
- **Lab:** model `Tenant`, `TenantId` and `FullName` twice, first with annotations and then with interfaces. Write the trade-offs in the note and keep one.
- **Boot 4 / Java:** `TenantId` is a record that implements `Identifier` and has a static `of(..)`. Week J5's converters need that factory.

#### Week J2 · DDD rules — `agilepm`
- **Read:** `JMoleculesDddRules`; Modulith *Verification*.
- **Look for:** the four rules: `entitiesShouldBeDeclaredForUseInSameAggregate`, `aggregateReferencesShouldBeViaIdOrAssociation`, `annotatedEntitiesAndAggregatesNeedToHaveAnIdentifier` and `valueObjectsMustNotReferToIdentifiables`, plus `all()`. With `jmolecules-archunit` on the classpath, `ApplicationModules.verify()` runs these rules automatically.
- **Lab:** a slice of `Product`, `BacklogItem` and `Task`. For each rule, put one violating fixture in `src/test` and assert that the rule flags it (`rule.evaluate(classes).hasViolation()`). Then make sure the real model passes.
- **Boot 4 / Modulith:** `ArchRule.check` vs `evaluate`. Rule 2 is IDDD's "reference other aggregates by identity" (Week 10).

#### Week J3 · Architecture stereotypes — `identityaccess`
- **Read:** the core README's architecture section; `JMoleculesArchitectureRules`; Modulith *Verification* (`VerificationOptions`, `withAdditionalVerifications`) and *Fundamentals* (detection strategies).
- **Look for:** the three styles:
  - Layered: `@DomainLayer` … `@InterfaceLayer`.
  - Onion: `@DomainRing` … `@InfrastructureRing`.
  - Hexagonal: `@Application`, `@PrimaryPort` / `@SecondaryPort`, `@PrimaryAdapter` / `@SecondaryAdapter`.

  Also look at:
  - Annotations on a package (`package-info.java`) vs on a type; the hexagonal annotations allow both.
  - `ensureHexagonal()` vs `ensureHexagonal(VerificationDepth)`.
  - jMolecules `@Module` vs Modulith `@ApplicationModule`.
- **Lab:** lay out `identityaccess` as `domain.model`, `application` and `port.adapter`. Add one secondary port with an in-memory adapter. Add `ensureHexagonal()` to verification, then break it once (domain → adapter).
- **Repo:** revisit `DomainRules.DOMAIN_ACCESSING_RULE`; it still targets `domain.repository` / `domain.service`. Only the hexagonal artifact is on the classpath, so read about layered and onion but don't code them. IDDD Week 4 builds on this.

#### Week J4 · Domain events — `identityaccess` → `collaboration`
- **Read:** `org.jmolecules.event`; Modulith *Events*; [Event Publication Registry](../event_publication_registry.md); [event listener patterns](../event_listener_patterns.md).
- **Look for:** `@DomainEvent` vs the `DomainEvent` type; `@DomainEventPublisher`; `@DomainEventHandler`; `@Externalized`. Separate what jMolecules only *documents* from what Spring actually *wires*.
- **Lab:** a `TenantProvisioned` record, published on provisioning. A listener in `collaboration` reacts, and a `Scenario` test proves it.
- **Find out:** without ByteBuddy, does anything on this classpath turn a `@DomainEventHandler` method into a listener? Prove the answer with a test.

#### Week J5 · Integrations — `identityaccess`
- **Read:** the integrations readmes for `jmolecules-spring`, `jmolecules-jackson` and `jmolecules-bytebuddy`; the examples repo's `spring-data-jdbc`.
- **Look for:**
  - `jmolecules-spring`: converters between primitives and `Identifier` / `Association` (they need a static `of(..)`), registered for Spring MVC and Spring Data, and `AssociationResolver`.
  - Jackson module: it writes an `Identifier` or a single-property `@ValueObject` as its raw value.
  - ByteBuddy: it adds missing `Persistable` and `@Table` for Spring Data JDBC, and Spring stereotypes.
- **Lab:**
  - Persist `Tenant` with its typed `TenantId` through Spring Data JDBC: `schema.sql` plus a `@DataJdbcTest` on Zonky.
  - Add `GET /tenants/{tenantId}`, binding the typed id, and check that the JSON shows the raw id.
  - Try ByteBuddy on a throwaway branch: run `javap -v` before and after, then decide whether to keep it.
- **Setup:** add `jmolecules-jackson3` (Boot 4 uses Jackson 3; it is in the BOM).
- **Open:** jMolecules `Association` or Spring Data JDBC `AggregateReference` for cross-aggregate references? This feeds IDDD Week 10.

---

## 3. Concept → jMolecules Cheat-Sheet

| DDD concept                  | Annotation                     | Type                    | Checked / used by                                       |
|------------------------------|--------------------------------|-------------------------|---------------------------------------------------------|
| Aggregate root               | `@AggregateRoot`               | `AggregateRoot<T, ID>`  | DDD rules; ByteBuddy → `Persistable`                    |
| Entity                       | `@Entity`                      | `Entity<A, ID>`         | DDD rules (used inside its own aggregate only)          |
| Identity                     | `@Identity` (field)            | `Identifier`            | DDD rules; Spring converters; Jackson                   |
| Value object                 | `@ValueObject`                 | `ValueObject`           | DDD rules (no references to identifiables); Jackson     |
| Reference to another aggregate | `@Association`               | `Association<T, ID>`    | DDD rules; Spring converters                            |
| Repository                   | `@Repository`                  | `Repository<T, ID>`     | ByteBuddy → Spring Data repository (type form only)     |
| Service / Factory            | `@Service` / `@Factory`        | —                       | ByteBuddy → Spring `@Service` / `@Component`            |
| Domain event                 | `@DomainEvent`                 | `DomainEvent`           | `@Externalized` → Modulith externalization              |
| Module / Bounded Context     | `@Module` / `@BoundedContext`  | —                       | Modulith module detection (`@Module`)                   |
| Port / Adapter               | `@PrimaryPort` … `@SecondaryAdapter` | —                 | `JMoleculesArchitectureRules.ensureHexagonal()`         |

---

## 4. Lab Note Template

```markdown
# Week JN · <topic>

## In my own words
<½ page: what the stereotype claims and why it matters>

## What jMolecules checks or generates here
<rule / converter / generated code, and the proof (test, javap)>

## Choices I made
<annotation vs type, keep vs drop, and why>

## Open questions
<what is still unclear or disputed>
```
