# Aggregate and Bounded Context

A **Bounded Context** (BC) sets the edges of a model and its language.
An **Aggregate** is a consistency boundary *inside* that model.
Each aggregate belongs to exactly one BC; a BC usually holds several aggregates.

---

## 1. Two Levels of Design

|                    | Bounded Context                                             | Aggregate                                                   |
|--------------------|-------------------------------------------------------------|-------------------------------------------------------------|
| Design level       | Strategic (big picture)                                     | Tactical (code level)                                       |
| What it bounds     | The meaning of a model and its terms                        | Which objects must stay consistent together                 |
| Size               | Large: one team or subsystem                                | Small: one root plus a few entities and value objects (VOs) |
| Consistency inside | The same terms mean the same thing everywhere in it         | Rules are always true, enforced in one transaction          |
| Consistency across | Other BCs updated later (eventual), via integration events or APIs | Other aggregates updated later, via domain events    |
| Reference across   | Contract plus translation (Anti-Corruption Layer, ACL)      | Other aggregate's ID only                                   |

Nesting:

```
System
 └─ Bounded Context (language + model)
     └─ Module
         └─ Aggregate (consistency/transaction boundary)
             └─ Root Entity → child Entities, Value Objects
```

---

## 2. How They Relate

1. **Containment** — an aggregate never spans two BCs. If it seems to, either the aggregate or the context split is wrong.
2. **Same real-world thing, different aggregates** — "Customer" in a Helpdesk BC is a `Requester` (contact, SLA tier);
   in a Billing BC it is an `Account` (payment terms, balance). Separate classes and tables, sharing only an ID.
   Do not share one class between them.
3. **The BC gives aggregates their meaning** — the rules an aggregate protects
   (e.g. "a closed ticket cannot get comments") only make sense in the BC's language.
4. **Communication gets looser at each level**:
   - Inside an aggregate: method calls, one transaction.
   - Between aggregates in one BC: reference by ID, domain events, eventual consistency, usually in-process.
   - Between BCs: integration events or a published API, translated at the edge, usually async.

---

## 3. Aggregate Design Rules (Vernon)

- Keep inside the boundary only rules that must *always* be true together.
- Keep aggregates small.
- Reference other aggregates by ID, not by object.
- Change one aggregate per transaction; everything else is updated later.

Example in a Helpdesk BC:

```java
class Ticket {                  // aggregate root
  TicketId id;
  AgentId assignee;             // ID ref to the Agent aggregate, not an Agent object
  List<Comment> comments;       // child entity, reached only via Ticket
  void addComment(...) { if (closed) throw ...; }   // rule enforced here
}
```

---

## 4. Bounded Context Boundaries

A BC boundary is the line where **a model and its words stop being valid**.
Past the line, the same word can mean something else, and translation is needed.
A real boundary usually lines up on several of these:

| Kind of boundary      | Signal                                                                                  |
|-----------------------|-----------------------------------------------------------------------------------------|
| Language              | A term changes meaning ("Order" in Sales vs. in Shipping)                               |
| Model                 | Different attributes, rules or lifecycle for the "same" thing                           |
| Team                  | One team owns it (Conway's law)                                                         |
| Data                  | It owns its own tables or schema; others don't write to them                            |
| Change rate           | It changes at its own pace for its own business reasons                                 |
| Deployment (optional) | Its own service. A BC does **not** have to be a microservice; a modulith module works   |

### Finding Boundaries

- Listen for the same word used with different meanings.
- Run Event Storming; look for key events where the business hands off work (e.g. `OrderPlaced` → fulfillment).
- Align with business capabilities or subdomains.
  A subdomain is part of the problem space; a BC is part of the solution space. Ideally 1:1, but not always.
- Look for clusters of rules that never reference each other.

### Context Mapping

How two BCs relate across a boundary:
- Partnership
- Shared Kernel
- Customer–Supplier
- Conformist
- Anti-Corruption Layer
- Open Host Service with a Published Language
- Separate Ways

---

## 5. BC Boundary vs. Aggregate Boundary

They protect different things:
- **BC boundary** — keeps a model and its words meaning one thing. It's about **meaning**.
- **Aggregate boundary** — keeps business rules true at every moment. It's about **consistency**.

|                           | BC boundary                                                              | Aggregate boundary                                                                    |
|---------------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| Question it answers       | "Where does this word or model stop meaning X?"                          | "Which data must change together, all or nothing?"                                    |
| Protects                  | The language and model staying consistent                                | Rules that must always hold (e.g. a closed ticket can't get comments)                 |
| Size                      | Coarse: a subsystem or team                                              | Fine: one root plus a few objects                                                     |
| Drawn by                  | Language, team ownership, business capability                            | The business rules                                                                    |
| Crossing it means         | Translating between models (ACL, published language)                     | Referring to the other aggregate by ID only, never holding it as an object            |
| Consistency across it     | Eventual, through integration events or APIs                             | Eventual, through domain events                                                       |
| Consistency inside it     | Same terms and rules everywhere, but many transactions                   | One transaction changes the whole aggregate at once                                   |
| What breaks if it's wrong | Tangled models, words with mixed meanings, teams stepping on each other  | Broken rules, or lock contention and slowness if it's too big                         |
| Enforced in this repo by  | Modulith modules, `@NamedInterface`, `modules.verify()`                  | Aggregate root's methods, one repository per root, one aggregate per transaction      |

Examples:
- **Crossing a BC boundary** — "Customer" in Helpdesk and in Billing are different models; translate between them.
- **Crossing an aggregate boundary** — `Ticket` and `Agent` share the Helpdesk language but are separate units,
  so `Ticket` holds only an `AgentId`.

The BC boundary decides **what things mean**; the aggregate boundary decides **what must change together**.
Every aggregate boundary sits inside one BC boundary, never across one.

---

## 6. Common Mistakes

- One aggregate per table — aggregates follow business rules, not the database schema.
- A huge aggregate holding everything → lock contention.
- Sharing entity classes or tables across BCs.
- Splitting BCs by technical layer (UI, service, DB) instead of by meaning.
- Treating BC as just another word for microservice.

---

## 7. Mapping to This Repo

- **Each Spring Modulith module is a BC** — `guru.ddd.helpdesk` is one; `modules.verify()` enforces the boundaries.
- **`@NamedInterface` packages are the published interface** (Open Host Service / Published Language);
  everything else stays internal.
- **Aggregates live in `guru.ddd.<module>.domain.model`**, one Spring Data JDBC repository per aggregate root.
  Spring Data JDBC treats each repository's root as a whole aggregate: loads and saves it entirely, no lazy loading.
  Cross-aggregate links can use `AggregateReference<T, ID>`.
- **Cross-module events** go through Modulith's event publication registry → eventual consistency between BCs.

---

## 8. FAQ

### Does a Bounded Context Contain Only One Aggregate?

No — a BC usually holds **several** aggregates. The two are sized by different things:
- A BC is sized by **language**: how far one consistent model and vocabulary reaches.
- An aggregate is sized by **business rules**: the smallest group of data that must change together.

One language normally covers many separate rule clusters.
E.g. a Helpdesk BC has `Ticket`, `Agent`, `SlaPolicy`, `Queue` — one vocabulary, each with its own rules.

| Count          | When                                                                                                  |
|----------------|-------------------------------------------------------------------------------------------------------|
| Many (typical) | A rich domain, e.g. Helpdesk with `Ticket`, `Agent`, `SlaPolicy`                                      |
| One            | Possible when the model is genuinely tiny, e.g. a Pricing BC with just `PriceList` — coincidence, not a rule |
| Zero           | Supporting or generic BCs built as simple CRUD, or read-only reporting contexts                        |

Where the idea comes from:
- "One microservice per aggregate" advice — that splits by *deployment*, not by model.
- Small tutorial examples showing only one aggregate.
- Mixing up the two boundaries (see [§5](#5-bc-boundary-vs-aggregate-boundary)).

Signs BCs are split too finely (one aggregate each):
- Two "BCs" use exactly the same words with the same meaning.
- Common use cases need both, so they constantly sync or call each other.
- Translation code (an ACL) exists between models that are actually identical.

Fix: merge them into one BC and keep the aggregates separate inside it.

---

## Sources

These are design heuristics, not a fixed spec.

- Eric Evans, *Domain-Driven Design* (2003)
- Vaughn Vernon, *Implementing Domain-Driven Design* (2013), and his "Effective Aggregate Design" essays
