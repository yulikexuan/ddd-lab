# Virtual-Thread Task Execution

- `spring.threads.virtual.enabled: true`
- `applicationTaskExecutor` / `SimpleAsyncTaskExecutor`
- `spring.task.execution.simple.*`
- sizing against the connection pool

Companion to [event_listener_patterns.md](./event_listener_patterns.md)
- §3.4 there says *"bound the executor"*;
- this document says **which knobs still exist**, what they cost, and what number to put in them.

---

> The rule this whole document serves:
> - **Virtual threads remove the thread limit. They do not remove the limit** — they move it to the connection pool, where it is much less forgiving.

## 0. The One-Screen Answer

One property flips **four** beans and silently deletes **one** signal:

```
spring.threads.virtual.enabled: true
  ➡️ applicationTaskExecutor   ThreadPoolTaskExecutor   ➡️ SimpleAsyncTaskExecutor(virtual)
  ➡️ taskScheduler             ThreadPoolTaskScheduler  ➡️ SimpleAsyncTaskScheduler(virtual)
  ➡️ Tomcat protocol handler   thread pool              ➡️ VirtualThreadExecutor("tomcat-handler-")
  ➡️ bootstrapExecutor         (alias of the above)
  ⚠️ executor.* metrics        present                  ➡️ gone, with no warning
```

| You had                              | You now have | Bound by                                     |
|--------------------------------------|--------------|----------------------------------------------|
| `spring.task.execution.pool.*`       | **ignored**  | nothing                                      |
| `server.tomcat.threads.max`          | **ignored**  | nothing                                      |
| `spring.task.scheduling.pool.size`   | **ignored**  | nothing                                      |
| a queue that absorbed bursts         | no queue     | `simple.concurrency-limit`, unset by default |
| `executor.queued`, `executor.active` | no meters    | —                                            |

> The single most common production surprise here:
> - **the app starts fine, load-tests fine, and then a burst of events exhausts the Hikari pool.**
>   - Every task started. None of them could get a connection. ➡️ See §4.

---

## 1. What the Flag Actually Flips

The gate is `org.springframework.boot.thread.Threading`:

```java
VIRTUAL {
    public boolean isActive(Environment environment) {
        return environment.getProperty("spring.threads.virtual.enabled", boolean.class, false)
                && JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.TWENTY_ONE);
    }
}
```

Two conditions, **silently** ANDed.
- On a JDK below 21 the property is accepted and ignored — no warning, no failure, just platform threads.
- This repo is on JDK 25, so the flag is live.

Every bean swap is one `@ConditionalOnThreading(Threading.VIRTUAL)`:

| Bean                                | `PLATFORM`                         | `VIRTUAL`                                      | Declared in                                            |
|-------------------------------------|------------------------------------|------------------------------------------------|--------------------------------------------------------|
| `applicationTaskExecutor`           | `ThreadPoolTaskExecutor` (`@Lazy`) | `SimpleAsyncTaskExecutor`                      | `TaskExecutorConfigurations.TaskExecutorConfiguration` |
| `simpleAsyncTaskExecutorBuilder`    | plain                              | `.virtualThreads(true)`                        | `SimpleAsyncTaskExecutorBuilderConfiguration`          |
| `taskScheduler`                     | `ThreadPoolTaskScheduler`          | `SimpleAsyncTaskScheduler`                     | `DefaultTaskSchedulerConfiguration`                    |
| Tomcat's `ProtocolHandler` executor | Tomcat's pool                      | `new VirtualThreadExecutor("tomcat-handler-")` | `TomcatVirtualThreadsWebServerFactoryCustomizer`       |
| `bootstrapExecutor`                 | alias ➡️ `applicationTaskExecutor` | same alias                                     | `BootstrapExecutorConfiguration`                       |

And `@Async` reaches the first of those through `ApplicationTaskExecutorAsyncConfigurer`, which resolves the bean **by name**:

```java
public Executor getAsyncExecutor() {
    Executor executor = (this.delegate != null) ? this.delegate.getAsyncExecutor() : null;
    return (executor != null) ? executor : getApplicationTaskExecutor();   // by bean name
}
```

So the chain that matters for [event_listener_patterns.md](./event_listener_patterns.md) is:

```
@ApplicationModuleListener ➡️ @Async ➡️ applicationTaskExecutor ➡️ SimpleAsyncTaskExecutor(virtual)
                                                                   ➡️ governed by spring.task.execution.simple.*
```

---

## 2. What Goes Dead — and Never Says So

Binding still succeeds for all of these. Nothing reads them.

| Property                                     | Fate under `VIRTUAL`                                                 |
|----------------------------------------------|----------------------------------------------------------------------|
| `spring.task.execution.pool.core-size` (8)   | dead — no `ThreadPoolTaskExecutor` is built                          |
| `spring.task.execution.pool.max-size`        | dead                                                                 |
| `spring.task.execution.pool.queue-capacity`  | dead — **and there is no queue at all now**                          |
| `spring.task.execution.pool.keep-alive`      | dead                                                                 |
| `spring.task.scheduling.pool.size` (1)       | dead — scheduling becomes unbounded-concurrent instead of serialized |
| `server.tomcat.threads.max` (200)            | dead — `VirtualThreadExecutor` starts one thread per request         |
| `server.tomcat.threads.min-spare`            | dead                                                                 |

`TaskExecutionProperties.Pool` says so in its own javadoc — *"Doesn't have an effect if virtual threads are enabled"* — which is the only place it is written down.
- The builder for these properties is still created (`ThreadPoolTaskExecutorBuilderConfiguration` is unconditional);
- it is simply never used to build the `applicationTaskExecutor` bean.

⚠️ `spring.task.scheduling.pool.size` deserves its own line.
- It defaults to **1**, so on platform threads your `@Scheduled` methods are implicitly serialized.
- Flip to virtual and `SimpleAsyncTaskScheduler` runs them **concurrently and unbounded**.
  - A `@Scheduled` job that was safe only because it could never overlap itself is now a race.
  - Bound it with `spring.task.scheduling.simple.concurrency-limit` — a *different* property from the execution one.

---

## 3. The Executor — `SimpleAsyncTaskExecutor`

`org.springframework.core.task.SimpleAsyncTaskExecutor`. Not a pool. Not an `ExecutorService`.

```java
protected void doExecute(Runnable task) {
    newThread(task).start();          // a brand-new virtual thread, every time
}
```

### 3.1 The only bound that exists

```yaml
spring:
  task:
    execution:
      simple:
        concurrency-limit: 8                     # unset ➡️ UNBOUNDED_CONCURRENCY (-1)
        reject-tasks-when-limit-reached: false   # the default
```

Both are read in `TaskExecutorConfigurations.SimpleAsyncTaskExecutorBuilderConfiguration#builder()` and applied to the virtual-threads builder, so they genuinely reach the executor `@Async` uses.

`concurrencyLimit` is a `@Nullable Integer`:
- leave it out and the builder never calls the setter,
- so the executor keeps `ConcurrencyThrottleSupport.UNBOUNDED_CONCURRENCY`.

### 3.2 A permit is not a thread — and it is taken on the *caller's* thread

This is the part that decides whether bounding is safe, and it is easy to get backwards.

```java
public void execute(Runnable task, long startTimeout) {
    …
    if (isThrottleActive() && startTimeout > TIMEOUT_IMMEDIATE) {
        this.concurrencyThrottle.beforeAccess();                 // ⬅️ on the SUBMITTING thread
        doExecute(new TaskTrackingRunnable(taskToUse, future));  // ⬅️ virtual thread created only now
    }
    …
}
```

`beforeAccess()` runs **before** the virtual thread exists, on whatever thread called `publishEvent` / the `@Async` proxy. If the limit is reached:

```java
protected void onLimitReached() {           // ConcurrencyThrottleSupport
    while (this.concurrencyCount >= this.concurrencyLimit) {
        this.concurrencyCondition.await();  // ReentrantLock condition — the CALLER parks here
    }
}
```

The permit is released in a `finally`, when the **task body finishes** (`SimpleAsyncTaskExecutor.TaskTrackingRunnable#run`) — not when the thread starts.
- So a permit is held for the task's entire duration, which is what makes it directly comparable to a connection.

Three consequences:

1. **The submitter blocks, and the submitter is the publisher.**
   - For `@ApplicationModuleListener` the submit happens inside the `AFTER_COMMIT` synchronization, on the publisher's thread, before its `@Transactional` method returns.
   - A saturated throttle therefore pushes back into the publisher's commit path.
   - This is the trade bounding buys: unbounded fan-out ➡️ publisher latency. It is the right trade, but it does qualify the `Blocks the publisher: no` cell in [event_listener_patterns.md](./event_listener_patterns.md) §1.
2. **No carrier-thread pinning.**
   - The throttle uses a `ReentrantLock` ➕ `Condition`, not `synchronized` ➕ `wait()`.
   - A blocked submitter parks its virtual thread and releases the carrier, on any JDK — this does not depend on JEP 491.
3. **It is a gate, not a queue.**
   - There is no backlog, no `queue-capacity`, and nothing to inspect.
   - Blocked submitters are invisible except as latency.

### 3.3 `reject-tasks-when-limit-reached: true` — only with the registry

```java
protected void onLimitReached() {                       // ConcurrencyThrottleAdapter
    if (rejectTasksWhenLimitReached) {
        onAccessRejected("Concurrency limit reached: " + getConcurrencyLimit());  // ➡️ TaskRejectedException
    }
    super.onLimitReached();
}
```

Follow where that exception goes.
- The submit is inside `TransactionSynchronization.afterCompletion`,
- and `TransactionSynchronizationUtils.invokeAfterCompletion` catches `Throwable` and logs it.

So:

| Classpath                                   | `reject = true` means                                                        |
|---------------------------------------------|------------------------------------------------------------------------------|
| `spring-modulith-starter-core` only (today) | the event is **dropped**, one `ERROR` line, no other trace                   |
| ➕ `spring-modulith-starter-jdbc`           | the publication row is never completed ➡️ still `PUBLISHED` ➡️ resubmittable |

Keep it `false` until the events starter is on the classpath.
- Blocking a publisher is recoverable; losing the event is not.

---

## 4. Sizing — the Recipe

### 4.1 The formula

Each `@ApplicationModuleListener` task holds one throttle permit for its whole life, and — via `REQUIRES_NEW` — one pooled connection for most of it. Permits and connections are therefore the same currency:

```
spring.task.execution.simple.concurrency-limit
      ≤  spring.datasource.hikari.maximum-pool-size
         −  peak concurrent web requests that need a connection
         −  headroom for @Scheduled / actuator / liveness probes
```

Size against the **pool**, never against CPU count.
- CPU count governed the old `ThreadPoolTaskExecutor`; it governs nothing here.
- These tasks are I/O-bound, and virtual threads are cheap precisely so that CPU count stops being the constraint.

### 4.2 This repo, before and after

`application.yml` configures no pool size, so:

| Setting                     | Before                    | After | Why                                                 |
|-----------------------------|---------------------------|-------|-----------------------------------------------------|
| `hikari.maximum-pool-size`  | **10** (HikariCP default) | 20    | explicit beats implicit; leaves room for both users |
| `hikari.connection-timeout` | **30 s** (default)        | 30 s  | unchanged — see below                               |
| `simple.concurrency-limit`  | **unbounded**             | 8     | ≤ 20, leaves 12 for web requests                    |

What the "before" column actually does under a burst of 32 events:
- 32 virtual threads start,
- 10 get connections,
- **22 block in `HikariPool.getConnection()` for 30 seconds** and then fail with `SQLTransientConnectionException`.

Nothing in the executor pushed back — the failure surfaces in the pool, attributed to whichever listener happened to ask last.

That is also why `concurrency-limit: 32` — the number in the current §3.4 of [event_listener_patterns.md](./event_listener_patterns.md) — is worse than useless against a pool of 10.
- It reads like a bound and reproduces the unbounded behaviour exactly.

### 4.3 Revisit it when `starter-jdbc` lands

Adding `spring-modulith-starter-jdbc` puts the publication registry on the same datasource:
- one extra write inside the publisher's transaction,
- plus one completion update per listener.

Those use the same pool. Re-check the arithmetic then rather than assuming the same numbers hold.

---

## 5. Shutdown — `server.shutdown: graceful` Does Not Cover This

```yaml
server:
  shutdown: graceful          # drains in-flight HTTP requests
```

That drains **Tomcat**. It says nothing about tasks already running on `applicationTaskExecutor`. Those are governed separately, and the default abandons them:

```java
TaskExecutionProperties.Shutdown shutdown = this.properties.getShutdown();
if (shutdown.isAwaitTermination()) {                       // ⬅️ defaults to FALSE
    builder = builder.taskTerminationTimeout(shutdown.getAwaitTerminationPeriod());
}
```

`awaitTermination` is `false` out of the box, so the builder never sets a termination timeout and context close does not wait.
- An `@ApplicationModuleListener` mid-transaction at deploy time is simply cut off.
- With the registry its row stays incomplete and is resubmittable; without it, the work is gone.

```yaml
spring:
  task:
    execution:
      shutdown:
        await-termination: true
        await-termination-period: 30s
```

Set the period shorter than your orchestrator's termination grace period, or the pod is killed mid-drain and you have achieved nothing.

---

## 6. Traps

| # | Trap                                                                                                                                                                                                                                                                                                           |
|---|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | **Any `Executor` bean disables the whole thing.** `OnExecutorCondition` is `@ConditionalOnMissingBean(Executor.class)` OR `spring.task.execution.mode=force`. Declare one `Executor` anywhere and `applicationTaskExecutor` is never created — every property in this document silently stops applying.        |
| 2 | **`mode: force` is the escape hatch**, not a qualifier on your own bean. `Mode.AUTO` (default) ➡️ create only if absent; `Mode.FORCE` ➡️ create regardless.                                                                                                                                                    |
| 3 | **JDK < 21 ignores the flag**, quietly (§1). Also true for a build that compiles on 25 and runs on an older JRE.                                                                                                                                                                                               |
| 4 | **`@Scheduled` loses its implicit serialization** (§2). The `scheduling` limit is a separate property from the `execution` one.                                                                                                                                                                                |
| 5 | **`ThreadLocal`, MDC, `SecurityContext` and request scope do not cross the hop.** Install a `TaskDecorator`, or set `spring.task.execution.propagate-context: true` — which requires Micrometer Context Propagation on the classpath (`@ConditionalOnClass(ContextSnapshot.class)`) and is a no-op without it. |
| 6 | **Thread names are not free diagnostics.** Default prefix is `task-`; Tomcat's is `tomcat-handler-`. A virtual thread has no pool identity to group by.                                                                                                                                                        |
| 7 | **`concurrency-limit: 0` is not "unbounded".** `NO_CONCURRENCY = 0` rejects every task; `-1` is `UNBOUNDED_CONCURRENCY`. Off-by-one here fails closed, loudly, at the first event.                                                                                                                             |
| 8 | **Bounding is per-executor, not per-listener.** One limit is shared by every `@Async` method, every `@ApplicationModuleListener`, and anything else that injects the default executor.                                                                                                                         |

---

## 7. Observability — the Hole This Opens

### 7.1 `executor.*` metrics disappear

`TaskExecutorMetricsAutoConfiguration` instruments **only** thread-pool types:

```java
if (executor instanceof ThreadPoolTaskExecutor threadPoolTaskExecutor) {
    monitor(registry, safeGetThreadPoolExecutor(threadPoolTaskExecutor), beanName);
}
else if (executor instanceof ThreadPoolTaskScheduler threadPoolTaskScheduler) {
    monitor(registry, safeGetThreadPoolExecutor(threadPoolTaskScheduler), beanName);
}
```

`SimpleAsyncTaskExecutor` matches neither branch — and it is not an `ExecutorService`, so `ExecutorServiceMetrics` could not bind it anyway.

Turning on virtual threads therefore **removes `executor.active`, `executor.queued`, `executor.pool.size` and friends**, with no warning and no replacement.
- If a dashboard or alert reads them, it goes flat rather than red.

### 7.2 What to watch instead

| Signal                                 | Source                                                              | Tells you                                                    |
|----------------------------------------|---------------------------------------------------------------------|--------------------------------------------------------------|
| `hikaricp.connections.pending`         | `DataSourcePoolMetricsAutoConfiguration` (already on the classpath) | **the real saturation signal** — tasks queued on connections |
| `hikaricp.connections.acquire` (timer) | same                                                                | how long the wait is                                         |
| `hikaricp.connections.timeout`         | same                                                                | tasks that gave up after 30 s                                |
| `jdk.VirtualThreadSubmitFailed` (JFR)  | JDK                                                                 | submission failures                                          |
| `jdk.VirtualThreadPinned` (JFR)        | JDK, `threshold: 20 ms` in `default.jfc`                            | carrier pinning, if any survives                             |
| `jdk.VirtualThreadStart` / `End` (JFR) | JDK                                                                 | actual fan-out — the count the executor no longer reports    |

All four JFR events exist on this repo's JDK (verified on 25.0.3).

### 7.3 Getting virtual-thread meters back

Boot will auto-configure a virtual-thread binder, but only if the optional artifact is present:

```java
private static final String VIRTUAL_THREAD_METRICS_CLASS =
        "io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics";

@ConditionalOnClass(name = VIRTUAL_THREAD_METRICS_CLASS)
static class VirtualThreadMetricsConfiguration { … }
```

`io.micrometer:micrometer-java21` is **not** in this repo's pinned local repository, so the binder is absent today.
- Adding it requires an online build (see [CLAUDE.md](../CLAUDE.md) on the pinned repo).
- Until then, Hikari's `pending` gauge is the only saturation signal you have — which is another argument for sizing the limit *below* the pool, where saturation is at least visible.

---

## 8. Recommended Configuration

```yaml
spring:
  threads:
    virtual:
      enabled: true                            # requires JDK 21+; silently ignored below that

  task:
    execution:
      simple:
        concurrency-limit: 8                   # ≤ pool size − web headroom (§4.1); unset ➡️ unbounded
        reject-tasks-when-limit-reached: false # true only once the publication registry exists (§3.3)
      shutdown:
        await-termination: true                # default false ➡️ in-flight listeners abandoned (§5)
        await-termination-period: 30s          # < the orchestrator's grace period
    scheduling:
      simple:
        concurrency-limit: 4                   # @Scheduled is no longer serialized (§2)

  datasource:
    hikari:
      maximum-pool-size: 20                    # was an implicit 10
```

Deliberately **not** set:

- `spring.task.execution.mode: force` — no competing `Executor` bean exists in this repo. Add it the day one does (§6, trap 1), not before.
- `spring.task.execution.propagate-context` — Micrometer Context Propagation is not on the classpath, so it would bind and do nothing.

---

##### Version Notes

Verified on **2026-09-22** by reading the resolved artifacts in this repo's pinned local repository (`C:/Users/yul/.m2/spring/repository`), not documentation summaries. Every property name in this document was cross-checked against the `spring-configuration-metadata.json` shipped in those jars.

- `TaskExecutorConfigurations`, `TaskExecutionProperties`, `TaskSchedulingConfigurations`, `DefaultTaskSchedulerConfiguration` — `spring-boot-autoconfigure:4.1.1`
- `Threading` — `spring-boot:4.1.1`
- `TomcatVirtualThreadsWebServerFactoryCustomizer`, `TomcatWebServerConfiguration` — `spring-boot-tomcat:4.1.1`
- `TaskExecutorMetricsAutoConfiguration`, `JvmMetricsAutoConfiguration` — `spring-boot-micrometer-metrics:4.1.1`
- `SimpleAsyncTaskExecutor`, `ConcurrencyThrottleSupport` — `spring-core:7.0.9`
- `TransactionSynchronizationUtils` — `spring-tx:7.0.9`
- `HikariConfig` (`DEFAULT_POOL_SIZE = 10`, `CONNECTION_TIMEOUT = 30 s`) — `HikariCP:7.0.2`
- JFR event names and the `20 ms` pinning threshold — `C:/dev/java/jdk-25/lib/jfr/default.jfc`, JDK 25.0.3

Not verified by running the application: this repo's runtime needs Postgres at `localhost:54316` and `DATASOURCE_PASSWORD`, and has no test datasource configuration yet.

/// :~
