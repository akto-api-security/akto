package handlers

import (
	"encoding/json"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
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

// The four File rows of the guardrail policy matrix, as the engine reports them:
// a Detection rule (PII action "block"/"warn") answers !Allowed carrying the policy-wide
// behaviour, and a Redaction rule (action "redact") answers Allowed+Modified with a
// hardcoded "alert" behaviour. Only Alert+Detection leaves the upload alone.
func TestChunkStopsFilePolicyMatrix(t *testing.T) {
	cases := []struct {
		policy string
		rule   string
		result *mcp.ValidationResult
		want   bool
	}{
		{"block", "detection", &mcp.ValidationResult{Allowed: false, Behaviour: "block", Reason: "ssn"}, true},
		{"block", "redaction", &mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "alert"}, true},
		{"alert", "detection", &mcp.ValidationResult{Allowed: false, Behaviour: "alert", Reason: "ssn"}, false},
		{"alert", "redaction", &mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "alert"}, true},
	}

	for _, tc := range cases {
		t.Run(tc.policy+"/"+tc.rule, func(t *testing.T) {
			if got := handlerWithBlockOnRedaction(true).chunkStopsFile(tc.result); got != tc.want {
				t.Fatalf("chunkStopsFile = %v, want %v", got, tc.want)
			}
		})
	}
}

// Only alert and mask are passive. Anything else a policy can carry — warn, either approval
// behaviour, an unset value, a behaviour this build does not know — keeps blocking, so a
// verdict the engine meant to enforce is never waved through on a behaviour string alone.
func TestChunkStopsFileOnlyRelaxesPassiveBehaviours(t *testing.T) {
	for _, behaviour := range []string{"block", "warn", "approval", "human_approval", "", "  ", "nonsense"} {
		t.Run("blocks/"+behaviour, func(t *testing.T) {
			r := &mcp.ValidationResult{Allowed: false, Behaviour: behaviour}
			if !handlerWithBlockOnRedaction(true).chunkStopsFile(r) {
				t.Fatalf("behaviour %q must keep stopping the file", behaviour)
			}
		})
	}
	for _, behaviour := range []string{"alert", "ALERT", " Alert ", "mask"} {
		t.Run("allows/"+behaviour, func(t *testing.T) {
			r := &mcp.ValidationResult{Allowed: false, Behaviour: behaviour}
			if handlerWithBlockOnRedaction(true).chunkStopsFile(r) {
				t.Fatalf("passive behaviour %q must not stop the file", behaviour)
			}
		})
	}
}

// Redaction outranks a passive behaviour: the response carries no ModifiedPayload, so an
// alert-behaviour mask verdict that was allowed through would ship the original spans.
func TestChunkStopsFileRedactionOutranksPassiveBehaviour(t *testing.T) {
	masked := &mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "alert"}
	if !handlerWithBlockOnRedaction(true).chunkStopsFile(masked) {
		t.Fatal("a masked chunk must stop the file even when its behaviour is passive")
	}
	if handlerWithBlockOnRedaction(false).chunkStopsFile(masked) {
		t.Fatal("opting out of BlockOnRedaction must still allow a masked chunk")
	}
}

// What the caller of /api/validate/file sees when a policy redacts a file's content, end to
// end through the chunk results and the response: a file can't come back redacted (the
// endpoint returns a verdict, never content), so a redaction blocks the upload whether the
// policy is in block or alert mode, the redacted text never leaks, and the block is a real
// verdict (not marked uninspected). The same path serves documents and OCR'd images: both
// reach the policy engine as text chunks.
func TestRedactedFileUploadIsBlocked(t *testing.T) {
	clean := &chunkResult{Result: &mcp.ValidationResult{Allowed: true}}
	// The engine answers a redaction rule Allowed+Modified with a hardcoded "alert" behaviour
	// (see TestChunkStopsFilePolicyMatrix), whatever the policy's own mode.
	redacted := &chunkResult{Result: &mcp.ValidationResult{
		Allowed: true, Modified: true, Behaviour: "alert",
		ModifiedPayload: `{"prompt":"Contact: [EMAIL_REDACTED]"}`,
		Metadata:        types.ThreatMetadata{PolicyName: "PII policy", RuleViolated: "PII-email"},
	}}
	for _, tc := range []struct {
		name   string
		chunks []*chunkResult
	}{
		{"redacted only chunk", []*chunkResult{redacted}},
		{"redacted chunk after clean ones", []*chunkResult{clean, clean, redacted}},
		{"redacted chunk before clean ones", []*chunkResult{redacted, clean}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			h := &ValidationHandler{logger: zap.NewNop(), cfg: &config.Config{File: config.FileConfig{BlockOnRedaction: true}}}
			fr := h.applyFileChunkResults(&fileResult{Filename: "escalation.png", Allowed: true}, tc.chunks)
			recorder := httptest.NewRecorder()
			c, _ := gin.CreateTestContext(recorder)
			h.writeMultiFileResponse(c, []*fileResult{fr}, false, false)

			var resp struct {
				Allowed         bool                 `json:"allowed"`
				ModifiedPayload string               `json:"modifiedPayload"`
				Reason          string               `json:"reason"`
				Metadata        types.ThreatMetadata `json:"metadata"`
			}
			if err := json.Unmarshal(recorder.Body.Bytes(), &resp); err != nil {
				t.Fatal(err)
			}
			if resp.Allowed {
				t.Fatalf("a redacted file must be blocked: %s", recorder.Body.String())
			}
			if !strings.Contains(resp.Reason, "redacted by guardrail policy") {
				t.Fatalf("reason must say the file was blocked for redacted content, got %q", resp.Reason)
			}
			if resp.ModifiedPayload != "" {
				t.Fatalf("the redacted text must not be returned, got %q", resp.ModifiedPayload)
			}
			if resp.Metadata.PolicyName != "PII policy" || resp.Metadata.RuleViolated != "PII-email" {
				t.Fatalf("metadata must name the redacting policy and rule, got %+v", resp.Metadata)
			}
			assertUninspected(t, recorder, false)
		})
	}

	t.Run("opting out lets the redacted file through", func(t *testing.T) {
		h := &ValidationHandler{logger: zap.NewNop(), cfg: &config.Config{File: config.FileConfig{BlockOnRedaction: false}}}
		fr := h.applyFileChunkResults(&fileResult{Filename: "escalation.png", Allowed: true}, []*chunkResult{clean, redacted})
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		h.writeMultiFileResponse(c, []*fileResult{fr}, false, false)
		assertFileAllowed(t, recorder, true)
	})
}
