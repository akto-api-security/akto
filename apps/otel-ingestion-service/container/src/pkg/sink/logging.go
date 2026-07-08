package sink

import (
	"context"
	"strings"

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

func (s *LoggingSink) Emit(_ context.Context, batch Batch) error {
	for _, e := range batch.Events {
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
