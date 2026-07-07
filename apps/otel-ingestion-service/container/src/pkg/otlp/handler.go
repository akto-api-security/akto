package otlp

// OTLP HTTP receiver for OpenTelemetry signals.
//
// Cowork admin configures POST /v1/logs with http/json or http/protobuf:
//   - https://claude.com/docs/cowork/monitoring#setup
//   - https://support.claude.com/en/articles/14477985-monitor-claude-cowork-activity-with-opentelemetry
//
// /v1/traces and /v1/metrics are standard OTLP paths (ack-fast stubs; Cowork does not send them today).
import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/akto-api-security/otel-ingestion-service/pkg/auth"
	"github.com/akto-api-security/otel-ingestion-service/pkg/pipeline"
	"go.opentelemetry.io/collector/pdata/plog/plogotlp"
)

type Handler struct {
	verifier *auth.Verifier
	queue    *pipeline.Queue
	maxBytes int
}

func NewHandler(verifier *auth.Verifier, queue *pipeline.Queue, maxBytes int) *Handler {
	return &Handler{
		verifier: verifier,
		queue:    queue,
		maxBytes: maxBytes,
	}
}

func (h *Handler) Register(mux *http.ServeMux) {
	mux.HandleFunc("GET /health", h.health)
	mux.HandleFunc("GET /backpressure", h.backpressure)
	mux.HandleFunc("POST /v1/logs", h.ingest(pipeline.SignalLogs))
	mux.HandleFunc("POST /v1/traces", h.ingest(pipeline.SignalTraces))
	mux.HandleFunc("POST /v1/metrics", h.ingest(pipeline.SignalMetrics))
}

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"success": true})
}

func (h *Handler) backpressure(w http.ResponseWriter, _ *http.Request) {
	enqueued, rejected, processed := h.queue.Stats()
	writeJSON(w, http.StatusOK, map[string]any{
		"queue_depth":    h.queue.Depth(),
		"queue_capacity": h.queue.Capacity(),
		"enqueued":       enqueued,
		"rejected":       rejected,
		"processed":      processed,
	})
}

func (h *Handler) ingest(signal pipeline.SignalType) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		accountID, err := h.verifier.Authenticate(r.Header.Get("Authorization"))
		if err != nil {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}

		if r.ContentLength > int64(h.maxBytes) {
			http.Error(w, "payload too large", http.StatusRequestEntityTooLarge)
			return
		}

		body, err := readBody(r.Body, h.maxBytes, h.queue.BufferPool())
		if err != nil {
			if errors.Is(err, errTooLarge) {
				http.Error(w, "payload too large", http.StatusRequestEntityTooLarge)
				return
			}
			http.Error(w, "bad request", http.StatusBadRequest)
			return
		}

		job := pipeline.Job{
			AccountID:   accountID,
			Signal:      signal,
			ContentType: r.Header.Get("Content-Type"),
			Body:        body,
		}
		if !h.queue.TryEnqueue(job) {
			h.queue.BufferPool().Put(body)
			http.Error(w, "service unavailable", http.StatusServiceUnavailable)
			return
		}

		writeExportResponse(w, r.Header.Get("Content-Type"))
	}
}

var errTooLarge = errors.New("payload too large")

func readBody(r io.Reader, maxBytes int, pool *pipeline.BufferPool) ([]byte, error) {
	buf := pool.Get()
	chunk := make([]byte, 32*1024)
	for {
		if len(buf) >= maxBytes {
			pool.Put(buf)
			return nil, errTooLarge
		}
		remaining := maxBytes - len(buf)
		if remaining > len(chunk) {
			remaining = len(chunk)
		}
		n, err := r.Read(chunk[:remaining])
		if n > 0 {
			buf = append(buf, chunk[:n]...)
		}
		if err == io.EOF {
			return buf, nil
		}
		if err != nil {
			pool.Put(buf)
			return nil, err
		}
	}
}

func writeExportResponse(w http.ResponseWriter, contentType string) {
	ct := strings.ToLower(strings.TrimSpace(contentType))
	if strings.Contains(ct, "json") {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("{}"))
		return
	}

	resp := plogotlp.NewExportResponse()
	body, err := resp.MarshalProto()
	if err != nil {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.Header().Set("Content-Type", "application/x-protobuf")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(body)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
