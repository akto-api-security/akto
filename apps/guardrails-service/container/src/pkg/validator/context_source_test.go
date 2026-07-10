package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
)

func TestResolveIngestContextSource(t *testing.T) {
	coworkTag := `{"gen-ai":"Gen AI","source":"ENDPOINT","ai-agent":"claude_cowork","mode":"observe"}`

	t.Run("cowork tag resolves to ENDPOINT even when fallback is AGENTIC", func(t *testing.T) {
		got := ResolveIngestContextSource(models.IngestDataBatch{Tag: coworkTag}, string(types.ContextSourceAgentic))
		if got != string(types.ContextSourceEndpoint) {
			t.Fatalf("got %q, want ENDPOINT", got)
		}
	})

	t.Run("explicit contextSource wins", func(t *testing.T) {
		got := ResolveIngestContextSource(models.IngestDataBatch{
			Tag:           coworkTag,
			ContextSource: "AGENTIC",
		}, string(types.ContextSourceEndpoint))
		if got != "AGENTIC" {
			t.Fatalf("got %q, want AGENTIC", got)
		}
	})

	t.Run("empty tag uses fallback", func(t *testing.T) {
		got := ResolveIngestContextSource(models.IngestDataBatch{}, string(types.ContextSourceAgentic))
		if got != string(types.ContextSourceAgentic) {
			t.Fatalf("got %q, want AGENTIC", got)
		}
	})

	t.Run("mcp-server tag resolves to ENDPOINT", func(t *testing.T) {
		tag := `{"gen-ai":"Gen AI","mcp-server":"MCP Server","mcp-client":"claude_cowork"}`
		got := ResolveIngestContextSource(models.IngestDataBatch{Tag: tag}, "")
		if got != string(types.ContextSourceEndpoint) {
			t.Fatalf("got %q, want ENDPOINT", got)
		}
	})
}
