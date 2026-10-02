# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

Code shows *what* the system does, but not *why* it was built that way or which alternatives were rejected. Without that, later changes risk undoing decisions that were made for good reasons.

## Decision

Significant design decisions are recorded as short Markdown files in `docs/adr/`, using this format: context, decision, consequences, and the alternatives considered.

## Consequences

- Reviewers and new contributors can understand the reasoning without asking.
- Writing an ADR takes a few minutes per decision.
