package handlers

import (
	"encoding/json"
	"testing"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"go.uber.org/zap"
)

// The browser extension names the signed-in user in the tag and sends only Host in
// requestHeaders, so without this conversion a Users-scoped policy never matches its
// traffic — filterPoliciesByDevice resolves the user from the headers alone.
func TestApplyIdentityFromTag(t *testing.T) {
	h := &ValidationHandler{logger: zap.NewNop()}
	const tagWithEmail = `{"gen-ai":"Gen AI","browser-llm-account-email":"someone@example.com"}`

	t.Run("tag email becomes the identity header", func(t *testing.T) {
		params := &models.ValidateRequestParams{
			RequestHeaders: `{"host":"someone.chrome.chatgpt.com"}`,
			Tag:            tagWithEmail,
		}
		h.applyIdentityFromTag(params)

		var headers map[string]string
		if err := json.Unmarshal([]byte(params.RequestHeaders), &headers); err != nil {
			t.Fatal(err)
		}
		if got := session.ExtractInstallerUserEmail(headers); got != "someone@example.com" {
			t.Fatalf("installer email = %q, want someone@example.com", got)
		}
		if headers["host"] != "someone.chrome.chatgpt.com" {
			t.Fatalf("Host was lost or rewritten: %v", headers)
		}
	})

	t.Run("an explicitly sent header is never overwritten", func(t *testing.T) {
		params := &models.ValidateRequestParams{
			RequestHeaders: `{"host":"h","x-akto-installer-user_email":"header@example.com"}`,
			Tag:            tagWithEmail,
		}
		h.applyIdentityFromTag(params)

		var headers map[string]string
		if err := json.Unmarshal([]byte(params.RequestHeaders), &headers); err != nil {
			t.Fatal(err)
		}
		if got := session.ExtractInstallerUserEmail(headers); got != "header@example.com" {
			t.Fatalf("installer email = %q, want the header value", got)
		}
	})

	// Nothing to add, or headers that are not a flat map: pass through byte-identical
	// rather than reshaping what the caller sent.
	t.Run("headers are left untouched", func(t *testing.T) {
		for _, tc := range []struct{ name, headers, tag string }{
			{"no tag", `{"host":"h"}`, ""},
			{"tag has no account email", `{"host":"h"}`, `{"gen-ai":"Gen AI"}`},
			{"tag email is a placeholder", `{"host":"h"}`, `{"browser-llm-account-email":"not-signed-in"}`},
			{"unparseable tag", `{"host":"h"}`, "not json"},
			{"unparseable headers", "not json", tagWithEmail},
			{"headers are not a flat map", `{"host":["h"]}`, tagWithEmail},
			{"empty headers", "", tagWithEmail},
		} {
			t.Run(tc.name, func(t *testing.T) {
				params := &models.ValidateRequestParams{RequestHeaders: tc.headers, Tag: tc.tag}
				h.applyIdentityFromTag(params)
				if params.RequestHeaders != tc.headers {
					t.Fatalf("headers = %q, want them unchanged (%q)", params.RequestHeaders, tc.headers)
				}
			})
		}
	})
}
