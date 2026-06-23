# Akto Guardrails - Dify Adapter

A thin HTTP service that implements [Dify's Moderation API Extension](https://docs.dify.ai/en/use-dify/workspace/api-extension/moderation-api-extension)
and forwards input/output content to Akto's guardrails for validation and traffic
ingestion.

It translates Dify's `{ point, params }` moderation calls into Akto's existing
`/api/http-proxy` flow and maps Akto's `guardrailsResult` back into Dify's
moderation response (`flagged` / `action` / `preset_response`).

## How it works

```
Dify app  --(app.moderation.input/output)-->  Dify Adapter  --(/api/http-proxy)-->  Akto
   ^                                                |
   |------------- {flagged, action, ...} -----------|
```

- `app.moderation.input` -> Akto `/api/http-proxy?guardrails=true&ingest_data=true&akto_connector=dify`
- `app.moderation.output` -> Akto `/api/http-proxy?response_guardrails=true&ingest_data=true&akto_connector=dify`
- `ping` -> `{ "result": "pong" }`

Akto verdicts map to Dify responses as:

| Akto `guardrailsResult` | Dify response |
| --- | --- |
| `Allowed=false` | `{ flagged: true, action: "direct_output", preset_response: <reason> }` |
| `Allowed=true` + `ModifiedPayload` | `{ flagged: true, action: "overridden", inputs/query \| text: <redacted> }` |
| otherwise | `{ flagged: false }` |

The adapter is fail-open: if Akto is unreachable or errors, content is allowed.

## Configuration

| Variable | Description | Default |
| --- | --- | --- |
| `AKTO_DATA_INGESTION_URL` | Base URL of the Akto data-ingestion service (required) | - |
| `AKTO_API_TOKEN` | Authorization token sent to Akto | empty |
| `AKTO_ACCOUNT_ID` | Akto account id | `1000000` |
| `DIFY_EXTENSION_TOKEN` | Bearer token Dify must present to this adapter | empty (auth disabled) |
| `TIMEOUT` | HTTP timeout (seconds) for Akto calls | `5` |
| `LOG_LEVEL` | Logging level | `INFO` |

Get `AKTO_API_TOKEN` from **Akto Dashboard -> Quick Setup -> Hybrid SaaS** (`databaseAbstractorToken`).

## Run

### Local

```bash
pip install -r requirements.txt

export AKTO_DATA_INGESTION_URL="https://<your-akto-ingestion-host>"
export AKTO_API_TOKEN="<token>"
export DIFY_EXTENSION_TOKEN="<a-secret-you-choose>"

uvicorn app:app --host 0.0.0.0 --port 8000
```

### Docker

```bash
docker build -t akto-dify-adapter .
docker run -p 8000:8000 \
  -e AKTO_DATA_INGESTION_URL="https://<your-akto-ingestion-host>" \
  -e AKTO_API_TOKEN="<token>" \
  -e DIFY_EXTENSION_TOKEN="<a-secret-you-choose>" \
  akto-dify-adapter
```

## Connect in Dify

1. In Dify, go to **Settings -> API Extension -> Add**.
2. Set the **API Endpoint** to this adapter's URL (e.g. `https://<adapter-host>/`).
3. Set the **API Key** to the value of `DIFY_EXTENSION_TOKEN` (Dify sends it as `Authorization: Bearer <token>`).
4. In each app, open **Content Moderation**, choose this API extension, and enable
   **Review Input Content** and/or **Review Output Content**.

## Verify

```bash
# Health
curl http://localhost:8000/health

# Ping (Dify health check contract)
curl -X POST http://localhost:8000/ \
  -H "Authorization: Bearer $DIFY_EXTENSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"point":"ping"}'

# Input moderation
curl -X POST http://localhost:8000/ \
  -H "Authorization: Bearer $DIFY_EXTENSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"point":"app.moderation.input","params":{"app_id":"demo","inputs":{},"query":"My SSN is 123-45-6789"}}'
```

Then confirm a `*.dify.agent` collection appears in the Akto dashboard inventory
and that guardrail activity is visible under Agentic Guardrails.
