# anonymizer-container

Standalone Presidio-based anonymization service. Exposes a single endpoint that
runs Microsoft Presidio's analyzer + anonymizer over an input text and returns
the sanitized version.

Deployed as a Cloudflare Container, owned by `anonymizer-worker`. Can also be
run as a plain Docker container anywhere for local dev/test.

## API

`POST /anonymize`

Request:
```json
{ "text": "Reach me at john.doe@example.com or 415-555-0100", "language": "en" }
```

Response:
```json
{
  "sanitized_text": "Reach me at <EMAIL_ADDRESS> or <PHONE_NUMBER>",
  "entities_found": [
    { "entity_type": "EMAIL_ADDRESS", "start": 12, "end": 32, "score": 1.0 },
    { "entity_type": "PHONE_NUMBER",  "start": 36, "end": 48, "score": 0.75 }
  ]
}
```

`GET /health` → `{ "ok": true }`.

## Local

```bash
docker build -t akto-anonymizer .
docker run --rm -p 8093:8093 akto-anonymizer

# smoke test
curl -s localhost:8093/health
curl -s localhost:8093/anonymize \
  -H 'content-type: application/json' \
  -d '{"text":"my ssn is 123-45-6789 and ip is 10.0.0.5"}'
```
