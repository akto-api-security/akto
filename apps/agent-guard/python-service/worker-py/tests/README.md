# Tests

Verifies all 7 scanners plus the cascade orchestration and GCP auth. The suite
runs under CPython 3.12 (deps fetched ephemerally by `uv`), so it does **not**
need the Worker runtime. Logic modules (`scanners/`, `model_map`, `llm_scanner`,
`providers`, `gcp_auth`) import cleanly outside Pyodide; only `entry.py` needs
the Worker, and it's a thin router covered by the integration test.

## Run

```bash
./tests/run.sh                 # all offline tests (~0.5s)
./tests/run.sh tests/test_secrets.py -v
```

## Coverage

| File | What it proves |
|------|----------------|
| `test_ban_substrings.py` | match/word-boundary/case/contains_all/redact logic |
| `test_secrets.py` | detect-secrets: AWS key, PEM key, **secret buried in a long multi-line prompt**, no false positives on clean prose, redaction |
| `test_token_limit.py` | tiktoken + embedded vocab: over/under limit, encode↔decode roundtrip, determinism |
| `test_parsers.py` | LLM + Qwen3Guard result parsing, logprob→confidence distribution |
| `test_model_map.py` | cascade routing: tier1/tier2/arbiter escalation, fail-closed, fail counts-as-unsafe, result shaping (fake async providers — no network) |
| `test_gcp_auth.py` | PKCS#8→PKCS#1 load, RS256 sign (signature verified), token caching (network mocked) |

## Live cascade (opt-in)

`tests/integration/test_live_cascade.py` hits real Vertex AI. Skipped unless
`AGW_LIVE=1` and the `QWEN3GUARD_*` / `GEMMA_VERTEX_*` env vars are set:

```bash
set -a; source .dev.vars; set +a
AGW_LIVE=1 ./tests/run.sh tests/integration -v
```
