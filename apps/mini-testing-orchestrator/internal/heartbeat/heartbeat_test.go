package heartbeat

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"regexp"
	"testing"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/akto"
)

func TestSendsModuleInfo(t *testing.T) {
	var got map[string]map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/exchangeToken" {
			w.WriteHeader(http.StatusUnprocessableEntity)
			return
		}
		if r.URL.Path != path {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		_ = json.NewDecoder(r.Body).Decode(&got)
	}))
	defer srv.Close()
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	s := New(akto.New(srv.URL, "tok", "MINI_TESTING", log), log, 0, "akto-external-testing", "0.1.0",
		func() map[string]any { return map[string]any{"replicas": 2, "code": "steady"} })

	s.send(context.Background())

	mi := got["moduleInfo"]
	if mi["moduleType"] != ModuleType || mi["name"] != "akto-external-testing" || mi["currentVersion"] != "0.1.0" {
		t.Fatalf("moduleInfo = %v", mi)
	}
	if !regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`).MatchString(mi["id"].(string)) {
		t.Errorf("id %v is not a v4 UUID", mi["id"])
	}
	if mi["lastHeartbeatReceived"].(float64) < mi["startedTs"].(float64) {
		t.Errorf("heartbeat before start: %v", mi)
	}
	data := mi["additionalData"].(map[string]any)
	if data["replicas"].(float64) != 2 || data["code"] != "steady" || data["tokenAuth"] == "" {
		t.Errorf("additionalData = %v", data)
	}
}
