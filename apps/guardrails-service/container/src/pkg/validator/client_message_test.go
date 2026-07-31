package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

func TestClientMessageForPolicy(t *testing.T) {
	const fallback = "Request blocked by guardrail policy (blocked host pattern: chatgpt.com)"

	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "configured"}, BlockedMessage: "Contact security@acme.com"},
		{Info: types.PolicyInfo{Name: "blank"}, BlockedMessage: "   "},
		{Info: types.PolicyInfo{Name: "unset"}},
	}

	tests := []struct {
		name       string
		policyName string
		want       string
	}{
		{"configured message wins", "configured", "Contact security@acme.com"},
		{"whitespace-only falls back", "blank", fallback},
		{"unset falls back", "unset", fallback},
		{"unknown policy falls back", "missing", fallback},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := clientMessageForPolicy(policies, tt.policyName, fallback); got != tt.want {
				t.Errorf("clientMessageForPolicy(%q) = %q, want %q", tt.policyName, got, tt.want)
			}
		})
	}
}
