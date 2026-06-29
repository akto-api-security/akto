package backpressure

import (
	"testing"
	"time"
)

// fakeClock returns a controllable clock and a function to advance it.
func fakeClock(start time.Time) (*time.Time, func() time.Time) {
	cur := start
	return &cur, func() time.Time { return cur }
}

func newTestBreaker(cfg Config) (*Breaker, *time.Time) {
	b := New("test", cfg)
	cur, now := fakeClock(time.Unix(1_000_000, 0))
	b.setNow(now)
	return b, cur
}

func TestDoesNotTripBelowMinSamples(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 4; i++ {
		b.Record(5000) // well over threshold, but below min_samples
	}
	if b.ShouldSkip() {
		t.Fatalf("breaker tripped with %d samples (< min 5)", b.RecentSampleCount())
	}
}

func TestTripsWhenAvgOverThreshold(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 5; i++ {
		b.Record(5000)
	}
	if !b.ShouldSkip() {
		t.Fatalf("breaker did not trip: avg over threshold with enough samples")
	}
}

func TestDoesNotTripWhenAvgBelowThreshold(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 10; i++ {
		b.Record(100) // healthy
	}
	if b.ShouldSkip() {
		t.Fatalf("breaker tripped on healthy latencies")
	}
}

func TestDisabledNeverTrips(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: false, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 20; i++ {
		b.Record(9999)
	}
	if b.ShouldSkip() {
		t.Fatalf("disabled breaker tripped")
	}
}

func TestSelfHealsAfterTTL(t *testing.T) {
	b, cur := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 5; i++ {
		b.Record(5000)
	}
	if !b.ShouldSkip() {
		t.Fatalf("expected tripped before TTL elapses")
	}
	// Advance past TTL: stale samples evicted, breaker heals with no new traffic.
	*cur = cur.Add(31 * time.Second)
	if b.ShouldSkip() {
		t.Fatalf("breaker did not self-heal after TTL eviction")
	}
	if c := b.RecentSampleCount(); c != 0 {
		t.Fatalf("expected 0 samples after TTL, got %d", c)
	}
}

func TestWindowBoundsMemory(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 10, TTLSeconds: 300})
	for i := 0; i < 50; i++ {
		b.Record(100)
	}
	if c := b.RecentSampleCount(); c != 10 {
		t.Fatalf("expected window capped at 10, got %d", c)
	}
}

func TestRecentSamplesDecideRetrip(t *testing.T) {
	// After healing, fresh healthy latencies should keep it open even though it
	// was previously tripped by stale high samples.
	b, cur := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 5; i++ {
		b.Record(5000)
	}
	*cur = cur.Add(31 * time.Second) // evict the high samples
	for i := 0; i < 5; i++ {
		b.Record(100) // fresh, healthy
	}
	if b.ShouldSkip() {
		t.Fatalf("breaker re-tripped despite fresh healthy latencies")
	}
}

func TestSnapshotReportsTripped(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: true, MinSamples: 5, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	for i := 0; i < 5; i++ {
		b.Record(2000)
	}
	snap := b.Snapshot()
	if !snap.Tripped {
		t.Fatalf("snapshot should report tripped, got %+v", snap)
	}
	if snap.RecentSampleCount != 5 {
		t.Fatalf("expected 5 samples in snapshot, got %d", snap.RecentSampleCount)
	}
	if snap.RecentAvgLatency != 2000 {
		t.Fatalf("expected avg 2000, got %v", snap.RecentAvgLatency)
	}
}

func TestNonPositiveLatencyIgnored(t *testing.T) {
	b, _ := newTestBreaker(Config{Enabled: true, MinSamples: 1, ThresholdMs: 1000, Window: 50, TTLSeconds: 30})
	b.Record(0)
	b.Record(-5)
	if c := b.RecentSampleCount(); c != 0 {
		t.Fatalf("expected non-positive latencies ignored, got %d samples", c)
	}
}
