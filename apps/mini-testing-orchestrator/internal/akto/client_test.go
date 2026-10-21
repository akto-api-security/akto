package akto

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
)

type fakeCyborg struct {
	mu       sync.Mutex
	exchange int  // HTTP status for /api/exchangeToken
	scoped   bool // reject the raw token on other paths, as AuthFilter does
	calls    []string
	auth     []string
}

func (f *fakeCyborg) handler(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.calls = append(f.calls, r.URL.Path)
	f.auth = append(f.auth, r.Header.Get("Authorization"))
	if r.URL.Path == "/api/exchangeToken" {
		var in map[string]string
		_ = json.NewDecoder(r.Body).Decode(&in)
		if f.exchange != http.StatusOK || in["moduleType"] != "MINI_TESTING" {
			w.WriteHeader(f.exchange)
			return
		}
		_, _ = w.Write([]byte(`{"token":"exchanged"}`))
		return
	}
	if f.scoped && r.Header.Get("Authorization") != "exchanged" {
		w.WriteHeader(http.StatusForbidden)
		return
	}
	_, _ = w.Write([]byte(`{}`))
}

func newClient(t *testing.T, f *fakeCyborg) *Client {
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	t.Cleanup(srv.Close)
	return New(srv.URL+"/", "raw", "MINI_TESTING", slog.New(slog.NewTextHandler(io.Discard, nil)))
}

func TestScopedTokenIsExchangedOnce(t *testing.T) {
	f := &fakeCyborg{exchange: http.StatusOK, scoped: true}
	c := newClient(t, f)
	for i := 0; i < 2; i++ {
		if err := c.Post(context.Background(), "/api/insertTestingLog", map[string]any{}); err != nil {
			t.Fatal(err)
		}
	}
	want := []string{"/api/exchangeToken", "/api/insertTestingLog", "/api/insertTestingLog"}
	if len(f.calls) != 3 || f.calls[0] != want[0] {
		t.Fatalf("calls = %v, want %v", f.calls, want)
	}
	if f.auth[1] != "exchanged" || f.auth[2] != "exchanged" {
		t.Errorf("auth = %v, want the exchanged token after the exchange", f.auth)
	}
	if c.TokenStatus() != "exchanged for MINI_TESTING" {
		t.Errorf("status = %q", c.TokenStatus())
	}
}

func TestUnscopedTokenUsedAsIs(t *testing.T) {
	// cyborg answers 422 for a token without a scope
	f := &fakeCyborg{exchange: http.StatusUnprocessableEntity}
	c := newClient(t, f)
	for i := 0; i < 2; i++ {
		if err := c.Post(context.Background(), "/api/insertTestingLog", map[string]any{}); err != nil {
			t.Fatal(err)
		}
	}
	if len(f.calls) != 3 {
		t.Fatalf("calls = %v, want one exchange then two posts", f.calls)
	}
	if f.auth[1] != "raw" {
		t.Errorf("auth = %q, want raw", f.auth[1])
	}
}

func TestNotConfigured(t *testing.T) {
	if New("", "tok", "MINI_TESTING", nil) != nil || New("http://x", "", "MINI_TESTING", nil) != nil {
		t.Fatal("expected nil client without url or token")
	}
}

func TestStatusErrorReturned(t *testing.T) {
	f := &fakeCyborg{exchange: http.StatusUnprocessableEntity, scoped: true}
	err := newClient(t, f).Post(context.Background(), "/api/updateModuleInfoForHeartbeat", map[string]any{})
	se, ok := err.(*StatusError)
	if !ok || se.Code != http.StatusForbidden {
		t.Fatalf("err = %v, want HTTP 403", err)
	}
}
