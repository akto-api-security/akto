.PHONY: build run stop clean test dev

# Build the custom Caddy binary locally
build:
	@echo "Building custom Caddy with embedded threat detector..."
	cd .. && go work init 2>/dev/null || true
	cd .. && go work use ./caddy-proxy ./mcp-threat
	go mod download
	go build -o caddy-with-threat-detector .
	@echo "✅ Built single binary: caddy-with-threat-detector"

# Run locally (development)
dev: build
	@echo "Running Caddy with threat detector (single process)..."
	OPENAI_API_KEY=$(OPENAI_API_KEY) \
	MCP_LLM_PROVIDER=openai \
	MCP_LLM_MODEL=gpt-4 \
	MCP_LLM_TIMEOUT=60 \
	MCP_LLM_TEMPERATURE=0.0 \
	MCP_DEBUG=false \
	./caddy-with-threat-detector run --config Caddyfile

# Build Docker image
docker-build:
	@echo "Building Docker image with embedded threat detector..."
	cd .. && docker build -f caddy-proxy/Dockerfile -t caddy-mcp-proxy .

# Run with Docker
docker-run: docker-build
	@echo "Running Caddy container (single container, single process)..."
	docker run --rm -d \
		--name caddy-mcp-proxy \
		-p 8080:8080 \
		-p 2019:2019 \
		-e OPENAI_API_KEY=$(OPENAI_API_KEY) \
		-e MCP_LLM_PROVIDER=openai \
		-e MCP_LLM_MODEL=gpt-4 \
		-e MCP_LLM_TIMEOUT=60 \
		-e MCP_LLM_TEMPERATURE=0.0 \
		-e MCP_DEBUG=false \
		caddy-mcp-proxy

# Stop Docker container
docker-stop:
	@echo "Stopping Caddy container..."
	docker stop caddy-mcp-proxy || true

# Clean up
clean:
	rm -f caddy-with-threat-detector
	docker-stop
	docker rmi caddy-mcp-proxy 2>/dev/null || true

# Test the proxy
test:
	@echo "Testing proxy..."
	@echo "Health check:"
	curl -s http://localhost:8080/health
	@echo "\n\nTesting Kite MCP (will be validated by embedded threat detector):"
	curl -s -N --max-time 2 http://localhost:8080/proxy/kite/sse || true

# View logs
logs:
	docker logs -f caddy-mcp-proxy