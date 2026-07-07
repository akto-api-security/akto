package otlp

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/akto-api-security/otel-ingestion-service/pkg/auth"
	"github.com/akto-api-security/otel-ingestion-service/pkg/pipeline"
)

func TestIngestAckFast(t *testing.T) {
	queue := pipeline.NewQueue(10)
	defer queue.Close()

	verifier := auth.NewVerifier(false, nil, nil)

	handler := NewHandler(verifier, queue, 1024*1024)
	mux := http.NewServeMux()
	handler.Register(mux)

	body := []byte(`{"resourceLogs":[]}`)
	req := httptest.NewRequest(http.MethodPost, "/v1/logs", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}

	select {
	case job := <-queue.Jobs():
		if job.Signal != pipeline.SignalLogs {
			t.Fatalf("expected logs signal, got %s", job.Signal)
		}
		if len(job.Body) == 0 {
			t.Fatal("expected body in queued job")
		}
	default:
		t.Fatal("expected job to be enqueued")
	}
}

func TestIngestQueueFullReturns503(t *testing.T) {
	queue := pipeline.NewQueue(1)
	// fill queue
	queue.TryEnqueue(pipeline.Job{Body: []byte("x")})

	verifier := auth.NewVerifier(false, nil, nil)
	handler := NewHandler(verifier, queue, 1024*1024)
	mux := http.NewServeMux()
	handler.Register(mux)

	req := httptest.NewRequest(http.MethodPost, "/v1/logs", bytes.NewReader([]byte(`{}`)))
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("expected 503, got %d", rec.Code)
	}
}
