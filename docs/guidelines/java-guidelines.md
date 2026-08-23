# Java Guidelines

These are the general guidelines for writing Java code in this Spring Boot 4 / Java 25 learning project.

**Tech Stack:** Java 25 LTS, Spring Boot 4.0.x, Spring Framework 7.0.x

## Table of Contents

- [1. Naming Conventions](#1-naming-conventions)
- [2. Code Layout](#2-code-layout)
- [3. Best Practices for Classes, Interfaces, and Enums](#3-best-practices-for-classes-interfaces-and-enums)
- [4. Exception Handling](#4-exception-handling)
- [5. Concurrency](#5-concurrency)
- [6. Use of `Optional`](#6-use-of-optional)
- [7. Stream API Best Practices](#7-stream-api-best-practices)
- [8. Collections](#8-collections)
- [9. Date and Time](#9-date-and-time)
- [10. Strings](#10-strings)
- [11. Java 25 Features](#11-java-25-features)

## 1. Naming Conventions

Follow the Java naming conventions.

- Package names: all lowercase, no underscores, short and meaningful.
- Class and interface names: `PascalCase`, nouns or noun phrases.
- Method names: `camelCase`, verbs or verb phrases.
- Variable names: `camelCase`, short and meaningful. Avoid single-letter names except for loop counters.
- Constant names: `UPPER_SNAKE_CASE`.

## 2. Code Layout

- Use 4 spaces for indentation. Do not use tabs.
- Keep lines under 120 characters (modern wide screens; the legacy 80-char rule is no longer enforced).
- When wrapping lines, break after a comma or an operator. Indent the continuation with 8 spaces.

## 3. Best Practices for Classes, Interfaces, and Enums

### 3.1. Use Records for data-holder classes

```java
// Good
public record CustomerDTO(String name, String email) { }

// Bad
public class CustomerDTO {
    private String name;
    private String email;
    // getters and setters
}
```

### 3.2. Immutability

Prefer immutable classes whenever possible. Immutable objects are inherently thread-safe and easier to reason about.

```java
// Good
public final class Customer {
    private final String name;
    private final String email;

    public Customer(String name, String email) {
        this.name = name;
        this.email = email;
    }
    // getters only
}
```

### 3.3. Program to Interfaces

```java
// Good
List<String> names = new ArrayList<>();

// Bad
ArrayList<String> names = new ArrayList<>();
```

### 3.4. Use Enums Instead of String/Integer Constants

```java
// Good
public enum Status { PENDING, ACTIVE, INACTIVE }

// Bad
public static final String STATUS_PENDING = "PENDING";
public static final int STATUS_ACTIVE = 1;
```

### 3.5. Use Sealed Types for Closed Hierarchies

Sealed interfaces/classes (final since Java 17) make exhaustive pattern matching possible. Combine with records for command/result types.

```java
public sealed interface PaymentResult
    permits PaymentResult.Approved, PaymentResult.Declined, PaymentResult.Failed {

    record Approved(String authCode)              implements PaymentResult { }
    record Declined(String reason)                implements PaymentResult { }
    record Failed(Throwable cause)                implements PaymentResult { }
}

// Exhaustive switch — compiler enforces every variant is handled
String describe(PaymentResult result) {
    return switch (result) {
        case PaymentResult.Approved a -> "approved: " + a.authCode();
        case PaymentResult.Declined d -> "declined: " + d.reason();
        case PaymentResult.Failed f   -> "failed: "   + f.cause().getMessage();
    };
}
```

## 4. Exception Handling

### 4.1. Catch Specific Exceptions

```java
// Good
try {
    // ...
} catch (IOException e) {
    // ...
}

// Bad
try {
    // ...
} catch (Exception e) {
    // ...
}
```

### 4.2. Don't Ignore Exceptions

Either handle or rethrow. If you catch, log with the exception attached.

```java
// Good
try {
    // ...
} catch (IOException e) {
    log.error("Failed to read file", e);
}
```

## 5. Concurrency

### 5.1. Prefer `java.util.concurrent`

Use the high-level utilities. Never construct raw `Thread` objects for application work.

```java
// Good
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
executor.submit(() -> { /* ... */ });
```

### 5.2. Virtual Threads by Default

Java 21+ virtual threads are stable. Use `Executors.newVirtualThreadPerTaskExecutor()` for I/O-bound work. Reserve platform-thread pools (`newFixedThreadPool`) for CPU-bound work.

### 5.3. Avoid `volatile` for Compound Operations

`volatile` ensures visibility but not atomicity. For compare-and-set or counters, use `java.util.concurrent.atomic` or locks.

### 5.4. Scoped Values over ThreadLocals (Java 25, Final)

`ScopedValue` (JEP 506, final in Java 25) is the preferred way to share immutable per-request context with callees and child virtual threads. It is cheaper than `ThreadLocal` and avoids unbounded inheritance.

```java
// Good — Java 25
private static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

ScopedValue.where(CONTEXT, ctx).run(() -> processRequest());

// Inside processRequest()
RequestContext ctx = CONTEXT.get();
```

### 5.5. Structured Concurrency (Preview in Java 25)

`StructuredTaskScope` (JEP 505, 5th preview in Java 25) gives lifetime-bound concurrent task management. **Preview only** — enable with `--enable-preview` and don't rely on a stable API yet. For production-grade code stick to `CompletableFuture` + virtual-thread executor.

## 6. Use of `Optional`

### 6.1. `Optional` for Return Types

```java
// Good
public Optional<Customer> findCustomerById(Long id) { /* ... */ }

// Bad
public Customer findCustomerById(Long id) {  // returns null if not found
    // ...
}
```

### 6.2. Don't Use `Optional` for Fields or Parameters

Use method overloading, a nullable annotation, or `@Nullable` (jspecify) for optional dependencies instead.

## 7. Stream API Best Practices

### 7.1. Avoid Side Effects in Stream Operations

```java
// Good
List<String> names = customers.stream()
    .map(Customer::getName)
    .toList();

// Bad
List<String> names = new ArrayList<>();
customers.stream().forEach(c -> names.add(c.getName()));
```

### 7.2. Prefer Method References

```java
// Good
.map(Customer::getName)

// Bad
.map(c -> c.getName())
```

### 7.3. Prefer `toList()` over `collect(Collectors.toList())`

`Stream.toList()` (Java 16+) returns an unmodifiable list and is more concise. Use `Collectors.toList()` only when you specifically need a mutable list.

## 8. Collections

### 8.1. Choose the Right Collection

Use `List` for ordered collections, `Set` for unordered unique elements, `Map` for key-value pairs.

### 8.2. Prefer `isEmpty()` over `size() == 0`

```java
if (names.isEmpty()) { /* ... */ }
```

### 8.3. Return Empty Collections, Not Null

```java
// Good
public List<String> getNames() {
    if (/* ... */) return List.of();
    // ...
}
```

### 8.4. Use Factory Methods for Immutable Collections

```java
List<String> ids   = List.of("a", "b", "c");
Set<String> tags   = Set.of("hot", "new");
Map<String, Integer> m = Map.of("a", 1, "b", 2);
```

### 8.5. Use Diamond Operator

```java
// Good
List<String> names = new ArrayList<>();
```

### 8.6. Use `for-each` Loop

```java
// Good
for (String name : names) { /* ... */ }
```

Use an iterator or indexed loop only when you need to modify the collection while iterating.

## 9. Date and Time

Use `java.time.*`. Never use `java.util.Date` or `java.util.Calendar` in new code.

```java
LocalDate birthday = LocalDate.of(1990, Month.MAY, 12);
LocalDate today    = LocalDate.now(ZoneId.of("UTC"));
Period age         = Period.between(birthday, today);

ZonedDateTime meeting = ZonedDateTime.of(
    2026, 2, 3, 9, 30, 0, 0, ZoneId.of("Europe/Berlin"));
String iso = meeting.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
```

The `java.time` API is immutable, thread-safe, and provides clear types for each concept (`Instant`, `LocalDate`, `LocalDateTime`, `ZonedDateTime`, `Duration`, `Period`).

## 10. Strings

### 10.1. Use Text Blocks for Multi-line Strings

```java
String sql = """
    SELECT id, name
    FROM customers
    WHERE status = 'ACTIVE'
    ORDER BY name
    """;
```

### 10.2. Use `String.formatted(...)` for Interpolation

String templates were withdrawn after preview. For now, use `formatted`:

```java
String msg = "Hello, %s. You have %d unread messages.".formatted(user, count);
```

## 11. Java 25 Features

Features useful in everyday code (LTS, released 2025-09-16).

### 11.1. Final / Production-Ready

| JEP | Feature                              | Notes for application code                                     |
|-----|--------------------------------------|----------------------------------------------------------------|
| 506 | Scoped Values                        | Use instead of `ThreadLocal` for per-request context.          |
| 511 | Module Import Declarations           | `import module java.base;` simplifies prototyping; use sparingly in module-strict code. |
| 512 | Compact Source Files & Instance Main | Great for scripts/learning; avoid in library modules.          |
| 513 | Flexible Constructor Bodies          | Run validation logic before `super(...)`. Useful for fail-fast invariants. |
| 510 | Key Derivation Function API          | Standard KDF API — use over hand-rolled HKDF/PBKDF2 wrappers. |

### 11.2. Preview (Behind `--enable-preview`)

| JEP | Feature                                              | Status            |
|-----|------------------------------------------------------|-------------------|
| 505 | Structured Concurrency                               | 5th preview       |
| 502 | Stable Values                                        | 1st preview       |
| 507 | Primitive Types in Patterns, `instanceof`, `switch`  | 3rd preview       |

**Rule:** Don't ship preview features to production. Use them for experiments and mark the file/module explicitly so it's clear the code requires `--enable-preview`.

### 11.3. Pattern Matching for `switch`

Stable since Java 21 — use it. Combined with sealed types, the compiler enforces exhaustiveness.

```java
String shape(Object o) {
    return switch (o) {
        case Integer i when i < 0 -> "negative int";
        case Integer i            -> "int " + i;
        case String s             -> "string " + s;
        case null                 -> "null";
        default                   -> "unknown";
    };
}
```
