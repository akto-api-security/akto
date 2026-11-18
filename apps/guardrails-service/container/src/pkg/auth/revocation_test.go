package auth

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"go.uber.org/zap"
)

// sha256("abc"); AuthTokenIds.idOf in libs/dao and the other services pin the same value
const abcSignatureID = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

func TestSignatureTokenID_MatchesJavaVector(t *testing.T) {
	if got := SignatureTokenID("header.payload.abc"); got != abcSignatureID {
		t.Fatalf("SignatureTokenID = %q", got)
	}
}

func TestSignatureTokenID_IgnoresBearerAndWhitespace(t *testing.T) {
	for _, raw := range []string{"h.p.abc", "  h.p.abc\n", "Bearer h.p.abc", "bearer   h.p.abc ", "BEARER other.payload.abc"} {
		if got := SignatureTokenID(raw); got != abcSignatureID {
			t.Fatalf("%q: id = %q, want the signature hash", raw, got)
		}
	}
}

func TestTokenID_Precedence(t *testing.T) {
	if got := TokenID(jwt.MapClaims{"tid": "p"}, "h.p.abc"); got != "p" {
		t.Fatalf("tid should win, got %q", got)
	}
	if got := TokenID(jwt.MapClaims{"tid": ""}, "h.p.abc"); got != abcSignatureID {
		t.Fatalf("empty tid must be ignored, got %q", got)
	}
	if got := TokenID(jwt.MapClaims{}, "h.p.abc"); got != abcSignatureID {
		t.Fatalf("signature fallback, got %q", got)
	}
}

func TestTokenID_SameClaimsDifferentSignaturesDiffer(t *testing.T) {
	privA, _ := mustKeyPair(t)
	privB, _ := mustKeyPair(t)
	claims := jwt.MapClaims{"accountId": 1}
	a, b := signToken(t, privA, claims), signToken(t, privB, claims)
	if SignatureTokenID(a) == SignatureTokenID(b) {
		t.Fatal("tokens with different signatures must get different ids")
	}
}

func TestPoller_LoadsRevokedIDsAndFailsOpen(t *testing.T) {
	var failing atomic.Bool
	var gotAuth atomic.Value
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth.Store(r.Header.Get("Authorization"))
		if r.URL.Path != "/api/fetchRevokedTokenIds" || r.Method != http.MethodPost {
			t.Errorf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if failing.Load() {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		_, _ = w.Write([]byte(`{"revokedTokenIds":["a","b"]}`))
	}))
	defer srv.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	set := startPoller(ctx, zap.NewNop(), srv.URL+"/", func() string { return "svc-token" }, 20*time.Millisecond, 20*time.Millisecond, srv.Client())

	waitFor(t, func() bool { return set.Contains("a") && set.Contains("b") })
	if gotAuth.Load() != "svc-token" {
		t.Fatalf("Authorization = %v, want raw service token", gotAuth.Load())
	}

	failing.Store(true)
	time.Sleep(100 * time.Millisecond)
	if !set.Contains("a") {
		t.Fatal("a failed poll must keep the last known set")
	}
}

func TestPoller_RetriesSoonerAfterAFailedPoll(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if calls.Add(1) == 1 {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		_, _ = w.Write([]byte(`{"revokedTokenIds":["a"]}`))
	}))
	defer srv.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	set := startPoller(ctx, zap.NewNop(), srv.URL+"/", func() string { return "svc-token" }, time.Hour, 20*time.Millisecond, srv.Client())

	waitFor(t, func() bool { return set.Contains("a") })
	if calls.Load() < 2 {
		t.Fatal("expected a retry after the failed first poll")
	}
}

func TestStartRevocationPoller_DisabledWithoutConfig(t *testing.T) {
	if set := StartRevocationPoller(context.Background(), zap.NewNop(), "", func() string { return "t" }); set != nil {
		t.Fatal("expected nil set without a URL")
	}
	if set := StartRevocationPoller(context.Background(), zap.NewNop(), "http://x", func() string { return "" }); set != nil {
		t.Fatal("expected nil set without a token")
	}
	if (*RevokedSet)(nil).Contains("a") {
		t.Fatal("nil set must revoke nothing")
	}
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
