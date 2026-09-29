# Salon Booking API

[![CI](https://github.com/afunemma/salon-booking-api/actions/workflows/ci.yml/badge.svg)](https://github.com/afunemma/salon-booking-api/actions/workflows/ci.yml)

A REST API that lets clients book appointments at salons (barbers, hair, braids, nails and beauty). It is built with Java and Spring Boot.

> **Status: work in progress.** The core scheduling logic is done and tested. The database, REST endpoints and security are next (see the [roadmap](#roadmap)).

## Why this project

This project started with real customer research. A local barber told me:

- **3 of about 30 clients a week don't show up.** That's roughly 10% of his income lost.
- He's busy cutting hair all day and **doesn't read booking messages**.
- Clients book by WhatsApp, calls and walk-ins, usually 1–2 days ahead.

The goal is to let clients book themselves and to cut no-shows, without adding admin work for the salon. The questions I use to interview more salons are in [`docs/salon-interview-script.md`](docs/salon-interview-script.md).

## Tech stack

| | |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 |
| Build | Maven (wrapper included) |
| Testing | JUnit 5, AssertJ |
| CI | GitHub Actions: builds and tests every push |

## Run it

You need Java 25 or newer.

```bash
./mvnw verify           # build and run all tests
./mvnw spring-boot:run  # start the app on http://localhost:8080
```

Health check: <http://localhost:8080/actuator/health>

## How free slots are found

`SlotFinder` walks through the day from opening time in fixed steps (e.g. every 15 minutes). It keeps a start time only if the service finishes by closing time and doesn't overlap an existing booking.

Two appointments overlap when **each one starts before the other ends**:

```java
start.isBefore(otherEnd) && otherStart.isBefore(end)
```

Edge cases covered by tests:

- back-to-back bookings (10:00–10:35, then 10:35)
- long services that only fit in big gaps (5-hour braids)
- late opening hours that must not wrap past midnight

## Roadmap

- [x] Slot-finding logic with unit tests
- [x] CI pipeline with GitHub Actions
- [ ] PostgreSQL + Flyway migrations, running in Docker
- [ ] REST API for salons, services, free slots and bookings, with OpenAPI docs
- [ ] Prevent double bookings when two clients book the same slot at once
- [ ] Authentication and roles (owner, staff, client), with each salon's data kept separate
- [ ] Error handling, logging and an architecture diagram
- [ ] Live demo deployment
