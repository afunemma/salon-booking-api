# Salon Booking API

[![CI](https://github.com/afunemma/salon-booking-api/actions/workflows/ci.yml/badge.svg)](https://github.com/afunemma/salon-booking-api/actions/workflows/ci.yml)

A REST API that lets clients book appointments at salons (barbers, hair, braids, nails and beauty). It is built with Java and Spring Boot.

> **Status: work in progress.** The REST API, scheduling logic and database layer are done and tested. Double-booking protection and security are next (see the [roadmap](#roadmap)).

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
| API | REST with Spring MVC, Bean Validation, OpenAPI / Swagger UI (springdoc) |
| Database | PostgreSQL 18, with Flyway migrations and Spring Data JPA |
| Build | Maven (wrapper included) |
| Testing | JUnit 5, AssertJ, Testcontainers (real PostgreSQL in Docker) |
| CI | GitHub Actions: builds and tests every push |

## Run it

You need Java 25 or newer and Docker.

```bash
./mvnw verify           # build and run all tests (starts a throwaway PostgreSQL in Docker)
./mvnw spring-boot:run  # start the app on http://localhost:8080
```

`spring-boot:run` starts PostgreSQL from `compose.yaml` automatically and stops it when the app stops. Flyway creates the tables on startup.

- Swagger UI (try every endpoint in the browser): <http://localhost:8080/swagger-ui.html>
- Health check: <http://localhost:8080/actuator/health>

## API

| Method | Endpoint | What it does |
|---|---|---|
| `POST` | `/api/salons` | Create a salon with opening hours |
| `GET` | `/api/salons/{salonId}` | Get a salon |
| `POST` | `/api/salons/{salonId}/services` | Add a service, e.g. a 35-minute haircut at R50–R100 |
| `GET` | `/api/salons/{salonId}/services` | List a salon's services |
| `GET` | `/api/salons/{salonId}/free-slots?serviceId=&date=` | Free start times for a service on a date |
| `POST` | `/api/salons/{salonId}/bookings` | Book a free slot |
| `GET` | `/api/salons/{salonId}/bookings?date=` | The salon's day view |
| `POST` | `/api/salons/{salonId}/bookings/{bookingId}/cancel` · `/complete` · `/no-show` | Update a booking |

Example: book a haircut.

```bash
curl -X POST localhost:8080/api/salons/1/bookings -H 'Content-Type: application/json' \
  -d '{"serviceId": 1, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "2030-01-07", "startTime": "10:00"}'
```

```json
{"id": 1, "serviceId": 1, "serviceName": "Haircut", "clientName": "Thabo", "clientPhone": "082 123 4567",
 "date": "2030-01-07", "startTime": "10:00:00", "endTime": "10:35:00", "status": "BOOKED"}
```

Errors use the standard [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) format, and validation errors name every invalid field:

```json
{"status": 409, "title": "Slot unavailable", "detail": "10:15 is not available on 2030-01-07 for Haircut"}

{"status": 400, "title": "Invalid request", "detail": "One or more fields are invalid",
 "errors": {"clientName": "must not be blank", "clientPhone": "must be a phone number, e.g. 082 123 4567"}}
```

### Design notes

- **Layers:** controller (HTTP) → service (business rules, transactions) → repository (database). Controllers never touch entities; they use request and response records, so the database can change without breaking API clients.
- **Business rules are checked on the server:** no bookings in the past, only times the salon actually offers, and a salon can only use its own services and bookings.
- **Time zone:** "today" and "now" come from an injected `Clock` set to `Africa/Johannesburg`, so the rules stay correct on a UTC server and tests can freeze time.

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

## Database design

The schema lives in versioned Flyway migrations (`src/main/resources/db/migration`). Hibernate only *validates* that the entities match it, and never changes the database itself.

```
salon ──< service_offering
  │             │
  └──< booking >┘
```

- **Prices are stored in cents** (R50 is `5000`) to avoid rounding errors, as a range, because many salons quote "R50 to R100".
- **The database enforces its own rules** with `CHECK` constraints: opening time before closing time, a price range that doesn't go backwards, and a valid booking status. Bad data is rejected even if the application has a bug.
- **Booking end times are stored**, not just calculated, so the database can later prevent overlapping bookings.
- `SlotFinder` works on plain `TimeRange` values and has no database dependency, so the core logic stays easy to test.

## Roadmap

- [x] Slot-finding logic with unit tests
- [x] CI pipeline with GitHub Actions
- [x] PostgreSQL + Flyway migrations, running in Docker, with Testcontainers tests
- [x] REST API for salons, services, free slots and bookings, with OpenAPI docs
- [ ] Prevent double bookings when two clients book the same slot at once
- [ ] Authentication and roles (owner, staff, client), with each salon's data kept separate
- [ ] Error handling, logging and an architecture diagram
- [ ] Live demo deployment
