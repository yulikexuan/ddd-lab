# The Pattern of Domain-Model

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

