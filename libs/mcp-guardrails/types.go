package guardrails

import (
	"time"
)

// MCPGuardrailTemplate represents a guardrail template from the API
type MCPGuardrailTemplate struct {
	ID            string        `json:"id"`
	Author        string        `json:"author"`
	Content       string        `json:"content"`
	CreatedAt     int64         `json:"createdAt"`
	UpdatedAt     int64         `json:"updatedAt"`
	Hash          int64         `json:"hash"`
	Inactive      bool          `json:"inactive"`
	Source        string        `json:"source"`
	RepositoryURL *string       `json:"repositoryUrl,omitempty"`
	Info          *TemplateInfo `json:"info,omitempty"`
}

// TemplateInfo contains metadata about the template
type TemplateInfo struct {
	Name        string `yaml:"name"`
	Description string `yaml:"description"`
	Details     string `yaml:"details"`
	Impact      string `yaml:"impact"`
	Category    struct {
		Name        string `yaml:"name"`
		DisplayName string `yaml:"displayName"`
	} `yaml:"category"`
	SubCategory string `yaml:"subCategory"`
	Severity    string `yaml:"severity"`
}

// ParsedTemplate represents a parsed YAML template
type ParsedTemplate struct {
	ID     string       `yaml:"id"`
	Filter Filter       `yaml:"filter"`
	Info   TemplateInfo `yaml:"info"`
}

// Filter contains the regex patterns for request and response payloads
type Filter struct {
	Or []FilterCondition `yaml:"or"`
}

// FilterCondition represents a single filter condition
type FilterCondition struct {
	RequestPayload  *PayloadFilter `yaml:"request_payload,omitempty"`
	ResponsePayload *PayloadFilter `yaml:"response_payload,omitempty"`
}

// PayloadFilter contains regex patterns for payload filtering
type PayloadFilter struct {
	Regex []string `yaml:"regex"`
}

// APIResponse represents the response from the fetchGuardrailTemplates API
type APIResponse struct {
	MCPGuardrailTemplates []MCPGuardrailTemplate `json:"mcpGuardrailTemplates"`
}

// GuardrailClient handles fetching and managing guardrail templates
type GuardrailClient struct {
	APIURL        string
	AuthToken     string
	Templates     map[string]ParsedTemplate
	LastFetch     time.Time
	FetchInterval time.Duration
}

// ModificationResult represents the result of request/response modification
type ModificationResult struct {
	Modified bool     `json:"modified"`
	Blocked  bool     `json:"blocked"`
	Reason   string   `json:"reason,omitempty"`
	Warnings []string `json:"warnings,omitempty"`
	Data     string   `json:"data"`
}

// ClientConfig holds configuration for the guardrail client
type ClientConfig struct {
	APIURL        string
	AuthToken     string
	FetchInterval time.Duration
}
