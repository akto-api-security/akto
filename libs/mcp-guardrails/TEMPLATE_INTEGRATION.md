# MCP Guardrail Template Integration

This document explains how MCP Guardrail policies are fetched from the database using YAML templates.

## Overview

The MCP Guardrail system now supports fetching policy templates from a database via the database-abstractor service. This allows for dynamic policy management and centralized configuration.

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Go Library    │───▶│ Database        │───▶│   MongoDB       │
│ (mcp-guardrails)│    │ Abstractor      │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Components

### 1. Database Layer (Java)

#### DTOs
- `YamlTemplate` - Represents a YAML template stored in the database (reuses existing class)
- `Info` - Metadata about the template (reuses existing class from test_editor)
- `Category` - Category information for organizing templates (reuses existing class)
- `MCPGuardrailType` - Enum defining different types of guardrails
- `MCPGuardrailConfig` - Parsed configuration from YAML templates

#### DAO
- `MCPGuardrailYamlTemplateDao` - Data access object for template operations
  - `fetchMCPGuardrailConfig()` - Fetch and parse all templates
  - `fetchActiveTemplates()` - Fetch only active templates
  - `fetchTemplatesByType()` - Fetch templates by guardrail type

#### Action
- `MCPGuardrailsAction` - REST API endpoints for template operations
  - `fetchMCPGuardrailTemplates` - Get all templates
  - `fetchMCPGuardrailTemplatesByType` - Get templates by type
  - `fetchMCPGuardrailConfigs` - Get parsed configurations
  - `fetchMCPGuardrailTemplate` - Get specific template by ID
  - `health` - Health check endpoint

### 2. API Endpoints

All endpoints are configured in `struts.xml` under the `/api/mcp/` namespace:

- `GET /api/mcp/health` - Health check
- `POST /api/mcp/fetchGuardrailTemplates` - Fetch all templates
- `POST /api/mcp/fetchGuardrailTemplatesByType` - Fetch templates by type
- `POST /api/mcp/fetchGuardrailConfigs` - Fetch parsed configurations
- `POST /api/mcp/fetchGuardrailTemplate` - Fetch specific template

### 3. Go Library Integration

#### New Types
- `YamlTemplate` - Go representation of database template (matches Java YamlTemplate)
- `Info` - Template metadata (matches Java Info)
- `Category` - Template category (matches Java Category)
- `MCPGuardrailConfig` - Go representation of parsed configuration
- `TemplateClient` - HTTP client for API communication
- `APIResponse` - Response structure from API calls

#### Template Client
```go
// Create a template client
client := guardrails.NewTemplateClient("http://localhost:8080")

// Fetch templates
templates, err := client.FetchGuardrailTemplates(true) // activeOnly=true
if err != nil {
    log.Fatal(err)
}

// Health check
err = client.HealthCheck()
if err != nil {
    log.Printf("API not available: %v", err)
}
```

#### Enhanced Guardrail Engine
```go
// Create engine with template client
engine := guardrails.NewGuardrailEngineWithClient(config, templateClient)

// Load templates from API
err := engine.LoadTemplatesFromAPI()
if err != nil {
    log.Printf("Failed to load templates: %v", err)
}

// Access loaded templates
templates := engine.GetAllTemplates()
configs := engine.GetAllConfigs()
```

## YAML Template Format

Templates are stored as YAML content in the database. Here's the structure:

```yaml
id: "data_sanitization_basic"
name: "Basic Data Sanitization"
description: "Sanitizes common sensitive data patterns"
version: "1.0.0"
type: "DATA_SANITIZATION"
enabled: true
priority: 100

configuration:
  sensitiveFields:
    - "password"
    - "api_key"
    - "secret"
  
  patterns:
    - name: "Credit Card"
      pattern: "\\b(?:\\d[ -]*?){13,16}\\b"
      replacement: "***CREDIT_CARD***"
  
  validationRules:
    method: "required"
    
  outputFilters:
    - "block_sensitive"

info:
  name: "Basic Data Sanitization"
  description: "Removes sensitive data patterns"
  category:
    name: "data_protection"
    displayName: "Data Protection"
  severity: "HIGH"
  tags:
    - "pii"
    - "data_protection"
```

## Guardrail Types

The system supports the following guardrail types:

- `DATA_SANITIZATION` - Remove or redact sensitive data
- `CONTENT_FILTERING` - Filter content based on rules
- `INPUT_VALIDATION` - Validate input parameters
- `OUTPUT_FILTERING` - Filter output content
- `RATE_LIMITING` - Limit request rates
- `CUSTOM` - Custom guardrail implementations

## Usage Examples

### 1. Basic Usage

```go
// Create template client
client := guardrails.NewTemplateClient("http://database-abstractor:8080")

// Create engine with client
engine := guardrails.NewGuardrailEngineWithClient(config, client)

// Load templates
if err := engine.LoadTemplatesFromAPI(); err != nil {
    log.Printf("Using default templates: %v", err)
}

// Process requests/responses
result := engine.ProcessResponse(response)
```

### 2. Type-Specific Loading

```go
// Load only data sanitization templates
err := engine.LoadTemplatesByType("DATA_SANITIZATION")
if err != nil {
    log.Printf("Failed to load data sanitization templates: %v", err)
}
```

### 3. Template Refresh

```go
// Periodically refresh templates
ticker := time.NewTicker(5 * time.Minute)
go func() {
    for range ticker.C {
        if err := engine.RefreshTemplates(); err != nil {
            log.Printf("Failed to refresh templates: %v", err)
        }
    }
}()
```

## Database Schema

Templates are stored in the `mcp_guardrail_yaml_templates` collection using the standard `YamlTemplate` schema:

- `id` (String) - Unique template identifier
- `createdAt` (int) - Creation timestamp
- `author` (String) - Template author
- `source` (String) - Template source (e.g., "AKTO_TEMPLATES")
- `updatedAt` (int) - Last update timestamp
- `hash` (int) - Content hash for change detection
- `content` (String) - YAML template content
- `info` (Info) - Template metadata
- `inactive` (boolean) - Whether template is active
- `repositoryUrl` (String) - Source repository URL

## Error Handling

The system gracefully handles various error scenarios:

1. **API Unavailable**: Falls back to default patterns
2. **Invalid Templates**: Logs errors and continues with valid templates
3. **Network Issues**: Retries with exponential backoff
4. **Parse Errors**: Skips invalid templates and logs warnings

## Security Considerations

1. **Authentication**: API endpoints should be secured with proper authentication
2. **Validation**: All YAML templates are validated before parsing
3. **Sanitization**: Template content is sanitized to prevent injection attacks
4. **Access Control**: Restrict template modification to authorized users

## Performance

- Templates are cached in memory for fast access
- Periodic refresh minimizes database load
- Lazy loading of templates by type when needed
- Connection pooling for database access

## Monitoring

Health check endpoint provides:
- Database connectivity status
- Template count
- Last successful refresh timestamp
- Error counts and rates
