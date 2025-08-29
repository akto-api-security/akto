# MCP Proxy Library - Go Implementation

A simple, high-performance Go library for analyzing and validating MCP (Model Context Protocol) requests and responses using LLM-based threat detection. Built with Go's simplicity and performance in mind.

## 🚀 Features

- **Core Functionality**: MCP request/response validation using LLM analysis
- **Multi-Provider Support**: OpenAI and self-hosted LLM APIs
- **Simple API**: Clean, straightforward interface for validation
- **Minimal Inputs**: Only requires MCP payload and optional tool description
- **High Performance**: Built in Go for excellent performance
- **Easy Deployment**: Single binary deployment with Docker support
- **REST API**: Built-in HTTP API server using Gin framework
- **CLI Tool**: Command-line interface for validation
- **Extensible**: Easy to add new LLM providers and features
- **No External Dependencies**: Uses only standard Go libraries and direct API calls

## 📋 Requirements

- Go 1.21 or later
- OpenAI API key (or self-hosted LLM endpoint)

## 🏗️ Architecture

```
mcp-proxy/
├── cmd/                    # Application entry points
│   ├── api/               # HTTP API server
│   └── cli/               # Command-line interface
├── mcp-threat/            # Core library packages
│   ├── types/             # Data structures and types
│   ├── config/            # Configuration management
│   ├── providers/         # LLM provider implementations
│   ├── validators/        # Validation logic
│   └── client/            # Main client interface
├── examples/               # Usage examples
├── Dockerfile             # Containerization
├── Makefile               # Build automation
├── go.mod                 # Go module definition
└── README_GO.md           # This file
```

## 🚀 Quick Start

### 1. Install the Library

```bash
# Install the specific version
go get github.com/akto-api-security/akto/libs/mcp-proxy@v1.0.1_mcpthreat
```

**Note**: This library uses version `v1.0.1_mcpthreat` to avoid conflicts with existing tags. Always specify the version when installing.

### 2. Clone and Setup

```bash
git clone <your-repo>
cd mcp-proxy
go mod download
```

### 2. Set Environment Variables

```bash
export OPENAI_API_KEY="your-api-key-here"
export MCP_LLM_PROVIDER="openai"
export MCP_LLM_MODEL="gpt-5"
```

### 3. Run the API Server

```bash
# Build and run
make build-api
./bin/mcp-api

# Or run directly
make run-api
```

### 4. Use the CLI

```bash
# Build and run
make build-cli
./bin/mcp-cli --payload '{"method": "tools/list"}'

# Or run directly
make run-cli
```

## 📦 Versioning

This library uses version `v1.0.1_mcpthreat` to avoid conflicts with existing tags in the repository.

### Installing Specific Version
```bash
go get github.com/akto-api-security/akto/libs/mcp-proxy@v1.0.1_mcpthreat
```

### In go.mod
```go
require (
    github.com/akto-api-security/akto/libs/mcp-proxy v1.0.1_mcpthreat
)
```

## 🔧 Configuration

### Environment Variables

```bash
# LLM Configuration
export MCP_LLM_PROVIDER="openai"           # openai, self_hosted
export MCP_LLM_API_KEY="your-api-key"      # API key for the provider
export MCP_LLM_MODEL="gpt-5"               # Model name (default: gpt-5)
export MCP_LLM_BASE_URL=""                 # Custom base URL (for self-hosted)
export MCP_LLM_TIMEOUT="60"                # Timeout in seconds
export MCP_LLM_TEMPERATURE="0.0"           # Temperature for generation

# App Configuration
export MCP_DEBUG="false"                   # Debug mode
export PORT="8080"                         # HTTP server port
```

### Configuration File

Create a `.env` file in your project root:

```env
# LLM Configuration
MCP_LLM_PROVIDER=openai
OPENAI_API_KEY=your-api-key-here
MCP_LLM_MODEL=gpt-5
MCP_LLM_TIMEOUT=60
MCP_LLM_TEMPERATURE=0.0

# App Configuration
MCP_DEBUG=false
PORT=8080
```

## 📚 Usage Examples

### As a Library

```go
package main

import (
    "context"
    "fmt"
    "log"

    "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/client"
    "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/config"
)

func main() {
    // Load configuration
    config, err := config.LoadConfigFromEnv()
    if err != nil {
        log.Fatalf("Failed to load config: %v", err)
    }

    // Create validator
    validator, err := client.NewMCPValidatorWithConfig(config)
    if err != nil {
        log.Fatalf("Failed to create validator: %v", err)
    }
    defer validator.Close()

    // Validate a request
    ctx := context.Background()
    mcpPayload := map[string]interface{}{
        "method": "tools/call",
        "params": map[string]interface{}{
            "name": "file_system/read_file",
            "arguments": map[string]interface{}{
                "path": "../../../etc/passwd",
            },
        },
    }

    // Optional tool description
    toolDescription := "File system operations for reading and writing files"

    response := validator.ValidateRequest(ctx, mcpPayload, &toolDescription)
    
    if response.Success {
        fmt.Printf("Malicious: %v\n", response.Verdict.IsMaliciousRequest)
        fmt.Printf("Confidence: %.2f\n", response.Verdict.Confidence)
        fmt.Printf("Action: %s\n", response.Verdict.PolicyAction)
    } else {
        fmt.Printf("Validation failed: %v\n", response.Error)
    }
}
```

### Using Different LLM Providers

```go
// OpenAI with direct API calls (no external library)
validator, err := client.NewMCPValidator("openai", map[string]interface{}{
    "model":       "gpt-5",
    "api_key":     "your-api-key",
    "temperature": 0.0,
})

// Self-hosted LLM
validator, err := client.NewMCPValidator("self_hosted", map[string]interface{}{
    "endpoint": "http://localhost:8080/v1/chat/completions",
    "model":    "llama2-7b",
    "api_key":  "optional-api-key",
})
```

## 🌐 HTTP API

### Start the Server

```bash
make run-api
# Server will be available at http://localhost:8080
```

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | API information |
| `POST` | `/api/v1/validate` | Generic validation |
| `POST` | `/api/v1/validate/request` | Request validation |
| `POST` | `/api/v1/validate/response` | Response validation |

### Required Inputs

The API only requires two inputs:

1. **`mcp_payload`** (required): The MCP request or response to validate
2. **`tool_description`** (optional): Description of the tool/resource being accessed

### Example API Usage

```bash
# Validate a request
curl -X POST http://localhost:8080/api/v1/validate/request \
  -H "Content-Type: application/json" \
  -d '{
    "mcp_payload": {"method": "tools/list"},
    "tool_description": "List available tools"
  }'

# Validate without tool description
curl -X POST http://localhost:8080/api/v1/validate/request \
  -H "Content-Type: application/json" \
  -d '{
    "mcp_payload": {"method": "tools/list"}
  }'
```

## 🖥️ Command Line Interface

### Basic Usage

```bash
# Validate a request
./bin/mcp-cli --payload '{"method": "tools/list"}'

# Validate from file
./bin/mcp-cli --payload @request.json

# Validate with tool description
./bin/mcp-cli --payload @request.json --tool-desc "File system operations"

# Validate response
./bin/mcp-cli --payload @response.json --type response

# Use specific provider
./bin/mcp-cli --payload @request.json --provider openai --model gpt-5

# Self-hosted LLM
./bin/mcp-cli --payload @request.json \
  --provider self_hosted \
  --endpoint http://localhost:8080/v1

# Verbose output with JSON
./bin/mcp-cli --payload @request.json --verbose --json
```

### CLI Options

```bash
./bin/mcp-cli --help

Flags:
  --payload string        MCP payload (raw JSON string or @path/to/file.json) [REQUIRED]
  --type string          Type of validation: request or response (default "request")
  --tool-desc string     Tool/Resource description (string or @file.txt) [OPTIONAL]
  --provider string      LLM provider to use (openai, self_hosted)
  --model string         Model name (overrides environment config)
  --endpoint string      Custom endpoint for self-hosted LLMs
  --api-key string       API key (overrides environment config)
  --json                 Output result in JSON format
  --verbose              Verbose output
  --timeout duration     Timeout for validation (default 1m0s)
```

## 🐳 Docker

### Build and Run

```bash
# Build Docker image
make docker-build

# Run container
make docker-run

# Or manually
docker run -p 8080:8080 --env-file .env mcp-proxy:latest
```

### Docker Compose

Create a `docker-compose.yml`:

```yaml
version: '3.8'
services:
  mcp-proxy:
    build: .
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - MCP_LLM_PROVIDER=openai
      - MCP_LLM_MODEL=gpt-5
      - MCP_DEBUG=false
    restart: unless-stopped
```

## 🧪 Testing

### Run Tests

```bash
# Run all tests
make test

# Run with coverage
go test -v -coverprofile=coverage.out ./...
go tool cover -html=coverage.out -o coverage.html
```

## 🔍 Code Quality

### Basic Checks

```bash
# Format code
make fmt

# Run go vet
make vet

# Run all quality checks
make all
```

## 📦 Building

### Build Binaries

```bash
# Build both API and CLI
make build

# Build only API
make build-api

# Build only CLI
make build-cli
```

### Install System-Wide

```bash
# Install to /usr/local/bin
make install

# Uninstall
make uninstall
```

## 🚀 Development

### Dependencies

```bash
# Download dependencies
make deps

# Tidy dependencies
make deps-tidy
```

## 🔒 Security Features

- **Input Validation**: Comprehensive input validation and sanitization
- **Secure Configuration**: Environment-based configuration management
- **Audit Logging**: Comprehensive logging for security analysis
- **Direct API Calls**: No external libraries for OpenAI API calls

## 🌟 Extensibility

The library is designed to be easily extensible:

### Adding New LLM Providers

1. Implement the `LLMProvider` interface in `pkg/providers/`
2. Add the provider type to constants
3. Update the `GetProvider` function

### Adding New Validation Types

1. Create a new validator struct in `pkg/validators/`
2. Implement the validation logic
3. Add to the main client if needed

### Adding New Features

The modular design makes it easy to add:
- New threat categories
- Additional validation rules
- Caching mechanisms
- Rate limiting
- Metrics and monitoring

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues**: Create an issue on GitHub
- **Documentation**: Check the examples and code comments
- **Community**: Join our discussions

## 🔄 Migration from Python

If you're migrating from the Python version:

1. **Configuration**: Update environment variable names (see Configuration section)
2. **API Changes**: The Go API is simplified to only require `mcp_payload` and optional `tool_description`
3. **Performance**: Expect significant performance improvements
4. **Deployment**: Use the single binary or Docker image
5. **Dependencies**: No external LLM libraries needed

## 📈 Future Enhancements

The simplified design makes it easy to add:

- [ ] GraphQL API support
- [ ] WebSocket support for real-time validation
- [ ] Advanced caching strategies
- [ ] Metrics and monitoring (Prometheus)
- [ ] Kubernetes deployment manifests
- [ ] More LLM provider integrations
- [ ] Machine learning model support for local validation
- [ ] Batch validation capabilities
- [ ] Custom validation rules
- [ ] Plugin system for extensibility
- [ ] Tool schema support
- [ ] Organization policy support

## 🔧 Technical Details

### Import Structure

The project uses relative imports for all internal packages:

```go
import (
    "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/client"
    "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/config"
    "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
    "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/validators"
)
```

### OpenAI Integration

Instead of using external libraries, the project makes direct HTTP calls to the OpenAI API:

```go
// Direct API call to OpenAI
req, err := http.NewRequestWithContext(ctx, "POST", "https://api.openai.com/v1/chat/completions", bytes.NewBuffer(jsonData))
req.Header.Set("Authorization", "Bearer "+apiKey)
```

This approach:
- Reduces external dependencies
- Provides better control over API calls
- Allows custom error handling
- Makes the codebase more self-contained

### Supported Providers

Currently, the library supports two LLM providers:

1. **OpenAI**: Direct API calls to OpenAI's chat completions endpoint
2. **Self-hosted**: Generic HTTP client for custom LLM endpoints

The modular design makes it easy to add more providers in the future. 