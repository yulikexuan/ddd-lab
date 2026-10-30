# hkj Study Roadmap

higher-kinded-j (hkj) brings Functor, Monad and friends to Java by emulating higher-kinded types (`Kind<F, A>`). On top of that it offers the Effect Path API, optics and a Spring Boot integration.
The track has 8 weekly labs (`H1`–`H8`) plus an optional `H9`. Do it after [jMolecules](../jmolecules/README.md) and before the [IDDD roadmap](../iddd/README.md).

| Artifact in `pom.xml`                         | Brings in                                                                                       |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------|
| `hkj-spring-boot-starter`                     | hkj core, Spring MVC return-value handlers, `HkjJacksonModule`, an actuator endpoint and metrics |
| `hkj-processor-plugins` (annotation processor) | generated optics (`XLenses`, prisms, `XFocus`), Focus paths and Effect paths                   |
| `hkj-test`                                    | `assertThatEither` / `Maybe` / `Try` / `Validated` / …; law checks for optics and mappings     |

Docs (D): `https://higher-kinded-j.github.io/latest/`. They track `main` and can be ahead of the release, so check names against the jar. Start with [Where to Start](https://higher-kinded-j.github.io/latest/where_to_start.html), the [Cheat Sheet](https://higher-kinded-j.github.io/latest/cheatsheet.html) and the [Glossary](https://higher-kinded-j.github.io/latest/glossary.html). For offline reading there is `llms-full.txt`. More resources, and why `hkj-test` matters for value objects: [modeling/hkj.md](../iddd/modeling/hkj.md).

**Tutorials:** [19 exercise journeys](https://higher-kinded-j.github.io/latest/tutorials/ch_intro.html). The exercises live in [`higher-kinded-j/higher-kinded-j`](https://github.com/higher-kinded-j/higher-kinded-j) under `hkj-examples/src/test/java/org/higherkindedj/tutorial/`. Clone that repo next to this one and do them there.

---

## 1. How to Study

**Every week:**

1. **Read** (1–2 h): the week's D pages.
2. **Tutorial** (1–2 h): the week's journey. Large journeys say how much to pick.
3. **Lab** (2–3 h): ask Claude to *"scaffold week H<N>"*. You get a spec, failing tests and TODO stubs. You make the tests green.
4. **Review:** ask Claude to *"review week H<N>"*, then address the findings.
5. **Note:** copy the [template](#4-lab-note-template) to `docs/hkj/labs/0N-<topic>.md`.
6. **Commit:** `HKJ-000N <topic>`.

**Recurring question:** which layer should see which hkj type? Should `Either` / `Validated` appear in the domain, `EitherPath` in application services, `Kind<F, A>` anywhere at all? Answer it in every note.

**Done when:** `mvn verify` is green, the note is written and the review is addressed.


---

## 2. Weekly Plan

#### Week H1 · HKT encoding and type classes — `agilepm`
- **Read:** [Quickstart](https://higher-kinded-j.github.io/latest/quickstart.html); [Foundations](https://higher-kinded-j.github.io/latest/hkts/foundations_intro.html); [Type classes](https://higher-kinded-j.github.io/latest/functional/ch_intro.html).
- **Look for:**
  - Why Java can't write `F<A>`.
  - `Kind<F extends WitnessArity<?>, A>` and witnesses such as `MaybeKind.Witness` and `EitherKind.Witness<L>`.
  - The per-type `widen` / `narrow` helpers (`MAYBE`, `EITHER`, `LIST`, …), and `KindUnwrapException`.
  - Instance lookup, e.g. `Instances.monad(Witnesses.maybe())`.
  - `Functor`, `Applicative`, `Monad`, `Foldable`, `Traverse`, `Semigroup` / `Monoid`.
- **Tutorial:** Core Foundations (37).
- **Lab:**
  - Learning tests for `widen` / `narrow`.
  - One function that is generic in `F` (e.g. total story points via `Foldable` + `Monoid`), run against two different `F`s.
  - Note: would you keep it in the domain?

#### Week H2 · Typed errors — `identityaccess`
- **Read:** [Either](https://higher-kinded-j.github.io/latest/monads/either_monad.html); [Validated](https://higher-kinded-j.github.io/latest/monads/validated_monad.html); the Maybe and Try pages (Monads chapter); `MonadError`.
- **Look for:**
  - Fail-fast (`Either`, `flatMap`) vs accumulating (`Validated` + `Semigroup`).
  - `Try` at boundaries where exceptions are thrown.
  - `raiseError` / `handleErrorWith`; sealed error types.
  - 0.5.0 rejects `null` in `Right` / `Left` / `Success`, and uses `Unit` instead of `Void`.
- **Tutorial:** Core Error Handling (26).
- **Lab:**
  - `EmailAddress.parse(String)` returns an `Either`.
  - `ContactInformation` validation returns a `Validated` that reports every bad field at once.
  - Test both with `assertThatEither` / `assertThatValidated`.
- **Compare:** IDDD ch 5–6 self-validation, which throws. When do exceptions still fit?

#### Week H3 · Effect Path API — `identityaccess`
- **Read:** [Effect quickstart](https://higher-kinded-j.github.io/latest/effect/quickstart.html); [Effect Path overview](https://higher-kinded-j.github.io/latest/effect/effect_path_overview.html); [Path types](https://higher-kinded-j.github.io/latest/effect/path_types.html); [Composition](https://higher-kinded-j.github.io/latest/effect/composition.html); [ForPath](https://higher-kinded-j.github.io/latest/effect/forpath_comprehension.html).
- **Look for:**
  - The railway model: `map`, `via`, `recover`, `focus`.
  - Choosing a Path: `MaybePath`, `EitherPath`, `TryPath`, `ValidationPath`, `IOPath`.
  - The docs' advice: use Paths first and drop to transformers only when needed.
- **Tutorial:** Effect API (17).
- **Lab:** the "register user" application service as one pipeline: parse the input → tenant is active → username is free → create `User`. Errors are typed values on the rails. Use in-memory repositories.

#### Week H4 · Comprehensions and state — `agilepm`
- **Read:** [For](https://higher-kinded-j.github.io/latest/functional/for_comprehension.html); [ForState](https://higher-kinded-j.github.io/latest/functional/forstate_comprehension.html); the Reader, State and Writer pages (Monads chapter).
- **Look for:**
  - `For` vs nested `flatMap`, and `MonadZero` for filtering inside `For`.
  - `State<S, A>` carries state forward without mutation.
  - Reader for dependencies, Writer for an accumulated log.
- **Tutorial:** Core Advanced (26); Expression ForState (13).
- **Lab:** commit backlog items to a `Sprint` with a capacity limit using `State`, and collect the resulting events with `Writer`. Compare with a method that mutates the aggregate.

#### Week H5 · Effects and concurrency — `identityaccess`
- **Read:** [VTask](https://higher-kinded-j.github.io/latest/monads/vtask_monad.html); the IO, Scope and Resource pages; [Resilience](https://higher-kinded-j.github.io/latest/resilience/ch_intro.html).
- **Look for:**
  - Describing an effect vs running it.
  - `VTask` on virtual threads ([this repo's setup](../virtual_thread_task_execution.md)).
  - `Scope` (structured concurrency) and `Resource` (safe cleanup).
  - `withRetry`, `withTimeout`, `withCircuitBreaker`, `withBulkhead`.
  - `SteppableClock` for tests that depend on time.
- **Tutorial:** Concurrency VTask (28); Scope and Resource (20); Resilience (24) is optional.
- **Lab:** an application service that loads tenant, user and roles in parallel, with a timeout and a retry. Make the tests deterministic with `SteppableClock`.

#### Week H6 · Optics I — `identityaccess`, `agilepm`
- **Read:** Scott Logic [Part 1: The Immutability Gap](https://blog.scottlogic.com/2026/01/09/java-the-immutability-gap.html) and [Part 2: Optics Fundamentals](https://blog.scottlogic.com/2026/01/16/optics-fundamentals.html); [Optics quickstart](https://higher-kinded-j.github.io/latest/optics/quickstart.html); [Lenses](https://higher-kinded-j.github.io/latest/optics/lenses.html); [Prisms](https://higher-kinded-j.github.io/latest/optics/prisms.html); [Annotations at a glance](https://higher-kinded-j.github.io/latest/optics/annotations_at_a_glance.html).
- **Look for:**
  - Deep updates of immutable records.
  - `Lens` (exactly one value), `Prism` (one case of a sealed type or enum), `Iso`.
  - Composing optics with `andThen`.
  - `@GenerateLenses` on records and `@GeneratePrisms` on sealed interfaces and enums.
  - Law checks (`LensLaws.assertLensLaws`, `PrismLaws`).
- **Tutorial:** Lens & Prism (33).
- **Lab:** update `Tenant → ContactInformation → EmailAddress` without hand-written `with…` chains. Add a Prism over a sealed `BacklogItemStatus`, and law tests for both.
- **Build:** the annotation processor order is load-bearing (see `CLAUDE.md`). Recompile after touching annotated records.

#### Week H7 · Optics II and boundary mapping — `agilepm`
- **Read:** [Traversals](https://higher-kinded-j.github.io/latest/optics/traversals.html); [Focus DSL](https://higher-kinded-j.github.io/latest/optics/focus_dsl.html); [Focus + Effect Paths](https://higher-kinded-j.github.io/latest/effect/focus_integration.html); [Boundary mapping](https://higher-kinded-j.github.io/latest/mapping/ch_intro.html).
- **Look for:**
  - `Traversal` (0..n), `Affine` (0..1), and `Fold` / `Getter` / `Setter`.
  - The Focus DSL (`FocusPath`, `AffinePath`, `TraversalPath`), generated by `@GenerateFocus`.
  - `@GenerateMapping`: DTO ↔ domain mapping that collects every field error.
- **Tutorial:** Traversals (28); Focus DSL (90, so pick ~30); Boundary Mapping (24).
- **Lab:** re-estimate every `Task` of a `BacklogItem` with one Traversal. Map a `PlanBacklogItemRequest` DTO into the domain with `@GenerateMapping`.

#### Week H8 · Spring Boot integration — `identityaccess`
- **Read:** [Spring chapter](https://higher-kinded-j.github.io/latest/spring/ch_intro.html); [Spring Boot integration](https://higher-kinded-j.github.io/latest/spring/spring_boot_integration.html); [Test assertions](https://higher-kinded-j.github.io/latest/tooling/test_assertions.html).
- **Look for:**
  - Controller return types:
    - `Either` / `EitherPath`.
    - `Validated` / `ValidationPath`: 422 when every error is a `FieldError`.
    - `MaybePath`: 404 on `Nothing`.
    - `VTaskPath`.
    - `VStreamPath`: Server-Sent Events.
  - How the error status is resolved: `hkj.web.error-status-mappings`, then a class-name heuristic, then the default. A custom `ErrorStatusCodeStrategy` bean replaces the default.
  - `HkjJacksonModule`; the actuator `hkj` endpoint and its metrics; the `hkj.*` properties.
- **Lab:** expose the H3 pipeline as `POST /tenants/{tenantId}/users` returning `EitherPath`, and a `GET` that returns `MaybePath`. Write MockMvc tests for success, 404 and 422.
- **Compare:** the jMolecules Jackson module (J5) and `HkjJacksonModule` side by side.

#### Week H9 (optional) · Transformers and effect handlers — `identityaccess`
- **Read:** [Transformers](https://higher-kinded-j.github.io/latest/transformers/ch_intro.html); [When to drop to transformers](https://higher-kinded-j.github.io/latest/transformers/when_to_drop_to_transformers.html); [Effect handlers](https://higher-kinded-j.github.io/latest/effect/effect_handlers_intro.html).
- **Look for:** `EitherT`, `ReaderT`, `StateT`; programs as data (`Free`); `@EffectAlgebra`, `@ComposeEffects`, `EffectBoundary`.
- **Tutorial:** Monad Transformers (29); Effect Handlers (19); Capstone (7).
- **Lab:** "provision tenant" as one program with two interpreters: in-memory for tests and JDBC at runtime. Expose it through `EffectBoundary` and a `FreePath` endpoint.

---

## 3. Java Habit → hkj Cheat-Sheet

| Plain Java habit                                       | hkj                                                                    |
|--------------------------------------------------------|------------------------------------------------------------------------|
| `null` / `Optional`                                    | `Maybe`, `MaybePath`                                                   |
| Throw on bad input                                     | `Either` (fail-fast) / `Validated` (collect every error)               |
| `try/catch` around library calls                       | `Try`, `TryPath`                                                       |
| Nested `flatMap`s                                      | `For`, `ForPath`                                                       |
| `withX(..)` chains on nested records                   | `Lens`, Focus DSL                                                      |
| `CompletableFuture` / executor plumbing                | `VTask`, `Scope`, `Resource`                                           |
| `@ExceptionHandler` mapping exceptions → HTTP status   | Return `EitherPath`; `hkj.web.error-status-mappings`                   |
| Resilience4j annotations                               | `withRetry`, `withTimeout`, `withCircuitBreaker`, `withBulkhead`       |
| Hand-written DTO ↔ domain mappers                      | `@GenerateMapping`                                                     |

---

## 4. Lab Note Template

```markdown
# Week HN · <topic>

## In my own words
<½ page: the abstraction and the problem it solves>

## Plain Java vs hkj
<the same code both ways; what got better, what got worse>

## Which layer sees which hkj type
<domain / application / adapter, and why>

## Open questions
<what is still unclear or disputed>
```
