package metrics

import (
	"testing"
	"time"
)

// p95 of 1..100 ms should be the 95th value (95ms) using nearest-rank ceiling.
func TestP95Value(t *testing.T) {
	samples := make([]int64, 0, 100)
	for k := 1; k <= 100; k++ {
		samples = append(samples, int64(k)*1_000_000) // k ms in ns
	}
	got := p95(samples)
	if got != 95.0 {
		t.Fatalf("p95 = %v, want 95.0", got)
	}
}

// Single sample must not panic and returns its own value in ms.
func TestP95SingleSample(t *testing.T) {
	if got := p95([]int64{2_500_000}); got != 2.5 {
		t.Fatalf("p95 single = %v, want 2.5", got)
	}
}

// A dense 1-minute window uses only in-window samples.
func TestWindowSetDense(t *testing.T) {
	now := time.Now().Unix()
	var s []sample
	for i := 0; i < 50; i++ {
		s = append(s, sample{ns: int64(i) * 1_000_000, at: now})
	}
	got := windowSet(s, now-windowSeconds)
	if len(got) != 50 {
		t.Fatalf("dense window len = %d, want 50", len(got))
	}
}

// A sparse 1-minute window widens to the most recent minSamples.
func TestWindowSetSparseWidens(t *testing.T) {
	now := time.Now().Unix()
	var s []sample
	// 40 old samples (outside the 1-min window) + 3 recent ones.
	for i := 0; i < 40; i++ {
		s = append(s, sample{ns: 1_000_000, at: now - 200})
	}
	for i := 0; i < 3; i++ {
		s = append(s, sample{ns: 9_000_000, at: now})
	}
	got := windowSet(s, now-windowSeconds)
	if len(got) != minSamples {
		t.Fatalf("sparse window len = %d, want %d (widened)", len(got), minSamples)
	}
}

// evictOlder drops only aged-out prefix samples.
func TestEvictOlder(t *testing.T) {
	now := time.Now().Unix()
	s := []sample{
		{ns: 1, at: now - 400}, // evicted
		{ns: 2, at: now - 100}, // kept
		{ns: 3, at: now},       // kept
	}
	got := evictOlder(s, now-maxLookbackSeconds)
	if len(got) != 2 || got[0].ns != 2 {
		t.Fatalf("evictOlder = %+v, want last two", got)
	}
}

// drain produces request+response metrics with correct ids, account and counts,
// and does not destroy the rolling window (samples remain for the next flush).
func TestDrainEmitsBothAndRetains(t *testing.T) {
	acc := &accountAccumulator{}
	for i := 0; i < 30; i++ {
		acc.recordRequest(int64(i+1) * 1_000_000)
		acc.recordResponse(int64(i+1) * 2_000_000)
	}
	out := acc.drain("1234")
	if len(out) != 2 {
		t.Fatalf("drain emitted %d metrics, want 2", len(out))
	}
	byID := map[string]MetricData{}
	for _, m := range out {
		byID[m.MetricId] = m
		if m.AccountId != 1234 {
			t.Errorf("%s accountId = %d, want 1234", m.MetricId, m.AccountId)
		}
		if m.ModuleType != "AKTO_AGENT_GATEWAY" || m.MetricType != "LATENCY" {
			t.Errorf("%s wrong module/metric type: %+v", m.MetricId, m)
		}
		if m.SampleCount != 30 {
			t.Errorf("%s sampleCount = %d, want 30", m.MetricId, m.SampleCount)
		}
	}
	if _, ok := byID["GUARDRAIL_REQUEST_LATENCY"]; !ok {
		t.Error("missing GUARDRAIL_REQUEST_LATENCY")
	}
	if _, ok := byID["GUARDRAIL_RESPONSE_LATENCY"]; !ok {
		t.Error("missing GUARDRAIL_RESPONSE_LATENCY")
	}
	// Rolling window: samples retained, a second drain still emits.
	if out2 := acc.drain("1234"); len(out2) != 2 {
		t.Fatalf("second drain emitted %d, want 2 (window should be retained)", len(out2))
	}
}

// Empty accumulator emits nothing and does not panic.
func TestDrainEmpty(t *testing.T) {
	acc := &accountAccumulator{}
	if out := acc.drain("1"); len(out) != 0 {
		t.Fatalf("empty drain emitted %d, want 0", len(out))
	}
}
