package logsink

import (
	"fmt"

	"go.uber.org/zap/zapcore"
)

// Millisecond precision in stored log text; API timestamp field stays epoch seconds.
const logTimeLayout = "2006-01-02 15:04:05.000"

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

	line := ent.Message
	if len(fields) > 0 {
		enc := zapcore.NewMapObjectEncoder()
		for _, f := range fields {
			f.AddTo(enc)
		}
		line = fmt.Sprintf("%s %v", ent.Message, enc.Fields)
	}

	c.sink.Enqueue(Entry{
		Message:   formatDBLogMessage(ent, line),
		Key:       levelToKey(ent.Level),
		Timestamp: int(ent.Time.Unix()),
	})
	return nil
}

func formatDBLogMessage(ent zapcore.Entry, line string) string {
	timeStr := ent.Time.Format(logTimeLayout)
	levelStr := ent.Level.CapitalString()

	if ent.Caller.Defined {
		return fmt.Sprintf("%s\t%s\t%s\t%s", timeStr, levelStr, ent.Caller.TrimmedPath(), line)
	}

	loggerName := ent.LoggerName
	if loggerName == "" {
		loggerName = "guardrails-service"
	}
	return fmt.Sprintf("%s\t%s\t%s\t%s", timeStr, levelStr, loggerName, line)
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
