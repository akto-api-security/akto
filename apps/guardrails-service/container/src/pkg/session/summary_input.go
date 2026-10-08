package session

import (
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
)

// ShouldGenerateRequestSummary is true only when the request was allowed (no block on winning pass).
func ShouldGenerateRequestSummary(result *mcp.ValidationResult) bool {
	return result != nil && result.Allowed
}

// RequestSummaryInput builds text for request-side session summarization (allowed turns only).
func RequestSummaryInput(rawPayload, validationPayload string, result *mcp.ValidationResult) string {
	if result == nil || !result.Allowed {
		return ""
	}

	source := rawPayload
	if result.Modified && strings.TrimSpace(validationPayload) != "" {
		source = validationPayload
	}

	return strings.TrimSpace(ExtractPromptFromRequestPayload(source))
}

// ResponseSummaryInput builds text for response-side session summarization (allowed turns only).
func ResponseSummaryInput(rawResponseBody, finalResponse string, allowed, modified bool) string {
	if !allowed {
		return ""
	}

	source := rawResponseBody
	if modified && strings.TrimSpace(finalResponse) != "" {
		source = finalResponse
	}

	return strings.TrimSpace(ExtractResponseFromResponsePayload(source))
}
