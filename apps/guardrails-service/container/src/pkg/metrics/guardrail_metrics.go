package metrics

import (
	"math"
	"sort"
	"strconv"
	"sync"
	"time"
)

// MetricData matches the Java MetricData DTO consumed by /ingestMetricsData on the db-abstractor.
// AccountId is sent as a JSON number so the Java Integer field deserializes correctly.
type MetricData struct {
	MetricId   string  `json:"metricId"`
	Value      float64 `json:"value"`
	Timestamp  int64   `json:"timestamp"`
	MetricType string  `json:"metricType"`
	ModuleType string  `json:"moduleType"`
	AccountId  int64   `json:"accountId,omitempty"`
	// SampleCount is the number of samples the P95 was computed over. Internal
	// only (not sent to the db-abstractor); used for observability logging.
	SampleCount int `json:"-"`
}

// Adaptive-window P95: each flush computes the percentile over the last
// windowSeconds (1 minute) for responsiveness, but if that minute holds fewer
// than minSamples — too few for a stable P95, where one slow outlier dominates —
// the window widens to the most recent minSamples we still retain (up to
// maxLookbackSeconds). High traffic gets per-minute resolution; sparse traffic
// gets a statistically meaningful value instead of a spiky one.
const (
	windowSeconds      = 60      // primary 1-minute window
	maxLookbackSeconds = 300     // retain up to 5 minutes for the sparse fallback
	minSamples         = 20      // minimum samples for a meaningful P95
	maxSamples         = 100_000 // hard per-direction cap to bound memory
)

type sample struct {
	ns int64
	at int64 // unix seconds when recorded
}

type accountAccumulator struct {
	mu              sync.Mutex
	requestSamples  []sample
	responseSamples []sample
}

func (a *accountAccumulator) recordRequest(ns int64) {
	a.mu.Lock()
	a.requestSamples = appendCapped(a.requestSamples, ns)
	a.mu.Unlock()
}

func (a *accountAccumulator) recordResponse(ns int64) {
	a.mu.Lock()
	a.responseSamples = appendCapped(a.responseSamples, ns)
	a.mu.Unlock()
}

func appendCapped(s []sample, ns int64) []sample {
	s = append(s, sample{ns: ns, at: time.Now().Unix()})
	if len(s) > maxSamples {
		s = s[len(s)-maxSamples:]
	}
	return s
}

// p95 returns the 95th-percentile of nanosecond samples converted to milliseconds.
func p95(samples []int64) float64 {
	sort.Slice(samples, func(i, j int) bool { return samples[i] < samples[j] })
	idx := int(math.Ceil(0.95*float64(len(samples)))) - 1
	if idx < 0 {
		idx = 0
	}
	return math.Round(float64(samples[idx])/1e4) / 100 // ns → ms, 2 decimal places
}

func (a *accountAccumulator) drain(accountId string) []MetricData {
	now := time.Now().Unix()
	retainCutoff := now - maxLookbackSeconds
	windowCutoff := now - windowSeconds
	a.mu.Lock()
	// Retain up to maxLookbackSeconds so the sparse-window fallback has history.
	a.requestSamples = evictOlder(a.requestSamples, retainCutoff)
	a.responseSamples = evictOlder(a.responseSamples, retainCutoff)
	reqVals := windowSet(a.requestSamples, windowCutoff)
	resVals := windowSet(a.responseSamples, windowCutoff)
	a.mu.Unlock()

	accIdInt, _ := strconv.ParseInt(accountId, 10, 64)
	var out []MetricData
	if len(reqVals) > 0 {
		out = append(out, MetricData{MetricId: "GUARDRAIL_REQUEST_LATENCY", Value: p95(reqVals), Timestamp: now, MetricType: "LATENCY", ModuleType: "AKTO_AGENT_GATEWAY", AccountId: accIdInt, SampleCount: len(reqVals)})
	}
	if len(resVals) > 0 {
		out = append(out, MetricData{MetricId: "GUARDRAIL_RESPONSE_LATENCY", Value: p95(resVals), Timestamp: now, MetricType: "LATENCY", ModuleType: "AKTO_AGENT_GATEWAY", AccountId: accIdInt, SampleCount: len(resVals)})
	}
	return out
}

// evictOlder drops samples recorded before cutoff. Samples are appended in time
// order, so the oldest are always in the prefix.
func evictOlder(s []sample, cutoff int64) []sample {
	i := 0
	for i < len(s) && s[i].at < cutoff {
		i++
	}
	return s[i:]
}

// windowSet returns the latency values to compute P95 over: samples within the
// last windowSeconds (those at-or-after windowCutoff), or — if that minute holds
// fewer than minSamples — the most recent minSamples we still retain, so a sparse
// minute widens its lookback instead of producing a spiky, outlier-driven value.
func windowSet(s []sample, windowCutoff int64) []int64 {
	i := 0
	for i < len(s) && s[i].at < windowCutoff {
		i++
	}
	if len(s)-i < minSamples {
		// Sparse window: widen to the most recent minSamples (bounded by retention).
		i = len(s) - minSamples
		if i < 0 {
			i = 0
		}
	}
	return latencies(s[i:])
}

// latencies copies the nanosecond values into a fresh slice so p95 can sort it
// without disturbing the retained rolling window.
func latencies(s []sample) []int64 {
	out := make([]int64, len(s))
	for i := range s {
		out[i] = s[i].ns
	}
	return out
}

// Accumulator collects guardrail latency samples per account and periodically drains to a flush function.
type Accumulator struct {
	mu       sync.RWMutex
	accounts map[string]*accountAccumulator
}

func NewAccumulator() *Accumulator {
	return &Accumulator{accounts: make(map[string]*accountAccumulator)}
}

func (a *Accumulator) get(accountId string) *accountAccumulator {
	a.mu.RLock()
	acc, ok := a.accounts[accountId]
	a.mu.RUnlock()
	if ok {
		return acc
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	if acc, ok = a.accounts[accountId]; ok {
		return acc
	}
	acc = &accountAccumulator{}
	a.accounts[accountId] = acc
	return acc
}

func (a *Accumulator) RecordRequest(accountId string, ns int64)  { a.get(accountId).recordRequest(ns) }
func (a *Accumulator) RecordResponse(accountId string, ns int64) { a.get(accountId).recordResponse(ns) }

// DrainAll returns P95 latency metrics grouped by account and resets sample buffers.
func (a *Accumulator) DrainAll() map[string][]MetricData {
	a.mu.RLock()
	ids := make([]string, 0, len(a.accounts))
	for id := range a.accounts {
		ids = append(ids, id)
	}
	a.mu.RUnlock()

	result := make(map[string][]MetricData)
	for _, id := range ids {
		a.mu.RLock()
		acc := a.accounts[id]
		a.mu.RUnlock()
		if batch := acc.drain(id); len(batch) > 0 {
			result[id] = batch
		}
	}
	return result
}
