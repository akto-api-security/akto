package policies

import "time"

// Policy represents a single validation policy
type Policy struct {
	ID     string `yaml:"id"`
	Filter Filter `yaml:"filter"`
}

// Filter contains the validation rules for request and response payloads
type Filter struct {
	RequestPayload  []PayloadValidator `yaml:"request_payload,omitempty"`
	ResponsePayload []PayloadValidator `yaml:"response_payload,omitempty"`
}

// PayloadValidator represents a single validation rule
type PayloadValidator struct {
	Regex             string             `yaml:"regex,omitempty"`
	ContainsEither    []string           `yaml:"contains_either,omitempty"`
	NLPClassification *NLPClassification `yaml:"nlp_classification,omitempty"`
}

// NLPClassification represents NLP-based validation configuration
type NLPClassification struct {
	Category string  `yaml:"category"`
	GT       float64 `yaml:"gt"` // Greater than threshold
}

// PolicyManager interface for loading and managing policies
type PolicyManager interface {
	LoadPolicies() ([]Policy, error)
	ReloadPolicies() error
	GetPolicyByID(id string) (*Policy, error)
	IsPolicyEnabled(id string) bool
}

// PolicyCache represents cached policy data
type PolicyCache struct {
	Policies []Policy  `json:"policies"`
	LastLoad time.Time `json:"last_load"`
	Version  string    `json:"version"`
}
