# ADR-0003: Store money as integer cents, with optional price ranges

- **Status:** Accepted
- **Date:** 2026-09-29

## Context

Prices need exact arithmetic. Floating-point types (`double`) can't represent most decimal amounts exactly: `0.1 + 0.2` is `0.30000000000000004`. Customer research also showed that many salons don't have fixed prices; a barber quoted "R50 to R100 depending on the cut".

## Decision

Prices are stored as integers in cents (R50.00 is `5000`), as two optional fields: `priceFromCents` and `priceToCents`. The database rejects negative values and ranges where "to" is less than "from".

## Consequences

- Adding and comparing amounts is exact and fast.
- Fixed prices (`from == to`), ranges, and "price on request" (both empty) are all supported.
- Clients must convert cents to rands for display.
- The currency is assumed to be ZAR. Supporting other currencies would need a currency field, and probably a `Money` value type.

## Alternatives considered

- **`BigDecimal` / `NUMERIC`:** also exact, but heavier, and easy to misuse (`equals` treats `1.0` and `1.00` as different). Not needed for whole-cent amounts.
- **A single fixed price:** doesn't match how many salons actually price.
