package kafka

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
)

var sampleEvent = mcp.ThreatMessage{
	Kind: mcp.ThreatMessageEvent,
	Key:  "sess-1",
	Body: []byte(`{"maliciousEvent":{"actor":"1.2.3.4","sessionId":"sess-1","filterId":"PromptInjection"}}`),
}

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
