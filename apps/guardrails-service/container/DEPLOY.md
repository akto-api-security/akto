# Build docker image for cloudflare (amd64)
```bash
cd apps/guardrails-service/container
GITHUB_TOKEN=xxx ./setup-netrc.sh
docker buildx build --platform linux/amd64 -t guardrails-service:1.0.0_local .
```

The image links **Vectorscan** (`libvectorscan5`) for Hyperscan-accelerated PII redaction. Runtime is `debian:bookworm-slim` (not distroless static) because CGO requires glibc + native libs.

Unit tests run in CI before `docker build` (not inside the image) so each platform only compiles the Hyperscan binary once. BuildKit cache mounts (`go mod`, `go-build`) speed up rebuilds.

# Local PII redaction smoke test (no go.mod bump)
```bash
cd apps/guardrails-service/container
AKTO_GATEWAY_ROOT=/path/to/akto-gateway ./scripts/local-test-pii.sh
```

See `README.md` → Development for full local build options (`GUARDRAILS_PII_ENGINE`, `link-local-akto-gateway.sh`).

# Push image to cloudflare
```bash
npx wrangler containers push guardrails-service:1.0.0_local
```

# Deploy to cloudflare
```bash
cd ../worker
npx wrangler deploy
```
