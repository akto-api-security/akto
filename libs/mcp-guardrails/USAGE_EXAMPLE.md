# Using MCP Guardrails as a Library

## How to Import and Use

Once the `mcp-guardrails` library is published to GitHub, you can use it in any Go project:

### 1. Import the Library

```go
import "github.com/akto-api-security/akto/libs/mcp-guardrails"
```

### 2. Basic Usage Example

```go
package main

import (
    "encoding/json"
    "fmt"
    "time"
    
    "github.com/akto-api-security/akto/libs/mcp-guardrails"
)

func main() {
    // Create configuration
    config := &guardrails.GuardrailConfig{
        EnableDataSanitization: true,
        SensitiveFields:        []string{"password", "api_key", "secret"},
        EnableContentFiltering: true,
        BlockedKeywords:        []string{"malicious", "exploit"},
        EnableRateLimiting:     true,
        RateLimitConfig: guardrails.RateLimitConfig{
            RequestsPerMinute: 100,
            BurstSize:         10,
            WindowSize:        time.Minute,
        },
    }
    
    // Create engine
    engine := guardrails.NewGuardrailEngine(config)
    
    // Process a request
    request := &guardrails.MCPRequest{
        ID:     "req_1",
        Method: "tools/call",
        Params: json.RawMessage(`{"tool": "file_read", "path": "/etc/passwd"}`),
    }
    
    result := engine.ProcessRequest(request)
    
    if result.Blocked {
        fmt.Printf("Request blocked: %s\n", result.BlockReason)
    } else {
        fmt.Println("Request allowed")
    }
    
    // Process a response
    response := &guardrails.MCPResponse{
        ID: "resp_1",
        Result: json.RawMessage(`{
            "content": "user data with password: secret123",
            "api_key": "sk-1234567890"
        }`),
    }
    
    result = engine.ProcessResponse(response)
    
    if result.SanitizedResponse != nil {
        fmt.Println("Response sanitized successfully")
    }
}
```

### 3. Integration with MCP Proxy

```go
// In your MCP proxy server
func (s *MCPProxyServer) handleRequest(req *MCPRequest) *MCPResponse {
    // Apply guardrails before processing
    guardrailResult := s.guardrailEngine.ProcessRequest(req)
    
    if guardrailResult.Blocked {
        return &MCPResponse{
            ID: req.ID,
            Error: &MCPError{
                Code:    -32000,
                Message: "Request blocked by guardrails",
                Data:    guardrailResult.BlockReason,
            },
        }
    }
    
    // Process the request normally
    response := s.processRequest(req)
    
    // Apply guardrails to response
    responseResult := s.guardrailEngine.ProcessResponse(response)
    
    if responseResult.SanitizedResponse != nil {
        return responseResult.SanitizedResponse
    }
    
    return response
}
```

## Publishing Steps

To make this library available for import:

1. **Push to GitHub**: Ensure the code is in the `akto-api-security/akto` repository
2. **Tag a Release**: Create a git tag for versioning
3. **Go Proxy**: The Go module proxy will automatically index it
4. **Import**: Other projects can then import it using the full module path

## Current Status

✅ **Ready for local development** - Works within the Go workspace  
🔄 **Ready for publishing** - Module name updated to match GitHub repository  
⏳ **Waiting for publish** - Need to push to GitHub and create release tags
