package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
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
