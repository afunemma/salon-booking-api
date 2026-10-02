# ADR-0005: Package by feature, with boundaries enforced by tests

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

Code is grouped by feature (`salon`, `booking`) rather than by layer (`controllers`, `services`). An architecture test run showed the packages depended on each other in cycles: `salon` used `booking.OpeningHours`, and `common`'s error handler imported `booking`'s exceptions. Cycles mean no package can be understood, tested or changed on its own.

## Decision

- **Package layout:**
  - `scheduling` holds the pure slot logic and depends on nothing.
  - `salon` depends on `scheduling`.
  - `booking` depends on `salon` and `scheduling`.
  - `common` holds shared infrastructure and depends on no feature.
- **Error categories:** `common` defines general error categories (`NotFoundException`, `BusinessRuleException`, `ConflictException`). Feature exceptions extend them, so the global error handler doesn't need to know about individual features.
- **Enforcement:** ArchUnit tests (`ArchitectureTest`) enforce the rules on every build:
  - no package cycles
  - controllers never use repositories or entities directly
  - `scheduling` stays free of Spring and JPA
  - constructor injection only
  - logging only through SLF4J

## Consequences

- A change that breaks the architecture fails the build, with a message naming the class and the rule.
- Each feature can later be extracted into its own module if needed.
- New code has to respect the rules. Changing a rule is a deliberate decision and gets a new ADR.

## Alternatives considered

- **Package by layer:** simple at first, but every feature spreads across all packages, and nothing stops cross-feature tangles.
- **Spring Modulith:** a fuller framework for the same goal. Worth revisiting if the project grows to many features; plain ArchUnit rules are enough for now.
