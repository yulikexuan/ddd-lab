# Event-Sourced Domain Model Pattern 

The event-sourced domain model pattern is based on the same premise as the domain model pattern:
- The business logic is complex and belongs to a Core Subdomain. 

Event-Sourced Domain Model Pattern uses the same tactical patterns as the domain model: 
- value objects, 
- aggregates, 
- domain events.

The event-sourced domain model uses the event sourcing pattern to manage the aggregates' states: 
- instead of persisting an aggregate's state, the model generates domain events describing each change and uses them as the source of truth for the aggregate's data.

- **The notion of event sourcing**

- **How event sourcing can be combined with the domain model pattern, making it an event-sourced domain model.** 

---

## Event Sourcing

> Show me your flowchart and conceal your tables, and I shall continue to be mystified. 
> Show me your tables, and I won’t usually need your flowchart; it'll be obvious. 
> —Fred Brooks1

The event sourcing pattern introduces the dimension of time into the data model.

An event-sourcing-based system persists events documenting every change in an aggregate's lifecycle.

### Search 

### Analysis 

### Source of Truth 

For the event sourcing pattern to work, all changes to an object's state should be represented and persisted as events. 
- These events become the system's source of truth (hence the name of the pattern) 
- The database that stores the system's events is the only strongly consistent storage: the system's source of truth. 
- The accepted name for the database that is used for persisting events is `event store` 

### Event Store 

The event store should not allow modifying or deleting the events since it's append-only storage. 

To support implementation of the event sourcing pattern, at a minimum, the event store has to support: 
- Fetch all events belonging to a specific business entity and append the events. 
- In the event store, every event has `expectedVersion`
  ``` 
  interface IEventStore {
      IEnumerable<Event> Fetch(Guid instanceId);
      void Append(Guid instanceId, Event[] newEvents, int expectedVersion);
  }
  ```
- The `expectedVersion` argument in the Append method is needed to implement **optimistic concurrency management**
  - when you append new events, you also specify the version of the entity on which you are basing your decisions 
    - If it's stale, new events were added after the expected version, the event store should raise a concurrency exception

---

## Event-Sourced Domain Model

The event-sourced domain model uses domain events exclusively for modeling the aggregates' lifecycles. 

Each operation on an event-sourced aggregate follows this script:
- Load the aggregate's domain events.
- Reconstitute (重構) a state representation 
  - project the events into a state representation that can be used to make business decisions.
- Execute the aggregate's command to execute the business logic, and consequently, produce new domain events.
- Commit the new domain events to the event store.