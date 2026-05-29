package logsink

import (
	"testing"
	"time"
)

func TestReserveSlotRateLimit(t *testing.T) {
	s := &AsyncSink{
		enabled: true,
		ch:      make(chan Entry, 4),
		stopCh:  make(chan struct{}),
	}
	s.logCountResetUnix.Store(time.Now().Unix())

	for i := 0; i < maxLogsPerMinute; i++ {
		if !s.reserveSlot() {
			t.Fatalf("expected allow at %d", i)
		}
	}
	if s.reserveSlot() {
		t.Fatal("expected rate limit after 1000 logs in one minute")
	}

	s.logCountResetUnix.Store(time.Now().Unix() - oneMinuteSeconds - 1)
	s.maybeResetWindow()
	if !s.reserveSlot() {
		t.Fatal("expected allow after window reset")
	}
}

func TestEnqueueReleasesSlotWhenQueueFull(t *testing.T) {
	s := &AsyncSink{
		enabled: true,
		ch:      make(chan Entry, 1),
		stopCh:  make(chan struct{}),
	}
	s.logCountResetUnix.Store(time.Now().Unix())

	s.ch <- Entry{Message: "fills queue", Key: "info", Timestamp: 1}
	s.Enqueue(Entry{Message: "dropped", Key: "info", Timestamp: 2})

	if s.dropped.Load() != 1 {
		t.Fatalf("expected 1 drop, got %d", s.dropped.Load())
	}
	if s.logCount.Load() != 0 {
		t.Fatalf("expected slot released on drop, count=%d", s.logCount.Load())
	}
}

func TestEnqueueDisabledNoPanic(t *testing.T) {
	s := &AsyncSink{enabled: false}
	s.Enqueue(Entry{Message: "x", Key: "info", Timestamp: 1})
}
