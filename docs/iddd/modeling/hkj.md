# Learning Functional Optics of Higher-Kinded-J

## Resources
- [The Official Interactive Tutorials](https://higher-kinded-j.github.io/)
    - [Optics](https://higher-kinded-j.github.io/latest/optics/ch_intro.html)
        - [The Focus DSL in Depth](https://higher-kinded-j.github.io/latest/optics/ch4_intro.html)
        - [Testing With hkj-test](https://higher-kinded-j.github.io/latest/tooling/test_assertions.html)
- The Scott Logic "Functional Optics" Masterclass Series
    - [Part 1: The Immutability Gap](https://blog.scottlogic.com/2026/01/09/java-the-immutability-gap.html)
    - [Part 2: Optics Fundamentals](https://blog.scottlogic.com/2026/01/16/optics-fundamentals.html)
    - [Part 6: From Theory to Practice](https://blog.scottlogic.com/2026/02/12/mfj-from-theory-to-practice.html)

### The Best Order of Learning for DDD

Digesting the material in the specific sequence below to avoid hitting an abstraction wall 👇

`Scott Logic Part 1` ➡️ `Official Quickstart - Optics` ➡️ `Focus DSL Journey` ➡️ `Scott Logic Part 2 (Traversals)`

---

## Why hkj-test is crucial for DDD Value Objects

When writing tests for your Domain-Driven Design side project, hkj-test provides two exceptional features that complement an immutable domain model:

- Custom AssertJ Helpers: It exposes native AssertJ matchers specifically designed for Higher-Kinded-J's functional types. 
  - Instead of executing clunky unwrapping assertions on monadic domain results, you can use highly semantic fluent assertions like assertThatEither(result).isRight().hasRight(validatedValueObject);.
- Optic Law Verification: 
  - In pure Domain-Driven Design, modifications to your structures must obey strict identity and composition laws. 
  - The hkj-test artifact ships built-in test harnesses to guarantee that the lenses and traversals generated over your Value Objects are legally sound and completely safe from side effects.
