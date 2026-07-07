package pipeline

import (
	"context"
	"sync"

	"github.com/akto-api-security/otel-ingestion-service/pkg/adapter"
	"github.com/akto-api-security/otel-ingestion-service/pkg/decode"
	"github.com/akto-api-security/otel-ingestion-service/pkg/sink"
	"go.uber.org/zap"
)

type WorkerPool struct {
	queue    *Queue
	registry *adapter.Registry
	sink     sink.EventSink
	logger   *zap.Logger
	wg       sync.WaitGroup
}

func NewWorkerPool(queue *Queue, registry *adapter.Registry, eventSink sink.EventSink, logger *zap.Logger, workers int) *WorkerPool {
	if workers < 1 {
		workers = 1
	}
	wp := &WorkerPool{
		queue:    queue,
		registry: registry,
		sink:     eventSink,
		logger:   logger,
	}
	for i := 0; i < workers; i++ {
		wp.wg.Add(1)
		go wp.run()
	}
	return wp
}

func (wp *WorkerPool) run() {
	defer wp.wg.Done()
	for job := range wp.queue.Jobs() {
		wp.process(job)
		wp.queue.MarkProcessed()
	}
}

func (wp *WorkerPool) process(job Job) {
	defer wp.queue.BufferPool().Put(job.Body)

	switch job.Signal {
	case SignalLogs:
		logs, err := decode.DecodeLogs(job.ContentType, job.Body)
		if err != nil {
			wp.logger.Warn("failed to decode OTLP logs",
				zap.Int("account_id", job.AccountID),
				zap.Error(err))
			return
		}
		events := wp.registry.ProcessLogs(logs, job.AccountID)
		if len(events) == 0 {
			return
		}
		if err := wp.sink.Emit(context.Background(), events); err != nil {
			wp.logger.Warn("sink emit failed", zap.Error(err))
		}
	default:
		wp.logger.Debug("signal accepted for future processing",
			zap.String("signal", string(job.Signal)),
			zap.Int("account_id", job.AccountID),
			zap.Int("bytes", len(job.Body)))
	}
}

func (wp *WorkerPool) Wait() {
	wp.wg.Wait()
}
