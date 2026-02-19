package session

import (
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"os"
	"strconv"
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

// ExtractRequestID extracts request ID from various headers with fallback
// Supports Kong (x-kong-request-id) and standard request ID headers
func ExtractRequestID(headers map[string]string) string {
	candidates := []string{
		"x-kong-request-id", "X-Kong-Request-Id", // Kong gateway
		"x-request-id", "X-Request-Id",           // Standard
		"request-id", "Request-Id",               // Alternative
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

func isSessionEnabled() bool {
	sessionEnabled := os.Getenv("SESSION_ENABLED")
	if sessionEnabled == "" {
		// Default to true if not set (backward compatibility)
		return true
	}
	enabled, err := strconv.ParseBool(sessionEnabled)
	if err != nil {
		// If parsing fails, default to true
		return true
	}
	return enabled
}

// ExtractSessionIDsFromRequest extracts session ID and request ID from HTTP request headers
// Returns empty strings if SESSION_ENABLED environment variable is set to false
func ExtractSessionIDsFromRequest(r *http.Request) (sessionID, requestID string) {
	// Check if session functionality is enabled
	if !isSessionEnabled() {
		return "", ""
	}

	headers := make(map[string]string)
	for key, values := range r.Header {
		if len(values) > 0 {
			headers[key] = values[0]
		}
	}

	sessionID = ExtractSessionID(headers)
	requestID = ExtractRequestID(headers)
	return sessionID, requestID
}
