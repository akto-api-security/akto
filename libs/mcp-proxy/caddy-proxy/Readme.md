# Caddy MCP Proxy with Embedded Threat Detection

## Key Architecture Difference from Traefik

This Caddy implementation provides **TRUE in-process threat detection**:

- ✅ **SINGLE BINARY**: Everything compiles into one Caddy executable
- ✅ **ZERO HTTP CALLS**: Threat detection via direct function calls
- ✅ **NO SEPARATE CONTAINERS**: One process handles everything
- ✅ **IN-MEMORY COMMUNICATION**: No network overhead between proxy and threat detector

## How It Works

1. **Compilation**: The threat detector module is compiled directly into Caddy
2. **Runtime**: When a request comes in:
   - Caddy receives the request
   - Calls `td.validator.Validate()` directly (in-process function call)
   - Based on the result, either blocks or forwards to the MCP server
3. **Single Process**: Everything runs in one Go process, one container

## Quick Start

### Option 1: Run Locally
```bash
# Set your API key
export OPENAI_API_KEY="your-key"

# Build and run (single binary)
make dev
```

### Option 2: Run with Docker
```bash
# Set your API key
export OPENAI_API_KEY="your-key"

# Build and run (single container)
make docker-run
```

## Test the Proxy

```bash
# Health check
curl http://localhost:8080/health

# Test MCP proxy (validated by embedded threat detector)
curl -X POST "http://localhost:8080/proxy/kite/message" \
  -H "Content-Type: application/json" \
  -d '{"id":"1","jsonrpc":"2.0","method":"tools/list","params":{}}'
```

## Files

- `main.go`: Entry point that embeds the threat detector into Caddy
- `threatdetector/threat_detector.go`: The Caddy module that calls your mcp-threat library
- `Caddyfile`: Configuration for routes and threat detection settings
- `Dockerfile`: Builds everything into a single container

## Key Code Points

In `threatdetector/threat_detector.go`:
```go
// This is a DIRECT FUNCTION CALL - no HTTP, no network
validationResponse := td.validator.Validate(ctx, mcpPayload, nil)
```

In `main.go`:
```go
// This import embeds the module into the binary at compile time
_ "github.com/akto-api-security/akto/libs/mcp-proxy/caddy-proxy/threatdetector"
```

## Environment Variables

- `OPENAI_API_KEY`: Your OpenAI API key
- `MCP_LLM_PROVIDER`: LLM provider (default: openai)
- `MCP_LLM_MODEL`: Model to use (default: gpt-4)
- `MCP_LLM_TIMEOUT`: Timeout in seconds (default: 60)
- `MCP_LLM_TEMPERATURE`: Temperature for LLM (default: 0.0)
- `MCP_DEBUG`: Enable debug logging (default: false)

## Advantages Over Traefik Solution

1. **Performance**: No inter-process communication overhead
2. **Simplicity**: Single binary, single container, single process
3. **Security**: No exposed internal APIs or services
4. **Debugging**: Easier to trace - everything in one process
5. **Deployment**: Just one binary to deploy

## Architecture Diagram

```
[Client Request]
        ↓
[Caddy Process]
    ├── HTTP Handler
    ├── Threat Detector Module (embedded)
    │   └── validator.Validate() (direct call)
    └── Reverse Proxy to MCP
```

Compare to Traefik:
```
[Client] → [Traefik Container] → [Threat Detector Container] → [MCP]
              (HTTP)                    (HTTP)
```

With Caddy:
```
[Client] → [Single Caddy Binary with Everything] → [MCP]
                (Direct Function Call Inside)