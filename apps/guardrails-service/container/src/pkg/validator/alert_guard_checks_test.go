package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
)

func TestPartitionAlertPolicies(t *testing.T) {
	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "alert"}, Behaviour: "Alert"},
		{Info: types.PolicyInfo{Name: "block"}, Behaviour: "block"},
		{Info: types.PolicyInfo{Name: "unset"}},
		{Info: types.PolicyInfo{Name: "approval"}, Behaviour: "approval"},
	}
	enforcing, alerting := partitionAlertPolicies(policies)
	if got := policyNames(alerting); len(got) != 1 || got[0] != "alert" {
		t.Errorf("alerting = %v, want [alert]", got)
	}
	if got := policyNames(enforcing); len(got) != 3 {
		t.Errorf("enforcing = %v, want block, unset and approval", got)
	}
}

// A blocked-host rule on an alert policy is found for reporting but never blocks; the same rule
// on an enforcing policy still does.
func TestBlockedHost_AlertPolicyNeverBlocks(t *testing.T) {
	alertPolicy := types.Policy{Info: types.PolicyInfo{Name: "alert"}, Behaviour: "alert", BlockedHosts: []string{"chatgpt.com"}}
	blockPolicy := types.Policy{Info: types.PolicyInfo{Name: "block"}, Behaviour: "block", BlockedHosts: []string{"deepseek.com"}}
	s := &Service{logger: zap.NewNop(), cache: &policyCache{
		blockedHosts: mcp.CompileBlockedHostRules([]types.Policy{alertPolicy, blockPolicy}),
	}}
	enforcing, alerting := partitionAlertPolicies([]types.Policy{alertPolicy, blockPolicy})
	skip := true
	params := &models.ValidateRequestParams{Path: "/backend-api/conversation", SkipThreat: &skip}
	ctxFor := func(host string) *mcp.ValidationContext {
		return &mcp.ValidationContext{Tag: browserTag, RequestHeaders: map[string]string{"host": host}}
	}

	if res := s.checkBlockedHost(params, ctxFor("chatgpt.com"), "{}", "", "", enforcing); res != nil {
		t.Errorf("an alert policy's host must not block, got %+v", res)
	}
	if name, pattern, hit := s.matchBlockedHost(params, ctxFor("chatgpt.com"), alerting); !hit || name != "alert" || pattern != "chatgpt.com" {
		t.Errorf("alert hit must still be found for reporting, got %q %q %v", name, pattern, hit)
	}
	if res := s.checkBlockedHost(params, ctxFor("deepseek.com"), "{}", "", "", enforcing); res == nil || res.Allowed {
		t.Errorf("an enforcing policy's host must still block, got %+v", res)
	}
}

func TestBlockedHostMetadata_AlertWording(t *testing.T) {
	if got := blockedHostMetadata("p", "x.com", "alert").Reason; got != "Request alerted by guardrail policy (blocked host pattern: x.com)" {
		t.Errorf("alert reason = %q", got)
	}
	if got := blockedHostMetadata("p", "x.com", "block").Reason; got != "Request blocked by guardrail policy (blocked host pattern: x.com)" {
		t.Errorf("block reason = %q", got)
	}
}
