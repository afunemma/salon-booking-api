# ADR-0002: Flyway owns the database schema

- **Status:** Accepted
- **Date:** 2026-09-29

## Context

Hibernate can create and update tables automatically from the entity classes (`ddl-auto=update`). That is convenient in development but risky in production: it can't rename columns or move data safely, its changes aren't reviewed, and nobody can tell exactly which schema version a database is on.

## Decision

All schema changes are versioned SQL migrations in `src/main/resources/db/migration`, applied by Flyway at startup. Hibernate runs with `ddl-auto=validate`: it only checks that the entities match the tables, and fails at startup if they don't.

## Consequences

- Every schema change is reviewed in a pull request and tracked in Git, like code.
- Every environment can be rebuilt to an identical schema, and Flyway records which migrations have run.
- Database features Hibernate can't generate, such as `CHECK` and exclusion constraints, are easy to use (see ADR-0004).
- Each change needs a hand-written migration. Migrations that have been released must never be edited; fixes go in a new migration.

## Alternatives considered

- **`ddl-auto=update`:** rejected for the reasons above.
- **Liquibase:** similar capabilities, but uses XML/YAML change sets. Flyway's plain SQL is simpler for a single-database project.
