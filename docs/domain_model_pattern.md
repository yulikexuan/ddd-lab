# The Domain-Model Pattern

The pattern is Domain-Model, and the aggregates and value objects are its building blocks.

## Domain-Model

The domain-model pattern is intended to cope with cases of complex business logic.

Developers deal with rules that have to be protected at all times:
1. complicated state transitions
2. business rules
3. invariants

> invariant: /ɪnˈveriənt/ always the same; never changing.

> Attempting to implement complex business logic using `active record objects` (CRUD) will make it easy to duplicate the logic and corrupt the system's state by misimplementing some of the business rules. 

---

### Implementation 

A domain-model is an object-model of the domain that incorporates both behavior and data.

Domain-Driven Design’s tactical patterns are the Building Blocks of such an object-model
- Aggregates 
- Value Objects 
- Domain Events 

All of these patterns share a common theme 
- They put the business logic first 

How the Domain-Model addresses different design concerns ??? 👇

#### Complexity

The objects used for modeling the domain's business logic should not introduce any additional accidental complexities 
- The domain’s business logic is already inherently complex 

The model should be devoid of any infrastructural or technological concerns 
- such as implementing calls to databases 
- or other external components of the system
- only emphasize on business logic instead of technical concerns

This restriction requires the model's objects to be plain old objects 

> Plain Old Objects: objects implementing business logic without relying on or directly incorporating any infrastructural components or frameworks 

#### Ubiquitous Language

Emphasizing on business logic instead of technical concerns makes it easier for the domain model's objects to follow the terminology of the bounded context's ubiquitous language. 

The Pattern of Domain-Model allows the code 
- to Speak the Ubiquitous Language 
- to Follow the Domain Experts' Mental Models 

---

### Building Blocks 

#### VALUE OBJECTS 

**The Concept of Value Object:** 
1. A value object is an object that can be identified by the composition of its values 
2. Changing the value of one of the fields of a value object will result in a new value object  
3. No two value objects can have the same values 
4. Also, two instances of the same value object must have the same values 
5. Therefore, no explicit identification field is needed to identify value objects

**Ubiquitous Language**

**the Primitive Obsession Code Smell:**
- Relying exclusively on the language's standard library's primitive data types to represent concepts of the business domain
  - primitive data types: such as strings, integers, or maps, etc.
- However, fields of value objects can have different conventions and validation rules 
  - For example, emails, phone numbers, addresses, country codes, etc.
  - The system cannot trust the user to always supply correct values 
    - and as a result, the class has to validate all input fields 
- This approach presents multiple design risks 
  1. the validation logic tends to be duplicated 
  2. it's hard to enforce calling the validation logic before the values are used 
  3. It will become even more challenging in the future, when the codebase will be evolved by other engineers 

> obsession: noun /əbˈseʃn/ the state in which a person's mind is completely filled with thoughts of one particular thing or person in a way that is not reasonable or normal.
> - Her fear of flying is bordering on obsession.
> - obsession with somebody or something: There's a national obsession with celebrity in England.
> - The media's obsession with the young prince continues.

**The Solution:** Instead of using primitive data types, creating Value Object types 
- To increase the clarity and make intent clear, even with shorter variable names 
- To make the validation logic reside in the value objects themselves 
  - so there is no need to validate the values before the assignment
- To centralize the business logic in the Value Object that manipulates the values 
  - The cohesive logic is implemented in one place and is easy to test 
- To make the code speak the **Ubiquitous Language [!Important]** 
  - value objects express the business domain's concepts 
- To eliminate the need for conventions 
  - For example, the need to keep in mind that this string is an email and the other string is a phone number 
- To make using the object Model less error-prone and more intuitive 

**The Implementation:**
- Value Objects are implemented as immutable objects
  - A change to one of the value object's fields conceptually creates a different value 
    - generating a different instance of a value object 

**When to Use Value Objects:** 
- WHENEVER YOU CAN, so to make the code not only more expressive but also make the code safer
  - Since value objects are immutable, the value objects’ behavior is free of side effects and is thread safe
- Use value objects for the domain's elements that describe properties of other objects 
  - This namely applies to properties of entities 
    - They are more about business domain–specific concepts that can be identified by their values and thus do not require an explicit identification field

---

#### ENTITIES 

**An entity requires an explicit identification field** to distinguish between the different instances of the entity.
- The central requirement for the identification field is that it should be unique for each instance of the entity.
- The value of an entity's identification field should remain immutable throughout the entity’s lifecycle. 

**Entities are not immutable**, and they are expected to change; 
- their properties are described by Value Objects.

**Entities are an essential building block** of any business domain. 

**Entities are only implemented in the context of the aggregate pattern.**

---

#### Aggregates – **An Aggregate is an Entity – an Aggregate is much more than just an Entity**

- An aggregate requires an explicit identification field 
- An aggregate's state is expected to change during an instance's lifecycle 
- The goal of the aggregate pattern is to **protect the consistency of the data of an aggregate (entity).**
- The aggregate pattern must keep its state consistent at all times, and it's challenging

**What Aggregates really are:**
``` 
class Ticket {
    private final UUID id;
    private final LocalDateTime registerTime;
    private final List<Message> messages;
    public void addMessage(UserId from, String body) {
        var message = new Message(from, body);
        messages.add(message); 
    }
}
```
- `Ticket` is the **aggregate root**; 
- The `aggregate` is `Ticket` + `messages`
- `List<Message> messages` is NOT an aggregate — it's an internal state the root owns.
  - Aggregate: the whole cluster: Ticket + all its Messages
  - Aggregate root: Ticket — the only entry point
  - Internal entity/VO: Message — no external reference to it
  - Identity: UUID id on Ticket only
  - Invariant boundary: addMessage — the root guards the rule
  - Transaction boundary: one Ticket per transaction
- A collection can't be an aggregate 
  - it has no identity, no lifecycle, no invariants to protect
- The aggregate is defined by the **consistency boundary**
  - the set of objects that must be transactionally consistent
  - and `Ticket` is the single object responsible for enforcing that
    - outside code cannot do `ticket.getMessages().add(...)` 
      - it must call `ticket.addMessage(from, body)`
    - This is exactly the **root is the only gate rule**

> Rule of thumb: one aggregate = one root entity = one repository = one transaction 

**Consistency Enforcement:** the aggregate is a consistency enforcement boundary
- The aggregate pattern draws a clear boundary between the aggregate and its outer scope: 
  - The aggregate's logic has to validate all incoming modifications and ensure that the changes do not contradict its business rules.
- From an implementation perspective, the consistency is enforced by allowing **only the aggregate's business logic to modify its state**. 
  - Its state can only be mutated by executing corresponding methods of the aggregate’s public interface.
  - All processes or objects external to the aggregate are only allowed to read the aggregate’s state. 
- The state-modifying methods exposed as an aggregate's public interface are often referred to as commands, as in "a command to do something"
  - A command can be implemented in two ways 
    - First, it can be implemented as a plain public method of the aggregate object 
      ``` 
      class Ticket {
          private final UUID id;
          private final LocalDateTime registerTime;
          private final List<Message> messages;
          public void addMessage(UserId from, String body) {
              var message = new Message(from, body);
              messages.add(message); 
          }
      }
      ```
    - Second, alternatively, a command can be represented as a parameter object 
      - which is also a value object that encapsulates all the input required for executing the command 
      ``` 
      sealed interface MessageCommand permits AddingMessageCommand, DeletingMessageCommand {
          UserId from();
          String body();
          default String body() { throw new UnsupportedOperationException(); }
          record AddingMessageCommand(UserId from, String body) implements MessageCommand {}
          record DeletingMessageCommand(UserId from) implements MessageCommand {}
      }
      class Ticket {
          private final List<Message> messages;
          public void execute(MessageCommand messageCommand) {
              switch (messageCommand) {
                  case AddingMessageCommand(from, body) -> messages.add(new Message(from, body));
                  case DeletingMessageCommand(from) -> messages.remove(from);
              }
          }
      }
      ```
- An aggregate's public interface is responsible for validating the input and enforcing all the relevant business rules and invariants. 
- This strict boundary also ensures that all business logic related to the aggregate is implemented in one place: the aggregate itself.
- Concurrency management can be implemented with a relational database.
- Concurrency management can be implemented elsewhere besides a relational database. 

--- 

**Transaction Boundary** 

The aggregate also acts as a transactional boundary since an aggregate's state can only be modified by its own business logic.
- All changes to the aggregate's state should be committed transactionally as one atomic operation. 
- If an aggregate's state is modified, either all the changes are committed or none of them are.
- No system operation can assume a multi-aggregate transaction. 
  - A change to an aggregate's state can only be committed individually
    - one aggregate per database transaction.
- One aggregate instance per transaction
  - this is ensuring that the design addresses the business domain's invariants and rules.
  - The need to commit changes in multiple aggregates signals a wrong transaction boundary and hence, wrong aggregate boundaries.

**Hierarchy of Entities:**
> - DO NOT use Entities as an independent pattern, only as part of an Aggregate
> - Entities are a building block of an Aggregate rather than of the overarching domain model.
> - Domain-Driven Design prescribes that a system's design should be driven by its business domain; Aggregates are no exception. 
- Business Domain: Supports changes to multiple objects that have to be applied in one atomic transaction.
- System's Design: The aggregate pattern resembles a hierarchy of entities, all sharing transactional consistency.
- The aggregate aggregates business entities and value objects that belong to the same transaction boundary.
- The aggregate ensures that all the conditions are checked against strongly consistent data 
  - and it won't change after the checks are completed, why?  
    - it's by ensuring that all changes to the aggregate's data are performed as one atomic transaction.

---

**Referencing other Aggregates:**

> Performance and scalability issues may arise if an aggregate grows too large
> - Since all objects contained by an aggregate share the same transactional boundary

- Only the information required by the aggregate's business logic to be strongly consistent should be a part of the aggregate.  
- All information that can be eventually consistent should reside outside the aggregate's boundary.
- The rule of thumb is to **keep the aggregates as small as possible** and include **only objects that are required to be in a strongly consistent state** by the aggregate’s business logic.

**How to decide whether an entity belongs to an aggregate or not?**
- Examine whether the aggregate contains business logic that can lead to an invalid system state if it works on eventually consistent data.
- An entity belongs inside an aggregate if the root must enforce a rule involving it that has to be true at every commit. 
  - Everything else is a secondary heuristic.
- Concretely, apply in this order:
  1. Write the invariant as a sentence: `An order's lines' total must not exceed the customer's credit limit.`
  2. Ask: `immediate` or `eventual` ? 
     - Must hold the instant the transaction ends, with no acceptable window of violation ➡️ Same Aggregate.
     - Can be fixed later by [a compensating action | process manager | domain event] ➡️ Separate Aggregates.
  3. If no invariant forces it in, keep it out. 
     - Default to the smallest aggregate: 
       - root + value objects
       - Pull entities in only under pressure 
- Two hard rules that fall out of this:
  - Aggregates reference other aggregates by ID only, never by object reference. 
    - If you want a direct pointer, you're probably declaring one aggregate.
    - One transaction = one aggregate 
      - If your use case modifies two, either the boundary is wrong or you need eventual consistency.
- FK ≠ containment
  - Order → Customer is a reference
  - Order → OrderLine is containment
  - Same arrow in the DB, different aggregate meaning
- Do not pulling an entity in just because you need to query it. 
  - Queries don't shape aggregates 
  - Use a read model / projection / query service. 
  - Aggregates are a write-side consistency device
- Avoid: the god aggregate
  - Customer owning orders owning shipments owning ... ... 
  - If you load a graph to change one field, the boundary is too big
- Avoid: ignoring the bounded context. 
  - The same concept can be a root in one context, a value object in another (Product is a root in Catalog, a VO in Order pricing). 
  - Decide membership per context, never globally.
- Entity that has no identity of its own inside the boundary 
  - that's a value object, not an entity. 
  - Model it as one and the question disappears.
- Practice:
  - Should `Address` be an entity in `Customer`?
    - Invariant? `A customer must have at most one default shipping address.`
      - Must hold immediately ➡️ yes | inside 
      - But does the address need its own identity? 
        - or is a set of value objects enough? Usually VO ➡️ collapse it.
  - Should Shipment be inside Order? 
    - Invariant? `Shipped qty ≤ ordered qty` 
      - Can that be eventually consistent? Usually yes
        - warehouse and ordering move at different speeds, and a violation is reconcilable
          - ➡️ Separate aggregate ➡️ Shipment holds OrderId.

**Immediately (transactionally) Consistent:**
- The rule is never observably false.
- The invariant is checked and enforced inside a single transaction, on a single aggregate instance, under one lock.  
  - If it is violated, the whole operation is rejected. 
  - There is no window in which the data is wrong.

**Eventually Consistent:**
- The rule is temporarily allowed to be false. 
- The system converges to a valid state after some delay, via a separate transaction triggered by an event.
- During the window, someone can read data that hasn't caught up.
  - 在此期間，其他人可以讀取尚未更新的資料。

**The critical word is `converge`:**
- Eventual consistency is a guarantee that the gap closes, not a shrug. 
- If there's no mechanism guaranteeing convergence, you don't have eventual consistency — you have a bug.

**The difference between the two different consistent that actually matters**

|              | Immediate                                                      | Eventually                                                  |
|--------------|----------------------------------------------------------------|-------------------------------------------------------------|
| Scope        | One aggregate, one transaction                                 | Across aggregates / contexts / services                     |
| Enforced by  | Invariant check in the root + DB transaction + optimistic lock | Domain event → handler → second transaction                 |
| On violation | Reject the command (throw)                                     | Accept, then compensate or reconcile                        |
| Failure mode | Operation fails, user retries                                  | Operation succeeds, correction arrives late — or gets stuck |
| Requires     | Nothing extra                                                  | Outbox, retries, idempotency, dead-letter, monitoring       |
| Cost         | Lock contention, big transactions, coupling                    | Complexity, temporal ambiguity, ops burden                  |

**A question for domain expert, in their words:**
- `If this is briefly out of sync — say 2 seconds, or 5 minutes — what happens? Who notices, and whose job is it to fix it?`
- Answer: `Nobody's — the system must never allow it.` 
  - ➡️ immediate and same aggregate
- Answer: `Someone would correct it / we'd issue a credit / the next run picks it up.`
  - ➡️ eventual and separate aggregates

> If fixing the inconsistency is a human's job in the business 
> - it's never a true invariant 
> - it's a business process 

---

**Immediate: the invariant lives in the root, the transaction is the boundary**

**Eventual: two transactions, joined by an event**

**Two non-negotiable on the eventual side:**
1. The event must be published atomically with the state change — transactional outbox 
  - not a `publish` after commit that can be lost 
  - `@ApplicationModuleListener` in Spring Modulith supports this: 
    - async, transactional, with an event publication registry that retries incomplete ones 
2. The handler must be idempotent because it will be delivered more than once

**The Bottom Line for Aggregate Design:** 
- Immediate consistency is what defines an aggregate boundary; 
- Eventual consistency is what lets you keep those boundaries small.
- You buy small aggregates by paying in events and compensations

---

**The Aggregate Root**
- An aggregate’s state can only be modified by executing one of its commands
- If an aggregate, the root one, represents a hierarchy of another entity 
  - then only one of the two entities should be designated as the aggregate’s public interface 
  - it's the **Root Entity**

---

**Domain Events**
- A domain event is a message describing a significant event that has occurred in the business domain. 
- Since domain events describe something that has already happened, their names should be formulated in the past tense.
- The goal of a domain event is to describe what has happened in the business domain and provide all the necessary data related to the event. 
- **Naming is Important**: Make sure the names of the domain events succinctly reflect exactly what has happened in the business domain
- Domain events are part of an aggregate's public interface. 
  - An aggregate publishes its domain events. 
  - Other processes, aggregates, or even external systems can subscribe to and execute their own logic in response to the domain events.

---

**Ubiquitous language**
- Aggregates should reflect the ubiquitous Language
- The terminology used for the aggregate's name, its data members, its actions, and its domain events all should be formulated in the bounded context's ubiquitous language.
- The code must be based on the same language the developers use when they speak with one another and with domain experts.

---

#### Domain Services 

**Domain-Driven Design proposes to implement the logic as a Domain Service**
- when the business logic that either doesn't belong to any aggregate or value object, or that seems relevant to multiple aggregates 
- A domain service is **a stateless object** that implements the business logic;
  - Such logic orchestrates calls to various components of the system to perform some calculation or analysis.
- Always keep in mind the aggregate pattern's limitation of modifying only one instance of an aggregate in one database transaction 
  - Domain services are not a loophole around this limitation. 
  - The rule of one instance per transaction still holds true. 
  - Instead, domain services lend themselves to implementing the business logic that requires reading the data of multiple aggregates (with repositories)
- Domain services have nothing to do with microservices, service-oriented architecture, or almost any other use of the word service in software engineering
  - It is just a stateless object used to host business logic

---

### Managing Complexity

> When discussing the complexity of a system, we are interested in evaluating **the difficulty of controlling and predicting the system's behavior**.
> - These two aspects are reflected by the system's **Degrees of Freedom**.
> - by Eliyahu M. Goldratt

#### Degrees of Freedom

A System's **Degrees of Freedom** are the data points needed to describe its state.

See below 👇 

The Degrees of Freedom of ClassA is 5 (ABCDE)

The Degrees of Freedom of ClassB is 2 (AD)

``` 
public class ClassA {
    public int A { get; set; }
    public int B { get; set; }
    public int C { get; set; }
    public int D { get; set; }
    public int E { get; set; }
}
```

``` 
public class ClassB {
    private int _a, _d;
    public int A {
        get => _a;
        set {
            _a = value;
            B = value / 2;
            C = value / 3;
        }
    }
    public int B { get; private set; }
    public int C { get; private set; }
    public int D {
        get => _d;
        set {
            _d = value;
            E = value * 2
        }
    }
    public int E { get; private set; }
}
```

So which class is more challenging in terms of **controlling and predicting its behavior?** 
- The answer is the one with more **degrees of freedom**, it's `ClassA`
- The invariants introduced in ClassB reduce its complexity. 
- That's what both aggregate and value object patterns do: 
  - **Encapsulate invariants** and thus reduce complexity.
- For Value Objects: all the business logic related to the state of a VO is located in its boundaries.
- For Aggregates: An aggregate can only be modified by its own methods. 
  - Its business logic encapsulates and protects business invariants, 
  - thus reducing the degrees of freedom.

#### The Domain Model Pattern

- Since the domain model pattern is applied only for subdomains with complex business logic, it's safe to assume that these Domain Models are core subdomains — the heart of the software.
- Because the Domain Model pattern pays off only where business logic is complex, it's typically reserved for core subdomains.
- But a domain model in the code is evidence, not proof — strategic classification is a business judgment about competitive advantage and must be made independently.

---

#### **What is Invariant?**
- An invariant is a statement about state that must be true at every moment an outside caller can observe the object 
  - true right after construction 
  - and true again after every method returns 
  - Always `immediately true`
  - Never `eventually true` 
- Contrast with validation: 
  - validation checks an incoming input; 
  - an invariant is a property of the object itself. 
  - validation is how you protect an invariant.
- **In a Value Object** 
  - Scope = the object's own fields. 
  - Because a VO is immutable, the invariant only has to be proven once, in the constructor — after that it cannot be broken.
- **In an Aggregate**
  - Scope = spanning multiple objects inside the **aggregate boundary**
    - This is the entire reason the **Aggregate Pattern** exists: 
      - the boundary is drawn around a set of invariants, not around a data model
  - The boundary case is worth remembering
    - A rule that spans two aggregates is not `an invariant`
      - it cannot be, since each aggregate commits in its own transaction
- So, one-liner per pattern:
  - **Value Object Invariant**: 
    - this combination of values is a meaningful value in the domain
      - Enforced in the constructor 
      - Guaranteed forever by immutability
  - **Aggregate invariant**
    - this cluster of objects is in a consistent business state
      - Enforced by root methods 
      - Guaranteed at every transaction boundary
- Examples: 

    | Rule                                                                      | Why it follows from invariants                                                                 |
    |---------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
    | Root is the only entry point                                              | if outsiders can grab a child and mutate it, no one can guarantee the multi-object rule        |
    | No public setters; expose intention-revealing methods (assignTo, close)   | a setter can only see one field, an invariant spans several fields                             |
    | Never leak the internal collection (return a copy / unmodifiable view)    | `ticket.getWorkEntries().add(...)` bypasses the budget check                                   |
    | One aggregate per transaction                                             | the invariant must hold at commit; that's the consistency boundary                             |
    | No "construct empty, then fill in"                                        | there'd be an observable window where the object is invalid — use a factory / rich constructor |


---

## Conclusion

The Domain Model pattern is aimed at cases of complex business logic. 

### Three Main Building Blocks

#### Value Objects 
- Concepts of the business domain that can be identified exclusively by their values and thus do not require an explicit ID field. 
- Since a change in one of the fields semantically creates a new value, value objects are immutable.
- Value objects model not only data, but behavior as well: 
  - methods manipulating the values and thus initializing new value objects.

#### Aggregates 
- A hierarchy of entities sharing a transactional boundary. 
- All the data included in an aggregate's boundary has to be strongly consistent to implement its business logic.
- The state of the aggregate, and its internal objects, can only be modified through its public interface by executing the aggregate's commands.
- The data fields are read-only for external components for the sake of ensuring that all the business logic related to the aggregate resides in its boundaries.
- The aggregate acts as a transactional boundary. 
  - All of its data, including all of its internal objects, has to be committed to the database as one atomic transaction.
- An aggregate can communicate with external entities by publishing domain events—messages describing important business events in the aggregate’s lifecycle. 
  - Other components can subscribe to the events and use them to trigger the execution of business logic.

#### Domain Services
- **A stateless object** that hosts business logic that naturally doesn't belong to any of the domain model's aggregates or value objects.
- Not everything outside an aggregate is a violation. 
  - Orchestration (load → call → save), cross-aggregate coordination in a domain service, and calculations spanning aggregates legitimately sit outside. 
  - What must never sit outside is a decision about whether this aggregate's state change is allowed.

---

### How Domain Model pattern managing complexity
- The domain model's building blocks tackle the complexity of the business logic by encapsulating it in the boundaries of value objects and aggregates. 
- The inability to modify the objects' state externally ensures that all the relevant business logic is implemented in the boundaries of aggregates and value objects and won't be duplicated in the application layer.