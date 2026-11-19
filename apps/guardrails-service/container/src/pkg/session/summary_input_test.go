package session

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
)

func TestShouldGenerateRequestSummary_AllowedOnly(t *testing.T) {
	if !ShouldGenerateRequestSummary(&mcp.ValidationResult{Allowed: true}) {
		t.Fatal("expected true for allowed")
	}
	if ShouldGenerateRequestSummary(&mcp.ValidationResult{Allowed: false}) {
		t.Fatal("expected false for blocked")
	}
}

func TestRequestSummaryInput_BlockedReturnsEmpty(t *testing.T) {
	out := RequestSummaryInput(`{"body":"x"}`, "", &mcp.ValidationResult{Allowed: false})
	if out != "" {
		t.Fatalf("expected empty summary input on block, got %q", out)
	}
}
