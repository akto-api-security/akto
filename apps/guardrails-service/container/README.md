# Guardrails Service

A Go service for validating HTTP request and response payloads against guardrail policies and reporting threats to the Akto dashboard.

## Overview

This service receives request/response payloads (similar to mini-runtime-service), validates them against guardrail policies fetched from the database-abstractor service, and publishes detected threats to the dashboard.

### Key Features

✅ **Policy-Based Validation**: Validates request and response payloads using the [akto-gateway/mcp-endpoint-shield](https://github.com/akto-api-security/akto-gateway/tree/mcp_guardrails_sync/mcp-endpoint-shield) library
✅ **Database Integration**: Fetches guardrail policies from database-abstractor service using JWT authentication
✅ **Threat Reporting**: Automatically publishes blocked or modified requests/responses to Akto dashboard
✅ **NLP Processing**: Leverages Agent Guard Engine for heavy NLP computations
✅ **Batch Processing**: Supports batch validation of multiple request/response pairs

## Architecture

```
┌──────────────┐
│   Client     │
│  (Traffic)   │
└──────┬───────┘
       │
       ▼
┌──────────────────────────────┐
│  Guardrails Service          │
│                              │
│  1. Receive batch data       │
│  2. Fetch policies (JWT)     │──────┐
│  3. Validate payloads        │      │
│  4. Report threats           │      │
└──────────────────────────────┘      │
       │                              │
       │                              │
  ┌────┴────────┬──────────────┬─────┴───────┐
  │             │              │             │
  ▼             ▼              ▼             ▼
┌──────────┐ ┌────────┐ ┌─────────┐ ┌────────────┐
│ Database │ │ Akto   │ │ Agent   │ │ Threat     │
│Abstractor│ │ Gateway│ │ Guard   │ │ Backend    │
│(Policies)│ │(Valida-│ │ Engine  │ │(Dashboard) │
└──────────┘ │ tion)  │ │ (NLP)   │ └────────────┘
             └────────┘ └─────────┘
```

## API Endpoints

### Batch Ingestion (Compatible with mini-runtime-service)
```bash
POST /api/ingestData
Content-Type: application/json

{
  "batchData": [
    {
      "path": "/api/users",
      "method": "POST",
      "requestPayload": "{\"user\":\"john\"}",
      "responsePayload": "{\"id\":123}",
      "requestHeaders": "{...}",
      "responseHeaders": "{...}",
      "ip": "192.168.1.100",
      "statusCode": "200",
      ...
    }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "result": "SUCCESS",
  "results": [
    {
      "index": 0,
      "method": "POST",
      "path": "/api/users",
      "requestAllowed": true,
      "requestModified": false,
      "responseAllowed": true,
      "responseModified": false
    }
  ]
}
```

### Individual Validation

**Validate Request:**
```bash
POST /api/validate/request
Content-Type: application/json

{
  "payload": "{\"key\": \"value\"}"
}
```

**Validate Response:**
```bash
POST /api/validate/response
Content-Type: application/json

{
  "payload": "{\"key\": \"value\"}"
}
```

### Health Check
```bash
GET /health
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP server port | `8080` |
| `DATABASE_ABSTRACTOR_SERVICE_URL` | Database abstractor service URL | `https://cyborg.akto.io` |
| `DATABASE_ABSTRACTOR_SERVICE_TOKEN` | JWT token for authentication | **Required** |
| `AGENT_GUARD_ENGINE_URL` | Agent Guard Engine URL for NLP | `https://akto-agent-guard-engine.billing-53a.workers.dev` |
| `THREAT_BACKEND_URL` | Threat backend service URL | `https://tbs.akto.io` |
| `THREAT_BACKEND_TOKEN` | Token for threat reporting | **Required** |
| `LOG_LEVEL` | Logging level (debug, info, warn, error) | `info` |
| `GIN_MODE` | Gin framework mode (debug, release) | `release` |

### Example Configuration

Copy `.env.example` to `.env` and update the values:

```bash
cp .env.example .env
# Edit .env with your values
```

## Building and Running

### Local Development

```bash
# Install dependencies
go mod download

# Run the service
export DATABASE_ABSTRACTOR_SERVICE_TOKEN="your-token"
export THREAT_BACKEND_TOKEN="your-token"
go run main.go
```

### Docker

```bash
# Build Docker image
docker build -t guardrails-service .

# Run container
docker run -p 8080:8080 \
  -e DATABASE_ABSTRACTOR_SERVICE_TOKEN="your-token" \
  -e DATABASE_ABSTRACTOR_SERVICE_URL="http://database-abstractor:9000" \
  -e THREAT_BACKEND_TOKEN="your-token" \
  -e AGENT_GUARD_ENGINE_URL="https://akto-agent-guard-engine.billing-53a.workers.dev" \
  guardrails-service
```

### Docker Compose

```yaml
version: '3.8'
services:
  guardrails-service:
    build: ./apps/guardrails-service
    ports:
      - "8080:8080"
    environment:
      - SERVER_PORT=8080
      - DATABASE_ABSTRACTOR_SERVICE_URL=http://database-abstractor:9000
      - DATABASE_ABSTRACTOR_SERVICE_TOKEN=${DATABASE_ABSTRACTOR_SERVICE_TOKEN}
      - THREAT_BACKEND_TOKEN=${THREAT_BACKEND_TOKEN}
      - AGENT_GUARD_ENGINE_URL=https://akto-agent-guard-engine.billing-53a.workers.dev
      - LOG_LEVEL=info
```

## Dependencies

- **[Gin](https://github.com/gin-gonic/gin)** - HTTP web framework
- **[Zap](https://github.com/uber-go/zap)** - Structured logging
- **[mcp-endpoint-shield](https://github.com/akto-api-security/akto-gateway/tree/mcp_guardrails_sync/mcp-endpoint-shield)** - Policy validation library
  - `PolicyValidator` - Validates request/response payloads
  - `ThreatReporter` - Reports threats to dashboard
  - Integration with Agent Guard Engine for NLP

## How It Works

### 1. Receive Batch Data
Service receives HTTP request/response batch data via `/api/ingestData` endpoint, compatible with mini-runtime-service format.

### 2. Fetch Guardrail Policies
Policies are fetched from database-abstractor service using JWT token authentication:
```
GET /api/guardrail-policies
Authorization: Bearer <DATABASE_ABSTRACTOR_SERVICE_TOKEN>
```

### 3. Validate Payloads
Each payload is validated using the akto-gateway library:
- **Request Validation**: `policyValidator.ValidateRequest(ctx, payload, valCtx)`
- **Response Validation**: `policyValidator.ValidateResponse(ctx, payload, valCtx)`

The validator checks for:
- Regex-based patterns
- PII detection and redaction
- Harmful content categories
- Prompt injection attacks
- Banned substrings and topics

### 4. Report Threats
When threats are detected (blocked or modified payloads), they are automatically reported to the dashboard:

```go
threatReporter.ReportThreat(
    ctx,
    requestPayload,
    responsePayload,
    metadata,
    sourceIP,
    endpoint,
    method,
    reqHeaders,
    respHeaders,
    statusCode,
)
```

Threats are sent to: `https://tbs.akto.io/api/threat_detection/record_malicious_event`

## Project Structure

```
guardrails-service/
├── main.go                    # Application entry point
├── go.mod                     # Go module dependencies
├── go.sum                     # Dependency checksums
├── Dockerfile                 # Container image definition
├── .env.example               # Example environment variables
├── README.md                  # This file
├── SETUP.md                   # Detailed setup documentation
│
├── handlers/                  # HTTP request handlers
│   └── validation.go          # Validation endpoints
│
├── models/                    # Data models
│   └── payload.go             # Request/response models
│
└── pkg/
    ├── auth/                  # Authentication utilities
    │   └── jwt.go             # JWT token retrieval
    │
    ├── config/                # Configuration management
    │   └── config.go          # Environment variable config
    │
    ├── dbabstractor/          # Database abstractor client
    │   └── client.go          # HTTP client for policies
    │
    └── validator/             # Validation service
        └── service.go         # Core validation + threat reporting
```

## Data Flow

### Request Validation Flow

```
1. HTTP Request arrives at /api/ingestData
2. Parse IngestDataBatch data
3. For each item in batch:
   a. ValidateRequest(requestPayload)
      - Check against guardrail policies
      - Call Agent Guard Engine for NLP checks
      - Return result (allowed/blocked/modified)
   b. ValidateResponse(responsePayload)
      - Check against guardrail policies
      - Return result (allowed/blocked/modified)
   c. If threat detected:
      - Build threat report with metadata
      - Send to ThreatReporter
      - Publish to dashboard
4. Return aggregated results
```

### Threat Report Structure

```json
{
  "maliciousEvent": {
    "actor": "192.168.1.100",
    "filterId": "policy-id",
    "detectedAt": "1234567890",
    "latestApiEndpoint": "/api/users",
    "latestApiMethod": "POST",
    "latestApiPayload": "{request and response data}",
    "eventType": "EVENT_TYPE_SINGLE",
    "category": "Blocked",
    "severity": "CRITICAL",
    "type": "Rule-Based",
    "metadata": {
      "policy_id": "...",
      "countryCode": "IN"
    }
  }
}
```

## Integration with Other Services

### Database Abstractor Service
- **Purpose**: Provides guardrail policies
- **Authentication**: JWT token via `DATABASE_ABSTRACTOR_SERVICE_TOKEN`
- **Endpoint**: `/api/guardrail-policies`

### Agent Guard Engine
- **Purpose**: Heavy NLP computations for content analysis
- **Integration**: Automatic via akto-gateway library
- **Configuration**: `AGENT_GUARD_ENGINE_URL` environment variable

### Threat Backend Service
- **Purpose**: Receives and displays threat reports in dashboard
- **Authentication**: Bearer token via `THREAT_BACKEND_TOKEN`
- **Endpoint**: `https://tbs.akto.io/api/threat_detection/record_malicious_event`

## Development

### Running Tests
```bash
go test ./...
```

### Building for Production
```bash
CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o guardrails-service .
```

### Debugging
Enable debug logging:
```bash
export LOG_LEVEL=debug
export GIN_MODE=debug
go run main.go
```

## Troubleshooting

### Dependencies Not Resolving
The service uses the `mcp-endpoint-shield` module from the akto-gateway repository. A `replace` directive is used in `go.mod`:

```go
replace github.com/akto-api-security/mcp-endpoint-shield => github.com/akto-api-security/akto-gateway/mcp-endpoint-shield v0.0.0-20251023163241-fedce031c3c9
```

If you encounter issues:
```bash
go clean -modcache
go mod tidy
```

### Connection Refused to Database Abstractor
Ensure `DATABASE_ABSTRACTOR_SERVICE_URL` is correct and the service is running:
```bash
curl http://database-abstractor:9000/health
```

### Threat Reporting Fails
Check `THREAT_BACKEND_TOKEN` is valid:
```bash
curl -H "Authorization: Bearer $THREAT_BACKEND_TOKEN" \
  https://tbs.akto.io/api/threat_detection/record_malicious_event
```

## License

See parent repository for license information.

## Support

For issues or questions, please refer to the main Akto repository or contact the development team.
