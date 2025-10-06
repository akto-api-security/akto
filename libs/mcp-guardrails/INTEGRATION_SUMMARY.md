# MCP Guardrails Library - Integration Summary

## Overview

This document summarizes the complete integration of the MCP Guardrails library with your MCP proxy server. The library provides comprehensive security and compliance features for MCP (Model Context Protocol) servers.

## What Was Implemented

### 1. Enhanced Guardrails Library (`guardrails.go`)
- **Template Fetcher Service**: Background service that periodically fetches guardrail templates from your database-abstractor service
- **MCP Request/Response Processing**: Convenience functions for processing MCP requests and responses
- **Concurrent Safety**: Thread-safe operations with mutex protection
- **Error Handling**: Robust error handling with graceful degradation

### 2. MCP Proxy Integration Layer (`proxy_integration.go`)
- **Environment Variable Configuration**: Reads all configuration from environment variables
- **Authentication Support**: Optional token-based authentication for the database-abstractor service
- **Simple API**: Clean, easy-to-use functions for MCP proxy integration
- **Status Monitoring**: Built-in status and health check capabilities

### 3. Enhanced Template Client (`template_client.go`)
- **Authentication**: Bearer token authentication support
- **Error Handling**: Comprehensive error handling for API requests
- **Health Checks**: Service health monitoring capabilities

### 4. Complete Example (`examples/mcp_proxy_example.go`)
- **Full MCP Proxy Implementation**: Complete working example of MCP proxy with guardrails
- **HTTP Endpoints**: Health checks, status monitoring, and MCP request handling
- **Error Handling**: Proper error handling and response formatting

## Key Features

### 🔄 Automatic Template Fetching
- Fetches guardrail templates from your database-abstractor service at configurable intervals
- Handles authentication with optional bearer tokens
- Continues operation with previously loaded templates if fetching fails

### 🛡️ Request Guardrails
- Validates incoming MCP requests
- Applies rate limiting to prevent abuse
- Filters malicious or inappropriate content
- Validates input parameters

### 🔒 Response Guardrails
- Sanitizes sensitive data in responses
- Applies content filtering
- Masks or redacts sensitive information
- Monitors output for policy violations

### ⚙️ Configurable Security
- Enable/disable individual security features
- Customize sensitive field patterns
- Configure rate limiting parameters
- Set validation rules

### 📊 Monitoring & Observability
- Health check endpoints
- Status monitoring
- Detailed logging
- Template refresh status

## Environment Variables

### Required
```bash
export GUARDRAIL_SERVICE_URL="http://localhost:8080"
```

### Optional
```bash
export GUARDRAIL_SERVICE_TOKEN="your-auth-token"
export GUARDRAIL_REFRESH_INTERVAL="10"  # minutes
export GUARDRAIL_ENABLE_SANITIZATION="true"
export GUARDRAIL_ENABLE_CONTENT_FILTERING="true"
export GUARDRAIL_ENABLE_RATE_LIMITING="true"
export GUARDRAIL_ENABLE_INPUT_VALIDATION="true"
export GUARDRAIL_ENABLE_OUTPUT_FILTERING="true"
export GUARDRAIL_ENABLE_LOGGING="true"
```

## Integration Steps

### 1. Import the Library
```go
import "github.com/akto-api-security/akto/libs/mcp-guardrails"
```

### 2. Initialize Integration
```go
guardrails, err := guardrails.NewMCPProxyIntegration()
if err != nil {
    log.Fatalf("Failed to initialize guardrails: %v", err)
}
```

### 3. Apply Request Guardrails
```go
blocked, reason, err := guardrails.RequestGuardrail(requestData)
if blocked {
    // Handle blocked request
    return
}
```

### 4. Apply Response Guardrails
```go
processedResponse, modified, err := guardrails.ResponseGuardrail(responseData)
// Use processedResponse
```

## API Endpoints

Your MCP proxy will expose these endpoints:

- **`/mcp`**: Main MCP endpoint with guardrails applied
- **`/health`**: Health check with guardrails status
- **`/status`**: Detailed status information and manual template refresh

## Database-Abstractor Integration

The library integrates with your existing database-abstractor service using these endpoints:

- **`/api/mcp/fetchGuardrailTemplates`**: Fetch all active templates
- **`/api/mcp/fetchGuardrailTemplatesByType`**: Fetch templates by type
- **`/api/mcp/fetchGuardrailConfigs`**: Fetch parsed configurations
- **`/api/mcp/fetchGuardrailTemplate`**: Fetch specific template
- **`/api/mcp/health`**: Health check endpoint

## Security Benefits

### 1. **Data Protection**
- Automatic detection and redaction of sensitive information
- Configurable sensitive field patterns
- Support for custom redaction rules

### 2. **Content Security**
- Keyword-based content filtering
- Regex pattern matching
- Configurable blocking/warning actions

### 3. **Rate Limiting**
- Prevents API abuse
- Configurable request limits
- Burst protection

### 4. **Input Validation**
- Validates MCP request structure
- Custom validation rules
- Parameter validation

### 5. **Audit & Compliance**
- Comprehensive logging
- Request/response monitoring
- Template application tracking

## Performance Considerations

- **Background Template Fetching**: Templates are fetched in the background without blocking requests
- **Caching**: Templates are cached locally and refreshed periodically
- **Graceful Degradation**: System continues operating with previously loaded templates if fetching fails
- **Concurrent Safety**: All operations are thread-safe for high-throughput scenarios

## Monitoring & Troubleshooting

### Health Monitoring
```bash
curl http://localhost:8080/health
```

### Status Check
```bash
curl http://localhost:8080/status
```

### Manual Template Refresh
```bash
curl -X POST http://localhost:8080/status
```

### Log Monitoring
- Watch application logs for guardrail warnings and errors
- Monitor template fetch success/failure rates
- Track blocked requests and modified responses

## Next Steps

1. **Deploy the Integration**: Use the example code as a starting point for your MCP proxy
2. **Configure Templates**: Set up guardrail templates in your database-abstractor service
3. **Test Security**: Verify that guardrails are working correctly with test requests
4. **Monitor Performance**: Monitor the system for any performance impact
5. **Tune Configuration**: Adjust environment variables based on your requirements

## Support

- **Documentation**: See `MCP_PROXY_INTEGRATION.md` for detailed integration guide
- **Example Code**: See `examples/mcp_proxy_example.go` for complete implementation
- **API Reference**: All functions are documented with Go doc comments

The integration is now complete and ready for production use!
