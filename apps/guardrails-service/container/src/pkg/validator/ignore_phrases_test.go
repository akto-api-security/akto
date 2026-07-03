package validator

import (
	"strings"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

func TestConvertGuardrailsToPolicyCarriesIgnorePhrases(t *testing.T) {
	gp := &mcp.GuardrailsPolicy{
		Name:   "policyA",
		Active: true,
		IgnorePhrases: []types.IgnorePhrase{
			{Phrase: "Acme Corp", IsRegex: false, CaseSensitive: false},
		},
	}
	policy := mcp.ConvertGuardrailsToPolicy(gp)
	if len(policy.IgnorePhrases) != 1 || policy.IgnorePhrases[0].Phrase != "Acme Corp" {
		t.Fatalf("expected IgnorePhrases to flow through ConvertGuardrailsToPolicy, got: %+v", policy.IgnorePhrases)
	}
}

func TestIgnorePhraseMatcherRedactLiteralCaseInsensitive(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "acme corp", IsRegex: false, CaseSensitive: false}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}
	redacted, restore, next := m.redact("Please contact ACME CORP for details.", 0)
	if strings.Contains(redacted, "ACME CORP") {
		t.Fatalf("expected match to be redacted, got: %s", redacted)
	}
	if len(restore) != 1 || restore[0].original != "ACME CORP" {
		t.Fatalf("expected one restore entry for original match, got: %+v", restore)
	}
	if next != 1 {
		t.Fatalf("expected next index 1, got %d", next)
	}
	if restoreIgnorePhrases(redacted, restore) != "Please contact ACME CORP for details." {
		t.Fatalf("restore did not reproduce original text: %s", restoreIgnorePhrases(redacted, restore))
	}
}

func TestIgnorePhraseMatcherLiteralMatchesWholeWordOnly(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "Acme", IsRegex: false, CaseSensitive: true}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}

	redacted, restore, _ := m.redact("Acme sells widgets.", 0)
	if strings.Contains(redacted, "Acme") {
		t.Fatalf("expected whole-word match to be redacted, got: %s", redacted)
	}
	if len(restore) != 1 {
		t.Fatalf("expected 1 match, got %d", len(restore))
	}

	// Substring occurrence inside a larger word must NOT be redacted.
	unchanged, restore2, _ := m.redact("AcmeCorp2024 released a new widget.", 0)
	if unchanged != "AcmeCorp2024 released a new widget." {
		t.Fatalf("expected substring-inside-a-larger-word to be left untouched, got: %s", unchanged)
	}
	if len(restore2) != 0 {
		t.Fatalf("expected no matches for substring-only occurrence, got %+v", restore2)
	}
}

func TestIgnorePhraseMatcherRedactRegex(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: `SSN-\d{3}-\d{2}-\d{4}`, IsRegex: true, CaseSensitive: true}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}
	redacted, restore, _ := m.redact("Sample record SSN-123-45-6789 for testing.", 0)
	if strings.Contains(redacted, "SSN-123-45-6789") {
		t.Fatalf("expected regex match to be redacted, got: %s", redacted)
	}
	if restoreIgnorePhrases(redacted, restore) != "Sample record SSN-123-45-6789 for testing." {
		t.Fatalf("restore did not reproduce original text")
	}
}

func TestIgnorePhraseMatcherNoMatchIsNoop(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "not-present", IsRegex: false, CaseSensitive: false}})
	redacted, restore, next := m.redact("nothing to see here", 5)
	if redacted != "nothing to see here" {
		t.Fatalf("expected payload unchanged, got: %s", redacted)
	}
	if len(restore) != 0 {
		t.Fatalf("expected no restore entries, got: %+v", restore)
	}
	if next != 5 {
		t.Fatalf("expected startIndex to pass through unchanged when nothing matched, got %d", next)
	}
}

func TestCompileIgnorePhraseMatchersByPolicyReadsFromPolicies(t *testing.T) {
	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "policyA"}, IgnorePhrases: []types.IgnorePhrase{{Phrase: "Acme", IsRegex: false}}},
		{Info: types.PolicyInfo{Name: "policyB"}, IgnorePhrases: []types.IgnorePhrase{{Phrase: "Widgets", IsRegex: false}}},
		{Info: types.PolicyInfo{Name: "policyC"}}, // no ignore phrases
	}
	matchers := compileIgnorePhraseMatchersByPolicy(policies)
	if len(matchers) != 2 {
		t.Fatalf("expected 2 compiled matchers, got %d: %+v", len(matchers), matchers)
	}
	if matchers["policyA"] == nil || matchers["policyB"] == nil {
		t.Fatalf("expected matchers for policyA and policyB, got: %+v", matchers)
	}
	if _, ok := matchers["policyC"]; ok {
		t.Fatal("expected no matcher for policyC (no ignore phrases)")
	}
}

func TestRedactIgnorePhrasesForPoliciesAppliesEveryApplicablePolicysPhrases(t *testing.T) {
	// ProcessRequestParallel takes one shared payload for the whole policies batch, so
	// this is a deliberate union: Policy A's phrase and Policy B's phrase are both
	// stripped from the single payload every policy in this call will see.
	policies := []types.Policy{
		{Info: types.PolicyInfo{Name: "policyA"}, IgnorePhrases: []types.IgnorePhrase{{Phrase: "Acme", IsRegex: false}}},
		{Info: types.PolicyInfo{Name: "policyB"}, IgnorePhrases: []types.IgnorePhrase{{Phrase: "Widgets", IsRegex: false}}},
		{Info: types.PolicyInfo{Name: "policyC"}}, // no ignore phrases — should be skipped without error
	}
	matchers := compileIgnorePhraseMatchersByPolicy(policies)

	redacted, restore, changed := redactIgnorePhrasesForPolicies("Acme sells Widgets worldwide.", policies, matchers)
	if !changed {
		t.Fatal("expected changed=true")
	}
	if strings.Contains(redacted, "Acme") || strings.Contains(redacted, "Widgets") {
		t.Fatalf("expected both phrases redacted, got: %s", redacted)
	}
	if len(restore) != 2 {
		t.Fatalf("expected 2 restore entries, got %d: %+v", len(restore), restore)
	}
	if restoreIgnorePhrases(redacted, restore) != "Acme sells Widgets worldwide." {
		t.Fatalf("restore did not reproduce original text: %s", restoreIgnorePhrases(redacted, restore))
	}
}

func TestRedactIgnorePhrasesForPoliciesNoMatchersIsNoop(t *testing.T) {
	redacted, restore, changed := redactIgnorePhrasesForPolicies("hello world", nil, nil)
	if changed {
		t.Fatal("expected changed=false when no matchers configured")
	}
	if redacted != "hello world" || len(restore) != 0 {
		t.Fatalf("expected payload untouched, got redacted=%q restore=%+v", redacted, restore)
	}
}
