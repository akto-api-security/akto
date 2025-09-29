# MCP Proxy Integration with Guardrails

This document explains how to integrate the MCP Guardrails library with your MCP proxy server.

## Overview

The MCP Guardrails library provides security and compliance features for MCP (Model Context Protocol) servers by:

1. **Fetching Templates**: Periodically retrieving guardrail templates from your database-abstractor service
2. **Request Guardrails**: Validating and filtering incoming MCP requests
3. **Response Guardrails**: Sanitizing and filtering outgoing MCP responses
4. **Rate Limiting**: Controlling request rates to prevent abuse
5. **Data Sanitization**: Removing or masking sensitive information

## Environment Variables

Configure the guardrails system using these environment variables:

### Required
- `GUARDRAIL_SERVICE_URL`: URL of your database-abstractor service (e.g., `http://localhost:8080`)

### Optional
- `GUARDRAIL_SERVICE_TOKEN`: Authentication token for the service API
- `GUARDRAIL_REFRESH_INTERVAL`: Template refresh interval in minutes (default: 10)
- `GUARDRAIL_ENABLE_SANITIZATION`: Enable data sanitization (default: true)
- `GUARDRAIL_ENABLE_CONTENT_FILTERING`: Enable content filtering (default: true)
- `GUARDRAIL_ENABLE_RATE_LIMITING`: Enable rate limiting (default: true)
- `GUARDRAIL_ENABLE_INPUT_VALIDATION`: Enable input validation (default: true)
- `GUARDRAIL_ENABLE_OUTPUT_FILTERING`: Enable output filtering (default: true)
- `GUARDRAIL_ENABLE_LOGGING`: Enable logging (default: true)

## Integration Example

```go
package main

import (
    "log"
    "net/http"
    
    "github.com/akto-api-security/akto/libs/mcp-guardrails"
)

func main() {
    // Initialize guardrails integration
    guardrails, err := guardrails.NewMCPProxyIntegration()
    if err != nil {
        log.Fatalf("Failed to initialize guardrails: %v", err)
    }

    // Create HTTP server with guardrails middleware
    server := &http.Server{
        Addr:    ":8080",
        Handler: createHandler(guardrails),
    }

    log.Println("MCP Proxy with Guardrails starting on :8080")
    log.Fatal(server.ListenAndServe())
}

func createHandler(guardrails *guardrails.MCPProxyIntegration) http.Handler {
    mux := http.NewServeMux()
    
    mux.HandleFunc("/mcp", func(w http.ResponseWriter, r *http.Request) {
        handleMCPRequest(w, r, guardrails)
    })
    
    return mux
}

func handleMCPRequest(w http.ResponseWriter, r *http.Request, guardrails *guardrails.MCPProxyIntegration) {
    // Read request body
    requestBody, err := io.ReadAll(r.Body)
    if err != nil {
        http.Error(w, "Failed to read request body", http.StatusBadRequest)
        return
    }

    // Apply request guardrails
    blocked, reason, err := guardrails.RequestGuardrail(requestBody)
    if err != nil {
        log.Printf("Guardrail processing error: %v", err)
        http.Error(w, "Internal server error", http.StatusInternalServerError)
        return
    }

    if blocked {
        // Send error response
        errorResponse := map[string]interface{}{
            "jsonrpc": "2.0",
            "error": map[string]interface{}{
                "code":    -32603,
                "message": reason,
            },
        }
        w.Header().Set("Content-Type", "application/json")
        w.WriteHeader(http.StatusForbidden)
        json.NewEncoder(w).Encode(errorResponse)
        return
    }

    // Process the request (forward to your MCP server)
    responseData, err := forwardToMCPServer(requestBody)
    if err != nil {
        // Handle error
        return
    }

    // Apply response guardrails
    processedResponse, modified, err := guardrails.ResponseGuardrail(responseData)
    if err != nil {
        log.Printf("Response guardrail processing error: %v", err)
        processedResponse = responseData // Use original response if processing fails
    }

    if modified {
        log.Println("Response was modified by guardrails")
    }

    // Send response
    w.Header().Set("Content-Type", "application/json")
    w.WriteHeader(http.StatusOK)
    w.Write(processedResponse)
}
```

## API Reference

### NewMCPProxyIntegration()

Creates a new MCP proxy integration instance. Reads configuration from environment variables and starts the background template fetcher.

```go
guardrails, err := guardrails.NewMCPProxyIntegration()
```

### RequestGuardrail(requestData []byte) (bool, string, error)

Processes an incoming MCP request and determines if it should be blocked.

- **Parameters**: `requestData` - Raw JSON bytes of the MCP request
- **Returns**: 
  - `bool` - True if request should be blocked
  - `string` - Reason for blocking (if blocked)
  - `error` - Processing error (if any)

```go
blocked, reason, err := guardrails.RequestGuardrail(requestData)
```

### ResponseGuardrail(responseData []byte) ([]byte, bool, error)

Processes an outgoing MCP response and applies sanitization/filtering.

- **Parameters**: `responseData` - Raw JSON bytes of the MCP response
- **Returns**:
  - `[]byte` - Processed response data (may be modified)
  - `bool` - True if response was modified
  - `error` - Processing error (if any)

```go
processedResponse, modified, err := guardrails.ResponseGuardrail(responseData)
```

### GetStatus() map[string]interface{}

Returns the current status of the guardrails system.

```go
status := guardrails.GetStatus()
// Returns: {"templates_loaded": 5, "configs_loaded": 3, "engine_active": true}
```

### RefreshTemplates() error

Manually triggers a template refresh from the database-abstractor service.

```go
err := guardrails.RefreshTemplates()
```

## Endpoints

The integration provides these HTTP endpoints:

### `/mcp`
Main MCP endpoint that applies guardrails to requests and responses.

### `/health`
Health check endpoint that returns the status of the guardrails system.

```json
{
  "status": "healthy",
  "guardrails": {
    "templates_loaded": 5,
    "configs_loaded": 3,
    "engine_active": true
  }
}
```

### `/status`
Status endpoint that returns detailed guardrails information.

**GET**: Returns current status
**POST**: Triggers manual template refresh

## Template Fetching

The system automatically fetches guardrail templates from your database-abstractor service at regular intervals. The templates are used to:

1. **Define Sensitive Data Patterns**: Regex patterns for detecting and redacting sensitive information
2. **Set Content Filters**: Rules for blocking or warning about specific content
3. **Configure Rate Limits**: Request rate limiting parameters
4. **Define Validation Rules**: Input validation requirements

## Error Handling

The guardrails system is designed to be resilient:

- **Template Fetch Failures**: If template fetching fails, the system continues with previously loaded templates
- **Processing Errors**: If guardrail processing fails, requests/responses are allowed through with warnings logged
- **Invalid JSON**: Malformed requests/responses are blocked with appropriate error messages

## Security Considerations

1. **Authentication**: Use `GUARDRAIL_SERVICE_TOKEN` to authenticate with your database-abstractor service
2. **Network Security**: Ensure the connection between the proxy and database-abstractor is secure
3. **Template Validation**: The system validates templates before applying them
4. **Logging**: Enable logging to monitor guardrail activity and potential security issues

## Monitoring

Monitor the guardrails system through:

1. **Health Checks**: Use the `/health` endpoint for health monitoring
2. **Status Monitoring**: Use the `/status` endpoint for detailed status information
3. **Logs**: Monitor application logs for guardrail warnings and errors
4. **Metrics**: Track blocked requests and modified responses

## Troubleshooting

### Common Issues

1. **Template Fetch Failures**
   - Check `GUARDRAIL_SERVICE_URL` is correct
   - Verify network connectivity
   - Check authentication token if required

2. **High Request Blocking**
   - Review template configurations
   - Check if rate limits are too restrictive
   - Verify content filter rules

3. **Performance Issues**
   - Adjust `GUARDRAIL_REFRESH_INTERVAL` to reduce API calls
   - Monitor template complexity
   - Check if too many patterns are being applied

### Debug Mode

Enable debug logging by setting:
```bash
export GUARDRAIL_ENABLE_LOGGING=true
```

This will provide detailed logs of guardrail operations.
