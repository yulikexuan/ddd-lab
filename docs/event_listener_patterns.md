# Event Listener Patterns

- `@EventListener` 
- `@Async` 
- `@TransactionalEventListener` 
- `@ApplicationModuleListener`

Companion to [communication_patterns.md](./communication_patterns.md) 
- the ACL there uses `@ApplicationModuleListener`; 
- this document explains why, and what the alternatives actually cost.

---

> The Domain-Driven Design Consistency Rule: 
> - **One transaction per aggregate/module, eventual consistency across boundaries.**

## 0. The One-Screen Answer

These are **not four competing annotations**. 

There are **three primitives** and **one bundle**:

```
@EventListener                 the base — "call me when this type is published"
  ➕ @Async                    an orthogonal AOP modifier — "on another thread"
  ➕ transaction phase         @TransactionalEventListener — "not now; at commit"
  ➕ @Transactional            "and give me my own unit of work"
       ⬇️
@ApplicationModuleListener     all three at once, ➕ the Modulith publication registry
```

| You want                                                 | Use                                                             |
|----------------------------------------------------------|-----------------------------------------------------------------|
| In-process decoupling, same unit of work, all-or-nothing | `@EventListener`                                                |
| Fire-and-forget with **no** database involved anywhere   | `@Async` ➕ `@EventListener`                                    |
| React only to committed facts, no new writes             | `@TransactionalEventListener`                                   |
| React to committed facts **and write**                   | `@TransactionalEventListener` ➕ `@Transactional(REQUIRES_NEW)` |
| Cross-module integration in a Modulith, durable          | `@ApplicationModuleListener`                                    |

> The single most common production bug in this area: 
> - **`@Async` + `@EventListener` on a listener that reads the row the publisher just wrote.**  
>   - It is a race, and it loses on a fast machine. > See §3.2.

---

## 1. The Four Axes that Actually Differ

Every question about "which one" reduces to four independent properties:

| Axis            | Question                                                    |
|-----------------|-------------------------------------------------------------|
| **Thread**      | publisher's thread, or an executor's?                       |
| **Timing**      | at `publishEvent(…)`, or at a transaction phase?            |
| **Transaction** | joins the publisher's tx, gets its own, or has none?        |
| **Failure**     | does the publisher find out, or is the exception swallowed? |

Here is the whole matrix, then the details:

|                                   | `@EventListener`       | `@Async @EventListener` | `@TransactionalEventListener` | `@TxEL` ➕ `@Tx(REQUIRES_NEW)` | `@ApplicationModuleListener`                                                                        |
|-----------------------------------|------------------------|-------------------------|-------------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------|
| Thread                            | publisher's            | executor                | publisher's                   | publisher's                    | executor                                                                                            |
| Timing                            | at `publishEvent`      | at `publishEvent`       | `AFTER_COMMIT` (default)      | `AFTER_COMMIT`                 | `AFTER_COMMIT`                                                                                      |
| Sees uncommitted publisher state  | yes                    | **race**                | no                            | no                             | no                                                                                                  |
| Runs if publisher rolls back      | yes                    | **yes**                 | no                            | no                             | no                                                                                                  |
| Runs if **no** transaction active | yes                    | yes                     | **no — silently dropped**     | no                             | no                                                                                                  |
| Writes get committed              | with publisher         | own tx or none          | **no — silently lost**        | yes                            | yes                                                                                                 |
| Exception reaches publisher       | **yes, rolls it back** | no                      | no (logged)                   | no (logged)                    | no (logged)                                                                                         |
| Survives a crash / restart        | no                     | no                      | no                            | no                             | **yes, with the registry**                                                                          |
| Blocks the publisher              | yes                    | no                      | yes (post-commit)             | yes (post-commit)              | no — until the throttle saturates ([§3.4](#34-which-executor--and-a-warning-specific-to-this-repo)) |

---

## 2. The Base — `@EventListener`

`org.springframework.context.event.EventListener`, since 4.2.

``` 
@Component
@RequiredArgsConstructor
class LeadAuditTrail {

    @EventListener
    void on(final CrmLeadEvent.LeadRegistered event) {
        // runs on the publisher's thread, inside the publisher's transaction
    }
}
```

Mechanics worth knowing:

- **Synchronous and in-thread.** 
  - `ApplicationEventPublisher.publishEvent(…)` does not return until every listener has run. 
  - It is a *method call with indirection*, not a queue.
- **Transaction:** 
  - whatever the publisher had. No transaction of its own. 
  - `@Transactional` on the method means `REQUIRED` ➡️ it joins.
- **Failure is loud** — and that is the feature. 
  - A thrown exception propagates back through `publishEvent` into the publisher 
    - A `RuntimeException` rolls the whole thing back. 
    - *Checked* exceptions are wrapped in `UndeclaredThrowableException` 
      - because the publisher can only handle unchecked ones.
- **Return values re-publish.** 
  - A non-`void` return value is published as a new event; an array or `Collection` publishes each element individually. 
    - `null` ➡️ nothing published. `Optional`, `Map` and records are **not** unwrapped — `Optional.empty()`
      is published *as* an event. Publish explicitly (`ifPresent(publisher::publishEvent)`) instead.
    - A `CompletionStage` is published when it completes; a `Publisher` is subscribed to and each `onNext` published.
    - The nested `publishEvent` is synchronous on the same thread ➡️ a cycle is a `StackOverflowError`,
      and a downstream failure rolls the publisher back.
  - **Silently dead under `@Async`** — the proxy returns `null` before the body runs, so there is nothing
    to publish. A `CompletableFuture` return type is the one that survives the thread hop.
  - Under `@TransactionalEventListener` the follow-up is published *outside* any transaction ➡️ downstream
    transactional listeners are skipped (§4.2).
  - Tidy for small chains, but invisible in the source — the link is a return type, not a call site. Use sparingly.
- Useful attributes: `condition()` (SpEL, with `#root.event`, `#root.args`, `#a0`/`#p0`, or parameter names), `id()`, and `@Order` alongside for deterministic ordering between listeners of the same event.
- Since 6.2 there is a `defaultExecution()` attribute, defaulting to `true`. You never set it directly —
  it exists so composed annotations can override it, and `@TransactionalEventListener#fallbackExecution`
  is its alias (§4).

**Use it when** the listener is genuinely part of the same unit of work: 
- an in-aggregate invariant
- a derived writing that must live or die with the original
- a domain event consumed inside one module.

**Do NOT use it** across a module boundary in a Modulith. 
- A failing listener there rolls back a transaction that belongs to somebody else's module — the exact coupling events were supposed to remove.

### 2.1 Why not across a module boundary

A plain `@EventListener` turns *"module B reacts to a fact"* into *"module B can veto module A's write"* —
and nothing in the toolchain tells you it happened. Four reasons, worst first.

#### 1. The failure path **is** the coupling

`publishEvent(…)` is a method call with indirection, and `SimpleApplicationEventMulticaster` has no
`errorHandler` by default — so a listener exception unwinds straight back into the publisher:

```
[crm tx] save(lead) ── publishEvent ──➡️ sales listener throws
                          ⬅️───────────── exception propagates
     tx marked rollback-only ➡️ crm's lead is gone
```

`crm` did nothing wrong. A bug in `sales` — or in the third module that subscribed last month — deleted
`crm`'s write.

The nastier variant is the one that survives review: the listener carries `@Transactional`, which is
`REQUIRED`, so it **joins**. It catches its own exception and returns normally. The transaction is still
marked rollback-only, and `crm` fails at `commit()` with `UnexpectedRollbackException` — a stack trace
pointing at `crm`, with no mention of the module that actually caused it.

#### 2. It is worse than a direct method call, not better

A direct `salesService.onLeadRegistered(…)` carries the *same* transactional coupling, but it is visible at
the call site — greppable, and `modules.verify()` rejects the illegal package dependency.

|                                              | direct call | `@EventListener` across the boundary                                     |
|----------------------------------------------|-------------|--------------------------------------------------------------------------|
| Shares the publisher's transaction           | yes         | yes                                                                      |
| Visible in the publisher's source            | yes         | **no**                                                                   |
| Rejected by `modules.verify()`               | yes         | **no** — consuming an exposed `@NamedInterface` event type is legal      |
| A new subscriber changes crm's failure modes | n/a         | **yes** — from a one-file change in somebody else's module               |

You pay the full price of coupling and lose the one thing that made it manageable: traceability.

#### 3. One transaction across two modules breaks the extraction contract

Shared transaction ➡️ shared connection, shared lock lifetime, shared timeout, shared isolation level.
`crm` holds its row locks for as long as every downstream listener takes, and `crm`'s p99 becomes the sum of
all subscribers.

Structurally: a module boundary exists so the module can later become a process. A transaction is the one
thing that cannot cross a network. Build on `@ApplicationModuleListener` and the semantics are already the
ones a broker gives you — commit first, react independently, at-least-once, idempotent — so extraction is a
deployment change. Build on `@EventListener` and extraction is a redesign.

It is also the plain DDD rule: one transaction per aggregate, eventual consistency across boundaries.
`@EventListener` quietly makes it *immediate* consistency, demoting the event from *a fact that happened* to
*a command with extra steps*.

#### 4. No registry entry

Modulith logs a publication only for listeners (meta-)annotated with `@TransactionalEventListener` (§5.1).
A plain `@EventListener` — with or without `@Async` — gets none: no durability, no resubmission, no `FAILED`
row. Across a boundary that is exactly the observability you need, and it is not there.

#### The legitimate exception

If two modules genuinely must succeed or fail together, that is a real requirement — but then say so **explicitly**: 
- call an exposed `@NamedInterface` service, or use `@TransactionalEventListener(BEFORE_COMMIT)` where the veto *is* the documented contract. 
- A veto is a `request / response`, not a notification.

What this rule forbids is acquiring atomic cross-module coupling *by accident*, from an annotation that reads like decoupling.

---

## 3. The Modifier — `@Async`

`org.springframework.scheduling.annotation.Async`.
- It is **NOT** an event annotation at all;
- It is generic AOP that says: "invoke this through an `Executor`".
- Stacking it on `@EventListener` is the idiom you have been using.

### 3.1 How it plugs in

`EventListenerMethodProcessor` builds a listener adapter that looks the bean up **by name from the context** and invokes the method on it 
- i.e., on the **proxy**. 
- That is why the `@Async` advice fires at all.

`AsyncAnnotationBeanPostProcessor` calls `setBeforeExistingAdvisors(true)` in its constructor:

> *"The underlying async advisor applies before existing advisors by default, to switch to async execution as early as possible in the invocation chain."*

This detail is load-bearing and is why §5 works: 
- when `@Async` and `@Transactional` sit on the same method, **the thread hop happens first and the transaction is opened on the new thread** — never on the caller's.

Requires `@EnableAsync` (this repo has it on `DomainDrivenDesignApplication`). 
Proxy rules apply: no self-invocation, no `private`/`final` methods, no `@Async` on `@Configuration` class methods. 
Package-private methods on a CGLIB proxy are fine — the proxy is generated in the same package.

### 3.2 The trap

```java
@Async
@EventListener
void on(final CrmLeadEvent.LeadRegistered event) {
    leads.findById(event.leadId());   // ⚠️ may not be there yet. Or ever.
}
```

`publishEvent` is still called **inside** the publisher's transaction. 

The async advice submits the task and returns immediately, so the listener starts running while the publisher's transaction is **still open**:

```
publisher thread  ──[tx begin]── save(lead) ── publishEvent ─────────── commit ──➡️
                                                    │
executor thread                                     └──➡️ findById(…) ➡️ empty. Or worse: the tx later rolls back.
```

Two failure modes, both intermittent:
1. **Read-your-write fails** — the row is not visible to another connection until commit.
2. **Phantom (like a ghost) reaction** — the publisher rolls back, but the listener already sent the email.

`@Async` + `@EventListener` is correct **only** when the listener touches no data written by the publisher:
- metrics, logging, cache eviction of unrelated keys, a webhook that carries the whole payload in the event.
- The moment a repository appears in the body, you need §4.

### 3.3 Failure handling

A `void` `@Async` method's exception cannot reach the caller. 
- It goes to the `AsyncUncaughtExceptionHandler` 
  - by default `SimpleAsyncUncaughtExceptionHandler`, which **logs and forgets**. 
  - Register your own bean via `AsyncConfigurer#getAsyncUncaughtExceptionHandler` if you want more.
- Async listeners also cannot publish a follow-up event by returning a value 
  - the proxy returns `null` before the body runs, so the adapter has nothing to publish (a `CompletableFuture` return type is the one exception, published on completion). 
  - Inject `ApplicationEventPublisher` and publish explicitly.

### 3.4 Which executor — and a warning specific to this repo

`application.yml` here sets `spring.threads.virtual.enabled: true`. 
- In Boot 4.1, `TaskExecutorConfigurations` then registers `applicationTaskExecutor` as a `SimpleAsyncTaskExecutor` with `virtualThreads(true)` instead of a `ThreadPoolTaskExecutor`.

`SimpleAsyncTaskExecutor` has **no queue and no pool**: 
- every submitted task starts a new virtual thread immediately. 
- Cheap, but **unbounded** 
  - A burst of events becomes a burst of database connections 
  - and the connection pool becomes the real (and much less forgiving) limit.

```yaml
spring:
  task:
    execution:
      simple:
        concurrency-limit: 8                   # unbounded when unset
        reject-tasks-when-limit-reached: false # false ➡️ the submitter blocks instead of failing
      shutdown:
        await-termination: true                # default false ➡️ in-flight listeners abandoned at context close
        await-termination-period: 30s
  datasource:
    hikari:
      maximum-pool-size: 20                    # was an implicit 10
```

Size `concurrency-limit` against the **datasource pool**, not against CPU count.
- A permit is held for the task's whole duration, so permits and connections are the same currency.
- HikariCP's default is **10** — a limit above that is not a bound, it is the unbounded behaviour with extra steps.

Two things the block does not say out loud:

- **Which thread blocks.**
  - The submit happens inside the `AFTER_COMMIT` synchronization, on the *publisher's* thread.
  - A saturated throttle therefore pushes back into the publisher's commit path — which is why §1's
    `Blocks the publisher` cell holds only while the throttle has headroom.
- **`reject-tasks-when-limit-reached: true` is unsafe in this repo today.**
  - The `TaskRejectedException` is thrown into `afterCompletion`, which swallows it (§4.1).
  - With the registry, the publication row is simply never completed and stays resubmittable; without it (§5.3), the event is gone.

➡️ Full treatment — what else the flag flips, what silently goes dead, the sizing formula, shutdown,
and the `executor.*` metrics it removes — in
[virtual_thread_task_execution.md](./virtual_thread_task_execution.md).

---

## 4. The Phase — `@TransactionalEventListener`

`org.springframework.transaction.event.TransactionalEventListener`, since 4.2. 

It **is** an `@EventListener` (meta-annotated), with one extra idea: 
- *defer the invocation to a transaction phase*.

```java
@TransactionalEventListener // phase = AFTER_COMMIT // The default phase is TransactionPhase.AFTER_COMMIT 
void on(final CrmLeadEvent.LeadStageChanged event) { … }
```

### 4.1 The Phases

| Phase                      | Runs                              | Exception                      | Use for                                                              |
|----------------------------|-----------------------------------|--------------------------------|----------------------------------------------------------------------|
| `BEFORE_COMMIT`            | inside the tx, just before commit | **rolls the transaction back** | last-chance validation, writes that must be atomic with the original |
| `AFTER_COMMIT` *(default)* | after a successful commit         | logged, swallowed              | reacting to facts that are now true                                  |
| `AFTER_ROLLBACK`           | after a rollback                  | logged, swallowed              | compensation, alerting                                               |
| `AFTER_COMPLETION`         | after either outcome              | logged, swallowed              | cleanup                                                              |

**Why "logged, swallowed":** 
- `AFTER_COMMIT` is a *specialization of* `AFTER_COMPLETION` and runs in `TransactionSynchronization.afterCompletion(int)`, **not** `afterCommit()`. 
- And `TransactionSynchronizationUtils.invokeAfterCompletion` is literally:

``` 
try {
    synchronization.afterCompletion(completionStatus);
} catch (Throwable ex) {
    logger.error("TransactionSynchronization.afterCompletion threw exception", ex);
}
```

**Your listener can fail completely, and the caller will never know.** Plan for it (§6).

### 4.2 Trap #1 — no transaction, no delivery

```java
// Whether the event should be handled if no transaction is running.
boolean fallbackExecution(); // default false;   // @AliasFor EventListener#defaultExecution
```

If the event is published outside an active transaction, the listener is **not invoked and nothing is logged at WARN**. 
- because `fallbackExecution` is `false` by default.
- This bites in exactly two places: 
  - a `@Test` that publishes directly 
  - a scheduled job or `@PostConstruct` path that forgot `@Transactional`. 
- Set `fallbackExecution = true` to run anyway when no transaction is in progress.

### 4.3 Trap #2 — writes in `AFTER_COMMIT` are silently lost

Straight from the annotation's own javadoc:

> *"the transaction will have been committed or rolled back already, but the transactional resources might still be active and accessible. 
> As a consequence, any data access code triggered at this point will still 'participate' in the original transaction, 
> but changes will not be committed to the transactional resource."*

So this is **broken**, and fails silently — no exception, no row:

```java
@Transactional                     // REQUIRED ➡️ joins the already-completed transaction
@TransactionalEventListener        // AFTER_COMMIT (default)
void on(final CrmLeadEvent.LeadTouched event) {
    translations.save(state);      // ⚠️ never committed
}
```

The fix is `REQUIRES_NEW` — a genuinely new unit of work:

`Propagation.REQUIRED`'s "create a new one if none exists" never fires at `TransactionPhase.AFTER_COMMIT`, because Spring still believes one exists.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener  // AFTER_COMMIT (default)
void on(final CrmLeadEvent.LeadTouched event) {
    translations.save(state);      // ✅ committed
}
```

That pairing — `AFTER_COMMIT` ➕ `REQUIRES_NEW` — is so nearly always what you want that Modulith gave it a name. Which is §5.

### 4.4 Without `@Async`, you are still on the publisher's thread

A non-async transactional listener runs in the commit path of the publisher's transaction. 

The publisher's `@Transactional` method does not return until your listener finishes. 

Correctness is fine; **latency is inherited**. 

Add `@Async` to break that, and the thread hop then happens *after* commit — which is the best of both.

---

## 5. The Bundle — `@ApplicationModuleListener`

`org.springframework.modulith.events.ApplicationModuleListener`, from **`spring-modulith-events-api`**.

### Mind the package — there were **two** annotations with this simple name

| FQN                                                             | Artifact                         | Status                                                                                      |
|-----------------------------------------------------------------|----------------------------------|---------------------------------------------------------------------------------------------|
| `org.springframework.modulith.ApplicationModuleListener`        | `spring-modulith-api`            | present in 1.0.0 ➡️ `@Deprecated(since = "1.1", forRemoval = true)` ➡️ **deleted in 2.0.0** |
| `org.springframework.modulith.events.ApplicationModuleListener` | `spring-modulith-**events**-api` | the current one — the only one that still exists                                            |

The deprecation Javadoc in 1.x said it plainly: 
- *"Prefer `org.springframework.modulith.events.ApplicationModuleListener` in `spring-modulith-events-api`."* 
- On 2.x there is nothing to migrate off — the old import simply will not compile.

This is the current annotation's actual declaration in 2.1.0:

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener
@Documented
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplicationModuleListener {

    @AliasFor(annotation = Transactional.class, attribute = "readOnly")
    boolean readOnlyTransaction() default false;

    @AliasFor(annotation = EventListener.class, attribute = "id")
    String id() default "";                       // since 1.1

    @AliasFor(annotation = EventListener.class, attribute = "condition")
    String condition() default "";                // since 1.2

    @AliasFor(annotation = Transactional.class, attribute = "propagation")
    Propagation propagation() default Propagation.REQUIRES_NEW;   // since 1.3
}
```

Nothing magic: it is §3 ➕ §4.3, pre-assembled. 

Note there is **no `phase()` attribute** — you get `AFTER_COMMIT` 
- And **if you need `BEFORE_COMMIT` you are writing the three annotations by hand.**

Combined with the async-advisor-first ordering from §3.1, the execution order is:

```
publisher tx commits
   ➡️ listener adapter invokes the proxy   (publisher's thread — returns immediately)
      ➡️ @Async advice submits to the executor
         ➡️ [executor thread]  @Transactional REQUIRES_NEW ➡️ begin
            ➡️ your method body
         ➡️ commit / rollback — independently of the publisher
```

### 5.1 What you get that hand-rolling does not give you: the registry

This is the real reason to prefer it, and it has a hard prerequisite.

With a persistence starter on the classpath (`spring-modulith-starter-jdbc`, `-jpa`, `-mongodb`, `-neo4j`), 
- `EventPublicationConfiguration` contributes three infrastructure beans, and it is worth knowing which does what:

| Bean                                    | Job                                                        |
|-----------------------------------------|------------------------------------------------------------|
| `DefaultEventPublicationRegistry`       | the state machine, over an `EventPublicationRepository`    |
| `PersistentApplicationEventMulticaster` | replaces the multicaster — **writes** one row per listener |
| `CompletionRegisteringAdvisor`          | an AOP advisor — **updates** that row around your method   |

The rows are written **at `publishEvent(...)` time, not at `commit`**, and before any listener is even registered as a transaction synchronization:

``` 
// PersistentApplicationEventMulticaster.multicastEvent (abridged)
var matchingListeners = candidates.stream()
        .filter(it -> matches(event, eventToPersist, it))                // condition() SpEL, evaluated eagerly
        .toList();

cache.computeIfAbsent(new CacheKey(type, getSourceType(event)),
        __ -> new TransactionalEventListeners(candidates, environment)) // AFTER_COMMIT transactional only
    .filter(matchingListeners::contains)
    .ifPresent(stream -> storePublications(stream, eventToPersist));   // ⬅️ the INSERTs

for (ApplicationListener listener : matchingListeners) {
    listener.onApplicationEvent(event);                                // ⬅️ then register the synchronization
}
```

`DefaultEventPublicationRegistry#store` carries no `@Transactional`, so it joins the publisher's unit of
work — which is the whole point: the row and the business write commit together. The cost is that
`publishEvent` is now N extra `INSERT`s on the publisher's connection. `condition()` SpEL is evaluated
eagerly beforehand, so a filtered-out listener leaves no orphan row.

> **The selection rule is the whole point of this document.** 
> The filter is not "is annotated with `@TransactionalEventListener`" — it is:
>
> ```java
> .filter(TransactionalApplicationListener.class::isInstance)
> .filter(it -> it.getTransactionPhase().equals(TransactionPhase.AFTER_COMMIT))
> .filter(byAnnotationFilter(environment))
> ```
>
> So a plain `@EventListener` — with or without `@Async` — gets **no registry entry and no durability**, and neither does `@TransactionalEventListener(BEFORE_COMMIT)`. 
> Only the `AFTER_COMMIT` phase is logged.
>
> `spring.modulith.events.registry-trigger-annotation` (2.1+) **only narrows**. 
> Left unset, the predicate returns `true` and every `AFTER_COMMIT` transactional listener is logged; 
> set it to restrict logging to, say, `@ApplicationModuleListener` alone. 
> It cannot widen — a plain `@EventListener` never reaches that filter, so no property makes it durable.

So the crash window closes:

```
without the registry:   commit ✅ ── [JVM dies] ── listener never ran, and nobody knows
with the registry:      commit ✅ (publication row written in the same tx) ── [JVM dies] ── row is still PUBLISHED, and resubmittable 
```

Publication states (2.0+): 
- `PUBLISHED` ➡️ `PROCESSING` ➡️ `COMPLETED` / `FAILED` ➡️ `RESUBMITTED` 
- each row tracking `completionAttempts` and last resubmission date (重新提交日期).

#### The transitions are separate (獨立的) transactions — and that *IS* the at-least-once guarantee

`CompletionRegisteringAdvisor` wraps every `AFTER_COMMIT` listener with `markProcessing` ➡️ `proceed()` ➡️ `markCompleted` / `markFailed` (a `CompletableFuture` return type defers the transition to completion).
- All three registry methods are declared `@Transactional(propagation = Propagation.REQUIRES_NEW)`, so one event reaching one listener is **four** transactions:

```
publisher thread  [tx 1]  business write ➕ INSERT publication (PUBLISHED)   ── COMMIT
                             ⬇️ AFTER_COMMIT ➡️ @Async submit
executor thread   [tx 2]  UPDATE status = PROCESSING                        ── COMMIT
                  [tx 3]  your listener body (REQUIRES_NEW)                 ── COMMIT   ⬅️ crash window
                  [tx 4]  UPDATE status = COMPLETED                         ── COMMIT
```

Two consequences are worth internalizing:

- **Completion is not atomic with your work.** 
  - Die between tx 3 and tx 4 and the row stays `PROCESSING`, so the listener is re-delivered. 
  - At-least-once is not a policy choice — it is this gap. 
    - The §6 `emittedAt` guard is the right shape of defense precisely because it lives inside tx 3.
- **Four transactions, four connection acquisitions** per event per listener. 
  - Size §3.4's `concurrency-limit` knowing a permit is worth rather more than one connection.

### 5.2 Configuration you should decide explicitly

```yaml
spring:
  modulith:
    events:
      completion-mode: archive                          # update (default) | delete | archive
      republish-outstanding-events-on-restart: false   # see the resubmission note below
      jdbc:
        schema-initialization:
          enabled: false
      staleness:                                       # 2.0+; all zero ➡️ monitor not registered
        published: 0
        processing: 0
        resubmitted: 0
```

- **`spring.modulith.events.jdbc.schema-initialization.enabled` is effectively `true` when absent** 
  - (`matchIfMissing = true` on the initializer beats the `false` in metadata; verified by `SchemaInitializationDefaultIT`). 
  - Set it explicitly either way: `false` means you own the DDL (this repo: `schema.sql`, see [event_publication_registry.md](./event_publication_registry.md)), otherwise no table and every `publish` fails. 
  - `use-legacy-structure` also defaults to `false`, so you get the v2 schema — note its hash index on `serialized_event`, 
    - because the fallback completion path matches rows by the serialized JSON rather than by id.
- **`completion-mode`**
  - `update` keeps completed rows (grows forever; purge via `CompletedEventPublications`), `delete` removes them, `archive` moves them to an archive table.
- **Re-delivery is opt-in.** Incomplete publications are **kept, not auto-retried**. 
  - You either restart with `republish-outstanding-events-on-restart: true`, 
  - or drive `IncompleteEventPublications` / `FailedEventPublications` (2.0+) yourself with `ResubmissionOptions` (`withBatchSize`, `withMinAge`, `withFilter`).
- **Resubmission is safe across instances, but the claim has no lease.** `processPublications` claims a row with a compare-and-set and skips it unless it won:

  ```sql
  UPDATE event_publication 
  SET STATUS = 'RESUBMITTED', COMPLETION_ATTEMPTS = COMPLETION_ATTEMPTS + 1, LAST_RESUBMISSION_DATE = ? 
  WHERE ID = ? AND STATUS != 'RESUBMITTED'
  ```

  `rowsAffected == 1` gates the invocation, and `ResubmissionOptions#maxInFlight` is enforced globally via `countByStatus(RESUBMITTED)`. 
  - Two instances will therefore not double-process. 
  - The real hazard is the other one: an instance that dies mid-resubmission leaves the row `RESUBMITTED` forever, because nothing expires the claim — which is exactly what `staleness.resubmitted` is for.
- **Staleness monitor** (2.0+) flips rows stuck in `PUBLISHED`/`PROCESSING`/`RESUBMITTED` to `FAILED` after the configured duration, so a crashed listener does not sit in limbo forever. 
  - Off unless you set a value.
- **This is also what makes executor back-pressure survivable.** 
  - If the task is never submitted at all — a bounded executor rejecting it (§3.4), or a crash between commit and dispatch — the row is simply never completed, so it remains `PUBLISHED` and resubmittable. 
  - Without the registry, the same rejection is one swallowed `ERROR` line.

### 5.3 Status in *this* repository

`spring-modulith-starter-jdbc` is in `pom.xml`, which resolves `events-api`, `events-core`, `events-jdbc` and `events-jackson`. 

So `org.springframework.modulith.events.ApplicationModuleListener` is available and the registry is wired. 

(The *old* `org.springframework.modulith.ApplicationModuleListener` is still gone — deleted in 2.0.0, nothing brings it back. Use the `events` package.)

`application.yml` states the §5.2 decisions explicitly, including `schema-initialization.enabled: false` 
- the tables come from `src/main/resources/schema.sql`; without them the multicaster INSERTs into a table that does not exist.

Still outstanding:

1. No `@ApplicationModuleListener` exists in the codebase yet — the §6 ACL is a sketch, not code.
2. There is no `src/test/resources`, so the Zonky datasource wiring that an `@ApplicationModuleTest` needs is not in place.

---

## 6. Applying It — the CRM ➡️ Sales ACL

The stateful ACL in [communication_patterns.md](./communication_patterns.md) is the sharp case, because every wrong choice fails in a *different* way. 

The listener must: react only to committed CRM facts, write its own translation state, never roll `crm` back, and survive a restart.

```java
//: guru.ddd.sales.integration.crm.CrmLeadAcl.java

@ApplicationModuleListener
void on(final CrmLeadEvent.LeadTouched event) {
    translations.save(stateOf(event.leadId()).touched());
}
```

| Instead write…                                                  | What breaks                                                                                                                                           |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@EventListener`                                                | An ACL bug rolls back a `crm` transaction. Modules coupled through the failure path — worse than a method call, because it is invisible (§2.1).       |
| `@Async @EventListener`                                         | Races the publisher's commit: `stateOf(…)` may not see a `LeadRegistered` that was written moments earlier, and reacts to leads that later roll back. |
| `@TransactionalEventListener` alone                             | `AFTER_COMMIT` ➕ default `REQUIRED` ➡️ `translations.save(…)` **never commits** (§4.3). Test passes in-transaction, production loses every card.     |
| `@TransactionalEventListener` ➕ `@Transactional(REQUIRES_NEW)` | Correct, but synchronous — `crm`'s commit path waits for the ACL — and still no publication log.                                                      |
| `@ApplicationModuleListener` ➕ starter-jdbc                    | ✅ All four requirements.                                                                                                                             |

And the one guarantee the ACL is built on:

```java
state.toQualifiedProspect(event.at())
        .ifPresent(publisher::publishEvent);   // the downstream event
translations.save(state);                      // the emittedAt stamp
```

Both statements are in **one** `REQUIRES_NEW` transaction. A crash cannot land between them, so
`emittedAt` is a trustworthy emit-once guard. With `@Async @EventListener` there is no transaction at all
here and that guard is a lie.

---

## 7. Gotchas Checklist

| #  | Gotcha                                                                                                                                                                                                                                                                                                                               | Applies to                                                  |
|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| 1  | **Self-invocation.** Calling the listener method from inside the same bean bypasses the proxy — no async, no transaction, no phase.                                                                                                                                                                                                  | `@Async`, `@Transactional`, all of them                     |
| 2  | **`private` / `final` methods are never advised.** Package-private is fine (CGLIB proxy is generated in the same package).                                                                                                                                                                                                           | same                                                        |
| 3  | **Missing `@EnableAsync`** ➡️ `@Async` is silently ignored and everything runs synchronously. Tests still pass; production is just slower.                                                                                                                                                                                           | `@Async`                                                    |
| 4  | **No transaction ➡️ silent no-op** unless `fallbackExecution = true`.                                                                                                                                                                                                                                                                | `@TransactionalEventListener`, `@ApplicationModuleListener` |
| 5  | **`@Order` is meaningless once async.** Ordering across async listeners is not defined; if you need ordering, you need a state machine, not annotations.                                                                                                                                                                             | `@Async`, `@ApplicationModuleListener`                      |
| 6  | **Exceptions vanish** in every variant except plain `@EventListener` and `BEFORE_COMMIT`. Register an `AsyncUncaughtExceptionHandler`; watch the registry's `FAILED` rows.                                                                                                                                                           | async / after-commit variants                               |
| 7  | **At-least-once, not exactly-once.** Retries and restarts re-deliver. Every cross-module listener needs an idempotency guard (`emittedAt`, a natural key, a version).                                                                                                                                                                | `@ApplicationModuleListener`                                |
| 8  | **Concurrency.** Two events for one entity can land on two virtual threads at once. `@Version` ➡️ `OptimisticLockingFailureException`; decide whether that retries or fails.                                                                                                                                                         | async variants                                              |
| 9  | **Unbounded virtual-thread executor** in this repo (§3.4) — bound it against the connection pool, and know that bounding moves the wait onto the publisher's commit path. Full treatment in [virtual_thread_task_execution.md](./virtual_thread_task_execution.md).                                                                  | `@Async`, `@ApplicationModuleListener`                      |
| 10 | **`MDC` / `SecurityContext` / request scope do not cross the thread hop** unless you install a `TaskDecorator` (or set `spring.task.execution.propagate-context: true` with Micrometer Context Propagation on the classpath).                                                                                                        | `@Async`, `@ApplicationModuleListener`                      |
| 11 | **The listener id is the method signature.** It defaults to `pkg.Class.method()` and is bean-name independent. Rename the method or the class, or move the package, and every in-flight publication row is orphaned — resubmission logs `Listener ... not found!` and marks them `FAILED`. Pin `id` explicitly on anything deployed. | `@ApplicationModuleListener`, `@TransactionalEventListener` |
| 12 | **Completion is a separate transaction from your work** (§5.1). A crash between your listener's commit and the `COMPLETED` update re-delivers the event. A registry row is not a distributed transaction.                                                                                                                            | `@ApplicationModuleListener`                                |

---

## 8. Testing Each Variant

| Variant                       | How to test                                                                                                                                                                                                                                                        |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@EventListener`              | Plain unit test. Call the method, or publish and assert inline — no waiting, no framework.                                                                                                                                                                         |
| `@Async @EventListener`       | Needs an awaiting assertion (Awaitility, or Modulith's `Scenario`). Alternatively define a synchronous `TaskExecutor` bean in the test context and keep the test deterministic.                                                                                    |
| `@TransactionalEventListener` | The test method must be **transactional**, or the listener never fires (§4.2). Beware: a `@Transactional` test rolls back, so `AFTER_COMMIT` never happens — use `TestTransaction.flagForCommit()` / `end()`, or drive it through a non-transactional entry point. |
| `@ApplicationModuleListener`  | `@ApplicationModuleTest` ➕ `Scenario` — it handles publishing, the transaction, and the async wait. Use `AssertablePublishedEvents` for plain assertions on what was published.                                                                                   |

```java
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

Outside `@ApplicationModuleTest`, `Scenario` injection requires `@EnableScenarios`.
The edge case that deserves its own test in every async listener: **publish the same event twice, assert the
effect happens once.**

---

## 9. Decision Procedure

```
Does the listener touch data the publisher wrote, or write data itself?
├─ no  ➡️ metrics, logs, a self-contained webhook
│        ➡️ @Async @EventListener          (bound the executor)
└─ yes
   └─ Must it succeed or fail together with the publisher?
      ├─ yes ➡️ same unit of work, same module
      │        ➡️ @EventListener            (or @TransactionalEventListener(BEFORE_COMMIT) for a veto)
      └─ no  ➡️ it reacts to a committed fact
         └─ Does it cross an application module boundary?
            ├─ no  ➡️ @TransactionalEventListener ➕ @Transactional(REQUIRES_NEW)
            └─ yes ➡️ @ApplicationModuleListener
                     └─ and add spring-modulith-starter-jdbc, or you have the semantics without the durability
```

One-line rule of thumb:

> **`@EventListener` for one unit of work. `@ApplicationModuleListener` for two.
> `@Async @EventListener` only when no database is involved on either side.**

---

##### Version Notes

Verified on **2026-09-15** by reading the actual sources, not documentation summaries:

- `ApplicationModuleListener` — `org.springframework.modulith:spring-modulith-events-api:2.1.0`
- `TransactionalEventListener`, `TransactionPhase`, `TransactionSynchronizationUtils` — `spring-tx:7.0.9`
- `EventListener`, `Async`, `AsyncAnnotationBeanPostProcessor`, `ApplicationListenerMethodAdapter` — `spring-context:7.0.9`
- `TaskExecutorConfigurations`, `TaskExecutionProperties` — `spring-boot-autoconfigure:4.1.1`
- Registry semantics, completion modes, staleness, resubmission — Spring Modulith 2.1 reference, *Events*

Extended on **2026-09-22**, again by reading sources, for §5.1, §5.2, §5.3, and gotchas 11–12.
§4.3 is **not** yet updated: the `never committed` claim there is the JPA-shaped failure, and the
JDBC-shaped one may differ (see the open question below). Sources read:

- `PersistentApplicationEventMulticaster`, `CompletionRegisteringAdvisor`, `DefaultEventPublicationRegistry`,
  `EventPublicationConfiguration` — `spring-modulith-events-core:2.1.0`
- `JdbcEventPublicationRepositoryV2`, `JdbcConfigurationProperties`, `schemas/v2/schema-postgresql.sql` —
  `spring-modulith-events-jdbc:2.1.0`
- `AbstractPlatformTransactionManager`, `TransactionSynchronizationManager` — `spring-tx:7.0.9`;
  `DataSourceTransactionManager` — `spring-jdbc:7.0.9`; `HikariConfig` — `com.zaxxer:HikariCP:7.0.2`

Two corrections to [communication_patterns.md](./communication_patterns.md) §3, which states that
`org.springframework.modulith.ApplicationModuleListener` "is deprecated". That was true on 1.x and is stale
on 2.x — the source history across `spring-modulith-api` versions:

| Version      | `org/springframework/modulith/ApplicationModuleListener.java`                                                                                  |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0.0        | present, not deprecated                                                                                                                        |
| 1.1 – 1.4.4  | present, `@Deprecated(since = "1.1", forRemoval = true)` → *"Prefer `org.springframework.modulith.events.ApplicationModuleListener`"*          |
| 2.0.0, 2.1.0 | **absent** — the jar holds only `ApplicationModule`, `ApplicationModuleInitializer`, `Modulith`, `Modulithic`, `NamedInterface`, `PackageInfo` |

Second: the annotation has a `propagation()` attribute since 1.3, so `REQUIRES_NEW` is an overridable
default rather than a fixed property of the annotation.

/// :~
