package kafka

import (
	"context"
	"encoding/json"
	"net/http"
	"sync/atomic"
	"testing"
	"time"

	kafkago "github.com/segmentio/kafka-go"
	"go.uber.org/zap"
)

// unreachableProducer writes to a broker that refuses connections, so every
// write fails fast and exercises the fallback path.
func unreachableProducer(t *testing.T) *ThreatProducer {
	t.Helper()
	writer := kafkago.NewWriter(kafkago.WriterConfig{
		Brokers:      []string{"127.0.0.1:1"},
		Topic:        "akto.threat_detection.guardrail_events",
		Balancer:     &kafkago.Hash{},
		BatchTimeout: 10 * time.Millisecond,
		WriteTimeout: 200 * time.Millisecond,
		MaxAttempts:  1,
	})
	t.Cleanup(func() { writer.Close() })
	return &ThreatProducer{writer: writer, logger: zap.NewNop()}
}

// When the broker is unreachable the event must still reach the backend, so
// turning the buffer on can never be worse than leaving it off.
func TestSink_FallsBackToDirectPostWhenBrokerDown(t *testing.T) {
	var hits int32
	var gotBody []byte
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		buf := make([]byte, r.ContentLength)
		r.Body.Read(buf)
		gotBody = buf
		w.WriteHeader(http.StatusAccepted)
	})

	sink := unreachableProducer(t).Sink()
	if err := sink(context.Background(), []byte(sampleBody)); err != nil {
		t.Fatalf("sink should have recovered via the direct POST, got: %v", err)
	}

	if got := atomic.LoadInt32(&hits); got != 1 {
		t.Fatalf("expected 1 fallback POST, got %d", got)
	}

	// The fallback must forward the body untouched — the threat client and the
	// direct path have to be interchangeable on the wire.
	var envelope map[string]any
	if err := json.Unmarshal(gotBody, &envelope); err != nil {
		t.Fatalf("fallback body is not valid JSON: %v", err)
	}
	if _, ok := envelope["maliciousEvent"]; !ok {
		t.Fatalf("fallback body lost the maliciousEvent wrapper: %s", gotBody)
	}
}

// If both the buffer and the backend are down the error surfaces, rather than
// the event vanishing silently.
func TestSink_ErrorsWhenBrokerAndBackendBothDown(t *testing.T) {
	withThreatAPI(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	})

	sink := unreachableProducer(t).Sink()
	err := sink(context.Background(), []byte(sampleBody))
	if err == nil {
		t.Fatal("expected an error when both Kafka and the backend are unavailable")
	}
}
