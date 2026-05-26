package logsink

import (
	"fmt"
	"strings"

	"go.uber.org/zap/zapcore"
)

// Nanosecond precision in stored log text; API timestamp field stays epoch seconds.
const logTimeLayout = "2006-01-02 15:04:05.000000000"

type dbCore struct {
	level zapcore.Level
	sink  *AsyncSink
}

// NewCore returns a zap core that forwards encoded log lines to the async DB sink.
func (s *AsyncSink) NewCore(level zapcore.Level) zapcore.Core {
	return &dbCore{level: level, sink: s}
}

func (c *dbCore) Enabled(l zapcore.Level) bool {
	return l >= c.level
}

func (c *dbCore) With(fields []zapcore.Field) zapcore.Core {
	return c
}

func (c *dbCore) Check(ent zapcore.Entry, ce *zapcore.CheckedEntry) *zapcore.CheckedEntry {
	if c.Enabled(ent.Level) {
		return ce.AddCore(ent, c)
	}
	return ce
}

func (c *dbCore) Write(ent zapcore.Entry, fields []zapcore.Field) error {
	if c.sink == nil || !c.sink.Enabled() {
		return nil
	}

	var fieldMap map[string]interface{}
	if len(fields) > 0 {
		enc := zapcore.NewMapObjectEncoder()
		for _, f := range fields {
			f.AddTo(enc)
		}
		fieldMap = enc.Fields
	}

	c.sink.Enqueue(Entry{
		Message:   formatDBLogMessage(ent, ent.Message, fieldMap),
		Key:       levelToKey(ent.Level),
		Timestamp: int(ent.Time.Unix()),
	})
	return nil
}

// formatDBLogMessage builds the stored log line: "<time> <message> [key=value ...]".
// Level and logger name are omitted here; they are stored separately in the log key field.
func formatDBLogMessage(ent zapcore.Entry, message string, fields map[string]interface{}) string {
	timeStr := ent.Time.Format(logTimeLayout)
	line := strings.TrimSpace(message)
	if extra := formatLogFields(fields); extra != "" {
		if line == "" {
			line = extra
		} else {
			line = line + " " + extra
		}
	}
	return timeStr + " " + line
}

func formatLogFields(fields map[string]interface{}) string {
	if len(fields) == 0 {
		return ""
	}
	parts := make([]string, 0, len(fields))
	for key, val := range fields {
		parts = append(parts, fmt.Sprintf("%s=%v", key, val))
	}
	return strings.Join(parts, " ")
}

func (c *dbCore) Sync() error {
	return nil
}

func levelToKey(level zapcore.Level) string {
	switch level {
	case zapcore.DebugLevel:
		return "debug"
	case zapcore.WarnLevel:
		return "warn"
	case zapcore.ErrorLevel, zapcore.DPanicLevel, zapcore.PanicLevel, zapcore.FatalLevel:
		return "error"
	default:
		return "info"
	}
}
