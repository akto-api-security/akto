package validator

import (
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// /api/validate/file keys remembered files on this fingerprint, so it must move whenever what is
// enforced on the caller moves, and stay put otherwise.
func TestApplicablePoliciesFingerprint(t *testing.T) {
	service := func(policies ...types.Policy) *Service {
		return &Service{logger: zap.NewNop(), cache: &policyCache{policies: policies, lastFetched: time.Now()}}
	}
	injection := types.Policy{Info: types.PolicyInfo{Name: "prompt-injection"}, Severity: "HIGH"}
	fingerprint := func(s *Service, contextSource, headers string) string {
		t.Helper()
		got, err := s.ApplicablePoliciesFingerprint(contextSource, headers, "")
		if err != nil {
			t.Fatal(err)
		}
		return got
	}

	base := fingerprint(service(injection), "AGENTIC", "")
	if base == "" || base != fingerprint(service(injection), "AGENTIC", "") {
		t.Fatal("the same policies must give the same fingerprint")
	}

	edited := injection
	edited.Severity = "CRITICAL"
	if fingerprint(service(edited), "AGENTIC", "") == base {
		t.Error("editing a policy must change the fingerprint")
	}
	if fingerprint(service(injection, types.Policy{Info: types.PolicyInfo{Name: "pii"}}), "AGENTIC", "") == base {
		t.Error("adding a policy must change the fingerprint")
	}
	if fingerprint(service(), "AGENTIC", "") == base {
		t.Error("removing every policy must change the fingerprint")
	}

	// A policy scoped to another context source doesn't apply, so it doesn't change what this caller gets.
	endpointOnly := types.Policy{Info: types.PolicyInfo{Name: "endpoint-only"}, ContextSource: "ENDPOINT"}
	if fingerprint(service(injection, endpointOnly), "AGENTIC", "") != base {
		t.Error("a policy that doesn't apply must not change the fingerprint")
	}

	// A user-targeted policy applies only with the matching identity header.
	targeted := types.Policy{Info: types.PolicyInfo{Name: "user-targeted"},
		UserMetadata: []types.AgenticUsers{{UserEmail: "someone@example.com"}}}
	without := fingerprint(service(injection, targeted), "AGENTIC", "")
	with := fingerprint(service(injection, targeted), "AGENTIC", `{"X-Akto-Installer-User_email":"someone@example.com"}`)
	if without != base || with == base {
		t.Errorf("a targeted policy must count only for its user (without=%v, with=%v)", without == base, with == base)
	}
}
