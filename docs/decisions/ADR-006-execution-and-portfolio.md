# ADR-006: Execute orders and update positions in one transaction

Status: Accepted

## Context
After validation an order must be filled and the user's position changed. The original plan had a
separate portfolio consumer reacting to `OrderExecuted`. A SELL also needs "does the user hold enough shares?"
checked safely, even when two sells for the same symbol arrive together.

## Decision
- A consumer on `orders.validated` (group `order-execution`) does, in ONE database transaction:
  dedupe row, lock the position (`SELECT ... FOR UPDATE`), check the limit and the holding,
  update the position, insert the execution, set the order to EXECUTED or REJECTED,
  and write `OrderExecuted` / `PositionUpdated` / `OrderRejected` to the outbox.
- Orders are filled at the synthetic market price. `price` in the request is a limit:
  BUY only if market <= limit, SELL only if market >= limit.
- `executions.order_id` is unique, so an order can never be filled twice.

## Alternatives
- Separate portfolio consumer: order could be EXECUTED while the position is not yet updated,
  and a failed position update would need compensation. More moving parts, no benefit inside one database.
- Check SELL holdings at validation time: can race with another sell executing in between.

## Consequences
- Order, execution and position are always consistent with each other.
- `portfolio.updated` is still published, so read models (e.g. a Redis cache in Phase 5) can react to it.
- If the portfolio is ever split into its own service, this becomes a saga; the events already exist for that.
