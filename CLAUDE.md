# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

For comprehensive development guidelines,
please refer to [java-guidelines.md](./docs/guidelines/java-guidelines.md), [java-test-guidelines.md](./docs/guidelines/java-test-guidelines.md), and [spring-boot-guidelines.md](./docs/guidelines/spring-boot-guidelines.md).

Please **Keep your replies extremely concise** and **Focus on conveying the key information.**
**No unnecessary fluff, no long code snippets.**

When working, you can use web search or `context7` mcp to find the relevant documentation if you want to.

Whenever working with any third-party library or something similar, you MUST look up the official documentation to ensure that you are working with up-to-date information.
Use the `DocsExplorer` subagent for efficient documentation lookup.

---

## What this is

`guru:ddd-lab` — "The Lab of Domain-Driven Design": a greenfield Spring Boot 4.1.1 / Spring Modulith 2.1.0 sandbox on Java 25. Currently a skeleton (one module `helpdesk`, one record `RGB`); most value here is in the conventions, not the code volume.

## Build & test

No Maven wrapper — use the system `mvn` (3.9.x). **Requires JDK 25**; the machine default `JAVA_HOME` is JDK 21, so override per invocation:

```bash
JAVA_HOME="C:/dev/java/jdk-25" mvn test-compile
```

| Task                                        | Command                                                                     |
|---------------------------------------------|-----------------------------------------------------------------------------|
| Compile                                     | `mvn compile`                                                               |
| Unit tests (Surefire)                       | `mvn test`                                                                  |
| Single test class                           | `mvn test -Dtest=ModuleStructureTest`                                       |
| Single method                               | `mvn test -Dtest='ModuleStructureTest#ddd_Modules_Form_A_Valid_Structure*'` |
| Integration tests (`**/*IT.java`, Failsafe) | `mvn verify`                                                                |
| Run app                                     | `mvn spring-boot:run`                                                       |

- `.mvn/maven.config` pins `-Dmaven.repo.local=C:/Users/yul/.m2/spring/repository` — a repo separate from the default `~/.m2/repository`. Deps resolve through the Tecsys Artifactory mirror in `~/.m2/settings.xml`; `mvn -o` will fail for anything not already cached there. First online build is slow (>10 min).
- Surefire is configured with `-Xshare:off` plus an explicit `-javaagent` for mockito-core resolved out of `${settings.localRepository}` — changing the local repo path or the mockito version breaks the argLine.
- Compiler runs `fork=true` with `-J--sun-misc-unsafe-memory-access=allow`.

## Architecture

### Bootstrap chain (non-standard — do not "fix" it)

`DomainDrivenDesignApplication` deliberately avoids `@SpringBootApplication`. It is `@SpringBootConfiguration + @EnableAutoConfiguration + @Modulithic(systemName = "ddd") + @EnableAsync`, and scans **only** `guru.ddd.config`. Beans are reached through an explicit two-hop scan:

```
DomainDrivenDesignApplication  →  scans guru.ddd.config
  DomainDrivenDesignCfg        →  scans guru.ddd.helpdesk.config
    HelpdeskCfg                →  (module-local beans)
```

**Adding a module** means: create `guru.ddd.<module>/config/<Module>Cfg.java` (package-private class, like the existing ones), add its config package to `DomainDrivenDesignCfg`'s `@ComponentScan`, and update the expected module list in `ModuleStructureTest`. Skipping any of the three leaves the module invisible or the build red.

### Module layout

`guru.ddd.<module>.{config,domain.model,...}` — Modulith treats each direct child package of `guru.ddd` as an application module. `guru.ddd.config` is explicitly excluded from module detection (passed as the ignored-package predicate to `ApplicationModules.of` in `ModuleStructureTest`), so it acts as the composition root rather than a module.

Cross-module access must go through `@NamedInterface`-exposed packages; `modules.verify()` enforces this and rejects cycles.

### Persistence

Spring Data **JDBC** (not JPA). Runtime Postgres at `localhost:54316`, password from env `DATASOURCE_PASSWORD`, `spring.sql.init.mode=always`. Tests use Zonky embedded Postgres (`embedded-database-spring-test` + `embedded-postgres-binaries` 18.4.0) — there is no `src/test/resources` yet, so any test datasource config must be added.

Virtual threads are on; server shutdown is graceful.

### Annotation processors

Order in `maven-compiler-plugin` is load-bearing: lombok → lombok-mapstruct-binding → `hkj-processor-plugins` → mapstruct-processor. `hkj-spring-boot-starter` (higher-kinded-j, 0.4.9) generates code at compile time — after touching annotated types, recompile rather than trusting stale `target/generated-sources`.

## Known gaps in the test suite

- `DomainRules` declares an `@ArchTest` rule but **no class in the project carries `@AnalyzeClasses`**, so the rule never executes. Its packages also still reference `com.tecsys.elite.coordinator.*` from a different project. Wire it up (or repoint it at `guru.ddd..`) before relying on it.
- `DomainDrivenDesignApplicationIT` is `@Disabled` and empty.

## Code conventions in this repo

- Every file opens with `//: <fully.qualified.Name>.java` and closes with `/// :~`.
- Config classes are package-private.
- Tests use `@DisplayName("... - ")` + `@DisplayNameGeneration(ReplaceUnderscores.class)`, method names in `Snake_Case_Sentences`, and Given/When/Then comment blocks. AssertJ for assertions.
