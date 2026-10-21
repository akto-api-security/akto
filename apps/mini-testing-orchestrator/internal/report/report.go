// Package report sends the orchestrator's important events (scaling, drains,
// stuck states, errors) to stdout and, when configured, to Akto's database
// abstractor (cyborg). There they land in the orchestrator's own log source
// (Settings > Logs > Mini Testing Orchestrator), so Akto can see what the
// orchestrator did even when a client does not share cluster logs. A cyborg
// without that source gets them as testing logs instead.
//
// Sending to cyborg never blocks the caller: events go through a bounded queue
// and are dropped when it is full or the per-minute budget is spent.
package report

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/akto-api-security/akto/apps/mini-testing-orchestrator/internal/akto"
)

const (
	queueSize    = 100
	failLogEvery = 5 * time.Minute

	logPath = "/api/insertMiniTestingOrchestratorLog"
	// Older cyborg releases only have the module's testing log source.
	fallbackLogPath = "/api/insertTestingLog"
	// While on the fallback, the orchestrator's own source is retried this
	// often, so a cyborg upgrade is picked up without restarting.
	recheckLogPathEvery = 10 * time.Minute
)

// Reporter logs events locally and forwards them to cyborg.
type Reporter struct {
	log      *slog.Logger
	cyborg   *cyborgSink // nil when not configured
	moduleID string
}

type Options struct {
	// Client sends to cyborg; nil disables sending.
	Client *akto.Client
	// ModuleName is the mini-testing module the orchestrator scales; it tags
	// every event the way LoggerMaker tags the module's own logs.
	ModuleName string
	// PerMinute caps events sent to cyborg.
	PerMinute int
}

func New(log *slog.Logger, opts Options) *Reporter {
	r := &Reporter{log: log, moduleID: opts.ModuleName}
	if opts.Client != nil {
		r.cyborg = newCyborgSink(log, opts)
	}
	return r
}

// Run delivers queued events until ctx is cancelled. A no-op without cyborg.
func (r *Reporter) Run(ctx context.Context) {
	if r.cyborg != nil {
		r.cyborg.run(ctx)
	}
}

func (r *Reporter) Info(msg string, args ...any)  { r.event(slog.LevelInfo, msg, args...) }
func (r *Reporter) Warn(msg string, args ...any)  { r.event(slog.LevelWarn, msg, args...) }
func (r *Reporter) Error(msg string, args ...any) { r.event(slog.LevelError, msg, args...) }

func (r *Reporter) event(level slog.Level, msg string, args ...any) {
	r.log.Log(context.Background(), level, msg, args...)
	if r.cyborg != nil {
		r.cyborg.enqueue(level, fmt.Sprintf("mini-testing-orchestrator : [%s] %s%s", r.moduleID, msg, formatArgs(args)))
	}
}

// formatArgs renders slog-style key/value pairs as " k=v k=v", sorted by key.
func formatArgs(args []any) string {
	var pairs []string
	for i := 0; i+1 < len(args); i += 2 {
		pairs = append(pairs, fmt.Sprintf("%v=%v", args[i], args[i+1]))
	}
	if len(pairs) == 0 {
		return ""
	}
	sort.Strings(pairs)
	return " " + strings.Join(pairs, " ")
}

type entry struct {
	level slog.Level
	text  string
	ts    int64
}

type cyborgSink struct {
	client     *akto.Client
	path       string
	fallbackAt time.Time
	perMinute  int
	log        *slog.Logger
	queue      chan entry

	mu          sync.Mutex
	windowStart time.Time
	sent        int
	dropped     int
	lastFailLog time.Time
}

func newCyborgSink(log *slog.Logger, opts Options) *cyborgSink {
	perMinute := opts.PerMinute
	if perMinute <= 0 {
		perMinute = 30
	}
	return &cyborgSink{
		client:    opts.Client,
		path:      logPath,
		perMinute: perMinute,
		log:       log,
		queue:     make(chan entry, queueSize),
	}
}

func (s *cyborgSink) enqueue(level slog.Level, text string) {
	select {
	case s.queue <- entry{level: level, text: text, ts: time.Now().Unix()}:
	default:
		s.mu.Lock()
		s.dropped++
		s.mu.Unlock()
	}
}

func (s *cyborgSink) run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case e := <-s.queue:
			if !s.allow() {
				continue
			}
			if err := s.send(ctx, e); err != nil {
				s.logFailure(err)
			}
		}
	}
}

// allow applies the per-minute budget. When a window closes with events
// dropped, it says so in the next event's place so the gap is visible.
func (s *cyborgSink) allow() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	if now.Sub(s.windowStart) >= time.Minute {
		s.windowStart = now
		s.sent = 0
	}
	if s.sent >= s.perMinute {
		s.dropped++
		return false
	}
	s.sent++
	return true
}

func (s *cyborgSink) send(ctx context.Context, e entry) error {
	s.mu.Lock()
	dropped := s.dropped
	s.dropped = 0
	s.mu.Unlock()
	if dropped > 0 {
		e.text += fmt.Sprintf(" (%d earlier event(s) not sent: rate limit or queue full)", dropped)
	}

	// The same shape LoggerMaker sends from the module: key is the level.
	body := map[string]any{
		"log": map[string]any{"log": e.text, "key": strings.ToLower(e.level.String()), "timestamp": e.ts},
	}
	if s.path == fallbackLogPath && time.Since(s.fallbackAt) >= recheckLogPathEvery {
		s.path = logPath
	}
	err := s.client.Post(ctx, s.path, body)
	var statusErr *akto.StatusError
	switch {
	case errors.As(err, &statusErr) && statusErr.Code == http.StatusNotFound && s.path == logPath:
		if s.fallbackAt.IsZero() {
			s.log.Info("cyborg has no orchestrator log source yet; sending events as testing logs", "path", fallbackLogPath)
		}
		s.path, s.fallbackAt = fallbackLogPath, time.Now()
		err = s.client.Post(ctx, s.path, body)
	case err == nil && s.path == logPath && !s.fallbackAt.IsZero():
		s.log.Info("cyborg now has the orchestrator log source; sending events there", "path", logPath)
		s.fallbackAt = time.Time{}
	}
	return err
}

func (s *cyborgSink) logFailure(err error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if time.Since(s.lastFailLog) < failLogEvery {
		return
	}
	s.lastFailLog = time.Now()
	s.log.Warn("sending events to Akto failed; they are still in these logs", "path", s.path, "err", err)
}
