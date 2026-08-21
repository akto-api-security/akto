package kafka

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	kafkago "github.com/segmentio/kafka-go"
	"go.uber.org/zap"
)

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

func testClient(t *testing.T, maxBackoffSec int) *ThreatClient {
	t.Helper()
	return &ThreatClient{
		cfg: config.ThreatClientConfig{
			Topic:         config.DefaultThreatTopic,
			GroupID:       config.DefaultThreatClientGroupID,
			MaxBackoffSec: maxBackoffSec,
		},
		logger: zap.NewNop(),
	}
}

const sampleBody = `{"maliciousEvent":{"actor":"1.2.3.4","sessionId":"sess-1","filterId":"PromptInjection"}}`

func msgOf(body string) kafkago.Message {
	return kafkago.Message{Value: []byte(body), Offset: 42}
}

// A 2xx commits after a single attempt.
func TestForwardWithRetry_SuccessCommits(t *testing.T) {
	var hits int32
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		w.WriteHeader(http.StatusAccepted)
	})

	ok := testClient(t, 1).forwardWithRetry(context.Background(), msgOf(sampleBody))
	if !ok {
		t.Fatal("expected the message to be committable after a 2xx")
	}
	if got := atomic.LoadInt32(&hits); got != 1 {
		t.Fatalf("expected 1 attempt, got %d", got)
	}
}

// This is the whole point of the buffer: a 5xx must not commit, and must keep
// retrying until the backend recovers.
func TestForwardWithRetry_RetriesUntilBackendRecovers(t *testing.T) {
	var hits int32
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		if atomic.AddInt32(&hits, 1) < 3 {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusAccepted)
	})

	ok := testClient(t, 1).forwardWithRetry(context.Background(), msgOf(sampleBody))
	if !ok {
		t.Fatal("expected the message to be committable once the backend recovered")
	}
	if got := atomic.LoadInt32(&hits); got != 3 {
		t.Fatalf("expected 3 attempts (2 failures then success), got %d", got)
	}
}

// A 4xx is a body the backend will never accept — drop it rather than wedge the
// partition retrying forever.
func TestForwardWithRetry_DropsOn4xx(t *testing.T) {
	var hits int32
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("Invalid request"))
	})

	ok := testClient(t, 1).forwardWithRetry(context.Background(), msgOf(sampleBody))
	if !ok {
		t.Fatal("expected a 4xx to be committed (dropped), not retried")
	}
	if got := atomic.LoadInt32(&hits); got != 1 {
		t.Fatalf("expected exactly 1 attempt for a 4xx, got %d", got)
	}
}

// 429 is the one 4xx worth retrying.
func TestForwardWithRetry_Retries429(t *testing.T) {
	var hits int32
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		if atomic.AddInt32(&hits, 1) < 2 {
			w.WriteHeader(http.StatusTooManyRequests)
			return
		}
		w.WriteHeader(http.StatusAccepted)
	})

	if ok := testClient(t, 1).forwardWithRetry(context.Background(), msgOf(sampleBody)); !ok {
		t.Fatal("expected success after the rate limit cleared")
	}
	if got := atomic.LoadInt32(&hits); got != 2 {
		t.Fatalf("expected 2 attempts, got %d", got)
	}
}

// Shutdown mid-retry must report "not committable" so the offset stays put and
// the next process redelivers the event.
func TestForwardWithRetry_ShutdownDoesNotCommit(t *testing.T) {
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	})

	ctx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
	defer cancel()

	ok := testClient(t, 1).forwardWithRetry(ctx, msgOf(sampleBody))
	if ok {
		t.Fatal("expected the message to stay uncommitted when shutting down mid-retry")
	}
}

// A transport failure (backend unreachable, not merely erroring) is retryable
// and must not be mistaken for a permanent rejection.
func TestForwardWithRetry_TransportErrorIsRetryable(t *testing.T) {
	prev := mcp.ThreatDetectionAPIURL
	// Port 1 on loopback refuses connections immediately.
	mcp.ThreatDetectionAPIURL = "http://127.0.0.1:1/api/threat_detection/record_malicious_event"
	t.Cleanup(func() { mcp.ThreatDetectionAPIURL = prev })

	ctx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
	defer cancel()

	if ok := testClient(t, 1).forwardWithRetry(ctx, msgOf(sampleBody)); ok {
		t.Fatal("expected an unreachable backend to be retried, not dropped")
	}
}

func TestThreatAPIErrorRetryable(t *testing.T) {
	cases := []struct {
		status int
		want   bool
	}{
		{http.StatusInternalServerError, true},
		{http.StatusBadGateway, true},
		{http.StatusServiceUnavailable, true},
		{http.StatusTooManyRequests, true},
		{http.StatusBadRequest, false},
		{http.StatusUnauthorized, false},
		{http.StatusForbidden, false},
	}
	for _, c := range cases {
		err := &mcp.ThreatAPIError{StatusCode: c.status}
		if got := err.Retryable(); got != c.want {
			t.Errorf("status %d: Retryable() = %v, want %v", c.status, got, c.want)
		}
		var target *mcp.ThreatAPIError
		if !errors.As(error(err), &target) {
			t.Errorf("status %d: errors.As failed to match ThreatAPIError", c.status)
		}
	}
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
