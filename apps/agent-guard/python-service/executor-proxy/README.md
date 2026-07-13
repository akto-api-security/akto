# executor-proxy

A tiny Cloudflare Worker that keeps the legacy URL

```
https://akto-agent-guard-executor.billing-53a.workers.dev
```

alive for already-installed devices, while transparently forwarding every
request to the agent-guard service now running on **Azure**.

Deployed devices hard-code the `*.workers.dev` URL and cannot be updated in the
field, and a `*.workers.dev` hostname is owned by Cloudflare (no DNS record to
repoint). So instead of moving the URL, we replace the Worker behind it with
this reverse proxy.

## How it works

- Forwards `GET /health`, `GET /scanners`, `POST /scan`, `POST /scan/batch`
  (and anything else) unchanged — same method, headers, body, path, query.
- Cloudflare rewrites the `Host` header to the Azure hostname, so Azure ingress
  routes correctly.
- 3xx responses are passed straight back (`redirect: "manual"`).

## Configure

Set `AZURE_ORIGIN` in `wrangler.jsonc` `vars` to your Azure origin, e.g.
`https://agent-guard.example.com`. It must be a bare origin (no path). To keep
it out of source, delete the `vars` entry and use a secret:

```bash
npx wrangler secret put AZURE_ORIGIN
```

## Deploy

> Deploying under the name `akto-agent-guard-executor` **overwrites** the
> existing Python executor Worker at the same URL. Its source is preserved in
> git (`../worker-py`, `main: src/entry.py`) for rollback.

Safe rollout — test under a staging name first:

```bash
cd apps/agent-guard/python-service/executor-proxy

# 1. Dry-run against the staging worker (edit "name" temporarily, or):
npx wrangler deploy --name akto-agent-guard-executor-proxy-staging
curl -s https://akto-agent-guard-executor-proxy-staging.billing-53a.workers.dev/health
curl -s -X POST https://akto-agent-guard-executor-proxy-staging.billing-53a.workers.dev/scan \
  -H 'content-type: application/json' \
  -d '{"scanner_name":"PromptInjection","scanner_type":"prompt","text":"hello"}'

# 2. Cut over — replaces the live executor URL:
npx wrangler deploy
```

## Rollback

Redeploy the original Python Worker:

```bash
cd ../worker-py
npx wrangler deploy --config wrangler-exec.jsonc
```
