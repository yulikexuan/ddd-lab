# Plan — scaffold jMolecules Week J1 (Stereotypes as code)

Decisions: sibling packages `annotated` / `typed` / `hybrid` · stubs carry **no** stereotypes (you add them) · minimal Tenant.

Order: `annotated` (done) → `typed` → `hybrid` (scaffold only after `typed` is implemented).

Hybrid split: annotations on aggregates/entities, types for ids — Tenant `@AggregateRoot` + `@Identity`, TenantId `implements Identifier`, FullName `@ValueObject`.

## Verified facts (jmolecules-ddd 2.0.1, jmolecules-apt 0.33.0, spring-modulith-core 2.1.2)

- `AggregateRoot<T extends AggregateRoot<T,ID>, ID extends Identifier> extends Entity<T,ID>`; `Entity<T extends AggregateRoot<T,?>, ID> extends Identifiable<ID>` → `ID getId()`.
- `@Identity` targets FIELD/METHOD; DDD annotations are RUNTIME-retained.
- jmolecules-apt fails `javac` when: `@AggregateRoot`/`@Entity` has no `@Identity`; an identifiable holds an aggregate field; a VO/`Identifier` holds an identifiable.
- Modulith detects both forms: `ApplicationModule#getAggregateRoots()`, `#getValueTypes()`.
- `Identifier` does not extend `ValueObject` → a hybrid `TenantId` is not a Modulith value type.
- ArchUnit `valueObjectsMustNotReferToIdentifiables` covers `Identifier` types only via the interface (no identifier annotation exists).

## Layout

```
main  guru.ddd.identityaccess.domain.model.identity.{annotated,typed,hybrid}  Tenant, TenantId, FullName
test  same packages                                                           TenantIdTest, TenantTest, FullNameTest, StereotypesTest
docs  docs/iddd/jmolecules/labs/J1-spec.md
```

## Stub API (bodies throw `UnsupportedOperationException("TODO J1")`)

| Type                   | API                                                                                                                                                                      |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TenantId` (record)    | `(String id)`, `static of(UUID)`                                                                                                                                         |
| `FullName` (record)    | `(String firstName, String lastName)`, `formattedName()`, `withChangedFirstName(..)`, `withChangedLastName(..)`                                                          |
| `Tenant` (final class) | `Tenant(TenantId, String name, boolean active)`, `name()`, `isActive()`, `activate()`, `deactivate()`; id accessor: `tenantId()` (annotated, hybrid) / `getId()` (typed) |

## Failing tests (same behavior in all three packages)

- `TenantId`: `of` keeps value; value equality; rejects null / blank / non-UUID.
- `FullName`: formatted name; `withChanged*` returns new instance, original untouched; value equality; rejects null / blank.
- `Tenant`: holds id/name/active; activate/deactivate; rejects null id, blank or >100-char name; **equality by identity only**.
- `StereotypesTest`:
  - annotated → `@AggregateRoot` on Tenant, `@Identity` field of type `TenantId`, `@ValueObject` on FullName, no jMolecules type interfaces.
  - typed → Tenant `AggregateRoot`, TenantId `Identifier`, FullName `ValueObject`, no DDD annotations.
  - hybrid → `@AggregateRoot` on Tenant, `@Identity` field of type `TenantId`, TenantId `Identifier` without `@ValueObject`, `@ValueObject` on FullName, no `AggregateRoot`/`Entity`/`ValueObject` interfaces.
  - all → Modulith lists Tenant in `identityaccess` aggregate roots and FullName in value types (TenantId only in annotated: `Identifier` is not a Modulith value type).

## Spec (`J1-spec.md`)

Goal, API table, rules, stereotype requirements, compile-time experiments (drop `@Identity`; wrong `Entity` generic; Tenant field in FullName), run commands, done criteria, note prompts (annotated `TenantId` = ? ; `getId()` vs `tenantId()`; compiler vs test enforcement; hybrid: what changes once `TenantId` is an `Identifier` instead of a `@ValueObject` — rules, Modulith, J5 integrations; which of the three to keep).

## Also

- README line 37: empty note path → `docs/iddd/jmolecules/labs/J<N>-<topic>.md`.
- No commit (you commit `JMOL-0001 …` after the lab).

## Verify

- `mvn test-compile` green.
- `mvn test`: only the new J1 tests red, and for the right reason (TODO / assertion); `ModuleStructureTest`, `ArchitectureTest` green.
