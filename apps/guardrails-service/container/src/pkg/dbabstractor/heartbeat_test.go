package dbabstractor

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"go.uber.org/zap"
)

type heartbeatServer struct {
	mu     sync.Mutex
	bodies []map[string]interface{}
	auth   string
	path   string
}

func newHeartbeatServer(t *testing.T) (*httptest.Server, *heartbeatServer) {
	t.Helper()
	rec := &heartbeatServer{}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		raw, _ := io.ReadAll(r.Body)
		var body map[string]interface{}
		_ = json.Unmarshal(raw, &body)
		rec.mu.Lock()
		rec.bodies = append(rec.bodies, body)
		rec.auth, rec.path = r.Header.Get("Authorization"), r.URL.Path
		rec.mu.Unlock()
		_, _ = w.Write([]byte(`{}`))
	}))
	t.Cleanup(srv.Close)
	return srv, rec
}

func (h *heartbeatServer) count() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.bodies)
}

func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("condition not met in time")
}

func moduleInfoOf(t *testing.T, body map[string]interface{}) map[string]interface{} {
	t.Helper()
	info, ok := body["moduleInfo"].(map[string]interface{})
	if !ok {
		t.Fatalf("body has no moduleInfo object: %v", body)
	}
	return info
}

func TestSendHeartbeat_PostsModuleInfoWithTheRawServiceToken(t *testing.T) {
	srv, rec := newHeartbeatServer(t)
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_URL", srv.URL)
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "svc-token")

	err := NewClient(zap.NewNop()).SendHeartbeat(ModuleHeartbeat{ID: "m1", ModuleType: "GUARDRAIL", Name: "n", StartedTs: 10, LastHeartbeatReceived: 20})
	if err != nil {
		t.Fatal(err)
	}
	if rec.path != "/api/updateModuleInfoForHeartbeat" || rec.auth != "svc-token" {
		t.Fatalf("path=%q auth=%q", rec.path, rec.auth)
	}
	info := moduleInfoOf(t, rec.bodies[0])
	if info["id"] != "m1" || info["moduleType"] != "GUARDRAIL" || info["name"] != "n" || info["startedTs"] != float64(10) || info["lastHeartbeatReceived"] != float64(20) {
		t.Fatalf("unexpected moduleInfo: %v", info)
	}
	if _, present := info["currentVersion"]; present {
		t.Fatal("an unset version must be omitted, not sent empty")
	}
}

func TestStartHeartbeat_KeepsOneModuleIdentityAndStopsWithTheContext(t *testing.T) {
	srv, rec := newHeartbeatServer(t)
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_URL", srv.URL)
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "svc-token")
	t.Setenv("GUARDRAILS_SERVICE_VERSION", "9.9.9")

	ctx, cancel := context.WithCancel(context.Background())
	NewClient(zap.NewNop()).StartHeartbeat(ctx, 20*time.Millisecond)
	waitFor(t, func() bool { return rec.count() >= 3 })
	cancel()
	time.Sleep(60 * time.Millisecond)
	stopped := rec.count()
	time.Sleep(80 * time.Millisecond)
	if rec.count() > stopped+1 {
		t.Fatal("heartbeats must stop once the context is cancelled")
	}

	first, last := moduleInfoOf(t, rec.bodies[0]), moduleInfoOf(t, rec.bodies[2])
	if first["id"] == "" || first["id"] != last["id"] || first["startedTs"] != last["startedTs"] {
		t.Fatalf("id and start time must stay the same across heartbeats: %v vs %v", first, last)
	}
	if first["moduleType"] != "GUARDRAIL" || first["currentVersion"] != "9.9.9" || !strings.HasPrefix(first["name"].(string), "guardrails-service:") {
		t.Fatalf("unexpected identity: %v", first)
	}
}

func TestStartHeartbeat_StaysOffWithoutAServiceToken(t *testing.T) {
	srv, rec := newHeartbeatServer(t)
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_URL", srv.URL)
	t.Setenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "")

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	NewClient(zap.NewNop()).StartHeartbeat(ctx, 10*time.Millisecond)
	time.Sleep(100 * time.Millisecond)
	if rec.count() != 0 {
		t.Fatal("no heartbeat may be sent without a service token")
	}
}
