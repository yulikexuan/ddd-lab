# Saga & Process Manager

> A Saga composes multiple local transactions, and each one publishes its events through an Outbox.
> - The steps are the local transactions, not the outboxes. 
>   - The Outbox is a supporting mechanism inside each step, not a step itself.
> - Microservices: each service usually has its own outbox table, so there really are several.
> - Modulith: there's usually one shared Event Publication Registry table that every module's steps write to.
> A Saga also doesn't strictly require an Outbox.
> - An orchestrator could call each step directly.
>   - Without an Outbox, though, a crash between commit and publish can leave the Saga stuck, so in practice they almost always go together.

## A saga is a long-running business process. 

It's long-running not necessarily in terms of time, as sagas can run from seconds to years, but rather in terms of transactions: 
- A business process that spans multiple transactions. 
- The transactions can be handled not only by aggregates but by any component emitting domain events and responding to commands. 
- The saga listens to the events emitted by the relevant components and issues subsequent commands to the other components. 
- If one of the execution steps fails, the saga is in charge of issuing relevant compensating actions to ensure the system state remains consistent.

### Consistency

> Only the data within an aggregate's boundaries can be considered Strongly Consistent.
> - Everything outside is Eventually Consistent.

- Although the saga pattern orchestrates a multicomponent transaction, **the states of the involved components are Eventually Consistent.** 
- Although the saga will eventually execute the relevant commands, **no two transactions can be Considered Atomic.**

### Matching Events to Commands 

The saga pattern manages a simple, linear flow. 
- Strictly speaking, a saga matches events to the corresponding commands. 

---

## Process Manager Pattern

> A Process Manager is a central processing unit that maintains the state of a business process and determines its next steps.
> - Origin: Hohpe & Woolf, *Enterprise Integration Patterns*.
> - Like a [Saga](saga_and_process_manager_patterns.md), it spans multiple transactions: no atomicity across steps, only Eventual Consistency, failures handled by compensating actions.

## Process Manager vs. Saga

|                    | **Saga**                                              | **Process Manager**                                                        |
|--------------------|-------------------------------------------------------|----------------------------------------------------------------------------|
| Flow               | Simple, **linear**: event X → command Y               | **Complex**: branching, conditions, loops, decisions                       |
| Logic              | Matches events to commands                            | Decides the next step based on the **current state** of the process        |
| State              | Minimal or none (often just enough for compensations) | Has its **own state**: steps done, pending, collected data                 |
| Identity/lifecycle | Implicitly started by an event                        | **Explicitly** instantiated, has its own ID (e.g. `TripBookingProcess#42`) |
| Coupling           | Low — reacts to events                                | Central coordinator — knows the whole workflow                             |

## Examples

**Saga** — each event maps to exactly one next command:

```
OrderPlaced → ReserveStock → StockReserved → ChargePayment → PaymentFailed → ReleaseStock (compensation)
```

**Process Manager** — booking a business trip:
1. Choose the cheapest flight on the route, then book a hotel near it.
2. Hotel full → try another; none left → ask the employee.
3. Flight above policy limit → request manager approval.
4. Waiting for approval → timeout + reminder.

Not expressible as "event → command". Requires a state machine.

## Implementation

- Commonly implemented as a state-based or [event-sourced](./event_sourced_domain_model_pattern.md) aggregate.
    - Its state is persisted, so the process survives crashes and can wait for days.
- Receives events from the involved components, updates its state, then issues the next command(s).
- Must handle timeouts: absence of an expected event is itself a trigger (e.g. a scheduled deadline check).

## Rule of Thumb

- Every step always leads to the same next step → **Saga**.
- Need `if/else`, waiting, retries, or accumulated state to pick the next step → **Process Manager**.

## Terminology Overlap

> Terms are not used consistently across the literature.

- Microservices literature (e.g. Chris Richardson) calls any multi-step distributed transaction with compensations a **saga**, in two styles:
    - **Choreography** — services react to each other's events ≈ Khononov's saga.
    - **Orchestration** — a central coordinator drives the steps ≈ **Process Manager**.
- "Saga" originally comes from Garcia-Molina & Salem (1987), about long-lived database transactions.


---

## FAQ

### Q: "A saga never requires persistence of its execution state." — Correct?

**No.** It holds for some simple sagas, not for sagas in general.

#### When it holds
A simple, linear saga where each event leads to exactly one command:
- The event carries everything the next step needs (order ID, amount, customer); the saga maps `StockReserved` → `ChargePayment` without remembering anything.
- Progress is already stored in the participants' own data (e.g. the Order aggregate knows it is `PAID`).
- Reliable delivery is handled by the Outbox / Event Publication Registry — persistence of the **events**, not of the saga's own state.

→ No saga state table needed: its state is spread across the participants and the events.

#### When it fails
The saga needs its own persisted state once it must remember something no single event carries:
1. **Compensating several earlier steps** — if step 4 fails, it must know which of steps 1–3 succeeded to undo them.
2. **Waiting for several events** — "continue once both `PaymentConfirmed` and `StockReserved` arrived" means remembering the first while waiting for the second.
3. **Timeouts** — "no `PaymentConfirmed` within 15 min → cancel" requires storing the deadline.
4. **Orchestrated sagas** — the orchestrator (Richardson) always stores a saga instance record (current step, collected data) to resume after a crash.
5. **Idempotency** — events are delivered at-least-once, so it must track which ones it already handled to avoid issuing the same command twice.

**Crash test:** in-memory-only state is lost on restart mid-process. The only safe way to skip persistence is to have no state beyond what the events and participants already hold.

#### Summary
- **Stateless saga** — simple choreography; each event → one command; events carry the context.
- **Stateful saga** — multi-step compensation, multiple events, timeouts, orchestration. At the far end → Process Manager.

> More accurate statement: *"A simple, choreographed saga may not need to persist its own execution state, but its events still have to be stored reliably."*
