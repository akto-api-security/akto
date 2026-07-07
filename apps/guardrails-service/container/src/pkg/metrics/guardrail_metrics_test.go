package metrics

import (
	"testing"
)

func TestAvgMilliseconds(t *testing.T) {
	got := avg([]int64{1_000_000, 3_000_000, 5_000_000})
	if got != 3.0 {
		t.Fatalf("avg = %v, want 3.0", got)
	}
}

func TestAvgEmpty(t *testing.T) {
	if got := avg(nil); got != 0 {
		t.Fatalf("avg empty = %v, want 0", got)
	}
}

func TestDrainEmitsBothAndResets(t *testing.T) {
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
	}
	if _, ok := byID["GUARDRAIL_REQUEST_LATENCY"]; !ok {
		t.Error("missing GUARDRAIL_REQUEST_LATENCY")
	}
	if _, ok := byID["GUARDRAIL_RESPONSE_LATENCY"]; !ok {
		t.Error("missing GUARDRAIL_RESPONSE_LATENCY")
	}
	if out2 := acc.drain("1234"); len(out2) != 0 {
		t.Fatalf("second drain emitted %d, want 0 (buffers reset)", len(out2))
	}
}

func TestDrainEmpty(t *testing.T) {
	acc := &accountAccumulator{}
	if out := acc.drain("1"); len(out) != 0 {
		t.Fatalf("empty drain emitted %d, want 0", len(out))
	}
}
