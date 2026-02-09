package session

import (
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"strings"
)

// ExtractSessionID extracts session ID from headers with fallback chain
func ExtractSessionID(headers map[string]string) string {
	candidates := []string{
		"x-session-id", "X-Session-Id",
		"x-conversation-id", "X-Conversation-Id",
		"authorization", "Authorization",
		"x-user-id", "X-User-Id",
	}

	for _, key := range candidates {
		if val, ok := headers[key]; ok && val != "" {
			return sanitizeSessionID(key, val)
		}
	}
	return ""
}

// ExtractKongRequestID extracts Kong request ID with fallback
func ExtractKongRequestID(headers map[string]string) string {
	candidates := []string{
		"x-kong-request-id", "X-Kong-Request-Id",
		"x-request-id", "X-Request-Id",
		"request-id", "Request-Id",
	}

	for _, key := range candidates {
		if val, ok := headers[key]; ok && val != "" {
			return val
		}
	}
	// Return empty string if no request ID found - request/response correlation won't work without it
	return ""
}

func sanitizeSessionID(headerName, value string) string {
	// Remove "Bearer " prefix from authorization header
	if strings.ToLower(headerName) == "authorization" {
		value = strings.TrimPrefix(value, "Bearer ")
		value = strings.TrimPrefix(value, "bearer ")
	}

	// Hash if too long (>100 chars)
	if len(value) > 100 {
		hash := sha256.Sum256([]byte(value))
		return hex.EncodeToString(hash[:])
	}

	return value
}

// ExtractSessionIDsFromRequest extracts headers and session/Kong IDs from HTTP request
func ExtractSessionIDsFromRequest(r *http.Request) (sessionID, kongRequestID string) {
	headers := make(map[string]string)
	for key, values := range r.Header {
		if len(values) > 0 {
			headers[key] = values[0]
		}
	}

	sessionID = ExtractSessionID(headers)
	kongRequestID = ExtractKongRequestID(headers)
	return sessionID, kongRequestID
}
