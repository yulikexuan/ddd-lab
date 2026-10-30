# Plan — jMolecules + hkj study roadmaps

- Decisions: two roadmaps · standalone, done **before** IDDD · labs in IDDD modules · FP level: Functor/Monad known.
- Order: jMolecules (5 wk) → hkj (8 wk + 1 optional). Week ids `J1…J5`, `H1…H9` so they don't clash with IDDD's `Week N`.
- Labs build small SaaSOvation slices (`Tenant`, `TenantId`, `EmailAddress`, `BacklogItem`…). IDDD weeks later deepen them.
- Same shape as `docs/iddd/README.md`: How to Study · Weekly Plan · cheat-sheet · lab-note template.

---

## Deliverables

1. `docs/jmolecules/README.md` — source: github.com/xmolecules/jmolecules, jmolecules-integrations, jmolecules-examples, Modulith verification docs.
2. `docs/hkj/README.md` — source: higher-kinded-j.github.io/latest (Where to Start reading order + 19 tutorial journeys).
3. Links: root `README.md`; IDDD README §1 "Week 0" → "do the jMolecules and hkj tracks first".
4. Lab notes go to `docs/{jmolecules,hkj}/labs/NN-<topic>.md`. Commits are `JMOL-000N <topic>` / `HKJ-000N <topic>`.

## jMolecules weeks

| Wk | Topic                                                                                                 | Lab (module)                                                       |
|----|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| J1 | Stereotypes as code: annotation model vs type model (`AggregateRoot<T,ID>`, `Identifier`, `Entity`)   | `Tenant`, `TenantId`, `FullName` both ways; pick one (identityaccess) |
| J2 | `JMoleculesDddRules` (4 rules) + auto-run inside Modulith `verify()`                                  | Break each rule on purpose, then fix it (agilepm `Product`/`BacklogItem`) |
| J3 | Architecture: layered / onion / hexagonal; `ensureHexagonal`; `@Module` vs `@ApplicationModule`      | Hexagonal layout + 1 port/adapter; `VerificationOptions` (identityaccess) |
| J4 | Events: `@DomainEvent`, `@DomainEventHandler`, `@Externalized` vs Modulith events                     | `TenantProvisioned` → listener in collaboration                    |
| J5 | Integrations: `jmolecules-spring` converters, `Association`, Jackson 3 module, ByteBuddy (spike only) | Persist `Tenant` (JDBC + Zonky); REST with typed id path variable  |

## hkj weeks

| Wk  | Topic                                                                  | Tutorial journey               | Lab (module)                                    |
|-----|------------------------------------------------------------------------|--------------------------------|-------------------------------------------------|
| H1  | HKT encoding: `Kind<F,A>`, witnesses, `widen`/`narrow`, `Instances`    | Core Foundations               | Generic `Functor`/`Monad` helper + tests        |
| H2  | `Maybe`, `Either`, `Try`, `Validated`; fail-fast vs accumulate         | Core Error Handling            | `EmailAddress.parse`, `ContactInformation` (identityaccess) |
| H3  | Effect Path API: `EitherPath`, `ValidationPath`, `via`/`recover`, `ForPath` | Effect API                | "Register user" as a railway (identityaccess)   |
| H4  | `For`, `ForState`, `Reader`/`State`/`Writer`                           | Core Advanced, ForState        | Sprint capacity planning with `State` (agilepm) |
| H5  | `IO`, `VTask`, `Scope`/`Resource`, resilience combinators              | VTask, Scope & Resource        | Parallel lookups in an app service              |
| H6  | Optics I: `Lens`, `Prism`, `Iso`; `@GenerateLenses`/`@GeneratePrisms`; law tests | Lens & Prism         | Nested VO update in `Tenant`; sealed status Prism |
| H7  | Optics II: `Traversal`, Focus DSL, boundary mapping (`@GenerateMapping`) | Traversals, Focus DSL (subset), Boundary Mapping | Bulk task update in `BacklogItem`; DTO→domain |
| H8  | Spring starter: return-value handlers, status mapping, 422, Jackson, actuator; `hkj-test` | Spring chapter | REST endpoints returning `EitherPath` + MockMvc |
| H9* | Optional: transformers (`EitherT`, `ReaderT`), Free, effect handlers   | Transformers, Effect Handlers  | Interpreted "provision tenant" program          |

## Setup issues to fix when J1/H1 is scaffolded (no changes now)

- hkj: the BOM is 0.5.0, but `hkj-processor-plugins` uses `${hkj-spring-boot-starter.version}` = 0.4.9. Align both to 0.5.0, then check whether `--enable-preview` is needed.
- jMolecules: `jmolecules-starter-ddd` has no Jackson module. Add `jmolecules-jackson3` in J5 (Boot 4 = Jackson 3). The earlier IDDD plan said "no ByteBuddy", so J5 does ByteBuddy only as a throwaway spike.

## Verify

- Every link and API name in both READMEs must trace back to the research sources. Mark anything unconfirmed.
