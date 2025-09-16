package policies

import (
	"context"
	"strings"
	"testing"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
)

func TestPolicyValidator(t *testing.T) {
	// Create a temporary policy manager with test policies
	policyManager := NewFilePolicyManager("../../yaml_policies")
	validator := NewPolicyValidator(policyManager)

	tests := []struct {
		name        string
		payload     string
		expectMatch bool
		description string
	}{
		{
			name:        "Sensitive data detection - password",
			payload:     `{"method": "login", "password": "secret123"}`,
			expectMatch: true,
			description: "Should detect password in payload",
		},
		{
			name:        "Sensitive data detection - token",
			payload:     `{"method": "api_call", "token": "abc123"}`,
			expectMatch: true,
			description: "Should detect token in payload",
		},
		{
			name:        "Admin access detection",
			payload:     `{"method": "admin_action", "user": "admin"}`,
			expectMatch: true,
			description: "Should detect admin keyword",
		},
		{
			name:        "Phishing detection - verify account",
			payload:     `{"message": "Please verify your account to continue"}`,
			expectMatch: true,
			description: "Should detect phishing keywords",
		},
		{
			name:        "Clean payload",
			payload:     `{"method": "get_data", "id": "123"}`,
			expectMatch: false,
			description: "Should not match clean payload",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			request := &types.ValidationRequest{
				MCPPayload: tt.payload,
			}

			response := validator.Validate(context.Background(), request)

			if response.Error != nil {
				t.Errorf("Validation failed with error: %v", *response.Error)
				return
			}

			if response.Verdict == nil {
				t.Errorf("Expected verdict but got nil")
				return
			}

			if response.Verdict.IsMaliciousRequest != tt.expectMatch {
				t.Errorf("Expected malicious=%v, got %v. %s",
					tt.expectMatch, response.Verdict.IsMaliciousRequest, tt.description)
			}

			if tt.expectMatch && len(response.Verdict.Evidence) == 0 {
				t.Errorf("Expected evidence for malicious payload but got none")
			}

			if tt.expectMatch {
				// Verify that evidence contains a known validator type
				hasValidValidatorType := false
				for _, evidence := range response.Verdict.Evidence {
					if strings.Contains(evidence, "regex") ||
						strings.Contains(evidence, "contains_either") ||
						strings.Contains(evidence, "nlp_classification") {
						hasValidValidatorType = true
						break
					}
				}
				if !hasValidValidatorType {
					t.Errorf("Expected evidence to contain a valid validator type")
				}
			}
		})
	}
}

func TestPolicyValidatorRegex(t *testing.T) {
	validator := NewPolicyValidator(NewFilePolicyManager("../../yaml_policies"))

	tests := []struct {
		name    string
		payload string
		expect  bool
	}{
		{
			name:    "Password assignment",
			payload: `password: "secret123"`,
			expect:  true,
		},
		{
			name:    "Token assignment",
			payload: `token="abc123"`,
			expect:  true,
		},
		{
			name:    "Normal assignment",
			payload: `name: "john"`,
			expect:  false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			request := &types.ValidationRequest{
				MCPPayload: tt.payload,
			}

			response := validator.Validate(context.Background(), request)

			if response.Error != nil {
				t.Errorf("Validation failed with error: %v", *response.Error)
				return
			}

			if response.Verdict.IsMaliciousRequest != tt.expect {
				t.Errorf("Expected malicious=%v, got %v for payload: %s",
					tt.expect, response.Verdict.IsMaliciousRequest, tt.payload)
			}
		})
	}
}
