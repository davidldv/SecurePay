# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

SecurePay — a multi-module Spring Boot 3 / Java 17 microservices platform simulating a bank payment backend. Not deployed; local dev via Docker Compose.

## Build & run

```bash
# First-time setup
cp .env.example .env
bash scripts/gen-keys.sh >> .env              # RSA keypair for JWT RS256
echo "SERVICE_TOKEN=$(openssl rand -hex 32)" >> .env

# Full stack
docker compose build
docker compose up -d
docker compose logs -f transaction notification

# Single service build (from repo root — parent POM is the Maven root)
mvn -B -pl services/auth -am -DskipTests package

# Run a specific test class
mvn -pl services/transaction test -Dtest=TransactionServiceTest
```

Services: `auth :8081`, `account :8082`, `transaction :8083`, `notification :8084`. All JARs land in `services/<name>/target/<name>-service.jar`.

## Module layout

Maven multi-module; parent `pom.xml` at root declares the BOM + module list.

```
libs/common/                shared DTOs: ApiError, ErrorCode, JwtProperties, RsaKeyLoader
services/auth/              user registration, login, JWT RS256 issuer, refresh-token rotation
services/account/           accounts, ledger, /internal/transfers (pessimistic lock)
services/transaction/       idempotent transfer orchestrator, publishes Kafka events
services/notification/      consumes tx.completed / tx.failed (mock email via logs)
```

Each service follows DDD-style packages: `api/` (controllers + DTOs), `application/` (services, use cases), `domain/` (entities, enums), `infrastructure/` (repos, security, clients). Flyway migrations in `src/main/resources/db/migration/`.

## Architecture — the load-bearing bits

**JWT RS256 with asymmetric keys.** `auth-service` holds the private key and signs access tokens; every other service verifies with the public key only. `JwtProperties` lives in `libs/common`; account + transaction services set `private-key: ""` in yml — they never sign. Role claim is extracted via a custom `JwtAuthenticationConverter` (see `account/infrastructure/SecurityConfig.java`).

**Transfer execution lives in account-service, not transaction-service.** This is deliberate: the pessimistic lock + balance update + ledger write must be one DB transaction. Cross-service REST cannot hold DB locks. The flow is:

1. Client → `transaction-service` with `Idempotency-Key` header
2. `TransactionService.initiate` — Redis SETNX dedupe → `TxRecordOps.insertPending` (unique `(idempotency_key, source_account)` is the hard guard)
3. `AccountClient` POSTs `/internal/transfers` to account-service (shared secret in `X-Service-Token`, validated by `ServiceTokenFilter` with constant-time SHA-256 compare)
4. account-service `TransferService.transfer` — locks both accounts in **ascending UUID order** (deadlock-free), debits/credits, inserts two `account_ledger` rows with unique `(tx_id, account_id, direction)`
5. transaction-service marks COMPLETED or FAILED, publishes `tx.completed` / `tx.failed` to Kafka
6. notification-service `@KafkaListener` logs it

**Why `TxRecordOps` exists:** `@Transactional(REQUIRES_NEW)` on `insertPending` / `markCompleted` / `markFailed` — these are called from `TransactionService.initiate` which is itself non-transactional on purpose (it spans network calls). Self-invocation wouldn't trigger Spring's proxy, so the ops are a separate bean.

**Idempotency is three layers deep:**
1. Redis `SETNX idem:{userId}:{key}` (24h TTL) — fast dedupe
2. DB unique index `(idempotency_key, source_account)` — survives Redis flush
3. Ledger unique `(tx_id, account_id, direction)` — catches orchestrator replay bugs

**Concurrency guard is also layered:** pessimistic `SELECT ... FOR UPDATE` in UUID order + DB `CHECK (balance >= 0)` + JPA `@Version`.

## Conventions

- REST paths plural (`/accounts`, `/transactions`). Kafka topics dotted, past tense (`tx.completed`).
- `account_id` / `user_id` never cross-DB FKs — logical refs only (DB-per-service).
- Money fields: `NUMERIC(19,4)` in Postgres ↔ `BigDecimal` in Java. Never `double`.
- Service-to-service auth = `X-Service-Token` header (shared secret). User auth = `Authorization: Bearer <jwt>`. `/internal/**` paths reject without the service token.
- `ddl-auto: validate` everywhere — schema is owned by Flyway, not JPA.
- New migration: `V<N>__<name>.sql` in the service's own `db/migration/`. Never edit a committed migration.

**Outbox pattern** (transaction-service): `markCompleted/markFailed` insert an `outbox_event` row inside the same DB transaction as the status update. `OutboxRelay` (`@Scheduled fixedDelay 1s`) selects pending rows via `findBatchForDispatch` — JPA `PESSIMISTIC_WRITE` + Hibernate hint `jakarta.persistence.lock.timeout=-2` translates to `FOR UPDATE SKIP LOCKED`, so adding a second instance is safe. On Kafka failure: `recordFailure` increments `attempts` and pushes `next_attempt_at` out via exponential backoff (capped 300s). `TxEventPublisher` was removed — Kafka writes only happen from the relay.

## Gateway

Nginx (`gateway` service) listens on `:8080` and is the only client-facing port. Per-zone `limit_req`: `auth_zone` 5r/s burst 10 (login/register brute-force defence), `tx_zone` 30r/s burst 60, `acct_zone` 60r/s burst 120. `/internal/**` returns 404 from the gateway — those endpoints only exist on the docker network for service-to-service calls. Service ports `:8081–:8084` remain exposed for direct testing.

## Tests

Integration tests use Testcontainers (Postgres, Redis via `GenericContainer`). `TransferConcurrencyIT` (account-service) covers the load-bearing concurrency primitives — total preservation, ledger unique-constraint replay rejection, and bidirectional deadlock-free locking. `TransactionIdempotencyIT` (transaction-service) mocks `AccountClient` + `KafkaTemplate` to verify N concurrent same-key `initiate` calls produce exactly one `transactions` row and one `outbox_event` row. Run a single suite: `mvn -pl services/account test -Dtest=TransferConcurrencyIT`.

## Status

Phase 3 complete (outbox + Testcontainers IT + Nginx gateway). Phase 4: AWS deploy (EC2 + RDS + ElastiCache + ALB).
