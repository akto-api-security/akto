package pipeline

import (
	"sync"
)

type SignalType string

const (
	SignalLogs    SignalType = "logs"
	SignalTraces  SignalType = "traces"
	SignalMetrics SignalType = "metrics"
)

type Job struct {
	AccountID   int
	AuthToken   string
	Signal      SignalType
	ContentType string
	Body        []byte
}

type Queue struct {
	ch       chan Job
	capacity int
	pool     *BufferPool

	enqueued   uint64
	rejected   uint64
	processed  uint64
	mu         sync.RWMutex
}

func NewQueue(capacity int) *Queue {
	return &Queue{
		ch:       make(chan Job, capacity),
		capacity: capacity,
		pool:     newBufferPool(),
	}
}

func (q *Queue) TryEnqueue(job Job) bool {
	select {
	case q.ch <- job:
		q.mu.Lock()
		q.enqueued++
		q.mu.Unlock()
		return true
	default:
		q.mu.Lock()
		q.rejected++
		q.mu.Unlock()
		return false
	}
}

func (q *Queue) Jobs() <-chan Job {
	return q.ch
}

func (q *Queue) Close() {
	close(q.ch)
}

func (q *Queue) Depth() int {
	return len(q.ch)
}

func (q *Queue) Capacity() int {
	return q.capacity
}

func (q *Queue) Stats() (enqueued, rejected, processed uint64) {
	q.mu.RLock()
	defer q.mu.RUnlock()
	return q.enqueued, q.rejected, q.processed
}

func (q *Queue) MarkProcessed() {
	q.mu.Lock()
	q.processed++
	q.mu.Unlock()
}

func (q *Queue) BufferPool() *BufferPool {
	return q.pool
}

type BufferPool struct {
	pool sync.Pool
}

func newBufferPool() *BufferPool {
	return &BufferPool{
		pool: sync.Pool{
			New: func() interface{} {
				b := make([]byte, 0, 64*1024)
				return &b
			},
		},
	}
}

func (p *BufferPool) Get() []byte {
	bptr := p.pool.Get().(*[]byte)
	return (*bptr)[:0]
}

func (p *BufferPool) Put(b []byte) {
	if cap(b) > 8*1024*1024 {
		return
	}
	bptr := &b
	p.pool.Put(bptr)
}
