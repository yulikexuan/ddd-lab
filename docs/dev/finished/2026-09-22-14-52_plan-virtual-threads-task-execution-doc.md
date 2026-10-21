# Plan — Virtual-Thread Task Execution doc + wiring

## Goal

New `docs/virtual_thread_task_execution.md`: what `spring.threads.virtual.enabled: true` actually
changes in Boot 4.1.1, a sizing recipe for this repo, an observability section. Cross-reference it
from `event_listener_patterns.md` and fix the two places that doc is now wrong. Apply the safe
config subset to `application.yml`.

## Facts verified (sources read, not recalled)

| Claim                                                                                           | Source                                                                             |
|-------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| `simple.concurrency-limit` / `reject-tasks-when-limit-reached` feed the virtual-thread executor | `TaskExecutorConfigurations.SimpleAsyncTaskExecutorBuilderConfiguration#builder()` |
| `@Async` resolves to `applicationTaskExecutor`                                                  | `TaskExecutorConfigurations.ApplicationTaskExecutorAsyncConfigurer`                |
| Executor bean only auto-configured when no other `Executor` bean; `mode: force` overrides       | `TaskExecutorConfigurations.OnExecutorCondition`                                   |
| Throttle blocks the **submitting** thread on a `ReentrantLock` condition (no pinning)           | `ConcurrencyThrottleSupport#onLimitReached`, spring-core 7.0.9                     |
| `reject=true` ➡️ `TaskRejectedException` from `beforeAccess`                                    | `SimpleAsyncTaskExecutor.ConcurrencyThrottleAdapter`                               |
| Permit held for the task's whole lifetime                                                       | `SimpleAsyncTaskExecutor.TaskTrackingRunnable#run` finally                         |
| `pool.*` dead under virtual threads                                                             | `TaskExecutionProperties.Pool` javadoc                                             |
| `shutdown.await-termination` defaults `false`; gates `taskTerminationTimeout`                   | `TaskExecutionProperties.Shutdown`, builder                                        |
| Tomcat swaps to `VirtualThreadExecutor("tomcat-handler-")`                                      | `TomcatVirtualThreadsWebServerFactoryCustomizer`, `TomcatWebServerConfiguration`   |
| `taskScheduler` becomes `SimpleAsyncTaskScheduler`; `scheduling.pool.size` (default 1) dead     | `DefaultTaskSchedulerConfiguration`, `TaskSchedulingProperties`                    |
| **No** `executor.*` metrics — only `ThreadPoolTask*` are instrumented                           | `TaskExecutorMetricsAutoConfiguration#bindTaskExecutorsToRegistry`                 |
| `VirtualThreadMetrics` gated on `micrometer-java21`, absent from the pinned repo                | `JvmMetricsAutoConfiguration`, local repo listing                                  |
| Hikari defaults: `maximumPoolSize` 10, `connectionTimeout` 30s                                  | `HikariConfig` 7.0.2                                                               |
| Virtual threads need Java 21+ *and* the property                                                | `org.springframework.boot.thread.Threading`                                        |

## Step 1 — `docs/virtual_thread_task_execution.md`

Repo doc conventions: snake_case filename, `#` title, `---` rules, tables, `/// :~` terminator.

1. **One-screen answer** — one property, four bean swaps, one silent metrics loss.
2. **What the flag actually flips** — `Threading.VIRTUAL` gate; table of bean-by-bean swaps
   (`applicationTaskExecutor`, `taskScheduler`, Tomcat protocol handler, bootstrap executor alias).
3. **What goes dead** — `execution.pool.*`, `scheduling.pool.size`, `server.tomcat.threads.*`;
   why setting them produces no warning.
4. **The executor** — `SimpleAsyncTaskExecutor` mechanics: no queue/no pool, permit acquired on the
   *caller's* thread before the virtual thread exists, released in `finally`, `ReentrantLock` not
   `synchronized`. Blocking vs. `TaskRejectedException`.
5. **Sizing recipe** — the formula, worked for this repo (Hikari 10 default vs. `concurrency-limit`),
   what to set now vs. after `spring-modulith-starter-jdbc` lands.
6. **Shutdown** — `server.shutdown: graceful` drains HTTP only; async tasks abandoned unless
   `await-termination: true`.
7. **Traps** — `OnExecutorCondition`; `@Async` + `@ApplicationModuleListener` submitting inside the
   after-commit synchronization; `ThreadLocal`/MDC across the hop; `TaskDecorator` /
   `propagate-context` needing micrometer-context-propagation.
8. **Observability** — the metrics hole, `micrometer-java21`, JFR `jdk.VirtualThreadPinned` /
   `jdk.VirtualThreadSubmitFailed`, Hikari pool metrics as the real saturation signal.
9. **Recommended config** — full annotated block.
10. **Version notes** — artifacts + versions read, dated.

## Step 2 — edits to `docs/event_listener_patterns.md`

| Where                  | Change                                                                                                                                                        |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| §1 matrix, line 69     | `Blocks the publisher` cell for `@ApplicationModuleListener`: `no` ➡️ `no — until the throttle saturates`                                                     |
| §3.4 (lines 258–278)   | Corrected YAML (add `mode: force`, `shutdown.*`, Hikari); state *which* thread blocks; note `reject=true` is only safe with the registry; link to the new doc |
| §7 gotcha #9, line 537 | Repoint to the new doc                                                                                                                                        |
| §5.2 / §6 vicinity     | One line: rejection leaves the publication row incomplete ➡️ resubmittable                                                                                    |

No other section touched.

## Step 3 — `src/main/resources/application.yml`

```yaml
spring:
  task:
    execution:
      simple:
        concurrency-limit: 8          # ≤ pool, leaves headroom for web requests
      shutdown:
        await-termination: true
        await-termination-period: 30s
  datasource:
    hikari:
      maximum-pool-size: 20           # was implicit 10
```

Not applied: `mode: force` (no competing `Executor` bean today),
`reject-tasks-when-limit-reached` (unsafe before the registry exists).

## Verification

- Every property key checked against `spring-configuration-metadata.json` in the resolved jars —
  this proves binding without needing a live Postgres.
- `JAVA_HOME="C:/dev/java/jdk-25" mvn test-compile` stays green.
- No runtime assertion claimed: the app needs Postgres at `localhost:54316` + `DATASOURCE_PASSWORD`.

## Out of scope

`pom.xml` untouched — `spring-modulith-starter-jdbc` and `micrometer-java21` are recommended in the
doc, not added (first resolution must be online).
