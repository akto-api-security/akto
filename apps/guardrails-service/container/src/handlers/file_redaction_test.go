package handlers

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/pkg/config"
)

func handlerWithBlockOnRedaction(v bool) *ValidationHandler {
	return &ValidationHandler{cfg: &config.Config{File: config.FileConfig{BlockOnRedaction: v}}}
}

func handlerWithAlertModeLookup(v bool, lookup alertModeLookup) *ValidationHandler {
	return &ValidationHandler{
		cfg:               &config.Config{File: config.FileConfig{BlockOnRedaction: v}},
		policyIsAlertMode: lookup,
	}
}

func TestChunkStopsFileOnRedaction(t *testing.T) {
	cases := []struct {
		name             string
		blockOnRedaction bool
		result           *mcp.ValidationResult
		want             bool
	}{
		{"clean chunk passes", true, &mcp.ValidationResult{Allowed: true}, false},
		{"blocked chunk stops", true, &mcp.ValidationResult{Allowed: false, Reason: "ssn"}, true},
		{
			"masked chunk stops when blocking on redaction",
			true,
			&mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "mask"},
			true,
		},
		{
			"masked chunk passes when opted out",
			false,
			&mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "mask"},
			false,
		},
		{
			"blocked chunk still stops when opted out",
			false,
			&mcp.ValidationResult{Allowed: false},
			true,
		},
		{"nil result is not a stop", true, nil, false},
		{
			"alert-mode match passes",
			true,
			&mcp.ValidationResult{Allowed: false, Reason: "ssn", Behaviour: "alert"},
			false,
		},
		{
			"alert-mode match passes regardless of case/whitespace",
			true,
			&mcp.ValidationResult{Allowed: false, Reason: "ssn", Behaviour: " Alert "},
			false,
		},
		{
			"warn-mode match still stops",
			true,
			&mcp.ValidationResult{Allowed: false, Reason: "ssn", Behaviour: "warn"},
			true,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := handlerWithBlockOnRedaction(tc.blockOnRedaction).chunkStopsFile(tc.result, ""); got != tc.want {
				t.Fatalf("chunkStopsFile = %v, want %v", got, tc.want)
			}
		})
	}
}

// The contract for files: an alert-mode policy never stops an upload, a block-mode policy
// always does — even when its rule only asked to redact, since this endpoint cannot return
// the redacted file. A redaction reports Behaviour "alert" whatever mode its policy is in
// (piiReportBehaviour hardcodes it), so the policy has to be consulted by name.
func TestChunkStopsFileRedactionFollowsPolicyMode(t *testing.T) {
	masked := func() *mcp.ValidationResult {
		return &mcp.ValidationResult{
			Allowed: true, Modified: true, Behaviour: "alert",
			Metadata: types.ThreatMetadata{PolicyName: "pii-policy"},
		}
	}

	t.Run("alert-mode policy allows its redaction through", func(t *testing.T) {
		h := handlerWithAlertModeLookup(true, func(contextSource, policyID string) bool {
			if policyID != "pii-policy" || contextSource != "ctx" {
				t.Fatalf("lookup got (%q, %q), want (%q, %q)", contextSource, policyID, "ctx", "pii-policy")
			}
			return true
		})
		if h.chunkStopsFile(masked(), "ctx") {
			t.Fatal("chunkStopsFile = true, want false: alert-mode policy must never block")
		}
	})

	t.Run("block-mode policy stops its redaction", func(t *testing.T) {
		h := handlerWithAlertModeLookup(true, func(contextSource, policyID string) bool { return false })
		if !h.chunkStopsFile(masked(), "ctx") {
			t.Fatal("chunkStopsFile = false, want true: block mode blocks even a redact rule")
		}
	})

	t.Run("no lookup wired keeps the enforcing behaviour", func(t *testing.T) {
		if !handlerWithBlockOnRedaction(true).chunkStopsFile(masked(), "ctx") {
			t.Fatal("chunkStopsFile = false, want true: unresolved policy must not fail open")
		}
	})

	t.Run("BlockOnRedaction opted out never reaches the lookup", func(t *testing.T) {
		h := handlerWithAlertModeLookup(false, func(contextSource, policyID string) bool {
			t.Fatal("lookup must not run when BlockOnRedaction is off")
			return false
		})
		if h.chunkStopsFile(masked(), "ctx") {
			t.Fatal("chunkStopsFile = true, want false: BlockOnRedaction is opted out")
		}
	})

	// An alert-mode policy whose rule action was block/warn needs no lookup at all: that
	// verdict carries the policy's mode on Behaviour, and allowAlertBehaviour has already
	// turned it into an allow upstream.
	t.Run("alert-mode block rule needs no lookup", func(t *testing.T) {
		h := handlerWithAlertModeLookup(true, func(contextSource, policyID string) bool {
			t.Fatal("lookup must not run for a non-redaction verdict")
			return false
		})
		blocked := &mcp.ValidationResult{Allowed: false, Behaviour: "alert", Reason: "ssn"}
		if h.chunkStopsFile(blocked, "ctx") {
			t.Fatal("chunkStopsFile = true, want false: alert-mode policy must never block")
		}
	})
}

// A mask never builds a blocked response, so Reason is empty and the caller would
// otherwise be told "content blocked by guardrail policy" with no hint that the file
// was rejected for content the policy wanted to redact.
func TestChunkBlockReasonNamesRedaction(t *testing.T) {
	cases := []struct {
		name   string
		result *mcp.ValidationResult
		want   string
	}{
		{
			"policy reason wins",
			&mcp.ValidationResult{Allowed: false, Reason: "SSN detected"},
			"SSN detected",
		},
		{
			"masked chunk names its behaviour",
			&mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "mask"},
			"file contains sensitive content redacted by guardrail policy (mask)",
		},
		{
			"masked chunk without behaviour",
			&mcp.ValidationResult{Allowed: true, Modified: true},
			"file contains sensitive content redacted by guardrail policy",
		},
		{
			"blocked chunk without reason",
			&mcp.ValidationResult{Allowed: false},
			"content blocked by guardrail policy",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := chunkBlockReason(tc.result); got != tc.want {
				t.Fatalf("chunkBlockReason = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestBlockOnRedactionDefaultsToTrue(t *testing.T) {
	t.Setenv("FILE_VALIDATE_BLOCK_ON_REDACTION", "")
	if !config.LoadConfig().File.BlockOnRedaction {
		t.Fatal("BlockOnRedaction must default to true: /api/validate/file cannot return the masked text")
	}
	t.Setenv("FILE_VALIDATE_BLOCK_ON_REDACTION", "false")
	if config.LoadConfig().File.BlockOnRedaction {
		t.Fatal("FILE_VALIDATE_BLOCK_ON_REDACTION=false must opt out")
	}
}
