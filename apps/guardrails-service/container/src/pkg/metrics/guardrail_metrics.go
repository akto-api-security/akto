package metrics

import (
	"math"
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

func (a *accountAccumulator) recordRequest(ns int64) {
	a.mu.Lock()
	a.requestSamples = append(a.requestSamples, ns)
	a.mu.Unlock()
}

func (a *accountAccumulator) recordResponse(ns int64) {
	a.mu.Lock()
	a.responseSamples = append(a.responseSamples, ns)
	a.mu.Unlock()
}

// avg returns the mean of nanosecond samples converted to milliseconds (2 dp).
func avg(samples []int64) float64 {
	if len(samples) == 0 {
		return 0
	}
	var sum int64
	for _, s := range samples {
		sum += s
	}
	meanNs := float64(sum) / float64(len(samples))
	return math.Round(meanNs/1e4) / 100 // ns → ms, 2 decimal places
}

// drain computes the average latency over the samples collected since the last
// flush (a ~1-minute window, driven by the flush ticker) and resets the buffers.
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
		out = append(out, MetricData{MetricId: "GUARDRAIL_REQUEST_LATENCY", Value: avg(reqSamples), Timestamp: now, MetricType: "LATENCY", ModuleType: "AKTO_AGENT_GATEWAY", AccountId: accIdInt})
	}
	if len(resSamples) > 0 {
		out = append(out, MetricData{MetricId: "GUARDRAIL_RESPONSE_LATENCY", Value: avg(resSamples), Timestamp: now, MetricType: "LATENCY", ModuleType: "AKTO_AGENT_GATEWAY", AccountId: accIdInt})
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

// DrainAll returns average latency metrics grouped by account and resets sample buffers.
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
