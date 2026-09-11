package session

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"os"
	"strconv"
	"strings"
)

// ExtractSessionID extracts session ID from headers with fallback chain
func ExtractSessionID(headers map[string]string) string {
	candidates := []string{
		"X-Akto-Installer-Akto_session_id", "x-akto-installer-akto_session_id", // Akto CLI hooks
		"x-session-id", "X-Session-Id",
		"x-conversation-id", "X-Conversation-Id",
		"x-user-id", "X-User-Id",
	}

	for _, key := range candidates {
		if val, ok := headers[key]; ok && val != "" {
			return sanitizeSessionID(val)
		}
	}
	return ""
}

// InstallerUserEmailHeader is the canonical spelling of the header carrying the user a
// request belongs to. Write it with this key; read it with ExtractInstallerUserEmail.
const InstallerUserEmailHeader = "X-Akto-Installer-User_email"

// tagKeyBrowserLLMAccountEmail is where the browser extension puts the signed-in account
// email: in the request tag, not in the headers.
const tagKeyBrowserLLMAccountEmail = "browser-llm-account-email"

var installerUserEmailHeaders = []string{
	InstallerUserEmailHeader, "x-akto-installer-user_email",
}

// ExtractInstallerUserEmail extracts the installer-supplied user email, used to resolve
// which devices a request's user is associated with for device-targeted policies.
func ExtractInstallerUserEmail(headers map[string]string) string {
	for _, key := range installerUserEmailHeaders {
		if val, ok := headers[key]; ok && val != "" {
			return val
		}
	}
	return ""
}

func CopyIdentityHeaders(dst map[string]string, h http.Header) {
	if dst == nil || h == nil {
		return
	}
	for _, key := range installerUserEmailHeaders {
		// http.Header.Get is case-insensitive, so the first key covers both spellings.
		if val := strings.TrimSpace(h.Get(key)); val != "" {
			dst[InstallerUserEmailHeader] = val
			return
		}
	}
}

// AccountEmailFromTag extracts the signed-in account email from a request tag (a JSON map,
// e.g. {"browser-llm-account-email":"someone@example.com"}). Empty when the tag is absent,
// unparseable, or carries a placeholder rather than an address.
//
// Callers convert this into InstallerUserEmailHeader at the edge, so that everything
// downstream resolves the user from the headers alone.
func AccountEmailFromTag(tag string) string {
	tag = strings.TrimSpace(tag)
	if tag == "" {
		return ""
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(tag), &m); err != nil {
		return ""
	}
	email := strings.TrimSpace(m[tagKeyBrowserLLMAccountEmail])
	if !strings.Contains(email, "@") {
		return ""
	}
	return email
}

// ExtractRequestID extracts request ID from various headers with fallback
// Supports Kong (x-kong-request-id) and standard request ID headers
func ExtractRequestID(headers map[string]string) string {
	candidates := []string{
		"x-kong-request-id", "X-Kong-Request-Id", // Kong gateway
		"x-request-id", "X-Request-Id", // Standard
		"request-id", "Request-Id", // Alternative
	}

	for _, key := range candidates {
		if val, ok := headers[key]; ok && val != "" {
			return val
		}
	}
	// Return empty string if no request ID found - request/response correlation won't work without it
	return ""
}

func sanitizeSessionID(value string) string {
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

func ExtractSessionIDsFromRequest(r *http.Request, requestHeadersJSON string) (sessionID, requestID string) {
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

	if sessionID != "" && requestID != "" {
		return sessionID, requestID
	}

	bodySession, bodyRequest := extractSessionIDsFromHeadersJSON(requestHeadersJSON)
	if sessionID == "" {
		sessionID = bodySession
	}
	if requestID == "" {
		requestID = bodyRequest
	}
	return sessionID, requestID
}

// extractSessionIDsFromHeadersJSON parses a JSON-encoded headers map (string→string,
// matching how the rest of this service deserializes requestHeaders) and extracts
// session and request IDs from it. Returns empty strings on empty or invalid input.
func extractSessionIDsFromHeadersJSON(headersJSON string) (sessionID, requestID string) {
	headersJSON = strings.TrimSpace(headersJSON)
	if headersJSON == "" {
		return "", ""
	}
	var headers map[string]string
	if err := json.Unmarshal([]byte(headersJSON), &headers); err != nil {
		return "", ""
	}
	return ExtractSessionID(headers), ExtractRequestID(headers)
}
