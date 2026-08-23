# Java Test Guidelines

## Tech Stack

- **Java:** 25 LTS
- **Spring Boot:** 4.0.x (Spring Framework 7.0.x)
- **JUnit Jupiter:** 6.x
- **Mockito:** 5.23.x (with BDDMockito)
- **AssertJ:** 4.x
- **Testcontainers:** latest
- **json-path:** 2.9.x

---

## 1. Method-Parameter Injection

Prefer parameter injection over static helpers, instance fields, or `@BeforeEach`-only setup.

- **Built-in resolvers:**
  - `TestInfo` — display name, tags, test class/method metadata.
  - `TestReporter` — publish key-value entries to the test report.
  - `RepetitionInfo` — only inside `@RepeatedTest` bodies.
  - `@TempDir Path` / `@TempDir File` — auto-created, auto-cleaned scratch directory.
- **Spring:** `SpringExtension` (transitively activated by `@WebMvcTest`, `@DataJdbcTest`, `@SpringBootTest`) injects beans straight into `@Test`-method parameters — prefer this over field `@Autowired` for one-off collaborators.
- **Custom `ParameterResolver`:** write one only when it removes setup duplication across **many** tests.

```java
@Test
void writes_invoice(@TempDir Path dir, TestInfo info) {
    // 'dir' is unique per test method; cleaned up after the test
    // 'info' carries the display name for log lines
}
```

---

## 2. Parameterized Tests

Prefer `@ParameterizedTest` over hand-rolled loops, `@RepeatedTest`, or copy-paste cases.

- **Sources:** `@ValueSource`, `@CsvSource` / `@CsvFileSource`, `@EnumSource`, `@FieldSource`, `@MethodSource`.
- **Named cases:** `Arguments.argumentSet("label", a, b)` (or `Named.of("label", value)`) so failure output reads `[1] rejects empty UPC` instead of `[1] "", BeerStyle.IPA`.
- **`@ParameterizedClass`** (JUnit 6 / 5.13+): parameterize an entire test class when many methods share the same inputs.
- **Non-static `@MethodSource`** is legal **only** under `@TestInstance(Lifecycle.PER_CLASS)`. Default `PER_METHOD` requires static factories.

```java
@ParameterizedTest(name = "{0}")
@MethodSource
void rejects_invalid_upc(String label, String upc) {
    assertThatThrownBy(() -> Beer.of(upc)).isInstanceOf(IllegalArgumentException.class);
}

static Stream<Arguments> rejects_invalid_upc() {
    return Stream.of(
        argumentSet("empty",        ""),
        argumentSet("blank",        "   "),
        argumentSet("non-numeric",  "abc"));
}
```

---

## 3. `@Nested` for Scenario Grouping

Use `@Nested` inner classes to group tests that share **fixture** or **scenario context** — not to mirror the SUT's package structure.

- Group by precondition: `WhenBeerExists`, `WhenBeerMissing`, `GivenInvalidPayload`.
- Apply `@DisplayName` per nest so the report reads like prose.
- **Hard cap: depth ≤ 2.** Deeper nesting hides setup.
- Inner class is non-static; it sees the outer class's `@BeforeEach` fixture.

```java
@DisplayName("BeerService")
class BeerServiceTest {

    @Nested
    @DisplayName("when the beer exists")
    class WhenBeerExists {
        @Test void returns_the_beer() { /* ... */ }
        @Test void increments_version_on_update() { /* ... */ }
    }

    @Nested
    @DisplayName("when the beer is missing")
    class WhenBeerMissing {
        @Test void throws_NotFoundException() { /* ... */ }
    }
}
```

---

## 4. Lifecycle — `@TestInstance`

- **Default `PER_METHOD`** is right almost always. Switch to `@TestInstance(Lifecycle.PER_CLASS)` **only** for:
  - Non-static `@BeforeAll` / `@AfterAll`.
  - Non-static `@MethodSource` / `@FieldSource` factories.
- Prefer `@BeforeEach` + local variables over shared mutable fields.
- Never mutate static state from a test; if you must, restore it in `@AfterEach`.

---

## 5. Assertions

This project standardizes on **AssertJ** for assertion style.

- **`assertAll(...)`** — surface every failure of one logical outcome in a single run; useful for asserting multiple fields of one returned object. Don't use it as a catch-all wrapper around an entire test method.
- **`assertThrows(...)`** returns the thrown exception; assert the message/cause via AssertJ for richer diff:

```java
IllegalArgumentException ex = assertThrows(
    IllegalArgumentException.class,
    () -> Beer.of(""));
assertThat(ex).hasMessageContaining("upc must not be blank");
```

- Prefer AssertJ `assertThat` everywhere else. Reserve raw `assertEquals` for trivial primitive checks.

---

## 6. Mockito — BDD Style

- Use **BDDMockito** (`given(...).willReturn(...)`, `then(...).should()`) over plain Mockito (`when`/`verify`).
- Prefer `@Captor` field over inline `ArgumentCaptor.forClass(...)` — generics are cleaner, less casting.
- Pair captor with BDD: `then(repo).should().save(captor.capture()); assertThat(captor.getValue())...`.
- Don't over-capture: when `eq(...)` suffices, use it instead of capture-then-assert.
- Avoid `mockStatic` — if you need it, the SUT design is the smell. Refactor first.

---

## 7. Display Names

- `@DisplayName` + `@DisplayNameGeneration(ReplaceUnderscores.class)` on every test class.
- Method names use snake-style underscores; the generator converts to readable display names automatically.
- Do **not** mix free-form per-method `@DisplayName` annotations on top of `ReplaceUnderscores` unless the underscored form is genuinely ambiguous.

---

## 8. Spring Boot 4 Slice Tests

- `@WebMvcTest` for MVC controller slices. Backed by `spring-boot-webmvc-test` in SB4.
- `@DataJdbcTest` for Spring Data JDBC slices.
- `@DataJpaTest` for Spring Data JPA slices.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` for full-stack integration tests.

### 8.1. Bean Overrides

- Use `@MockitoBean` (not the deprecated `@MockBean`) to replace a bean with a Mockito mock.
- Use `@MockitoSpyBean` to wrap an existing bean.
- Use `@TestBean` to supply a programmatic replacement via a static factory method.
- All three work with prototype/custom-scoped beans in Spring Framework 7+.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService orderService;

    @Test
    void returns_404_when_missing() throws Exception {
        given(orderService.find("X")).willReturn(Optional.empty());
        mvc.perform(get("/api/v1/orders/X"))
           .andExpect(status().isNotFound());
    }
}
```

### 8.2. RestTestClient (Spring Boot 4)

`RestTestClient` is the new test-time HTTP client supported by `@SpringBootTest`. Prefer it for end-to-end controller tests where you want a real HTTP call without dragging in `WebTestClient` reactive types.

---

## 9. Testcontainers + `@ServiceConnection`

```java
@SpringBootTest
@Testcontainers
class OrderRepoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired OrderRepo repo;

    @Test
    void persists_and_loads() {
        // ...
    }
}
```

`@ServiceConnection` removes the need to wire `spring.datasource.*` properties — Spring Boot reads connection details directly from the container.

---

## 10. Async / Concurrency Assertions

- Use **Awaitility** (`await().atMost(2, SECONDS).until(future::isDone)`) for async assertions, not `Thread.sleep`.
- For virtual-thread-based code, prefer Awaitility over hand-rolled polling loops — it integrates cleanly with `@Timeout`.

---

## 11. Other Annotations Worth Reaching For

| Annotation                                       | Use it for                                                                                                  |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `@TempDir`                                       | File I/O tests — auto-cleanup beats manual tearDown.                                                        |
| `@TestFactory`                                   | Runtime-generated dynamic tests when parameterized sources can't express the cases.                         |
| `@EnabledIfEnvironmentVariable` / `@EnabledOnOs` | Environment-driven skips — preferred over `Assumptions.assumeTrue`.                                         |
| `@Disabled`                                      | Allowed only with a linked ticket / reason in the annotation `value`. No orphan disables.                   |
| `@Timeout`                                       | Guard against tests that block forever; 1 s is generally too tight, 10 s is generally enough.               |

---

## 12. What NOT to Do

- Don't use `@MockBean` — deprecated. Use `@MockitoBean`.
- Don't use `Mockito.when(...)`. Use `BDDMockito.given(...)`.
- Don't `Thread.sleep` in tests.
- Don't mock the database in integration tests — use Testcontainers.
- Don't use `--enable-preview` features in production tests. Preview Java 25 features (Structured Concurrency, Stable Values, primitive patterns) are fine for spike tests, not for CI gates.
