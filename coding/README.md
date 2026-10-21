# ProLean

Project and timeline management for laptop and tablet browsers.

## Services

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `auth-service` | 8081 | `auth_db` | Credentials, login, RS256/JWKS, refresh families, password changes |
| `user-service` | 8082 | `user_db` | Users, roles, projects, memberships |
| `task-service` | 8083 | `task_db` | Tasks, PIC snapshots, calendar-day duration |
| `timeline-service` | 8084 | `timeline_db` | Versioned timelines, publish/revision, holidays, working-day plan % |

There is one PostgreSQL instance with four isolated databases. No service role can connect to
another service's database. Credentials and password hashes exist only in `auth-service`.

## Prerequisites

JDK 21, Maven 3.9+, Node 22+ (frontend), Docker 29+ with Compose v2/v5.
The environment check is recorded in `docs/environment-check.md`.

## Build and test

```bash
cd coding/backend
mvn -B clean verify
```

Tests use real PostgreSQL through Testcontainers. H2 is not used. The build enforces JaCoCo line
coverage on the domain/service/calculator/mapper packages, a branch gate on the working-day
calculator, and PIT mutation coverage (>= 90%) for the calculator.

Frontend checks:

```bash
cd coding/frontend
npm ci --legacy-peer-deps
npm run lint
npm run test:coverage
npm run build
```

## Run locally without Compose

A development PostgreSQL container is expected on port `55432`:

```bash
docker start prolean-dev-postgres
mvn -B -pl auth-service spring-boot:run
mvn -B -pl user-service spring-boot:run
mvn -B -pl task-service spring-boot:run
mvn -B -pl timeline-service spring-boot:run
```

Each service reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; defaults point at the `55432`
development container. Flyway creates the schema on startup.

## Run the stack

```bash
cd coding/backend
mvn -B -DskipTests package
cd ../deploy
cp .env.example .env
docker compose up -d --build
docker compose ps
```

The Compose stack publishes PostgreSQL on `5432`, the services on `8081`–`8084`, and the frontend on
`http://localhost:8080`. Nginx serves the SPA and routes `/api/v1/**` to the owning service on the
same origin, so the refresh cookie path `/api/v1/auth` remains valid. The development container on
`55432` is separate and does not conflict.

Run the end-to-end smoke flow after the stack is healthy:

```bash
cd coding/deploy
./smoke.sh
```

The script logs in as the bootstrap admin, lists projects, creates a task, creates/publishes a
timeline, creates/publishes a revision, and verifies an idempotent create replay.

Bootstrap admin: the configured `BOOTSTRAP_ADMIN_*` values. Change the password immediately outside
local development.

## Production hardening

The checked-in Compose values are development defaults so a fresh local stack can start without
extra setup. Before exposing the stack, provide your own `POSTGRES_PASSWORD`,
`BOOTSTRAP_ADMIN_PASSWORD` and four `INTERNAL_KEY_*` values (at least 32 characters each), set
`AUTH_COOKIE_SECURE=true` behind HTTPS, and keep `OPENAPI_ENABLED=false` unless the documentation
endpoints are intentionally public. The services validate internal-key length at startup and reject
short secrets.

## API

Each service publishes OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
Internal endpoints under `/internal/**` require `X-Internal-Key-Id` and `X-Internal-Api-Key` and are
never reachable with a browser JWT.

Error responses use RFC 7807 `application/problem+json`. Lists use the standard envelope:
`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.

## Database access check

```bash
cd coding
./scripts/db-check.sh
```

This verifies that each role can connect only to its own database. Start the Compose stack first.
