# SecurePay Microservices Platform

Fintech payment backend: auth, accounts, transfers (later), notifications (later).

Stack: Java 17 · Spring Boot 3 · PostgreSQL 16 · Redis 7 · Docker · (AWS later).

## Status

Phase 2: **Auth** + **Account** + **Transaction** (pessimistic lock + idempotency) + **Notification** (Kafka consumer).

## Architecture (Phase 1)

```
client ─▶ :8081 auth-service ──▶ postgres-auth
       ─▶ :8082 account-service ▶ postgres-account
                     │
                     └────▶ redis (shared)
```

Stateless JWT (RS256). Auth signs with private key; account verifies with public key. No shared secret.

## Requirements

- JDK 17+, Maven 3.9+ (or use Dockerized build)
- Docker + Docker Compose
- `openssl` (to generate JWT keys)
- `curl`, `jq` (to test)

## Setup

```bash
# 1. Env file
cp .env.example .env

# 2. Generate JWT keys, append to .env
bash scripts/gen-keys.sh >> .env

# 3. Generate a service token
echo "SERVICE_TOKEN=$(openssl rand -hex 32)" >> .env

# 4. Build + run
docker compose build
docker compose up -d

# 5. Check health
for p in 8081 8082 8083 8084; do curl -s localhost:$p/actuator/health; echo; done
```

## API

### Auth (`:8081`)

```bash
# Register
curl -sX POST localhost:8081/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"Str0ngP@ss!"}' | jq

# Login
ACCESS=$(curl -sX POST localhost:8081/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"Str0ngP@ss!"}' | jq -r .accessToken)

# Refresh
curl -sX POST localhost:8081/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<token>"}' | jq
```

### Accounts (`:8082`)

```bash
# Create
curl -sX POST localhost:8082/accounts \
  -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"currency":"USD"}' | jq

# List mine
curl -s localhost:8082/accounts -H "Authorization: Bearer $ACCESS" | jq

# Balance
curl -s localhost:8082/accounts/<id>/balance -H "Authorization: Bearer $ACCESS" | jq

# History
curl -s "localhost:8082/accounts/<id>/history?page=0&size=20" \
  -H "Authorization: Bearer $ACCESS" | jq
```

### Transactions (`:8083`)

```bash
# Transfer (idempotent, retry-safe)
curl -sX POST localhost:8083/transactions \
  -H "Authorization: Bearer $ACCESS" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{"sourceAccount":"<uuid>","destAccount":"<uuid>","amount":"150.00","currency":"USD"}' | jq

# Status
curl -s localhost:8083/transactions/<id> -H "Authorization: Bearer $ACCESS" | jq
```

Notification service tails Kafka `tx.completed` + `tx.failed` and logs (mock email).
```bash
docker compose logs -f notification
```

## Project layout

```
securepay/
├── pom.xml                     # parent POM / BOM
├── docker-compose.yml
├── libs/common/                # shared errors, JWT utils
├── services/auth/              # Auth service
├── services/account/           # Account service
├── scripts/gen-keys.sh
└── .env.example
```

## Roadmap

- [x] Phase 1: Auth + Account + Flyway + Docker
- [x] Phase 2: Transaction service (pessimistic lock + idempotency key + Kafka event) + Notification consumer
- [ ] Phase 3: Hardening — tests (Testcontainers race/idempotency), rate limits, Nginx gateway
- [ ] Phase 4: AWS deploy (EC2 + RDS + ElastiCache + ALB)

## Security notes

- JWT RS256, 15m access, 7d refresh with rotation.
- BCrypt cost 12.
- HTTPS terminated at proxy (Nginx / ALB) in deployed environments.
- Secrets via env in dev, AWS SSM Parameter Store in prod.
