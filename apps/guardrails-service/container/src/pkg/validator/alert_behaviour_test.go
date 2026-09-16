package validator

import (
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"go.uber.org/zap"
)

func TestAllowAlertBehaviour(t *testing.T) {
	cases := []struct {
		name string
		in   *mcp.ValidationResult
		want bool // Allowed after
	}{
		{"alert-mode block is allowed", &mcp.ValidationResult{Allowed: false, Behaviour: "alert"}, true},
		{"alert case/whitespace normalised", &mcp.ValidationResult{Allowed: false, Behaviour: " Alert "}, true},
		{"warn still blocks", &mcp.ValidationResult{Allowed: false, Behaviour: "warn"}, false},
		{"block still blocks", &mcp.ValidationResult{Allowed: false, Behaviour: "block"}, false},
		{"empty behaviour still blocks", &mcp.ValidationResult{Allowed: false, Behaviour: ""}, false},
		{"already allowed is untouched", &mcp.ValidationResult{Allowed: true, Behaviour: "alert"}, true},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			allowAlertBehaviour(tc.in)
			if tc.in.Allowed != tc.want {
				t.Fatalf("Allowed = %v, want %v", tc.in.Allowed, tc.want)
			}
		})
	}

	t.Run("nil result does not panic", func(t *testing.T) {
		allowAlertBehaviour(nil)
	})
}

// PolicyIsAlertMode is what tells an alert-mode policy's redaction apart from a block-mode
// one: both report Behaviour "alert" on the verdict, so only the policy itself can say.
func TestPolicyIsAlertMode(t *testing.T) {
	serviceWith := func(policies ...types.Policy) *Service {
		return &Service{
			logger: zap.NewNop(),
			config: &config.Config{PolicyRefreshIntervalMin: 5},
			cache:  &policyCache{policies: policies, lastFetched: time.Now()},
		}
	}
	policy := func(id, behaviour string) types.Policy {
		return types.Policy{
			ActualPolicyID: id,
			Info:           types.PolicyInfo{Name: id},
			Behaviour:      behaviour,
		}
	}

	s := serviceWith(policy("alerting", "alert"), policy("blocking", "block"), policy("unset", ""))

	cases := []struct {
		name     string
		policyID string
		want     bool
	}{
		{"alert-mode policy", "alerting", true},
		{"block-mode policy", "blocking", false},
		{"policy with no behaviour set", "unset", false},
		{"unknown policy does not fail open", "missing", false},
		{"empty policy id does not fail open", "", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := s.PolicyIsAlertMode("", tc.policyID); got != tc.want {
				t.Fatalf("PolicyIsAlertMode(%q) = %v, want %v", tc.policyID, got, tc.want)
			}
		})
	}

	t.Run("case and whitespace normalised", func(t *testing.T) {
		s := serviceWith(policy("loud", " Alert "))
		if !s.PolicyIsAlertMode("", "loud") {
			t.Fatal("PolicyIsAlertMode = false, want true")
		}
	})

	t.Run("service without a policy cache does not panic or fail open", func(t *testing.T) {
		if (&Service{logger: zap.NewNop()}).PolicyIsAlertMode("", "alerting") {
			t.Fatal("PolicyIsAlertMode = true, want false")
		}
	})
}
