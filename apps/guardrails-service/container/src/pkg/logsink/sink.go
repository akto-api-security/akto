package logsink

import (
	"os"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
)

const (
	defaultQueueSize = 4096
	defaultWorkers   = 4
	maxWorkers       = 16
	maxLogsPerMinute = 1000
	oneMinuteSeconds = 60
)

// Entry is a single log line to persist via database-abstractor.
type Entry struct {
	Message   string
	Key       string
	Timestamp int
}

// AsyncSink forwards logs to database-abstractor via a fixed worker pool.
//
// Enqueue never blocks the caller. If the queue is full or the per-minute cap is hit,
// new logs are dropped (not stalled). A pool of workers drains the queue in parallel
// so slow HTTP responses do not serialize all inserts behind one goroutine.
//
// One goroutine per log is intentionally avoided: under bursts that would spawn
// unbounded goroutines and amplify load on database-abstractor with no backpressure.
type AsyncSink struct {
	client  *dbabstractor.LogClient
	ch      chan Entry
	enabled bool
	stopCh  chan struct{}
	wg      sync.WaitGroup

	logCount          atomic.Int32
	logCountResetUnix atomic.Int64
	dropped           atomic.Uint64
}

// NewAsyncSink creates a sink. When disabled or client is nil, Enqueue is a no-op.
func NewAsyncSink(client *dbabstractor.LogClient) *AsyncSink {
	blockLogs := os.Getenv("BLOCK_LOGS") == "true"
	enabled := client != nil && !blockLogs && dbabstractor.HasServiceToken()

	s := &AsyncSink{
		client:  client,
		ch:      make(chan Entry, defaultQueueSize),
		enabled: enabled,
		stopCh:  make(chan struct{}),
	}
	s.logCountResetUnix.Store(time.Now().Unix())

	if enabled {
		for i := 0; i < workerCount(); i++ {
			s.wg.Add(1)
			go s.run()
		}
	}
	return s
}

func workerCount() int {
	if v := os.Getenv("LOG_SINK_WORKERS"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			if n > maxWorkers {
				return maxWorkers
			}
			return n
		}
	}
	return defaultWorkers
}

// Enabled reports whether DB log forwarding is active.
func (s *AsyncSink) Enabled() bool {
	return s.enabled
}

// Enqueue schedules a log for async delivery. Never blocks the caller.
func (s *AsyncSink) Enqueue(entry Entry) {
	if !s.enabled {
		return
	}
	if !s.reserveSlot() {
		return
	}
	select {
	case s.ch <- entry:
	default:
		s.logCount.Add(-1)
		s.dropped.Add(1)
	}
}

// Dropped returns how many log lines were discarded because the queue was full.
func (s *AsyncSink) Dropped() uint64 {
	return s.dropped.Load()
}

// Close stops workers and waits for in-flight HTTP inserts to finish.
func (s *AsyncSink) Close() {
	if !s.enabled {
		return
	}
	close(s.stopCh)
	s.wg.Wait()
}

func (s *AsyncSink) reserveSlot() bool {
	for {
		s.maybeResetWindow()
		count := s.logCount.Load()
		if count >= maxLogsPerMinute {
			return false
		}
		if s.logCount.CompareAndSwap(count, count+1) {
			return true
		}
	}
}

func (s *AsyncSink) maybeResetWindow() {
	now := time.Now().Unix()
	resetAt := s.logCountResetUnix.Load()
	if now-resetAt < oneMinuteSeconds {
		return
	}
	if s.logCountResetUnix.CompareAndSwap(resetAt, now) {
		s.logCount.Store(0)
	}
}

func (s *AsyncSink) run() {
	defer s.wg.Done()
	for {
		select {
		case <-s.stopCh:
			for {
				select {
				case entry := <-s.ch:
					s.flush(entry)
				default:
					return
				}
			}
		case entry := <-s.ch:
			s.flush(entry)
		}
	}
}

func (s *AsyncSink) flush(entry Entry) {
	if s.client == nil {
		return
	}
	_ = s.client.InsertGuardrailsServiceLog(entry.Key, entry.Message, entry.Timestamp)
}
