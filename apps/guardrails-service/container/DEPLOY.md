# Build docker image for cloudflare (amd64)
```bash
cd apps/guardrails-service/container
GITHUB_TOKEN=xxx ./setup-netrc.sh
docker buildx build --platform linux/amd64 -t guardrails-service:1.0.0_local .
```

# Push image to cloudflare
```bash
npx wrangler containers push guardrails-service:1.0.0_local
```

# Deploy to cloudflare
```bash
cd ../worker
npx wrangler deploy
```
