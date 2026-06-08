package logsink

import (
	"strings"
	"testing"
	"time"

	"go.uber.org/zap/zapcore"
)

func TestFormatDBLogMessagePlain(t *testing.T) {
	ent := zapcore.Entry{
		Time:    time.Date(2026, 5, 26, 9, 7, 28, 416123456, time.UTC),
		Level:   zapcore.InfoLevel,
		Message: "Session manager started",
	}
	fields := map[string]interface{}{
		"cyborgURL":    "https://ultron.akto.io",
		"syncInterval": "5m0s",
	}

	msg := formatDBLogMessage(ent, ent.Message, fields)
	if strings.Contains(msg, "\tINFO\t") || strings.Contains(msg, "guardrails-service") {
		t.Fatalf("log must not include level or logger name prefix, got %q", msg)
	}
	if !strings.HasPrefix(msg, "2026-05-26 09:07:28.416123456 ") {
		t.Fatalf("expected nanosecond timestamp prefix, got %q", msg)
	}
	if !strings.Contains(msg, "Session manager started") {
		t.Fatalf("missing message, got %q", msg)
	}
	if strings.Contains(msg, "map[") {
		t.Fatalf("fields must be key=value, got %q", msg)
	}
	if !strings.Contains(msg, "cyborgURL=https://ultron.akto.io") {
		t.Fatalf("missing structured field, got %q", msg)
	}
}

func TestFormatDBLogMessageTimestampSeconds(t *testing.T) {
	ent := zapcore.Entry{
		Time: time.Date(2026, 5, 26, 6, 31, 2, 500_123_456, time.UTC),
	}
	wholeSeconds := time.Date(2026, 5, 26, 6, 31, 2, 0, time.UTC).Unix()
	if ent.Time.Unix() != wholeSeconds {
		t.Fatalf("timestamp field must be whole seconds, got %d want %d", ent.Time.Unix(), wholeSeconds)
	}
	if ent.Time.Format(logTimeLayout) != "2026-05-26 06:31:02.500123456" {
		t.Fatalf("log text must include nanoseconds, got %s", ent.Time.Format(logTimeLayout))
	}
}
