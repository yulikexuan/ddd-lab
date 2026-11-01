# Week J1 · Stereotypes as code — Spec

* Model `Tenant`, `TenantId` and `FullName` three times — annotations, types, hybrid 
* Then find out what each form lets javac, jmolecules-apt, ArchUnit and Spring Modulith check. 
* Finally, we can Keep one.

| Package     | Status                           |
|-------------|----------------------------------|
| `annotated` | done — the reference             |
| `typed`     | done                             |
| `hybrid`    | next — scaffolded                |

Base package: `guru.ddd.identityaccess.domain.model.identity.{annotated,typed,hybrid}` (main and test).

---

## 1. API (same in all three packages)

| Type                   | API                                                                                                             |
|------------------------|-----------------------------------------------------------------------------------------------------------------|
| `TenantId` (record)    | `(String id)`, `static of(UUID)`; implements `guru.ddd.common.domain.model.IdentitySpec`                        |
| `FullName` (record)    | `(String firstName, String lastName)`, `formattedName()`, `withChangedFirstName(..)`, `withChangedLastName(..)` |
| `Tenant` (final class) | `Tenant(TenantId, String name, boolean active)`, `name()`, `isActive()`, `activate()`, `deactivate()`           |

`id` accessor on `Tenant`: 
- `tenantId()` in `annotated` and `hybrid`; 
- `getId()` in `typed` (forced by `Identifiable<ID>`).

---

## 2. Rules

**TenantId**

- `id` must match `IdentitySpec.UUID_FORMAT` (canonical UUID, any case, no trimming) → else `IllegalArgumentException`.
- `null` → `NullPointerException`.
- Equality by value.

**FullName**

- Each part: not blank, trimmed, 1–50 characters after trimming.
- Messages: 
  - `First name is required.` 
  - `Last name is required.` 
  - `The first name must be 50 characters or less.` 
  - `The last name must be 50 characters or less.`
- `null` part → `NullPointerException`.
- `formattedName()` = `first + " " + last`.
- `withChanged*` returns a new, validated instance; the original is unchanged.
- Equality by value (after normalization).

**Tenant**

- Name: not blank, trimmed, ≤ 100 characters after trimming. 
- Messages: 
  - `The tenant name is required.` 
  - `The name must be 100 characters or less.`
- `null` id or name → `NullPointerException`.
- `activate()` / `deactivate()` are idempotent.
- Equality and hash code by identity only; both survive state changes.

---

## 3. Stereotypes

|                                | `annotated`                         | `typed`                                            | `hybrid`                                              |
|--------------------------------|-------------------------------------|----------------------------------------------------|-------------------------------------------------------|
| `Tenant`                       | `@AggregateRoot`, `@Identity` field | `implements AggregateRoot<Tenant, TenantId>`       | `@AggregateRoot`, `@Identity` field                   |
| `TenantId`                     | `@ValueObject`                      | `implements Identifier`                            | `implements Identifier`, **no** `@ValueObject`        |
| `FullName`                     | `@ValueObject`                      | `implements ValueObject`                           | `@ValueObject`                                        |
| Forbidden                      | any `org.jmolecules.ddd.types` type | any `org.jmolecules.ddd.annotation` DDD annotation | `AggregateRoot` / `Entity` / `ValueObject` interfaces |
| Modulith `getAggregateRoots()` | `Tenant`                            | `Tenant`                                           | `Tenant`                                              |
| Modulith `getValueTypes()`     | `FullName`, `TenantId`              | `FullName`                                         | `FullName`                                            |

**All three must pass the four `JMoleculesDddRules` (as in `annotated/StereotypesTest`).**

Why `TenantId` drops out of Modulith's value types in `typed` and `hybrid`:
- Modulith counts a type as a value object only if it is `@ValueObject` or assignable to `ValueObject` (spring-modulith-core 2.1.2, `ArchitecturallyEvidentType`) 
- and `Identifier` does not extend `ValueObject` (jmolecules-ddd 2.0.1).

---

## 4. Experiments (break, compile, observe, revert)

Compile with `JAVA_HOME="C:/dev/java/jdk-25" mvn -q compile`.
jmolecules-apt is an annotation processor: its errors (E1, E3) appear only at build time (`mvn compile` or IntelliJ *Build Project*, Ctrl+F9), never in editor highlighting.

| #  | Package               | Change                                                                    | Expected                                                                      | Checked by     |
|----|-----------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------|----------------|
| E1 | `annotated`, `hybrid` | remove `@Identity` from `Tenant`                                          | error: `Tenant needs identity declaration on either field or method!`         | jmolecules-apt |
| E2 | `typed`               | `AggregateRoot<Tenant, String>`, then `AggregateRoot<FullName, TenantId>` | error: `type argument … is not within bounds of type-variable …`              | javac generics |
| E3 | all                   | add a `Tenant` field to `FullName`                                        | error: `Value object or identifier must not refer to identifiables!`          | jmolecules-apt |
| E4 | `typed`               | `TenantId implements Identifier, ValueObject`; run `StereotypesTest`      | compiles; Modulith now lists `TenantId` as a value type → Modulith test fails | Modulith       |

E1 has no `typed` counterpart: `getId()` is the identity declaration.

## 5. Run

```bash
# one package
JAVA_HOME="C:/dev/java/jdk-25" mvn test -Dtest='guru/ddd/identityaccess/domain/model/identity/typed/*Test'
# structure + architecture
JAVA_HOME="C:/dev/java/jdk-25" mvn test -Dtest='ModuleStructureTest,ArchitectureTest'
# everything
JAVA_HOME="C:/dev/java/jdk-25" mvn verify
```

## 6. Done when

- `TenantIdTest`, `FullNameTest`, `TenantTest`, `StereotypesTest` green in all
  three packages.
- E1–E4 run and reverted.
- `mvn verify` green (incl. `ModuleStructureTest`, `ArchitectureTest`).
- Note written at `docs/iddd/jmolecules/labs/J1-stereotypes-as-code.md`
  ([template](../README.md#4-lab-note-template)).
- Review addressed; commit `JMOL-0001 Stereotypes as code`.

## 7. Note prompts

1. Annotated `TenantId` is a `@ValueObject` because there is no identifier
   annotation. What does it lose compared with `implements Identifier` (J5:
   Spring converters, Jackson)?
2. `getId()` (forced by the type model) vs `tenantId()` (ubiquitous language):
   which reads better, and can you keep both?
3. Map each of E1–E4 to who caught it (javac, jmolecules-apt, ArchUnit,
   Modulith) and when (compile vs test). Which form gets more checked at compile
   time?
4. Hybrid: once `TenantId` is an `Identifier` instead of a `@ValueObject`, what
   changes for the DDD rules, for Modulith, and for J5's integrations?
5. Which of the three do you keep for `identityaccess`, and why?
