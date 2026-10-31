# Architectural Patterns 

**Explore tactical design decisions in a broader context:** 
the different ways to orchestrate the interactions and dependencies between a system's components.

---

## Business Logic Versus Architectural Patterns

- Architectural Patterns introduce **organizational principles** for the different aspects of a codebase
- Architectural Patterns also present **clear boundaries** between the different aspects of a codebase: 
  - How the business logic is wired to the system's input, output, and other infrastructural components 
  - This affects how these components interact with each other: 
    - what knowledge they share and how the components reference each other.
- Choosing the appropriate way to organize the codebase, or the correct architectural pattern 
  - This is crucial to support implementation of the business logic in the short term and alleviate maintenance in the long term

---

## Layered Architecture

It's important to note that in the context of the architectural pattern, the service layer is a logical boundary. 
- It is not a physical service.

### Layers Versus Tiers 

**Layers and tiers are conceptually different:** 
- A layer is a logical boundary, whereas a tier is a physical boundary. 
- All layers in the layered architecture are bound by the same lifecycle: 
  - they are implemented, evolved, and deployed as one single unit. 
- On the other hand, a tier is an independently deployable service, server, or system.
  - Since each component can be deployed and managed independently of the rest, these are tiers and not layers.

--- 

## Ports & Adapters

The **Ports & Adapters** architecture addresses the shortcomings of the layered architecture 
and is a better fit for implementation of more complex business logic. 

--- 

### Terminology

#### Infrastructure Layer

Essentially, both the presentation layer and data access layer represent integration with external components: 
- databases, external services, and user interface frameworks 
- These technical implementation details do not reflect the system’s business logic
- Unify all such infrastructural concerns into a single "Infrastructure Layer"

#### Dependency Inversion Principle

- That high-level modules, which implement the business logic, should not depend on low-level modules
- Low-level modules depend on high-level modules (the business logic)
- Add an application layer between the infrastructure layer and the business logic layer
- infrastructure layer -> 