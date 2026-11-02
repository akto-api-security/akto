package report

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/akto"
)

// captured is a fake cyborg. Without the orchestrator log route it answers
// 404 there, like a cyborg release that predates it.
type captured struct {
	mu          sync.Mutex
	noOwnSource bool
	paths       []string
	auth        []string
	bodies      []map[string]map[string]any
}

func (c *captured) server(t *testing.T) *httptest.Server {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/api/exchangeToken":
			w.WriteHeader(http.StatusUnprocessableEntity) // unscoped token
			return
		case r.URL.Path == logPath && c.noOwnSource, r.URL.Path != logPath && r.URL.Path != fallbackLogPath:
			w.WriteHeader(http.StatusNotFound)
			return
		}
		var b map[string]map[string]any
		_ = json.NewDecoder(r.Body).Decode(&b)
		c.mu.Lock()
		c.paths = append(c.paths, r.URL.Path)
		c.auth = append(c.auth, r.Header.Get("Authorization"))
		c.bodies = append(c.bodies, b)
		c.mu.Unlock()
	}))
	t.Cleanup(srv.Close)
	return srv
}

func (c *captured) count() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.bodies)
}

func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for !cond() {
		if time.Now().After(deadline) {
			t.Fatal("timed out")
		}
		time.Sleep(10 * time.Millisecond)
	}
}

func quietLogger() *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }

func start(t *testing.T, c *captured, opts Options) *Reporter {
	srv := c.server(t)
	opts.Client = akto.New(srv.URL+"/", "tok", "MINI_TESTING", quietLogger())
	r := New(quietLogger(), opts)
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	go r.Run(ctx)
	return r
}

func TestSendsToOwnLogSource(t *testing.T) {
	var c captured
	r := start(t, &c, Options{ModuleName: "akto-external-testing"})

	r.Warn("scaling stuck", "reason", "not_ready", "for", "10m0s")
	waitFor(t, func() bool { return c.count() == 1 })

	log := c.bodies[0]["log"]
	if c.paths[0] != logPath {
		t.Errorf("path = %s, want %s", c.paths[0], logPath)
	}
	if c.auth[0] != "tok" {
		t.Errorf("Authorization = %q", c.auth[0])
	}
	if log["key"] != "warn" {
		t.Errorf("key = %v, want warn", log["key"])
	}
	want := "mini-testing-orchestrator : [akto-external-testing] scaling stuck for=10m0s reason=not_ready"
	if log["log"] != want {
		t.Errorf("log = %q, want %q", log["log"], want)
	}
	if _, ok := log["timestamp"].(float64); !ok {
		t.Errorf("timestamp missing: %v", log)
	}
}

func TestFallsBackToTestingLogsOnOlderCyborg(t *testing.T) {
	c := captured{noOwnSource: true}
	r := start(t, &c, Options{ModuleName: "m"})

	r.Info("first")
	r.Info("second")
	waitFor(t, func() bool { return c.count() == 2 })
	for i, p := range c.paths {
		if p != fallbackLogPath {
			t.Errorf("event %d went to %s, want %s", i, p, fallbackLogPath)
		}
	}
}

func TestRateLimitReportsDroppedCount(t *testing.T) {
	var c captured
	r := start(t, &c, Options{ModuleName: "m", PerMinute: 2})

	for i := 0; i < 5; i++ {
		r.Info("event")
	}
	waitFor(t, func() bool { return c.count() == 2 })
	time.Sleep(100 * time.Millisecond)
	if got := c.count(); got != 2 {
		t.Fatalf("sent %d events, want 2 within the budget", got)
	}

	// A new window: the next event carries the dropped count.
	r.cyborg.mu.Lock()
	r.cyborg.windowStart = time.Time{}
	r.cyborg.mu.Unlock()
	r.Info("later")
	waitFor(t, func() bool { return c.count() == 3 })
	if text := c.bodies[2]["log"]["log"].(string); !strings.Contains(text, "3 earlier event(s) not sent") {
		t.Errorf("dropped count missing: %q", text)
	}
}

func TestNeverBlocksWhenCyborgIsDown(t *testing.T) {
	// No Run goroutine, so nothing drains the queue: enqueue must still return.
	client := akto.New("http://127.0.0.1:1", "tok", "MINI_TESTING", quietLogger())
	r := New(quietLogger(), Options{Client: client, ModuleName: "m"})
	done := make(chan struct{})
	go func() {
		for i := 0; i < queueSize*3; i++ {
			r.Error("event")
		}
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("reporting blocked")
	}
}

func TestDisabledWithoutClient(t *testing.T) {
	r := New(quietLogger(), Options{})
	if r.cyborg != nil {
		t.Fatal("cyborg sink created without a client")
	}
	r.Info("only logged locally")
}

func TestReturnsToOwnSourceOnceCyborgHasIt(t *testing.T) {
	c := captured{noOwnSource: true}
	r := start(t, &c, Options{ModuleName: "m"})

	r.Info("while cyborg is old")
	waitFor(t, func() bool { return c.count() == 1 })

	// cyborg is upgraded; the next send after the recheck interval uses the own source again
	c.mu.Lock()
	c.noOwnSource = false
	c.mu.Unlock()
	r.cyborg.fallbackAt = time.Now().Add(-recheckLogPathEvery)
	r.Info("after the upgrade")
	r.Info("and after")
	waitFor(t, func() bool { return c.count() == 3 })
	if c.paths[0] != fallbackLogPath || c.paths[1] != logPath || c.paths[2] != logPath {
		t.Fatalf("paths = %v", c.paths)
	}
}
