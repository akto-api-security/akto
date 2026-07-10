package validator

import (
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
)

// ResolveIngestContextSource maps ingest metadata to guardrail policy context (AGENTIC vs ENDPOINT).
// Priority: explicit batch field > tag metadata (tag.source=ENDPOINT, mcp-server, …) > fallback > AGENTIC.
// Mirrors akto-endpoint-shield/mcp.IsEndpointOrMcpRequest used by endpoint-shield inline validation.
func ResolveIngestContextSource(data models.IngestDataBatch, fallback string) string {
	if cs := strings.TrimSpace(data.ContextSource); cs != "" {
		return strings.ToUpper(cs)
	}
	if mcp.IsEndpointOrMcpRequest(data.Tag, fallback) {
		return string(types.ContextSourceEndpoint)
	}
	if cs := strings.TrimSpace(fallback); cs != "" {
		return strings.ToUpper(cs)
	}
	return string(types.ContextSourceAgentic)
}
