package guardrails

import (
	"encoding/json"
	"net/http"
	"time"
)

// MCPRequest represents an MCP server request
type MCPRequest struct {
	ID       string          `json:"id"`
	Method   string          `json:"method"`
	Params   json.RawMessage `json:"params,omitempty"`
	Metadata map[string]any  `json:"metadata,omitempty"`
}

// MCPResponse represents an MCP server response
type MCPResponse struct {
	ID       string          `json:"id"`
	Result   json.RawMessage `json:"result,omitempty"`
	Error    *MCPError       `json:"error,omitempty"`
	Metadata map[string]any  `json:"metadata,omitempty"`
}

// MCPError represents an MCP error response
type MCPError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

// GuardrailConfig holds configuration for different types of guardrails
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
	EnableRateLimiting bool            `json:"enable_rate_limiting"`
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

// RateLimitConfig holds rate limiting configuration
type RateLimitConfig struct {
	RequestsPerMinute int           `json:"requests_per_minute"`
	BurstSize         int           `json:"burst_size"`
	WindowSize        time.Duration `json:"window_size"`
}

// GuardrailResult represents the result of applying guardrails
type GuardrailResult struct {
	SanitizedResponse *MCPResponse `json:"sanitized_response,omitempty"`
	Blocked           bool         `json:"blocked"`
	BlockReason       string       `json:"block_reason,omitempty"`
	Warnings          []string     `json:"warnings,omitempty"`
	Logs              []LogEntry   `json:"logs,omitempty"`
}

// LogEntry represents a log entry for guardrail operations
type LogEntry struct {
	Timestamp time.Time `json:"timestamp"`
	Level     string    `json:"level"`
	Message   string    `json:"message"`
	Data      any       `json:"data,omitempty"`
}

// SensitiveDataPattern represents patterns for detecting sensitive data
type SensitiveDataPattern struct {
	Name        string `json:"name"`
	Pattern     string `json:"pattern"`
	Replacement string `json:"replacement"`
	Description string `json:"description"`
}

// ContentFilter represents content filtering rules
type ContentFilter struct {
	Type        string `json:"type"` // "keyword", "regex", "domain"
	Pattern     string `json:"pattern"`
	Action      string `json:"action"` // "block", "warn", "sanitize"
	Description string `json:"description"`
}

// YamlTemplate represents a YAML template from the database (matches Java YamlTemplate)
type YamlTemplate struct {
	ID            string `json:"id"`
	CreatedAt     int    `json:"createdAt"`
	Author        string `json:"author"`
	Source        string `json:"source"`
	UpdatedAt     int    `json:"updatedAt"`
	Hash          int    `json:"hash"`
	Content       string `json:"content"`
	Info          *Info  `json:"info"`
	Inactive      bool   `json:"inactive"`
	RepositoryURL string `json:"repositoryUrl"`
}

// Info represents template metadata (matches Java Info)
type Info struct {
	Name        string    `json:"name"`
	Description string    `json:"description"`
	Details     string    `json:"details"`
	Impact      string    `json:"impact"`
	Remediation string    `json:"remediation"`
	Category    *Category `json:"category"`
	SubCategory string    `json:"subCategory"`
	Severity    string    `json:"severity"`
	Tags        []string  `json:"tags"`
	References  []string  `json:"references"`
	Cwe         []string  `json:"cwe"`
	Cve         []string  `json:"cve"`
}

// Category represents a template category (matches Java Category)
type Category struct {
	Name        string `json:"name"`
	DisplayName string `json:"displayName"`
	ShortName   string `json:"shortName"`
}

// MCPGuardrailConfig represents a parsed guardrail configuration
type MCPGuardrailConfig struct {
	ID              string                 `json:"id"`
	Name            string                 `json:"name"`
	Description     string                 `json:"description"`
	Version         string                 `json:"version"`
	Author          string                 `json:"author"`
	CreatedAt       int                    `json:"createdAt"`
	UpdatedAt       int                    `json:"updatedAt"`
	Content         string                 `json:"content"`
	Type            string                 `json:"type"`
	Enabled         bool                   `json:"enabled"`
	Priority        int                    `json:"priority"`
	Configuration   map[string]interface{} `json:"configuration"`
	SensitiveFields []string               `json:"sensitiveFields"`
	ValidationRules map[string]string      `json:"validationRules"`
	OutputFilters   []string               `json:"outputFilters"`
	RateLimitConfig map[string]interface{} `json:"rateLimitConfig"`
}

// APIResponse represents the response structure from the database abstractor API
type APIResponse struct {
	McpGuardrailTemplates []YamlTemplate                `json:"mcpGuardrailTemplates"`
	McpGuardrailConfigs   map[string]MCPGuardrailConfig `json:"mcpGuardrailConfigs"`
	McpGuardrailTemplate  *YamlTemplate                 `json:"mcpGuardrailTemplate"`
	ActionErrors          []string                      `json:"actionErrors"`
}

// TemplateClient represents a client for fetching templates from the API
type TemplateClient struct {
	BaseURL    string
	HTTPClient *http.Client
	AuthToken  string
}
