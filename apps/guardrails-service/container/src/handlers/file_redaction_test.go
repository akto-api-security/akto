package handlers

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/pkg/config"
)

func handlerWithBlockOnRedaction(v bool) *ValidationHandler {
	return &ValidationHandler{cfg: &config.Config{File: config.FileConfig{BlockOnRedaction: v}}}
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
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := handlerWithBlockOnRedaction(tc.blockOnRedaction).chunkStopsFile(tc.result); got != tc.want {
				t.Fatalf("chunkStopsFile = %v, want %v", got, tc.want)
			}
		})
	}
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
