# otel-ingestion-service

Multi-tenant OTLP HTTP ingestion service for [Claude Cowork](https://claude.com/docs/cowork/monitoring) and other OTel exporters.

## Phase 1 (current)

- Ack-fast `POST /v1/logs` — **http/json** and **http/protobuf**
- Multi-tenant JWT auth (Mongo `HYBRID_SAAS` or `RSA_PUBLIC_KEY`)
- Parse Cowork events → structured logs (`LoggingSink`)
- Phase 2 will forward to per-tenant data-ingestion-service

## Build image

```bash
cd apps/otel-ingestion-service
make docker
# → aktosecurity/otel-ingestion-service:local
```

Or directly:

```bash
docker build -t aktosecurity/otel-ingestion-service:local apps/otel-ingestion-service/container
```

## Local run (without Docker)

### Option A — auth disabled (quickest)

```bash
cd apps/otel-ingestion-service/container/src
export AKTO_OTLP_AUTHENTICATE=false
export LOG_LEVEL=debug
go run .
```

### Option B — auth enabled with dev JWT

```bash
cd apps/otel-ingestion-service
chmod +x scripts/*.sh
./scripts/generate_dev_token.sh          # writes .dev-keys/env.snippet
source .dev-keys/env.snippet
cd container/src && go run .
```

## Docker Compose

```bash
cd apps/otel-ingestion-service/container
# Edit docker.env — set AKTO_OTLP_AUTHENTICATE=false for quick start, or RSA_PUBLIC_KEY from dev-keys
docker compose up --build
# OTLP → http://localhost:4318/v1/logs
```

## Functional test (curl)

Service must be running on `localhost:8080` (go run) or `localhost:4318` (docker compose).

```bash
cd apps/otel-ingestion-service
chmod +x scripts/*.sh

# go run on :8080
./scripts/functional-test.sh

# docker compose on :4318
BASE_URL=http://localhost:4318 ./scripts/functional-test.sh

# with auth
./scripts/generate_dev_token.sh
source .dev-keys/env.snippet
./scripts/functional-test.sh
```

### Manual curls

**Health:**
```bash
curl -s http://localhost:8080/health
```

**Cowork-style OTLP JSON** ([event schema](https://claude.com/docs/cowork/monitoring)):
```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/v1/logs \
  -H "Content-Type: application/json" \
  --data-binary @scripts/fixtures/cowork_logs.json
```

**Cowork-style OTLP protobuf:**
```bash
cd container/src && go run ./cmd/devtools protobuf-fixture --out ../../scripts/fixtures/cowork_logs.pb
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/v1/logs \
  -H "Content-Type: application/x-protobuf" \
  --data-binary @scripts/fixtures/cowork_logs.pb
```

**With JWT** (after `source .dev-keys/env.snippet`):
```bash
curl -X POST http://localhost:8080/v1/logs \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  --data-binary @scripts/fixtures/cowork_logs.json
```

**Backpressure:**
```bash
curl -s http://localhost:8080/backpressure | python3 -m json.tool
```

## Benchmark

Requires [hey](https://github.com/rakyll/hey): `go install github.com/rakyll/hey@latest`

```bash
cd apps/otel-ingestion-service
./scripts/benchmark.sh

# custom
BASE_URL=http://localhost:4318 DURATION=30s CONCURRENCY=100 ./scripts/benchmark.sh
```

## Cowork admin setup

| Field | Value |
|-------|-------|
| OTLP endpoint | `https://<host>/v1/logs` |
| OTLP protocol | `http/json` or `http/protobuf` |
| OTLP headers | `Authorization=<DATABASE_ABSTRACTOR_SERVICE_TOKEN>` |

Docs: [Anthropic OTel setup](https://support.claude.com/en/articles/14477985-monitor-claude-cowork-activity-with-opentelemetry)

## Endpoints

| Path | Cowork uses? | Behavior |
|------|--------------|----------|
| `POST /v1/logs` | Yes | Full pipeline |
| `POST /v1/traces` | No | Ack-fast stub |
| `POST /v1/metrics` | No | Ack-fast stub |
| `GET /health` | — | Liveness |
| `GET /backpressure` | — | Queue stats |

## Azure Container Apps (next step)

After local functional + benchmark tests pass:

1. Push image: `docker tag ... <acr>.azurecr.io/otel-ingestion-service:<tag> && docker push ...`
2. Deploy ACA with external ingress, VNet integration if Mongo is private
3. Set secrets: `RSA_PUBLIC_KEY` (recommended over Mongo on ACA), `AKTO_OTLP_AUTHENTICATE=true`
4. Scale: 1 CPU / 2 Gi, min 2 replicas, HTTP concurrency rule

See [plan](.cursor/plans/cowork_otlp_ingestion_service_32cb3865.plan.md) for sizing.
