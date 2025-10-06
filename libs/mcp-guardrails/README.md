# MCP Guardrails Library

A Golang library for implementing guardrails in MCP (Model Context Protocol) applications. This library fetches guardrail templates from an API and provides functions to modify requests and responses based on regex patterns defined in the templates.

## Features

- **Automatic Template Fetching**: Fetches guardrail templates from the API at regular intervals (default: 10 minutes)
- **Request/Response Modification**: Modifies requests and responses based on regex patterns in templates
- **YAML Template Parsing**: Parses YAML templates containing request_payload and response_payload filters
- **JSON Support**: Handles both string and JSON data modification
- **Health Monitoring**: Provides health status and template statistics
- **Sensitive Data Detection**: Detects and sanitizes sensitive data based on patterns
- **Thread-Safe**: All operations are thread-safe with proper locking

## Installation

```bash
go get github.com/akto-api-security/akto/libs/mcp-guardrails
```

## Quick Start

```go
package main

import (
    "fmt"
    "time"
    "github.com/akto-api-security/akto/libs/mcp-guardrails"
)

func main() {
    // Create configuration
    config := guardrails.ClientConfig{
        APIURL:        "http://localhost:8082",
        AuthToken:     "your-auth-token",
        FetchInterval: 10 * time.Minute,
    }

    // Create and start the guardrail engine
    engine := guardrails.NewGuardrailEngine(config)
    engine.Start()

    // Modify a request
    requestData := `{"method": "test", "data": "1234-5678-9012-3456"}`
    result := engine.ModifyRequest(requestData)
    
    if result.Blocked {
        fmt.Printf("Request blocked: %s\n", result.Reason)
    }
}
```

## API Reference

### ClientConfig

Configuration for the guardrail client.

```go
type ClientConfig struct {
    APIURL        string        // API base URL
    AuthToken     string        // Authentication token
    FetchInterval time.Duration // Template fetch interval
}
```

### GuardrailEngine

Main engine for managing guardrails.

#### Methods

- `NewGuardrailEngine(config ClientConfig) *GuardrailEngine` - Creates a new engine
- `Start()` - Starts periodic template fetching
- `Stop()` - Stops the engine
- `TriggerTemplateFetching() error` - Manually triggers template fetching
- `ModifyRequest(requestData string) ModificationResult` - Modifies a request
- `ModifyResponse(responseData string) ModificationResult` - Modifies a response
- `ModifyRequestJSON(requestData []byte) (ModificationResult, error)` - Modifies JSON request
- `ModifyResponseJSON(responseData []byte) (ModificationResult, error)` - Modifies JSON response
- `GetTemplates() map[string]ParsedTemplate` - Returns all loaded templates
- `GetTemplate(id string) (ParsedTemplate, bool)` - Returns a specific template
- `GetTemplateStats() map[string]interface{}` - Returns template statistics
- `SanitizeData(data string, patterns []string) string` - Sanitizes sensitive data
- `CheckForSensitiveData(data string, patterns []string) (bool, []string)` - Checks for sensitive data
- `IsHealthy() bool` - Checks if the engine is healthy
- `GetHealthStatus() map[string]interface{}` - Returns detailed health status

### ModificationResult

Result of request/response modification.

```go
type ModificationResult struct {
    Modified bool     `json:"modified"`  // Whether data was modified
    Blocked  bool     `json:"blocked"`  // Whether request/response was blocked
    Reason   string   `json:"reason"`   // Block reason (if blocked)
    Warnings []string `json:"warnings"` // Warning messages
    Data     string   `json:"data"`     // Modified data
}
```

## Template Format

Templates are YAML files with the following structure:

```yaml
id: PIIDataLeak
filter:
  or:
    - request_payload:
        regex:
          - "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"
    - response_payload:
        regex:
          - "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"
info:
  name: "PIIDataLeak"
  description: "PII Data Leak detection"
  severity: MEDIUM
  category:
    name: "PIIDataLeak"
    displayName: "PII Data Leak"
```

## Usage Examples

### Basic Usage

```go
// Create engine
config := guardrails.ClientConfig{
    APIURL:        "http://localhost:8082",
    AuthToken:     "testing",
    FetchInterval: 10 * time.Minute,
}
engine := guardrails.NewGuardrailEngine(config)
engine.Start()

// Modify request
requestData := `{"method": "test", "data": "1234-5678-9012-3456"}`
result := engine.ModifyRequest(requestData)

if result.Blocked {
    log.Printf("Request blocked: %s", result.Reason)
} else if result.Modified {
    log.Printf("Request modified: %s", result.Data)
}
```

### JSON Processing

```go
// Process JSON request
jsonRequest := []byte(`{"method": "test", "data": "1234-5678-9012-3456"}`)
result, err := engine.ModifyRequestJSON(jsonRequest)
if err != nil {
    log.Printf("Error: %v", err)
    return
}

if result.Blocked {
    log.Printf("JSON request blocked: %s", result.Reason)
}
```

### Health Monitoring

```go
// Check health
if !engine.IsHealthy() {
    log.Printf("Guardrail engine is not healthy")
}

// Get detailed health status
status := engine.GetHealthStatus()
fmt.Printf("Health: %v\n", status["healthy"])
fmt.Printf("Templates: %v\n", status["template_count"])
```

### Sensitive Data Handling

```go
// Check for sensitive data
data := "My credit card is 1234-5678-9012-3456"
patterns := []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`}

hasSensitive, matchedPatterns := engine.CheckForSensitiveData(data, patterns)
if hasSensitive {
    log.Printf("Sensitive data detected: %v", matchedPatterns)
}

// Sanitize data
sanitized := engine.SanitizeData(data, patterns)
log.Printf("Sanitized: %s", sanitized)
```

## Testing

Run the tests:

```bash
go test ./...
```

Run tests with coverage:

```bash
go test -cover ./...
```

Run benchmarks:

```bash
go test -bench=. ./...
```

## Integration with MCP Proxy

This library is designed to be imported by the MCP proxy application. The proxy can use it to:

1. Fetch guardrail templates from the API
2. Apply guardrails to incoming requests
3. Apply guardrails to outgoing responses
4. Monitor the health of the guardrail system

Example integration:

```go
// In your MCP proxy
import "github.com/akto-api-security/akto/libs/mcp-guardrails"

// Initialize guardrails
config := guardrails.ClientConfig{
    APIURL:        "http://localhost:8082",
    AuthToken:     "your-token",
    FetchInterval: 10 * time.Minute,
}
guardrailEngine := guardrails.NewGuardrailEngine(config)
guardrailEngine.Start()

// In your request handler
func handleRequest(requestData []byte) ([]byte, error) {
    result, err := guardrailEngine.ModifyRequestJSON(requestData)
    if err != nil {
        return nil, err
    }
    
    if result.Blocked {
        return nil, fmt.Errorf("request blocked: %s", result.Reason)
    }
    
    // Process the request...
    responseData := processRequest(result.Data)
    
    // Apply response guardrails
    responseResult, err := guardrailEngine.ModifyResponseJSON(responseData)
    if err != nil {
        return nil, err
    }
    
    if responseResult.Blocked {
        return nil, fmt.Errorf("response blocked: %s", responseResult.Reason)
    }
    
    return []byte(responseResult.Data), nil
}
```

## Configuration

The library supports the following configuration options:

- `APIURL`: Base URL of the API server
- `AuthToken`: Bearer token for API authentication
- `FetchInterval`: How often to fetch templates (default: 10 minutes)

## Error Handling

The library provides comprehensive error handling:

- Network errors during template fetching
- Invalid JSON/YAML parsing
- Regex compilation errors
- Template validation errors

All errors are logged and the library continues to operate with previously loaded templates.

## Performance

The library is optimized for performance:

- Thread-safe operations with minimal locking
- Efficient regex compilation and caching
- Minimal memory allocation
- Fast template matching

Benchmark results are available in the test suite.

## License

This library is part of the Akto project and follows the same licensing terms.