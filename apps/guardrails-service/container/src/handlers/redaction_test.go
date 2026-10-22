package handlers

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

func redactHandler() *ValidationHandler {
	return &ValidationHandler{
		logger: zap.NewNop(),
		cfg:    &config.Config{RedactMaxItems: 2, RedactMaxPayloadBytes: 1024},
	}
}

func postRedact(h *ValidationHandler, body string) *httptest.ResponseRecorder {
	gin.SetMode(gin.TestMode)
	recorder := httptest.NewRecorder()
	c, _ := gin.CreateTestContext(recorder)
	c.Request = httptest.NewRequest(http.MethodPost, "/api/redact/payloads", strings.NewReader(body))
	c.Request.Header.Set("Content-Type", "application/json")
	h.RedactPayloads(c)
	return recorder
}

func TestRedactPayloads_RejectsBadRequests(t *testing.T) {
	cases := map[string]struct {
		body string
		want int
	}{
		"malformed json":  {`{"items":`, http.StatusBadRequest},
		"no items":        {`{"items":[]}`, http.StatusBadRequest},
		"too many items":  {`{"items":[{"id":"1"},{"id":"2"},{"id":"3"}]}`, http.StatusBadRequest},
		"missing id":      {`{"items":[{"method":"GET"}]}`, http.StatusBadRequest},
		"duplicate id":    {`{"items":[{"id":"1"},{"id":"1"}]}`, http.StatusBadRequest},
		"id too long":     {`{"items":[{"id":"` + strings.Repeat("x", 129) + `"}]}`, http.StatusBadRequest},
		"path too long":   {`{"items":[{"id":"1","path":"` + strings.Repeat("p", 4097) + `"}]}`, http.StatusBadRequest},
		"host too long":   {`{"items":[{"id":"1","host":"` + strings.Repeat("h", 256) + `"}]}`, http.StatusBadRequest},
		"body over limit": {`{"items":[{"id":"1","requestPayload":"` + strings.Repeat("a", 5<<20) + `"}]}`, http.StatusRequestEntityTooLarge},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			got := postRedact(redactHandler(), tc.body)
			if got.Code != tc.want {
				t.Errorf("status = %d, want %d, body = %.200s", got.Code, tc.want, got.Body.String())
			}
		})
	}
}

func TestRedactPayloads_ValidRequestWithoutServiceIsUnavailableNotEmpty(t *testing.T) {
	got := postRedact(redactHandler(), `{"items":[{"id":"1","requestPayload":"x"}]}`)
	if got.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503, body = %s", got.Code, got.Body.String())
	}
}
