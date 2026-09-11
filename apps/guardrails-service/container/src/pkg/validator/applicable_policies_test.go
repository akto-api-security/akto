package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// applicablePolicies is the single answer both enforcement (ValidateRequest/ValidateResponse)
// and the pre-flight gate (HasApplicablePolicies, used by /api/validate/file to skip reading
// content when nothing applies) are built on, so what it drops is what never gets enforced.
func TestApplicablePolicies(t *testing.T) {
	s := &Service{logger: zap.NewNop()}
	ctxFor := func(mcpServerName string, headers map[string]string) *mcp.ValidationContext {
		return &mcp.ValidationContext{McpServerName: mcpServerName, RequestHeaders: headers}
	}
	userPolicy := types.Policy{
		Info:         types.PolicyInfo{Name: "user-targeted"},
		UserMetadata: []types.AgenticUsers{{UserEmail: "someone@example.com"}},
	}
	untargeted := types.Policy{Info: types.PolicyInfo{Name: "everyone"}}

	t.Run("untargeted policy applies with no identity at all", func(t *testing.T) {
		if got := s.applicablePolicies([]types.Policy{untargeted}, ctxFor("", nil)); len(got) != 1 {
			t.Fatalf("expected the untargeted policy to apply, got %d", len(got))
		}
	})

	t.Run("user-targeted policy needs the matching email header", func(t *testing.T) {
		got := s.applicablePolicies([]types.Policy{userPolicy}, ctxFor("", nil))
		if len(got) != 0 {
			t.Fatalf("expected no policy without an email header, got %d", len(got))
		}

		got = s.applicablePolicies([]types.Policy{userPolicy},
			ctxFor("", map[string]string{"X-Akto-Installer-User_email": "someone@else.com"}))
		if len(got) != 0 {
			t.Fatalf("expected no policy for a non-matching email, got %d", len(got))
		}

		got = s.applicablePolicies([]types.Policy{userPolicy},
			ctxFor("", map[string]string{"X-Akto-Installer-User_email": "someone@example.com"}))
		if len(got) != 1 {
			t.Fatalf("expected the policy to apply to its targeted user, got %d", len(got))
		}
	})

	t.Run("approval policy on an already-approved server is bypassed", func(t *testing.T) {
		approved := types.Policy{
			Info:              types.PolicyInfo{Name: "approval"},
			Behaviour:         "approval",
			ApplyToAllServers: true,
			ApprovedServers:   []types.ApprovedServer{{ServerId: "device1.cursor.filesystem", Mode: "ALWAYS"}},
		}
		got := s.applicablePolicies([]types.Policy{approved}, ctxFor("device1.cursor.filesystem", nil))
		if len(got) != 0 {
			t.Fatalf("expected the approved server to bypass the policy, got %d", len(got))
		}
	})
}

// applyToDeviceIds carries whatever casing module_info.name was created with, while the device
// label on the wire is re-derived from the current login — the same person reaches us as
// "AlexTaylor" in the policy and "alextaylor" on the request. Compared exactly, the policy is
// dropped and the traffic silently goes uninspected.
func TestFilterPoliciesByDeviceIgnoresLabelCasing(t *testing.T) {
	s := &Service{logger: zap.NewNop()}
	policy := types.Policy{
		Info:             types.PolicyInfo{Name: "device-targeted"},
		ApplyToDeviceIds: []string{"AlexTaylor", "jordan", "SamRivera"},
	}

	for _, tc := range []struct {
		name          string
		mcpServerName string
		applies       bool
	}{
		{"stored capitalised, wire lowercase", "alextaylor.chrome.chatgpt.com", true},
		{"stored capitalised, wire same case", "AlexTaylor.chrome.chatgpt.com", true},
		{"stored lowercase, wire capitalised", "Jordan.chrome.chatgpt.com", true},
		{"mixed every which way", "sAmRiVeRa.chrome.chatgpt.com", true},
		// Only casing is forgiven: a different identity must still miss, and the match stays
		// whole-string so neither a prefix nor a superstring of a listed label can slip in.
		{"different account entirely", "ataylor.chrome.chatgpt.com", false},
		{"prefix of a listed label", "alex.chrome.chatgpt.com", false},
		{"listed label as a prefix of the device", "alextaylor2.chrome.chatgpt.com", false},
		{"no device label in the host", "chatgpt.com", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			got := s.filterPoliciesByDevice([]types.Policy{policy}, tc.mcpServerName, nil)
			if applied := len(got) == 1; applied != tc.applies {
				t.Fatalf("policy applied = %v for %q, want %v", applied, tc.mcpServerName, tc.applies)
			}
		})
	}
}
