# Communication Patterns 

## ACL & OHS 

In Domain-Driven Design (DDD), **Anticorruption Layer (ACL) and Open-Host Service (OHS)** 
are architectural concepts rather than single out-of-the-box software products or libraries. 

Because they represent architectural boundaries and design patterns,  
so you implement them using common libraries and frameworks within the Java ecosystem.

Here is how these two concepts translate into comparable technologies and concrete patterns in Java 👇

---

### Anticorruption Layer (ACL) in Java

> contaminate /kənˈtæmɪneɪt/ 
> - something (with something): to make a substance or place dirty or no longer pure by adding a substance that is dangerous or carries disease.

In Java, an ACL is typically built by combining structural design patterns (Facade, Adapter, Translator) with the following tooling:

1. **Object Mapping Libraries (The Translators, stateless):**
- To map external Data Transfer Objects (DTOs) into your internal Domain Entities without leaking external field names 
  - MapStruct: `Entity ↔️ DTO`
    - A compile-time, type-safe bean mapping framework.
    - Generates efficient mapping code at compile time, ensuring your core domain doesn't depend on external types
2. **Declarative HTTP Clients (The Adapters, stateless):**
   - To wrap external APIs inside a clean interface defined by your domain (Dependency Inversion), Java developers use:
     - Spring `RestClient (the primary synchronous HTTP client)`: 
       - Used within an infrastructure service class that acts as the ACL adapter.
       - ``` 
         @Service
         public class TodoService {
             private final RestClient restClient;
             public TodoService(RestClient.Builder builder) {
                 this.restClient = builder.baseUrl("https://typicode.com").build();
             }
             public TodoDto getTodoById(Long id) {
                 return this.restClient.get()
                         .uri("/todos/{id}", id)
                         .retrieve()
                         .body(TodoDto.class); // Automatically deserializes JSON
             }
         }
         ```
3. **Enterprise Integration Gateways (stateful):**
- Apache Camel: An enterprise integration pattern (EIP) framework perfectly suited to act as a robust, stateful, or stateless ACL boundary.

---

### Open-Host Service (OHS) in Java

Usually, it's stateless.

An OHS is used by an upstream system to provide a stable, public interface (a "Published Language") to multiple consumers, protecting the service provider's internal domain from breaking changes. 

In Java, this is achieved using APIs and API gateways: 

1. **Web Services & API Frameworks (The Implementation):**
- Spring REST API
- Jakarta RESTful Web Services
- gRPC Java: Used for high-performance internal microservice communication
2. **API Gateways & Reverse Proxies (The Gatekeeper):**
  - To decouple consumers from underlying microservice changes, enforce versioning, or handle routing, Java-centric gateways are deployed as an OHS layer
  - Spring Cloud Gateway: A Java-native gateway for routing, rate-limiting, and protocol translation.
3. **Contract Definition Tools:** 
- OpenAPI / Swagger (Springdoc-openapi): 
  - Tools used to rigorously define and expose the public API contract, separating internal domain capabilities from what is explicitly permitted to external consumers.

### Summary Comparison

| DDD Concept                | Core Intent                                   | Java Ecosystem Representation                                                                              |
|----------------------------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Anticorruption Layer (ACL) | Protect your system from external chaos       | MapStruct (mapping), OpenFeign (adapters), Apache Camel (mediation), and dedicated infrastructure packages |
| Open-Host Service (OHS)    | Provide a stable public API to many consumers | Spring Boot Controllers (REST), gRPC (proto contracts), and Spring Cloud Gateway                           |

---

## Model Translation

A bounded context is the boundary of a model — a ubiquitous language. 

### Stateless Model Translation 

The bounded context that owns the translation (OHS for upstream, ACL for downstream) implements the **proxy design pattern** 
- to interject the incoming and outgoing requests 
- and map the source model to the bounded context's target model.

Implementation of the proxy depends on whether the bounded contexts are communicating synchronously or asynchronously.

#### Synchronous

Implemented by offloading the translation logic to an external component such as an API gateway pattern
- The primary and recommended framework for implementing the API Gateway pattern in the Spring ecosystem is `Spring Cloud Gateway` 
  - It provides a flexible, programmable, and reactive way to route client requests to backend microservices while centralizing cross-cutting concerns.
  - For bounded contexts implementing the open-host pattern, the API gateway is responsible for converting the internal model into the integration-optimized published language. 
  - Having an explicit API gateway can alleviate the process of managing and serving multiple versions of the bounded context's API.

Anticorruption layers implemented using an API gateway can be consumed by multiple downstream bounded contexts. 
- Such bounded contexts, which are mainly in charge of transforming models for more convenient consumption by other components, are often referred to as interchange contexts.
- RestClient, for example

#### Asynchronous

Message Proxy: an intermediary component subscribing to messages coming from the source-bounded context. 
- The proxy will apply the required model transformations and forward the resultant messages to the target subscriber
- In addition to translating the messages’ model, the intercepting component can also reduce the noise on the target bounded context by filtering out irrelevant messages.

```
[erp module] ──(publishes raw)── ➡️ [Spring Event Bus] ──(Asynchronous)── ➡️ [order module (ACL)] ──(Stateless Mapper)── ➡️ [Clean Core Domain]
```

---

### Stateful Model Translation

Stateless translation maps **one message at a time** — the mapper is a pure function, and two instances of it behave identically.

Some translations cannot work that way: the translator needs **memory** like DB of what it has already seen.

Two cases require it 👇
1. **Aggregating incoming data**: many fine-grained upstream messages grow into one coarse-grained fact that the downstream actually cares about.
2. **Unifying multiple sources**: data arriving from several upstreams is held, partially complete, until the picture is whole.

The consequence defines the pattern: **a stateful ACL owns a database**.
- It is no longer a pure function — it has a lifecycle, a schema, migrations, and a cleanup policy.
- Therefore, such a translator is often deployed as a **standalone service**: its own storage, its own scaling, its own failure domain.
- In a modular monolith, the compromise is: 
  - the state lives inside the **downstream** module, in its own package and its own tables 
  - invisible to the domain model and to every other module.

---

#### First, The Legacy Vocabulary

The example below borrows a domain everybody has met at least once. In plain words 👇

**CRM** — *Customer Relationship Management* 
- It is the system a company uses to track everyone it talks to:
  - Salesforce, HubSpot, Dynamics, Zoho. 
- It is the archetypal (典型的) legacy upstream: 
  - old, generic schema, opaque codes, and an event for every twitch.

A **lead** is a person who has shown a flicker of interest but is not a customer yet
- filled in a web form, scanned a badge at a trade show, downloaded a white-paper. 

The usual lifecycle 👇 
```
lead ➡️ contacted (repeatedly) ➡️ qualified ➡️ opportunity ➡️ customer
```

| Upstream term         | In plain English                                                 | Why it is legacy noise                      |
|-----------------------|------------------------------------------------------------------|---------------------------------------------|
| `leadId`              | the CRM's own identifier for that person                         | meaningful only inside `crm`                |
| `sourceCode`          | where the lead came from — `"WEB"`, `"TRADESHOW"`, `"PARTNER"`   | a code, not a concept                       |
| *touch*               | **one** contact attempt: a phone call, an email, a demo          | one message per touch ➡️ hundreds per lead  |
| `attribute` / `value` | a field on the record was edited — job title, company size       | a generic name/value bag, all `String`      |
| `stageCode`           | the pipeline stage: `S10` new, `S20` contacted, `S30` qualified  | **invented, and meaningless on purpose**    |

> `S10` / `S20` / `S30` are **NOT** a standard — there is nothing to look up.
> They stand in for what legacy systems really hand you: a column full of `S30`, `04` or `Z`, whose meaning
> lives in somebody's head, or in a wiki page from 2009 — never in the data itself.

The single most important word in the whole example is the one that **changes** at the boundary:

| `crm` says | `sales` says |
|------------|--------------|
| lead       | prospect     |

The same human being, two ubiquitous languages. 
- Neither context should have to learn the other's vocabulary
- and that is exactly the job the anticorruption layer is paid to do.

---

#### Simplifying the Integration Model

The upstream `crm` module is a legacy lead-tracking context. It is chatty, and it speaks its own language:
one message per touch, per attribute, per stage transition — with legacy stage codes.

The downstream `sales` module wants exactly **one** fact:

> *a qualified prospect appeared — and here is the whole picture of it.*

The gap is not vocabulary alone, it is **granularity**. 
- A stateless mapper cannot close it:
  - the terminal message (`stage ➡️ S30`) carries **no** email, **no** channel, **no** touch count. 
  - All of that arrived earlier, in other messages 
    - something has to remember.

```
[crm module]
   ├─ LeadRegistered ──────┐
   ├─ LeadTouched × N ─────┤  chatty, legacy codes,
   ├─ LeadAttributeChanged ┤  wrong granularity
   └─ LeadStageChanged ────┘
                 ⬇️  Spring event bus (async, after commit)
[sales module › integration.crm]  ⟵ the ACL
   growing ➡️ crm_lead_translation (the translation state)
                 ⬇️  once, and only on S30
[sales module › domain]  QualifiedProspectIdentified
```

---

#### How It Works, In Plain Words

Think of the ACL as a **secretary sitting between two offices**, with a filing cabinet of index cards.

The noisy office (next door) keeps shouting single facts through the door 
- *"someone new signed up"*, *"we phoned him"*, *"we emailed her"*, *"his job title changed"*. 
- None of those is worth interrupting the sales team for. 
- So the secretary does **not** pass them on. 
  - They pull out that person's card, write the one new fact on it, and put the card back in the cabinet.

That has gone on for days. 

Then one particular shout arrives — ***"this one is now qualified"*** — and then everything changes.
- The secretary takes out the card, 
- reads the **whole** story off it, 
- writes one clean note in the sales team's own words, 
- walks it through the door and stamps the card **DONE** so the same announcement can never be made twice.

```
Mon 09:12  "new lead L-1, ada@lovelace.io, from the web"  ➡️ card created
Mon 14:03  "L-1 was phoned"                               ➡️ touch 1, card updated
Tue 10:41  "L-1 job title changed"                        ➡️ nothing anybody needs
Thu 11:05  "L-1 got a demo"                               ➡️ touch 2, card updated
Sat 16:20  "L-1 moved to S30"                             ➡️ card is complete ➡️ ONE announcement
```

So, step by step 👇

1. The upstream **just talks**. 
   - It has never heard of the downstream, and it is not slowed down by it.
2. Spring delivers each message to the ACL **after** the upstream's transaction has committed — so the ACL never reacts to something that was rolled back.
3. The ACL finds that lead's card — or starts a new one — adds what the message told it, and saves it.
   - **That card is the state.** It is the entire difference from stateless translation.
4. Most messages stop right there. 
   - Nothing crosses the boundary. 
   - The noise has been absorbed.
5. When the message that completes the story arrives, 
   - the ACL reads the card, 
   - builds **one** coarse-grained event in the downstream's language, 
   - publishes it, 
   - and marks the card as emitted — all in one transaction,
   - so it is impossible to announce without stamping, or stamp without announcing.
6. The downstream domain receives one clean fact and never learns a single upstream word.

Two questions answer themselves from this picture 👇

**Why a database, and not a `Map` in memory?**
- Because those messages arrive minutes, days, sometimes weeks apart. 
- The cabinet has to survive a restart, a deployment, and a crash. 
- Anything that must outlive the JVM is not a cache 
  - It is a database.

**Why the DONE Stamp?**
Because the mail can be delivered twice. Retries, redeliveries, and restarts are normal, not exceptional.
Without the stamp, one qualified lead could be announced to the sales team over and over.

And the reason this cannot be done statelessly is one sentence long:

> The message that triggers the announcement — *"moved to S30"* — carries **none** of the information the announcement needs. 
> All of them arrived earlier, in other messages. 
> Somebody has to have been taking notes.

---

#### 1. The Upstream Publishes Its Own Language (OHS)

Exposed to other modules through a named interface — `package-info.java` of the events package:

``` 
@org.springframework.modulith.NamedInterface("events")
package guru.ddd.crm.events;
```

``` 
//: guru.ddd.crm.events.CrmLeadEvent.java

public sealed interface CrmLeadEvent {

    String leadId();

    record LeadRegistered(String leadId, String email, String sourceCode, Instant at)
            implements CrmLeadEvent {}

    record LeadTouched(String leadId, String channelCode, Instant at)
            implements CrmLeadEvent {}

    record LeadAttributeChanged(String leadId, String attribute, String value, Instant at)
            implements CrmLeadEvent {}

    record LeadStageChanged(String leadId, String stageCode, Instant at)
            implements CrmLeadEvent {}

} /// :~
```

Nothing in `sales` may import anything from `crm` outside `guru.ddd.crm.events` — `modules.verify()` enforces it.

---

#### 2. The Translation State

A Spring Data JDBC aggregate, owned by the ACL package. 

It is **not** a domain object: it has no business invariants to protect, only the bookkeeping the translation needs.

``` 
//: guru.ddd.sales.integration.crm.LeadTranslation.java

@Table("crm_lead_translation")
class LeadTranslation {

    @Id
    private final String leadId;      // natural key — the upstream's id, kept only here

    @Version
    private Long version;             // optimistic locking + `isNew` detection

    private String email;
    private String channelCode;
    private Instant registeredAt;
    private int touchCount;
    private Instant emittedAt;        // the emit-once guard

    // ... withers / accessors
} /// :~
```

Two subtleties hide in those few lines 👇
- `@Id` here is `org.springframework.data.annotation.Id`, **not** JPA's `jakarta.persistence.Id`
  — Spring Data JDBC ignores the JPA one, and nothing fails at compile time;
  - `@Version` is likewise `org.springframework.data.annotation.Version`;
    only `@Table` / `@Column` come from `org.springframework.data.relational.core.mapping`.
- With an **assigned** (non-generated) key, the id can never be `null`, so Spring Data JDBC would always
  choose `UPDATE` — and `updateWithoutVersion` discards the row count, so the row is silently never written.
  `@Version` moves the `isNew` decision onto the version property and fixes that.
  `Long` (new when `null`, first insert writes `0`) and `long` (new when `0`, first insert writes `1`) both
  work; prefer `Long`, so a `version = 0` written outside Spring Data is not mistaken for a new row.

```sql
CREATE TABLE IF NOT EXISTS crm_lead_translation (
    lead_id       VARCHAR(64) PRIMARY KEY,
    version       BIGINT      NOT NULL,
    email         VARCHAR(320),
    channel_code  VARCHAR(32),
    registered_at TIMESTAMPTZ,
    touch_count   INT         NOT NULL DEFAULT 0,
    emitted_at    TIMESTAMPTZ
);
```

---

#### 3. The ACL — Grow, Then Emit Once

```java
//: guru.ddd.sales.integration.crm.CrmLeadAcl.java

@Component
@RequiredArgsConstructor
class CrmLeadAcl {

    // the only place in the whole application where "S30" means anything
    private static final String QUALIFIED_STAGE_CODE = "S30";

    private final LeadTranslations translations;
    private final ApplicationEventPublisher publisher;

    @ApplicationModuleListener
    void on(final CrmLeadEvent.LeadRegistered event) {
        translations.save(stateOf(event.leadId()).registeredAs(
                event.email(), event.sourceCode(), event.at()));
    }

    @ApplicationModuleListener
    void on(final CrmLeadEvent.LeadTouched event) {
        translations.save(stateOf(event.leadId()).touched());
    }

    @ApplicationModuleListener
    void on(final CrmLeadEvent.LeadStageChanged event) {

        // noise reduction: every other stage transition dies right here
        if (!QUALIFIED_STAGE_CODE.equals(event.stageCode())) {
            return;
        }

        final LeadTranslation state = stateOf(event.leadId());

        state.toQualifiedProspect(event.at())   // Optional — empty if already emitted
                .ifPresent(publisher::publishEvent);

        translations.save(state);
    }

    private LeadTranslation stateOf(final String leadId) {
        return translations.findById(leadId)
                .orElseGet(() -> LeadTranslation.of(leadId));
    }

} /// :~
```

`@ApplicationModuleListener` is `org.springframework.modulith.events.ApplicationModuleListener`
- the one in `org.springframework.modulith` is deprecated. 
- It is meta-annotated with `@Async` + `@TransactionalEventListener` + `@Transactional(propagation = REQUIRES_NEW)`, which means:

| Property              | Consequence for the ACL                                                                   |
|-----------------------|-------------------------------------------------------------------------------------------|
| fires `AFTER_COMMIT`  | the ACL never sees an upstream change that was rolled back                                |
| runs `@Async`         | the upstream transaction is not slowed down by translation                                |
| own `REQUIRES_NEW` tx | the state write and the outgoing publication log entry commit **together**, or not at all |

That last row is what makes `emittedAt` a trustworthy emit-once guard: 
- a crash *between* "published" and "saved" is not possible — both happen in one transaction.

---

#### 4. The Downstream Domain Only Ever Sees the Coarse-Grained Event

```java
//: guru.ddd.sales.domain.model.QualifiedProspectIdentified.java

public record QualifiedProspectIdentified(
        ProspectId id,
        EmailAddress contact,
        AcquisitionChannel channel,
        int touchCount,
        Duration timeToQualify,
        Instant qualifiedAt) { }
```

No `sourceCode`. No `"S30"`. No `attribute`/`value` string pairs. The legacy vocabulary stopped at the ACL —
which is the entire point: **the integration model got simpler, the upstream did not have to change.**

---

#### 5. What You Must Design For

| Reality                                     | Why it happens                                                                | Handling in the example                                                       |
|---------------------------------------------|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| The same message is delivered twice         | the registry keeps an entry until the listener succeeds; retries re-invoke it | `emittedAt` guard ➕ idempotent accrual                                       |
| Messages arrive out of order                | async delivery, no global ordering                                            | never assume `LeadRegistered` came first — upsert                             |
| Two messages for one lead land concurrently | virtual threads ➕ async listeners                                            | `@Version` ➡️ `OptimisticLockingFailureException`, the entry stays incomplete |
| The state table grows forever               | one row per lead, ever                                                        | purge emitted rows on a schedule — the ACL owns this                          |
| A listener fails permanently                | bad data, a bug                                                               | the publication entry is **kept, not auto-retried**                           |

Re-delivery is **opt-in**, not automatic:

```yaml
spring:
  modulith:
    events:
      completion-mode: update                          # update (default) | delete | archive
      republish-outstanding-events-on-restart: false   # careful in multi-instance deployments
      jdbc:
        schema-initialization:
          enabled: true
```

---

#### 6. Wiring It Into `ddd-lab`

1. `pom.xml` ➡️ add `org.springframework.modulith:spring-modulith-starter-jdbc`
   (only `starter-core` is there today, and the JDBC starter is **not** in the pinned local repo yet — that first build must be online).
2. Create `guru.ddd.crm.config.CrmCfg` and `guru.ddd.sales.config.SalesCfg` — package-private, like `HelpdeskCfg`.
3. Add both config packages to `DomainDrivenDesignCfg`'s `@ComponentScan`.
4. Update `ModuleStructureTest`: `hasSize(3)` / `containsExactlyInAnyOrder("helpdesk", "crm", "sales")`.

Skip any one of the last three and the module is invisible, or the build is red.

---

#### 7. Testing It

There is no `src/test/resources` yet, so the embedded datasource is declared on the test itself:

```java
//: guru.ddd.sales.integration.crm.CrmLeadAclTest.java

@DisplayName("CRM Lead ACL - ")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@AutoConfigureEmbeddedDatabase(type = DatabaseType.POSTGRES)
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
class CrmLeadAclTest {

    @Test
    void Chatty_Crm_Messages_Become_One_Qualified_Prospect(Scenario scenario) {

        // Given
        scenario.publish(new LeadRegistered("L-1", "ada@lovelace.io", "WEB", NOW))
                .andWaitForStateChange(() -> translations.findById("L-1"))
                .andVerify(state -> assertThat(state).isPresent());

        // When & Then
        scenario.publish(new LeadStageChanged("L-1", "S30", NOW.plusSeconds(60)))
                .andWaitForEventOfType(QualifiedProspectIdentified.class)
                .matching(e -> e.id().equals(ProspectId.of("L-1")))
                .toArriveAndVerify(e -> assertThat(e.channel())
                        .isEqualTo(AcquisitionChannel.WEB));
    }

} /// :~
```

- `Scenario` is injected as a test-method parameter; outside `@ApplicationModuleTest` it needs `@EnableScenarios`.
- `BootstrapMode.DIRECT_DEPENDENCIES` boots `sales` plus what it depends on — `STANDALONE`, the default, would not start `crm`.
- For plain assertions on what was published, inject `AssertablePublishedEvents` instead of driving a `Scenario`.

The edge case that deserves its own test: publish `LeadStageChanged("S30")` **twice**, assert the downstream event is emitted **once**.

---

#### When Not To Hand-Roll It

| Situation                                               | Better tool                                               |
|---------------------------------------------------------|-----------------------------------------------------------|
| In-process, modest volume, correlation by a natural key | exactly what is above — Modulith ➕ Spring Data JDBC      |
| High-volume streams, windowing, time-based aggregation  | Kafka Streams / Flink — they *are* stateful translators   |
| Rich routing, many heterogeneous upstreams              | Apache Camel `aggregate` EIP with a persistent repository |
| The translation needs no memory at all                  | do **not** pay for a database — stay stateless            |

> Rule of thumb: reach for stateful translation only when the downstream genuinely cannot be served by a
> single incoming message. State is the expensive part of an ACL, and it is permanent.

---

##### Version Notes

Verified against the Spring Modulith 2.1.x reference documentation and the Spring Data Relational documentation
(Boot 4.1.1 / Modulith 2.1.0 / Java 25).
The published Modulith compatibility matrix still shows `2.1 ➡️ Boot 3.5` and is **stale** — Modulith 2.1.0's own
POM pins Boot 4.1.0, so this repo's pairing is correct.
