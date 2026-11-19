package validator

import (
	"slices"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

func TestPoliciesByName(t *testing.T) {
	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "PII Strict"}},
		{Info: types.PolicyInfo{Name: "Prompt Injection"}},
		{Info: types.PolicyInfo{Name: "Secrets"}},
	}

	cases := []struct {
		name       string
		policyName string
		want       []string
	}{
		{"empty names nothing", "", []string{}},
		{"blank names nothing", " , ", []string{}},
		{"single name", "Secrets", []string{"Secrets"}},
		{"case and space insensitive", "  pii strict ", []string{"PII Strict"}},
		{"comma separated", "Secrets,PII Strict", []string{"PII Strict", "Secrets"}},
		{"unknown names are ignored", "Secrets,nope", []string{"Secrets"}},
		{"no match", "nope", []string{}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := policyNames(policiesByName(policies, tc.policyName)); !slices.Equal(got, tc.want) {
				t.Fatalf("policiesByName(%q) = %v, want %v", tc.policyName, got, tc.want)
			}
		})
	}
}

// A policy the request names is enforced whatever its context source or scope; a request naming
// no active policy gets no guardrails; without a name it gets the policies in scope for it.
func TestEnforcedPolicies(t *testing.T) {
	outOfScopeUser := types.Policy{
		Info:              types.PolicyInfo{Name: "block employee pii"},
		ContextSource:     "ENDPOINT",
		ApplyToAllServers: true,
		UserMetadata:      []types.AgenticUsers{{UserEmail: "someone@example.com"}},
	}
	otherContext := types.Policy{Info: types.PolicyInfo{Name: "agentic only"}, ContextSource: "AGENTIC"}
	everyone := types.Policy{Info: types.PolicyInfo{Name: "everyone"}, ContextSource: "ENDPOINT", ApplyToAllServers: true}

	s := &Service{logger: zap.NewNop(), cache: &policyCache{
		policies: []types.Policy{outOfScopeUser, otherContext, everyone},
	}}
	// What getCachedPolicies("ENDPOINT") hands the validate flow.
	endpointPolicies := []types.Policy{outOfScopeUser, everyone}
	// Request from a user the user-targeted policy does not cover.
	valCtx := &mcp.ValidationContext{
		McpServerName:  "abhijeet.ai-agent.opencode-litellm",
		RequestHeaders: map[string]string{"x-akto-installer-user_email": "other@example.com"},
	}

	cases := []struct {
		name       string
		policyName string
		want       []string
		wantOK     bool
	}{
		{"no name gives the policies in scope", "", []string{"everyone"}, true},
		{"named policy out of user scope is enforced", "Block Employee PII", []string{"block employee pii"}, true},
		{"named policy of another context source is enforced", "agentic only", []string{"agentic only"}, true},
		{"only the named policies are enforced", "block employee pii,everyone", []string{"block employee pii", "everyone"}, true},
		{"unknown names next to a known one are ignored", "everyone,nope", []string{"everyone"}, true},
		{"no matching name applies no guardrails", "nope", []string{}, false},
		{"inactive or misspelled names apply no guardrails", "block employe pii,other", []string{}, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, ok := s.enforcedPolicies(endpointPolicies, valCtx, tc.policyName)
			if ok != tc.wantOK || !slices.Equal(policyNames(got), tc.want) {
				t.Fatalf("enforcedPolicies(%q) = %v, %v; want %v, %v", tc.policyName, policyNames(got), ok, tc.want, tc.wantOK)
			}
		})
	}
}
