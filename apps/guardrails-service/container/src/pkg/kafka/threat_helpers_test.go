package kafka

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
)

const sampleBody = `{"maliciousEvent":{"actor":"1.2.3.4","sessionId":"sess-1","filterId":"PromptInjection"}}`

// withThreatAPI points the endpoint-shield POST helper at a test server for the
// duration of the test.
func withThreatAPI(t *testing.T, handler http.HandlerFunc) {
	t.Helper()
	srv := httptest.NewServer(handler)
	prev := mcp.ThreatDetectionAPIURL
	mcp.ThreatDetectionAPIURL = srv.URL
	t.Cleanup(func() {
		mcp.ThreatDetectionAPIURL = prev
		srv.Close()
	})
}

func TestPartitionKey(t *testing.T) {
	cases := []struct {
		name string
		body string
		want string
	}{
		{"session id wins", sampleBody, "sess-1"},
		{"falls back to actor", `{"maliciousEvent":{"actor":"9.9.9.9"}}`, "9.9.9.9"},
		{"empty when neither present", `{"maliciousEvent":{}}`, ""},
		{"unparseable body yields no key", `not json`, ""},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if got := string(partitionKey([]byte(c.body))); got != c.want {
				t.Fatalf("partitionKey() = %q, want %q", got, c.want)
			}
		})
	}
}
