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
}

type accountAccumulator struct {
	mu              sync.Mutex
	requestSamples  []int64
	responseSamples []int64
}

func (a *accountAccumulator) recordRequest(ms int64) {
	a.mu.Lock()
	a.requestSamples = append(a.requestSamples, ms)
	a.mu.Unlock()
}

func (a *accountAccumulator) recordResponse(ms int64) {
	a.mu.Lock()
	a.responseSamples = append(a.responseSamples, ms)
	a.mu.Unlock()
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
	a.mu.Lock()
	reqSamples := a.requestSamples
	a.requestSamples = nil
	resSamples := a.responseSamples
	a.responseSamples = nil
	a.mu.Unlock()

	accIdInt, _ := strconv.ParseInt(accountId, 10, 64)
	now := time.Now().Unix()
	var out []MetricData
	if len(reqSamples) > 0 {
		out = append(out, MetricData{MetricId: "GUARDRAIL_REQUEST_LATENCY", Value: p95(reqSamples), Timestamp: now, MetricType: "LATENCY", ModuleType: "AKTO_AGENT_GATEWAY", AccountId: accIdInt})
	}
	if len(resSamples) > 0 {
		out = append(out, MetricData{MetricId: "GUARDRAIL_RESPONSE_LATENCY", Value: p95(resSamples), Timestamp: now, MetricType: "LATENCY", ModuleType: "AKTO_AGENT_GATEWAY", AccountId: accIdInt})
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

func (a *Accumulator) RecordRequest(accountId string, ms int64)  { a.get(accountId).recordRequest(ms) }
func (a *Accumulator) RecordResponse(accountId string, ms int64) { a.get(accountId).recordResponse(ms) }

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
