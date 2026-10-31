# Learning jMolecules

## Resources
- [Architecturally evident code with jMolecules](https://odrotbohm.github.io/2021/02/Architecturally-evident-code-with-jMolecules) — Oliver Drotbohm's blog post
    - Written by the library's creator
    - Explains why jMolecules exists and how it expresses DDD concepts in code, through annotations or interfaces
- [Baeldung: DDD with jMolecules](https://www.baeldung.com/java-jmolecules-domain-driven-design)
    - Hands-on walkthrough of value objects, entities, aggregates, plus the JPA and Spring integrations
    - ⚠️ May target older versions; check its dependencies against this repo's
- [odrotbohm/tactical-ddd-workshop](https://github.com/odrotbohm/tactical-ddd-workshop)
    - Workshop code from the same author, built on Spring Boot and jMolecules
    - Closest match to what this lab builds
- Talks & Slides
    - [Spring I/O 2023: Architecturally evident Spring applications with jMolecules](https://2023.springio.net/sessions/architecturally-evident-spring-applications-with-jmolecules/)
    - [Architecturally evident Java applications with jMolecules (Speakerdeck slides)](https://speakerdeck.com/olivergierke/architecturally-evident-java-applications-with-jmolecules)
    - [JUG Zurich 2026: Implementing DDD made easy with Spring and jMolecules (PDF slides)](https://www.jug.ch/events/slides/260114_Implementing-DDD-made-easy-with-Spring-and-jMolecules.pdf) — the most recent
- Reference
    - [xmolecules/jmolecules](https://github.com/xmolecules/jmolecules) README
        - Covers the Spring Modulith and ArchUnit integrations this repo already uses
    - [Spring Modulith reference docs](https://docs.spring.io/spring-modulith/reference/)

### The Best Order of Learning for This Lab

[`Drotbohm blog post`](https://odrotbohm.github.io/2021/02/Architecturally-evident-code-with-jMolecules) ➡️ [`tactical-ddd-workshop`](https://github.com/odrotbohm/tactical-ddd-workshop) + [`Spring Modulith reference docs`](https://docs.spring.io/spring-modulith/reference/) ➡️ [`Baeldung`](https://www.baeldung.com/java-jmolecules-domain-driven-design) / `Talks & Slides` (above) as needed

---

## Caveat

None of these articles or talks has been checked against this repo's versions (`jmolecules-bom` 2025.0.2, Spring Boot 4.1.2, Spring Modulith 2.1.2). 

Before copying any setup, check its dependencies against the current versions.
