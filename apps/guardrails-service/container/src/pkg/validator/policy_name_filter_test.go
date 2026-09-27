package validator

import (
	"slices"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

func TestFilterPoliciesByName(t *testing.T) {
	s := &Service{logger: zap.NewNop()}
	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "PII Strict"}},
		{Info: types.PolicyInfo{Name: "Prompt Injection"}},
		{Info: types.PolicyInfo{Name: "Secrets"}},
	}
	all := []string{"PII Strict", "Prompt Injection", "Secrets"}

	cases := []struct {
		name       string
		policyName string
		want       []string
	}{
		{"empty keeps all", "", all},
		{"blank keeps all", " , ", all},
		{"single name", "Secrets", []string{"Secrets"}},
		{"case and space insensitive", "  pii strict ", []string{"PII Strict"}},
		{"comma separated", "Secrets,PII Strict", []string{"PII Strict", "Secrets"}},
		{"unknown names are ignored", "Secrets,nope", []string{"Secrets"}},
		{"no match falls back to all", "nope", all},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := policyNames(s.filterPoliciesByName(policies, tc.policyName)); !slices.Equal(got, tc.want) {
				t.Fatalf("filterPoliciesByName(%q) = %v, want %v", tc.policyName, got, tc.want)
			}
		})
	}
}
