# Build stage - compile custom Caddy with threat detector embedded
FROM golang:1.23-alpine AS builder

WORKDIR /workspace

# Install dependencies
RUN apk add --no-cache git

# Copy mcp-threat library
COPY mcp-threat /workspace/mcp-threat

# Copy caddy-proxy with our custom module
COPY caddy-proxy /workspace/caddy-proxy

# Set up go workspace to use both modules
WORKDIR /workspace
RUN go work init && \
    go work use ./caddy-proxy ./mcp-threat

# Build custom Caddy binary with threat detector compiled in
# This creates a SINGLE BINARY with everything embedded
WORKDIR /workspace/caddy-proxy
RUN go mod download && \
    go build -o caddy-with-threat-detector .

# Runtime stage - single binary, no separate processes
FROM alpine:latest

RUN apk --no-cache add ca-certificates

WORKDIR /app

# Copy the single binary that has everything
COPY --from=builder /workspace/caddy-proxy/caddy-with-threat-detector /usr/bin/caddy
COPY --from=builder /workspace/caddy-proxy/Caddyfile /etc/caddy/Caddyfile

EXPOSE 8080 2019

# Environment variables (can be overridden at runtime)
ENV MCP_LLM_PROVIDER=openai \
    MCP_LLM_MODEL=gpt-4 \
    MCP_LLM_TIMEOUT=60 \
    MCP_LLM_TEMPERATURE=0.0 \
    MCP_DEBUG=false

# Run the single Caddy binary - no separate containers needed!
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]