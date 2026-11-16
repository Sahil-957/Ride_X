# RideX

Bike-taxi booking web app (college project) with Passenger, Driver and Admin interfaces.
**Read [docs/PLAN.md](docs/PLAN.md) before starting any phase**: it holds the architecture, ER diagram,
API surface, page list, roadmap and key decisions. Update it when a decision changes.

## Working rules
- The stack is fixed: Java 21, Spring Boot 4, Maven, Spring Security, Spring Data JPA, Flyway, Neon PostgreSQL,
  static HTML/CSS/vanilla JS + Bootstrap, Leaflet + OpenStreetMap. Any new library or tool needs the user's OK first.
- Build one roadmap phase at a time. A phase is done when `./mvnw verify` passes and the feature is checked by hand;
  then stop and report to the user before starting the next phase.
- Ask before any destructive database action: DROP/TRUNCATE/DELETE, resetting or deleting a Neon branch,
  editing a migration that has already run.
- Backend operations report real outcomes: return an error status when something fails.

## Secrets
- Local secrets live in `.env` / `.env.local` (gitignored), loaded by `spring.config.import` in `application.yml`.
  They resolve from the working directory, so run Maven from the project root.
- Neon's CLI owns the database variables: `neon env pull` writes `DATABASE_URL` (pooled) and
  `DATABASE_URL_UNPOOLED` (direct) into `.env.local`. `config/DatabaseConfig` converts the direct one to JDBC
  for the app and Flyway.
- Neon CLI: this project's Neon account is the `ridex` CLI login, which `.neon` does not record. Run every
  Neon command as `NEON_PROFILE=ridex npx neon@latest <command>`; the DEFAULT login belongs to other projects.
  `neon.ts` + `package.json` exist only for Neon's config tooling; the app itself stays Java.
- New secret → reference it as `${NAME}` in `application.yml` and add `NAME=` (no value) to `.env.example`.

## Backend conventions
- Package by layer under `com.ridex`: config, controller, service, repository, entity, dto, enums, security, exception.
- Flyway owns the schema (`ddl-auto=validate`). Schema change = new `V{n}__description.sql`.
- Enums: `@Enumerated(EnumType.STRING)` over `varchar` + CHECK. Timestamps: `timestamptz` / `Instant`.
  Money: `numeric(10,2)` / `BigDecimal`.
- DTOs are Java records; controllers take and return DTOs, entities stay inside the service layer.
- Constructor injection. `@Transactional` on service methods that write.
- Rule violations throw `ApiException(HttpStatus, message)`; `GlobalExceptionHandler` turns them into ProblemDetail JSON.
  Validate input with Jakarta annotations on DTOs + `@Valid`.
- Booking status changes are conditional updates (`... WHERE id = ? AND status = <expected>`); 0 rows updated → 409.
  The fare is always recalculated on the server.

## Frontend conventions
- Static files in `src/main/resources/static`; each page is `name.html` + `name.js` in its role folder.
- Bootstrap 5 and Leaflet from CDN with pinned versions. Shared code lives in `js/api.js` (fetch + CSRF header +
  error handling), `js/ui.js` and `js/map.js`.
- Every screen shows real API data and has loading, empty and error states. Mobile-first layouts.

## Tests
- Database tests run against Neon (a separate test branch, credentials from `.env`); keep tests on PostgreSQL.
