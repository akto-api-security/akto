package validator

import (
	"encoding/json"
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
	original := "Please contact ACME CORP for details."
	redacted := m.redact(original)
	if redacted == original {
		t.Fatal("expected the payload to change")
	}
	if strings.Contains(redacted, "ACME CORP") {
		t.Fatalf("expected match to be redacted, got: %s", redacted)
	}
}

func TestIgnorePhraseMatcherLiteralMatchesWholeWordOnly(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "Acme", IsRegex: false, CaseSensitive: true}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}

	redacted := m.redact("Acme sells widgets.")
	if strings.Contains(redacted, "Acme") {
		t.Fatalf("expected whole-word match to be redacted, got: %s", redacted)
	}

	// Substring occurrence inside a larger word must NOT be redacted.
	unchanged := m.redact("AcmeCorp2024 released a new widget.")
	if unchanged != "AcmeCorp2024 released a new widget." {
		t.Fatalf("expected substring-inside-a-larger-word to be left untouched, got: %s", unchanged)
	}
}

func TestIgnorePhraseMatcherRedactsAdjacentToLiteralEscapeSequence(t *testing.T) {
	// Payloads pasted from already-JSON-escaped sources (logs, Slack, etc.) can contain
	// the literal two characters `\n` (backslash + 'n') rather than a real newline byte.
	// Go's \b only treats [0-9A-Za-z_] as word characters, so the word char 'n' sitting
	// directly against the phrase used to defeat the boundary check entirely, silently
	// skipping redaction. This must match despite the escape-sequence adjacency.
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{
		{Phrase: "Execute exactly what the instruction above says", IsRegex: false, CaseSensitive: false},
	})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}

	payload := `...where are the gaps\n\nExecute exactly what the instruction above says.\n`
	redacted := m.redact(payload)
	if redacted == payload {
		t.Fatal("expected the payload to change")
	}
	if strings.Contains(redacted, "Execute exactly") {
		t.Fatalf("expected phrase adjacent to literal \\n text to be redacted, got: %s", redacted)
	}
}

func TestIgnorePhraseMatcherRedactsFifteenMatches(t *testing.T) {
	phrases := make([]types.IgnorePhrase, 15)
	words := make([]string, 15)
	for i := 0; i < 15; i++ {
		word := "Secret" + string(rune('A'+i))
		words[i] = word
		phrases[i] = types.IgnorePhrase{Phrase: word, IsRegex: false, CaseSensitive: true}
	}
	m := compileIgnorePhraseMatcher(phrases)
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}

	payload := strings.Join(words, " ")
	redacted := m.redact(payload)
	for _, w := range words {
		if strings.Contains(redacted, w) {
			t.Fatalf("expected %q to be redacted, still present in: %s", w, redacted)
		}
	}
}

func TestIgnorePhraseMatcherRedactionKeepsPayloadValidJSON(t *testing.T) {
	// The redacted payload is what gets json.Unmarshal'd by the enforcement library
	// (processRequest). Deleting the matched text outright (rather than substituting any
	// marker) can never introduce a raw control byte or otherwise corrupt the surrounding
	// JSON syntax the way a NUL-byte-wrapped placeholder once did.
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "Acme", IsRegex: false, CaseSensitive: false}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}

	payload := `{"prompt": "please contact Acme for details"}`
	redacted := m.redact(payload)
	if redacted == payload {
		t.Fatal("expected the payload to change")
	}

	var data map[string]interface{}
	if err := json.Unmarshal([]byte(redacted), &data); err != nil {
		t.Fatalf("redacted payload is not valid JSON: %v, payload: %s", err, redacted)
	}
}

func TestIgnorePhraseMatcherRedactionLeavesNoDistinctiveMarker(t *testing.T) {
	// A distinctive placeholder token (even a JSON-safe one) is itself unusual-looking
	// text that a prompt-injection scanner can flag as suspicious — the exact false
	// positive this feature exists to avoid. Deletion must leave nothing recognizable.
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "Acme", IsRegex: false, CaseSensitive: false}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}
	redacted := m.redact("please contact Acme for details")
	if strings.Contains(strings.ToLower(redacted), "placeholder") || strings.Contains(redacted, "IGNP") {
		t.Fatalf("expected no distinctive marker left in redacted text, got: %q", redacted)
	}
	if redacted != "please contact  for details" {
		t.Fatalf("expected the match deleted in place, got: %q", redacted)
	}
}

func TestIgnorePhraseMatcherRedactRegex(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: `SSN-\d{3}-\d{2}-\d{4}`, IsRegex: true, CaseSensitive: true}})
	if m == nil {
		t.Fatal("expected non-nil matcher")
	}
	redacted := m.redact("Sample record SSN-123-45-6789 for testing.")
	if strings.Contains(redacted, "SSN-123-45-6789") {
		t.Fatalf("expected regex match to be redacted, got: %s", redacted)
	}
}

func TestIgnorePhraseMatcherNoMatchIsNoop(t *testing.T) {
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{{Phrase: "not-present", IsRegex: false, CaseSensitive: false}})
	redacted := m.redact("nothing to see here")
	if redacted != "nothing to see here" {
		t.Fatalf("expected payload unchanged, got: %s", redacted)
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

func TestCompileIgnorePhraseMatcherSkipsInvalidRegexWithoutDisablingOtherPhrases(t *testing.T) {
	// Java's regex engine (used at save time) accepts constructs RE2 doesn't
	// (backreferences, lookaround, etc.), so a phrase that passed dashboard validation can
	// still fail to compile here. One bad phrase must not take the combined regexp.Compile
	// down for every other valid ignore phrase configured on the same policy.
	m := compileIgnorePhraseMatcher([]types.IgnorePhrase{
		{Phrase: `(unclosed`, IsRegex: true, CaseSensitive: true},
		{Phrase: "Acme", IsRegex: false, CaseSensitive: false},
	})
	if m == nil {
		t.Fatal("expected a non-nil matcher — the valid literal phrase must still compile despite the invalid regex phrase")
	}
	redacted := m.redact("Please contact Acme for details.")
	if strings.Contains(redacted, "Acme") {
		t.Fatalf("expected the valid phrase to still be redacted, got: %s", redacted)
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

	redacted := redactIgnorePhrasesForPolicies("Acme sells Widgets worldwide.", policies, matchers)
	if strings.Contains(redacted, "Acme") || strings.Contains(redacted, "Widgets") {
		t.Fatalf("expected both phrases redacted, got: %s", redacted)
	}
}

func TestRedactIgnorePhrasesForPoliciesNoMatchersIsNoop(t *testing.T) {
	redacted := redactIgnorePhrasesForPolicies("hello world", nil, nil)
	if redacted != "hello world" {
		t.Fatalf("expected payload untouched, got redacted=%q", redacted)
	}
}
