package probe

import (
	"context"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"
)

// fakePod mimics the mini-testing endpoints, including the token check.
func fakePod(t *testing.T, token string) (ip string, port int) {
	t.Helper()
	draining := false
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get(TokenHeader) != token {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/status":
		case r.Method == http.MethodPost && r.URL.Path == "/drain":
			draining = true
		case r.Method == http.MethodPost && r.URL.Path == "/undrain":
			draining = false
		default:
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"busy":false,"draining":` + strconv.FormatBool(draining) + `}`))
	}))
	t.Cleanup(srv.Close)
	host, p, _ := net.SplitHostPort(srv.Listener.Addr().String())
	port, _ = strconv.Atoi(p)
	return host, port
}

func TestHTTPProber(t *testing.T) {
	ip, port := fakePod(t, "secret")
	p := NewHTTPProber(port, "secret", time.Second)
	ctx := context.Background()

	if st, err := p.Status(ctx, ip); err != nil || st.Draining {
		t.Fatalf("Status = %+v, %v", st, err)
	}
	if st, err := p.Drain(ctx, ip); err != nil || !st.Draining {
		t.Fatalf("Drain = %+v, %v", st, err)
	}
	if st, err := p.Undrain(ctx, ip); err != nil || st.Draining {
		t.Fatalf("Undrain = %+v, %v", st, err)
	}
}

func TestHTTPProberWrongToken(t *testing.T) {
	ip, port := fakePod(t, "secret")
	_, err := NewHTTPProber(port, "wrong", time.Second).Status(context.Background(), ip)
	if !errors.Is(err, ErrUnauthorized) {
		t.Fatalf("err = %v, want ErrUnauthorized", err)
	}
}
