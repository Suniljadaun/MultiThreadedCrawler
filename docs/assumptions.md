# Assumptions

| ID | Assumption | Why | Status |
|---|---|---|---|
| A-001 | Quantity is whole shares (no fractions) | keeps position math simple | Accepted |
| A-002 | No cash balance check on BUY orders | plan does not require a cash account | Accepted |
| A-003 | SELL requires enough quantity in the position | cannot sell what you don't hold | Accepted |
| A-004 | Execution price comes from synthetic market data | no real market feeds | Accepted |
| A-005 | Supported symbols are a fixed list in config | avoids external symbol lookup | Accepted |
| A-006 | No authentication in the first MVP | plan says avoid auth complexity early | Accepted |
| A-007 | Per-user event ordering is enough (no global order) | portfolio state is per user | Accepted |
| A-008 | Build targets Java 21; developer machine has JDK 24 | plan asks for an LTS target | Open: install JDK 21 |
