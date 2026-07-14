package metrics

import (
	"os"
	"runtime"
	"strconv"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/auth"
	"github.com/shirou/gopsutil/v3/process"
)

// moduleTypeAgentGateway is the module label shared by all guardrail metrics.
const moduleTypeAgentGateway = "AKTO_AGENT_GATEWAY"

// SystemSampler emits instance-level resource gauges (CPU, memory, goroutines)
// for this guardrails process. Unlike the per-account latency Accumulator, these
// describe the process itself, so they are attributed to the single account that
// owns DATABASE_ABSTRACTOR_SERVICE_TOKEN and tagged with an instance id so
// multiple replicas of that account don't collapse into one series.
type SystemSampler struct {
	proc       *process.Process
	accountId  int64
	instanceId string
}

// NewSystemSampler binds to the current process and resolves the owning account
// from the service token. It returns an error only if the process handle can't
// be created (gopsutil needs it for CPU/RSS); callers may treat that as fatal or
// simply skip system metrics.
func NewSystemSampler() (*SystemSampler, error) {
	p, err := process.NewProcess(int32(os.Getpid()))
	if err != nil {
		return nil, err
	}
	// 0 (omitted on the wire) when the token is absent/unparseable — the
	// db-abstractor then falls back to the account it derives from the same
	// token on the receiving side.
	accID, _ := strconv.ParseInt(auth.AccountIDFromServiceToken(), 10, 64)
	host, _ := os.Hostname()
	return &SystemSampler{proc: p, accountId: accID, instanceId: host}, nil
}

// metric builds a MetricData for one instance-level reading of the given type.
func (s *SystemSampler) metric(metricId, metricType string, value float64, now int64) MetricData {
	return MetricData{
		MetricId:   metricId,
		Value:      value,
		Timestamp:  now,
		MetricType: metricType,
		ModuleType: moduleTypeAgentGateway,
		AccountId:  s.accountId,
		InstanceId: s.instanceId,
	}
}

func (s *SystemSampler) gauge(metricId string, value float64, now int64) MetricData {
	return s.metric(metricId, "GAUGE", value, now)
}

func (s *SystemSampler) sum(metricId string, value float64, now int64) MetricData {
	return s.metric(metricId, "SUM", value, now)
}

// Sample reads current CPU%, resident memory and goroutine count. It is meant to
// be called once per flush tick; the long-lived process handle lets Percent
// report usage over the interval since the previous call.
func (s *SystemSampler) Sample() []MetricData {
	now := time.Now().Unix()
	out := make([]MetricData, 0, 3)

	// CPU percent since the last Sample call (relative to a single core).
	if cpu, err := s.proc.Percent(0); err == nil {
		out = append(out, s.gauge("GUARDRAIL_CPU_USAGE", cpu, now))
	}
	// Resident set size — actual physical memory the process holds, in MB.
	if mem, err := s.proc.MemoryInfo(); err == nil {
		rssMB := float64(mem.RSS) / (1024 * 1024)
		out = append(out, s.gauge("GUARDRAIL_MEMORY_USAGE", rssMB, now))
	}
	// Goroutine count — the Go analog of JVM thread count.
	out = append(out, s.gauge("GUARDRAIL_GOROUTINES", float64(runtime.NumGoroutine()), now))

	return out
}

// CacheStats builds SUM metrics for the full-text semantic-cache hit/miss
// counts accumulated since the last call — a delta (e.g. from
// guardcache.DrainStats), not a running total, matching MetricType SUM.
// Hits are split by match type: exact (literal repeat) vs fuzzy (embedding
// similarity match), since they represent different cache behavior.
func (s *SystemSampler) CacheStats(hitsExact, hitsFuzzy, misses uint64) []MetricData {
	now := time.Now().Unix()
	return []MetricData{
		s.sum("GUARDRAIL_CACHE_HIT_EXACT", float64(hitsExact), now),
		s.sum("GUARDRAIL_CACHE_HIT_FUZZY", float64(hitsFuzzy), now),
		s.sum("GUARDRAIL_CACHE_MISS", float64(misses), now),
	}
}
