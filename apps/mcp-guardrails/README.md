# MCP Guardrails Library

A Go library for implementing security guardrails in Model Context Protocol (MCP) proxy servers. This library provides comprehensive data sanitization, content filtering, rate limiting, and input validation capabilities to secure MCP communications.

## Features

- **Data Sanitization**: Automatically redact sensitive information like credit cards, SSNs, emails, API keys, and passwords
- **Content Filtering**: Block or warn about malicious content, SQL injection attempts, XSS, and other security threats
- **Rate Limiting**: Implement token bucket rate limiting to prevent abuse
- **Input Validation**: Validate MCP requests and parameters
- **Output Filtering**: Filter and sanitize MCP responses
- **Comprehensive Logging**: Detailed logging of all guardrail operations
- **Extensible**: Easy to add custom patterns and filters

## Installation

```bash
go get github.com/akto/mcp-guardrails
```

## Quick Start

```go
package main

import (
    "encoding/json"
    "log"
    "time"
    
    "github.com/akto/mcp-guardrails"
)

func main() {
    // Create guardrail configuration
    config := &guardrails.GuardrailConfig{
        EnableDataSanitization: true,
        SensitiveFields:        []string{"password", "api_key", "secret"},
        
        EnableContentFiltering: true,
        BlockedKeywords:        []string{"malicious", "dangerous"},
        
        EnableRateLimiting: true,
        RateLimitConfig: guardrails.RateLimitConfig{
            RequestsPerMinute: 100,
            BurstSize:         10,
            WindowSize:        time.Minute,
        },
        
        EnableInputValidation: true,
        ValidationRules: map[string]string{
            "method": "required",
        },
        
        EnableLogging: true,
        LogLevel:      "INFO",
    }

    // Create guardrail engine
    engine := guardrails.NewGuardrailEngine(config)

    // Process MCP response
    response := &guardrails.MCPResponse{
        ID: "1",
        Result: json.RawMessage(`{
            "user": {
                "name": "John Doe",
                "email": "john@example.com",
                "password": "secret123",
                "credit_card": "1234-5678-9012-3456"
            }
        }`),
    }

    result := engine.ProcessResponse(response)
    
    if result.Blocked {
        log.Printf("Response blocked: %s", result.BlockReason)
        return
    }

    if len(result.Warnings) > 0 {
        log.Printf("Warnings: %v", result.Warnings)
    }

    // Use sanitized response
    sanitizedResponse := result.SanitizedResponse
    log.Printf("Response sanitized successfully")
}
```

## Configuration

### GuardrailConfig

The main configuration struct that controls all guardrail features:

```go
type GuardrailConfig struct {
    // Data Sanitization
    EnableDataSanitization bool     `json:"enable_data_sanitization"`
    SensitiveFields        []string `json:"sensitive_fields"`
    RedactionPatterns      []string `json:"redaction_patterns"`
    
    // Content Filtering
    EnableContentFiltering bool     `json:"enable_content_filtering"`
    BlockedKeywords        []string `json:"blocked_keywords"`
    AllowedDomains         []string `json:"allowed_domains"`
    
    // Rate Limiting
    EnableRateLimiting bool          `json:"enable_rate_limiting"`
    RateLimitConfig    RateLimitConfig `json:"rate_limit_config"`
    
    // Input Validation
    EnableInputValidation bool              `json:"enable_input_validation"`
    ValidationRules       map[string]string `json:"validation_rules"`
    
    // Output Filtering
    EnableOutputFiltering bool     `json:"enable_output_filtering"`
    OutputFilters         []string `json:"output_filters"`
    
    // Logging
    EnableLogging bool   `json:"enable_logging"`
    LogLevel      string `json:"log_level"`
}
```

### Rate Limit Configuration

```go
type RateLimitConfig struct {
    RequestsPerMinute int           `json:"requests_per_minute"`
    BurstSize         int           `json:"burst_size"`
    WindowSize        time.Duration `json:"window_size"`
}
```

## Usage Examples

### Data Sanitization

```go
config := &guardrails.GuardrailConfig{
    EnableDataSanitization: true,
    SensitiveFields: []string{"password", "api_key", "secret"},
}

engine := guardrails.NewGuardrailEngine(config)

// Add custom sensitive pattern
customPattern := guardrails.SensitiveDataPattern{
    Name:        "custom_id",
    Pattern:     `\b[A-Z]{2}\d{6}\b`,
    Replacement: "***REDACTED_ID***",
    Description: "Custom ID format",
}
engine.AddSensitivePattern(customPattern)
```

### Content Filtering

```go
config := &guardrails.GuardrailConfig{
    EnableContentFiltering: true,
}

engine := guardrails.NewGuardrailEngine(config)

// Add custom content filter
filter := guardrails.ContentFilter{
    Type:        "keyword",
    Pattern:     "internal",
    Action:      "warn",
    Description: "Internal information detected",
}
engine.AddContentFilter(filter)
```

### Rate Limiting

```go
config := &guardrails.GuardrailConfig{
    EnableRateLimiting: true,
    RateLimitConfig: guardrails.RateLimitConfig{
        RequestsPerMinute: 60,
        BurstSize:         5,
        WindowSize:        time.Minute,
    },
}

engine := guardrails.NewGuardrailEngine(config)

// Process requests (rate limiting is automatic)
request := &guardrails.MCPRequest{
    ID:     "test",
    Method: "tools/list",
}

result := engine.ProcessRequest(request)
if result.Blocked {
    log.Printf("Request blocked: %s", result.BlockReason)
}
```

### Input Validation

```go
config := &guardrails.GuardrailConfig{
    EnableInputValidation: true,
    ValidationRules: map[string]string{
        "method": "required",
        "id":     "alphanumeric",
    },
}

engine := guardrails.NewGuardrailEngine(config)
```

## Default Patterns

The library includes built-in patterns for common sensitive data:

- **Credit Card Numbers**: `\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b`
- **Social Security Numbers**: `\b\d{3}-\d{2}-\d{4}\b`
- **Email Addresses**: `\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b`
- **Phone Numbers**: `\b\d{3}[-.]?\d{3}[-.]?\d{4}\b`
- **API Keys**: `(api[_-]?key|access[_-]?token|secret[_-]?key)\s*[:=]\s*["']?[A-Za-z0-9]{20,}["']?`
- **Passwords**: `(password|passwd|pwd)\s*[:=]\s*["']?[^"'\s]+["']?`
- **IP Addresses**: `\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b`
- **AWS Access Keys**: `AKIA[0-9A-Z]{16}`
- **Private Keys**: `-----BEGIN\s+(?:RSA\s+)?PRIVATE\s+KEY-----`

## Default Content Filters

Built-in content filters for security threats:

- **Code Execution**: `(eval|exec|system|shell_exec)`
- **SQL Injection**: `(union\s+select|drop\s+table|delete\s+from)`
- **XSS**: `<script[^>]*>.*?</script>`
- **Sensitive Keywords**: `password`, `secret`, `token`, `admin`, `root`

## API Reference

### Main Functions

- `NewGuardrailEngine(config *GuardrailConfig) *GuardrailEngine`
- `ProcessResponse(response *MCPResponse) *GuardrailResult`
- `ProcessRequest(request *MCPRequest) *GuardrailResult`
- `AddSensitivePattern(pattern SensitiveDataPattern)`
- `AddContentFilter(filter ContentFilter)`
- `UpdateConfig(config *GuardrailConfig)`

### Data Structures

- `MCPRequest`: MCP request structure
- `MCPResponse`: MCP response structure
- `GuardrailResult`: Result of guardrail processing
- `SensitiveDataPattern`: Pattern for sensitive data detection
- `ContentFilter`: Content filtering rule
- `LogEntry`: Log entry structure

## Testing

Run the test suite:

```bash
go test ./...
```

Run tests with coverage:

```bash
go test -cover ./...
```

## Integration with MCP Proxy

This library is designed to be integrated into MCP proxy servers. Here's a typical integration pattern:

```go
// In your MCP proxy
func handleMCPResponse(originalResponse *MCPResponse) *MCPResponse {
    result := guardrailEngine.ProcessResponse(originalResponse)
    
    if result.Blocked {
        // Return error response
        return &MCPResponse{
            ID: originalResponse.ID,
            Error: &MCPError{
                Code:    403,
                Message: result.BlockReason,
            },
        }
    }
    
    // Return sanitized response
    return result.SanitizedResponse
}
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Security

This library is designed for security but should be used as part of a comprehensive security strategy. Always:

- Keep the library updated
- Review and customize patterns for your specific needs
- Monitor logs for security events
- Test thoroughly in your environment
- Consider additional security measures 