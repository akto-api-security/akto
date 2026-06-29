// Package backpressure provides an adaptive, self-healing circuit breaker for a
// slow downstream dependency. It is the Go counterpart of agent-guard's
// LatencyBreaker (cascade_backpressure.py): a rolling, time-bounded latency
// window that trips when the recent average latency meets a threshold.
//
// guardrails-service synchronously fans every request out to agent-guard's
// /scan endpoint (via the mcp-endpoint-shield processor). When agent-guard is
// slow or unresponsive, each ValidateRequest/ValidateResponse blocks on that
// call (up to the 60s HTTP timeout), exhausting the connection pool and
// collapsing throughput. The breaker watches that call's latency; once the
// recent average crosses the threshold it short-circuits subsequent requests
// to a fail-open (Allowed) verdict instead of waiting, so the service keeps
// serving while the dependency recovers.
//
// Recovery: samples older than the TTL are evicted on every read/write,
// independently of new traffic. While tripped the protected work doesn't run,
// so no fresh latency is recorded — a purely count-based window would latch ON
// forever. Time eviction ages stale samples out; once fewer than MinSamples
// remain, ShouldSkip returns false, the next request runs as a natural probe,
// and fresh latencies decide whether to re-trip. A transient slowdown
// self-clears within ~TTL instead of requiring a restart.
//
// State is per-process. The zero value is not usable; construct with New or
// NewFromEnv.
package backpressure

import (
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Config tunes a Breaker. All fields are overridable via env (see NewFromEnv).
type Config struct {
	Enabled     bool
	MinSamples  int
	ThresholdMs float64
	Window      int
	TTLSeconds  float64
}

// DefaultConfig mirrors agent-guard's MODEL (cascade) breaker defaults. The
// 8000ms threshold sits well above a healthy guardrail validation (~1-2s incl.
// the agent-guard cascade) and trips decisively when the downstream stalls.
func DefaultConfig() Config {
	return Config{
		Enabled:     true,
		MinSamples:  5,
		ThresholdMs: 8000.0,
		Window:      50,
		TTLSeconds:  30.0,
	}
}

type sample struct {
	ts        time.Time
	latencyMs float64
}

// Breaker is a self-healing, time-bounded rolling-average latency circuit breaker.
type Breaker struct {
	reason string
	cfg    Config

	mu      sync.Mutex
	samples []sample
	now     func() time.Time // indirected so tests can control time
}

// New builds a Breaker with an explicit config. reason labels skip/diagnostic output.
func New(reason string, cfg Config) *Breaker {
	if cfg.Window < 1 {
		cfg.Window = 1
	}
	if cfg.MinSamples < 1 {
		cfg.MinSamples = 1
	}
	return &Breaker{
		reason:  reason,
		cfg:     cfg,
		samples: make([]sample, 0, cfg.Window),
		now:     time.Now,
	}
}

// NewFromEnv builds a Breaker, overlaying env overrides (prefix_ENABLED,
// prefix_MIN_SAMPLES, prefix_AVG_LATENCY_MS, prefix_WINDOW, prefix_TTL_SECONDS)
// on top of DefaultConfig. e.g. prefix "GUARDRAILS_BACKPRESSURE".
func NewFromEnv(prefix, reason string) *Breaker {
	d := DefaultConfig()
	cfg := Config{
		Enabled:     envBool(prefix+"_ENABLED", d.Enabled),
		MinSamples:  envInt(prefix+"_MIN_SAMPLES", d.MinSamples),
		ThresholdMs: envFloat(prefix+"_AVG_LATENCY_MS", d.ThresholdMs),
		Window:      envInt(prefix+"_WINDOW", d.Window),
		TTLSeconds:  envFloat(prefix+"_TTL_SECONDS", d.TTLSeconds),
	}
	return New(reason, cfg)
}

// Config returns the breaker's effective configuration.
func (b *Breaker) Config() Config { return b.cfg }

// pruneLocked drops samples older than the TTL. Caller must hold b.mu.
func (b *Breaker) pruneLocked(now time.Time) {
	if b.cfg.TTLSeconds <= 0 {
		return
	}
	cutoff := now.Add(-time.Duration(b.cfg.TTLSeconds * float64(time.Second)))
	i := 0
	for i < len(b.samples) && b.samples[i].ts.Before(cutoff) {
		i++
	}
	if i > 0 {
		b.samples = b.samples[i:]
	}
}

// Record adds a latency observation (milliseconds). Non-positive values are ignored.
func (b *Breaker) Record(elapsedMs float64) {
	if elapsedMs <= 0 {
		return
	}
	now := b.now()
	b.mu.Lock()
	defer b.mu.Unlock()
	b.samples = append(b.samples, sample{ts: now, latencyMs: elapsedMs})
	// Bound memory under bursts: keep only the most recent Window samples.
	if len(b.samples) > b.cfg.Window {
		b.samples = b.samples[len(b.samples)-b.cfg.Window:]
	}
	b.pruneLocked(now)
}

// avgAndCountLocked returns the rolling average and count of non-expired samples.
func (b *Breaker) avgAndCountLocked(now time.Time) (float64, int) {
	b.pruneLocked(now)
	n := len(b.samples)
	if n == 0 {
		return 0, 0
	}
	var sum float64
	for _, s := range b.samples {
		sum += s.latencyMs
	}
	return sum / float64(n), n
}

// ShouldSkip reports whether the average of non-expired latencies meets the
// threshold. Stale samples are evicted first so this recovers on its own.
func (b *Breaker) ShouldSkip() bool {
	if !b.cfg.Enabled {
		return false
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	avg, count := b.avgAndCountLocked(b.now())
	if count < b.cfg.MinSamples {
		return false
	}
	return avg >= b.cfg.ThresholdMs
}

// RecentAvgLatencyMs returns the rolling average of non-expired latencies and
// whether any samples exist.
func (b *Breaker) RecentAvgLatencyMs() (float64, bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	avg, count := b.avgAndCountLocked(b.now())
	return avg, count > 0
}

// RecentSampleCount returns the number of non-expired samples.
func (b *Breaker) RecentSampleCount() int {
	b.mu.Lock()
	defer b.mu.Unlock()
	_, count := b.avgAndCountLocked(b.now())
	return count
}

// Reason returns the breaker's label.
func (b *Breaker) Reason() string { return b.reason }

// Snapshot is a point-in-time view of the breaker, for diagnostics endpoints.
type Snapshot struct {
	Reason            string  `json:"reason"`
	Enabled           bool    `json:"enabled"`
	Tripped           bool    `json:"tripped"`
	RecentSampleCount int     `json:"recent_sample_count"`
	RecentAvgLatency  float64 `json:"recent_avg_latency_ms"`
	ThresholdMs       float64 `json:"threshold_ms"`
	MinSamples        int     `json:"min_samples"`
	TTLSeconds        float64 `json:"ttl_seconds"`
}

// Snapshot returns the current window state for diagnostics.
func (b *Breaker) Snapshot() Snapshot {
	b.mu.Lock()
	avg, count := b.avgAndCountLocked(b.now())
	b.mu.Unlock()
	tripped := b.cfg.Enabled && count >= b.cfg.MinSamples && avg >= b.cfg.ThresholdMs
	return Snapshot{
		Reason:            b.reason,
		Enabled:           b.cfg.Enabled,
		Tripped:           tripped,
		RecentSampleCount: count,
		RecentAvgLatency:  round2(avg),
		ThresholdMs:       b.cfg.ThresholdMs,
		MinSamples:        b.cfg.MinSamples,
		TTLSeconds:        b.cfg.TTLSeconds,
	}
}

func round2(f float64) float64 {
	return float64(int64(f*100+0.5)) / 100
}

// setNow overrides the clock (test-only).
func (b *Breaker) setNow(fn func() time.Time) {
	b.mu.Lock()
	b.now = fn
	b.mu.Unlock()
}

func envBool(key string, def bool) bool {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return def
	}
	switch strings.ToLower(raw) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	}
	return def
}

func envInt(key string, def int) int {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return def
	}
	v, err := strconv.Atoi(raw)
	if err != nil || v < 1 {
		return def
	}
	return v
}

func envFloat(key string, def float64) float64 {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return def
	}
	v, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return def
	}
	return v
}
