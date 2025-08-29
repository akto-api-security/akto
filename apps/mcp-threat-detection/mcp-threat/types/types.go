package types

import (
	"encoding/json"
)

// PolicyAction represents the recommended action for a validation result
type PolicyAction string

const (
	PolicyActionBlock PolicyAction = "block"
	PolicyActionAllow PolicyAction = "allow"
)

// ThreatCategory represents different types of threats
type ThreatCategory string

const (
	// OWASP LLM Categories
	ThreatCategoryLLM01 ThreatCategory = "LLM01"
	ThreatCategoryLLM02 ThreatCategory = "LLM02"
	ThreatCategoryLLM03 ThreatCategory = "LLM03"
	ThreatCategoryLLM04 ThreatCategory = "LLM04"
	ThreatCategoryLLM05 ThreatCategory = "LLM05"
	ThreatCategoryLLM06 ThreatCategory = "LLM06"
	ThreatCategoryLLM07 ThreatCategory = "LLM07"
	ThreatCategoryLLM08 ThreatCategory = "LLM08"
	ThreatCategoryLLM09 ThreatCategory = "LLM09"
	ThreatCategoryLLM10 ThreatCategory = "LLM10"

	// MCP-specific Categories
	ThreatCategoryToolAbuse          ThreatCategory = "TOOL_ABUSE"
	ThreatCategorySchemaViolation    ThreatCategory = "SCHEMA_VIOLATION"
	ThreatCategorySSRF               ThreatCategory = "SSRF"
	ThreatCategoryFSTraversal        ThreatCategory = "FS_TRAVERSAL"
	ThreatCategoryCodeExec           ThreatCategory = "CODE_EXEC"
	ThreatCategoryDataExfil          ThreatCategory = "DATA_EXFIL"
	ThreatCategorySecrets            ThreatCategory = "SECRETS"
	ThreatCategoryPII                ThreatCategory = "PII"
	ThreatCategoryDOS                ThreatCategory = "DOS"
	ThreatCategoryEmbeddingPoison    ThreatCategory = "EMBEDDING_POISON"
	ThreatCategoryToolChaining       ThreatCategory = "TOOL_CHAINING"
	ThreatCategoryEnvAbuse           ThreatCategory = "ENV_ABUSE"
	ThreatCategoryRecursiveInjection ThreatCategory = "RECURSIVE_INJECTION"
	ThreatCategoryOOBPromptInjection ThreatCategory = "OOB_PROMPT_INJECTION"
	ThreatCategorySuspiciousKeyword  ThreatCategory = "SUSPICIOUS_WORD"
)

// Verdict represents the validation result
type Verdict struct {
	IsMaliciousRequest bool             `json:"is_malicious_request"`
	Confidence         float64          `json:"confidence"`
	Categories         []ThreatCategory `json:"categories"`
	Evidence           []string         `json:"evidence"`
	PolicyAction       PolicyAction     `json:"policy_action"`
	Reasoning          string           `json:"reasoning"`
	Raw                json.RawMessage  `json:"raw"`
}

// ValidationRequest represents a request for validation
type ValidationRequest struct {
	MCPPayload      interface{} `json:"mcp_payload"`
	ToolDescription *string     `json:"tool_description,omitempty"`
}

// ValidationResponse represents the response from validation
type ValidationResponse struct {
	Success        bool     `json:"success"`
	Verdict        *Verdict `json:"verdict,omitempty"`
	Error          *string  `json:"error,omitempty"`
	ProcessingTime float64  `json:"processing_time_ms"`
}

// LLMConfig represents configuration for LLM providers
type LLMConfig struct {
	ProviderType string  `json:"provider_type"`
	APIKey       *string `json:"api_key,omitempty"`
	Model        string  `json:"model"`
	BaseURL      *string `json:"base_url,omitempty"`
	Timeout      int     `json:"timeout"`
	Temperature  float64 `json:"temperature"`
}

// AppConfig represents the main application configuration
type AppConfig struct {
	LLM   LLMConfig `json:"llm"`
	Debug bool      `json:"debug"`
}

// ChatMessage represents a message in the LLM conversation
type ChatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// LLMRequest represents a request to the LLM
type LLMRequest struct {
	Model       string        `json:"model"`
	Messages    []ChatMessage `json:"messages"`
	Temperature float64       `json:"temperature"`
	MaxTokens   int           `json:"max_tokens"`
}

// LLMResponse represents a response from the LLM
type LLMResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
	Error *struct {
		Message string `json:"message"`
	} `json:"error,omitempty"`
}

// String returns the string representation of PolicyAction
func (pa PolicyAction) String() string {
	return string(pa)
}

// String returns the string representation of ThreatCategory
func (tc ThreatCategory) String() string {
	return string(tc)
}

// IsValid checks if a PolicyAction is valid
func (pa PolicyAction) IsValid() bool {
	switch pa {
	case PolicyActionBlock, PolicyActionAllow:
		return true
	default:
		return false
	}
}

// IsValid checks if a ThreatCategory is valid
func (tc ThreatCategory) IsValid() bool {
	validCategories := []ThreatCategory{
		ThreatCategoryLLM01, ThreatCategoryLLM02,
		ThreatCategoryLLM03, ThreatCategoryLLM04,
		ThreatCategoryLLM05, ThreatCategoryLLM06,
		ThreatCategoryLLM07, ThreatCategoryLLM08,
		ThreatCategoryLLM09, ThreatCategoryLLM10,
		ThreatCategoryToolAbuse,
		ThreatCategorySchemaViolation, ThreatCategorySSRF,
		ThreatCategoryFSTraversal, ThreatCategoryCodeExec,
		ThreatCategoryDataExfil, ThreatCategorySecrets,
		ThreatCategoryPII, ThreatCategoryDOS, ThreatCategoryEmbeddingPoison,
		ThreatCategoryToolChaining, ThreatCategoryEnvAbuse,
		ThreatCategoryRecursiveInjection, ThreatCategoryOOBPromptInjection,
	}

	for _, valid := range validCategories {
		if tc == valid {
			return true
		}
	}
	return false
}

// NewVerdict creates a new Verdict with default values
func NewVerdict() *Verdict {
	return &Verdict{
		IsMaliciousRequest: false,
		Confidence:         0.0,
		Categories:         []ThreatCategory{},
		Evidence:           []string{},
		PolicyAction:       PolicyActionAllow,
		Reasoning:          "",
		Raw:                json.RawMessage{},
	}
}

// NewValidationResponse creates a new ValidationResponse
func NewValidationResponse() *ValidationResponse {
	return &ValidationResponse{
		Success:        false,
		Verdict:        nil,
		Error:          nil,
		ProcessingTime: 0.0,
	}
}

// SetError sets an error message on the ValidationResponse
func (vr *ValidationResponse) SetError(err string) {
	vr.Success = false
	vr.Error = &err
}

// SetSuccess sets the response as successful with a verdict
func (vr *ValidationResponse) SetSuccess(verdict *Verdict, processingTime float64) {
	vr.Success = true
	vr.Verdict = verdict
	vr.ProcessingTime = processingTime
	vr.Error = nil
}

// AddCategory adds a threat category if it's valid
func (v *Verdict) AddCategory(category ThreatCategory) {
	if category.IsValid() {
		v.Categories = append(v.Categories, category)
	}
}

// AddEvidence adds evidence to the verdict
func (v *Verdict) AddEvidence(evidence string) {
	v.Evidence = append(v.Evidence, evidence)
}

// IsBlocked returns true if the verdict recommends blocking
func (v *Verdict) IsBlocked() bool {
	return v.PolicyAction == PolicyActionBlock
}
