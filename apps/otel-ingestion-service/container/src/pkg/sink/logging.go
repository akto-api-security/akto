package sink

import (
	"context"
	"strings"

	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
	"go.uber.org/zap"
)

var sensitiveKeys = []string{
	"prompt",
	"response",
	"tool_parameters",
	"tool_input",
}

type LoggingSink struct {
	logger       *zap.Logger
	logSensitive bool
}

func NewLoggingSink(logger *zap.Logger, logSensitive bool) *LoggingSink {
	return &LoggingSink{logger: logger, logSensitive: logSensitive}
}

func (s *LoggingSink) Emit(_ context.Context, events []model.OtelIngestEvent) error {
	for _, e := range events {
		fields := []zap.Field{
			zap.Int("account_id", e.AccountID),
			zap.String("source", e.Source),
			zap.String("signal_type", e.SignalType),
			zap.String("event_name", e.EventName),
			zap.String("correlation_id", e.CorrelationID),
			zap.Time("event_timestamp", e.Timestamp),
		}
		for k, v := range e.Attributes {
			if !s.logSensitive && isSensitiveKey(k) {
				fields = append(fields, zap.String(k, "[redacted]"))
				continue
			}
			fields = append(fields, zap.String(k, v))
		}
		s.logger.Info("otel_event", fields...)
	}
	return nil
}

func isSensitiveKey(key string) bool {
	lower := strings.ToLower(key)
	for _, sk := range sensitiveKeys {
		if lower == sk || strings.HasSuffix(lower, "."+sk) {
			return true
		}
	}
	return false
}

// KafkaSink is a Phase 2 placeholder for async Kafka publishing.
type KafkaSink struct{}

func (k *KafkaSink) Emit(_ context.Context, _ []model.OtelIngestEvent) error {
	return nil
}

// HTTPSink is a Phase 2 placeholder for async HTTP downstream calls.
type HTTPSink struct{}

func (h *HTTPSink) Emit(_ context.Context, _ []model.OtelIngestEvent) error {
	return nil
}
