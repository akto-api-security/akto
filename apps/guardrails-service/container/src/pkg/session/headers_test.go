package session

import (
	"net/http"
	"testing"
)

// The browser extension names the signed-in user in the request tag rather than the
// headers, so the endpoints convert this into InstallerUserEmailHeader at the edge.
func TestAccountEmailFromTag(t *testing.T) {
	for _, tc := range []struct {
		name, tag, want string
	}{
		{"account email", `{"gen-ai":"Gen AI","browser-llm-account-email":"someone@example.com"}`, "someone@example.com"},
		{"surrounding whitespace is trimmed", `{"browser-llm-account-email":"  someone@example.com  "}`, "someone@example.com"},
		{"no email key", `{"gen-ai":"Gen AI"}`, ""},
		{"placeholder is not an address", `{"browser-llm-account-email":"not-signed-in"}`, ""},
		{"empty value", `{"browser-llm-account-email":""}`, ""},
		{"unparseable", "not json", ""},
		{"non-string values", `{"browser-llm-account-email":["someone@example.com"]}`, ""},
		{"empty tag", "", ""},
		{"blank tag", "   ", ""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := AccountEmailFromTag(tc.tag); got != tc.want {
				t.Fatalf("AccountEmailFromTag(%q) = %q, want %q", tc.tag, got, tc.want)
			}
		})
	}
}

// Whatever CopyIdentityHeaders writes must be readable by ExtractInstallerUserEmail —
// they are the write and read halves of the same key.
func TestCopyIdentityHeadersRoundTrip(t *testing.T) {
	// Whatever casing a client sends, net/http canonicalizes it on the way in, so all
	// three of these arrive as the same key.
	for _, header := range []string{"x-akto-installer-user_email", "X-Akto-Installer-User_email", "X-AKTO-INSTALLER-USER_EMAIL"} {
		t.Run(header, func(t *testing.T) {
			req := http.Header{}
			req.Set(header, "someone@example.com")
			dst := map[string]string{}
			CopyIdentityHeaders(dst, req)
			if got := ExtractInstallerUserEmail(dst); got != "someone@example.com" {
				t.Fatalf("round trip via %q = %q", header, got)
			}
		})
	}

	t.Run("nothing to copy", func(t *testing.T) {
		req := http.Header{}
		req.Set("Host", "example.com")
		dst := map[string]string{}
		CopyIdentityHeaders(dst, req)
		if len(dst) != 0 {
			t.Fatalf("expected no identity headers, got %v", dst)
		}
	})
}
