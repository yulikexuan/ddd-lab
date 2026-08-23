# Spring Boot Guidelines

Coding standards and best practices for this learning project on **Spring Boot 4.0.x** + **Spring Framework 7.0.x** + **Java 25**.

> **Baseline (verified against Spring Boot 4.0 release notes):** Spring Framework 7.0, Spring Security 7.0, Jackson 3.0, Hibernate 7.1, Jakarta EE 10 (Servlet 6.1, Validation 3.1).

## 1. Prefer Constructor Injection

- Declare mandatory dependencies as `final` fields and inject through the constructor.
- Spring auto-detects the single constructor — no `@Autowired` annotation needed.
- Avoid field/setter injection in production code.

## 2. Prefer package-private for Spring Components

Declare controllers, `@Configuration` classes, `@Bean` methods, and request-handling methods as package-private when possible. There is no obligation to make everything `public`.

## 3. Organize Configuration with Typed Properties

- Group app-specific properties under a common prefix in `application.properties` / `.yml`.
- Bind to `@ConfigurationProperties` records with `jakarta.validation` annotations — fail-fast on bad config.
- Prefer environment variables over profiles for environment-specific values.

```java
@ConfigurationProperties("app.payment")
@Validated
public record PaymentProps(
    @NotBlank String apiKey,
    @Positive int timeoutSeconds
) { }
```

## 4. Define Clear Transaction Boundaries

- Each service-layer method is a transactional unit.
- Annotate query-only methods with `@Transactional(readOnly = true)`.
- Annotate data-modifying methods with `@Transactional`.
- Keep the transactional scope as small as possible.

## 5. Disable Open Session in View

When using Spring Data JPA, set:

```properties
spring.jpa.open-in-view=false
```

## 6. Separate Web Layer from Persistence Layer

- Don't expose entities directly from controllers.
- Define explicit request/response `record` DTOs.
- Apply `jakarta.validation` annotations on request records.

## 7. REST API Design

- **Versioned URLs:** `/api/v{version}/resources` (e.g. `/api/v1/orders`).
- **Built-in API versioning (Spring Boot 4):** Configure via `spring.mvc.apiversion.*` or `spring.webflux.apiversion.*` properties — prefer this over hand-rolled URL/header parsing.
- **Consistent collection/sub-resource patterns:** `/posts`, `/posts/{slug}/comments`.
- **Explicit HTTP status codes:** Use `ResponseEntity<T>` to return correct status with body.
- **Pagination** for unbounded collections.
- **JSON payloads:** top-level structure is a JSON object (allow for future extension); use `camelCase` or `snake_case` consistently.

### 7.1. HTTP Service Clients (Spring Boot 4)

Spring Boot 4 auto-configures `@HttpExchange` interfaces. Prefer declarative HTTP clients over hand-rolled `RestTemplate`/`RestClient` calls:

```java
@HttpExchange("/api/v1/inventory")
public interface InventoryClient {
    @GetExchange("/{sku}")
    StockLevel getStock(@PathVariable String sku);
}
```

## 8. Use Command Objects for Business Operations

Wrap input data in purpose-built command records (`CreateOrderCommand`, `UpdateCustomerCommand`). Service methods accept the command.

## 9. Centralize Exception Handling

- One global `@RestControllerAdvice` (REST) or `@ControllerAdvice` (MVC) with `@ExceptionHandler` methods.
- Return consistent error responses. Use `ProblemDetail` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) — Spring Framework supports it natively.

```java
@ExceptionHandler(OrderNotFoundException.class)
ProblemDetail handleNotFound(OrderNotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
}
```

## 10. Actuator

Expose only essential endpoints publicly (`/health`, `/info`, `/metrics`). Secure all others.

```properties
management.endpoints.web.exposure.include=health,info,metrics
```

## 11. Internationalization

Externalize all user-facing text into `ResourceBundle`s. Never embed labels/prompts in code.

## 12. Use Testcontainers for Integration Tests

Spin up real databases, brokers, etc. via Testcontainers. Spring Boot's `@ServiceConnection` wires container-provided connection details into the context automatically.

```java
@Testcontainers
@SpringBootTest
class OrderRepoIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
}
```

## 13. Random Port for Integration Tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

## 14. Logging

- **Use SLF4J**, never `System.out.println` for application logging.
- **Protect sensitive data** — no credentials, PII, or tokens in log output.
- **Guard expensive log calls** — use parameterized templates or suppliers:

```java
log.atDebug()
    .setMessage("Detailed state: {}")
    .addArgument(() -> computeExpensiveDetails())
    .log();
```

- Use the new `logging.console.enabled` property (Spring Boot 4) to disable console logging in production where a file/JSON-only sink is desired.

## 15. Observability

Spring Boot 4 ships a dedicated `spring-boot-starter-opentelemetry` for OTLP metrics and traces. Use it instead of stitching together Micrometer + an OTLP exporter manually if your stack is OTel-native.

## 16. Bean Override in Tests

Spring Framework 7 / Spring Boot 4 lifts the singleton-only restriction — `@MockitoBean`, `@MockitoSpyBean`, and `@TestBean` now also work with prototype and custom-scoped beans.

```java
@MockitoBean
private PricingService pricing;   // works for any scope in SF7+
```

## 17. Testing Slices & Starters

Spring Boot 4 harmonizes starters so most technologies have both a runtime starter and a **test starter companion**:

- `spring-boot-starter-test` — general test support (JUnit Platform, AssertJ, Mockito, Testcontainers helpers).
- `spring-boot-webmvc-test` — slice-test support for `@WebMvcTest` controllers.
- Other domain-specific test starters follow the same `*-test` suffix pattern.

> **Note:** The artifact is `spring-boot-webmvc-test`, **not** `spring-boot-starter-webmvc-test`. Check Maven Central before assuming the name.

## 18. Code Style

- **Path comments:** Optional — only when files are deeply nested and the IDE doesn't show full path.
- **Null-safety:** Use `@NullMarked` (jspecify) on packages/classes that enforce null-safety; mark nullable values with `@Nullable`.
- **Lombok:** Use sparingly. Prefer records, `@RequiredArgsConstructor` and `@Slf4j` are acceptable; avoid `@Data` on domain objects.
- **Records:** Default to records for DTOs and domain models.
- **Java 25 features:** Use sealed types + exhaustive switch, scoped values, virtual threads. Don't ship preview features (`--enable-preview`) to production.

## 19. Unit Test Tooling

- **JUnit 5/6** (Jupiter).
- **Mockito (BDDMockito)** — prefer `BDDMockito.given(...)` / `then(...)` over plain `Mockito.when(...)` / `verify(...)`.
- **AssertJ** for assertions, not raw JUnit assertions.
- **`@MockitoBean`** for Spring slice/context tests (not the long-deprecated `@MockBean`).

See [java-test-guidelines.md](./java-test-guidelines.md) for the full test playbook.
