package sink

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
	"github.com/akto-api-security/otel-ingestion-service/pkg/tenant"
	"go.uber.org/zap"
)

func TestHTTPSinkEmit(t *testing.T) {
	var gotAuth string
	var gotBody ingestDataRequest

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/ingestData" {
			t.Fatalf("unexpected path %s", r.URL.Path)
		}
		gotAuth = r.Header.Get("Authorization")
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	router := tenant.NewRouter(srv.URL, "", nil)
	sink := NewHTTPSink(router, 2*time.Second, zap.NewNop())

	err := sink.Emit(context.Background(), Batch{
		AccountID: 42,
		AuthToken: "jwt-token",
		Events: []model.OtelIngestEvent{{
			AccountID:  42,
			Source:     "claude_code",
			EventName:  "claude_code.user_prompt",
			Attributes: map[string]string{"service.name": "cowork", "prompt.id": "p1"},
		}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if gotAuth != "jwt-token" {
		t.Fatalf("expected auth forwarded, got %q", gotAuth)
	}
	if len(gotBody.BatchData) != 1 {
		t.Fatalf("expected 1 batch item, got %d", len(gotBody.BatchData))
	}
	if gotBody.BatchData[0].PublishToGuardrails != true {
		t.Fatal("expected publishToGuardrails")
	}
}
