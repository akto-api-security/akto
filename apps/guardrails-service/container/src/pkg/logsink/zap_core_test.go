package logsink

import (
	"testing"
	"time"

	"go.uber.org/zap/zapcore"
)

func TestFormatDBLogMessageWithCaller(t *testing.T) {
	ent := zapcore.Entry{
		LoggerName: "session",
		Time:       time.Date(2026, 5, 26, 6, 31, 2, 158894540, time.UTC),
		Level:      zapcore.InfoLevel,
		Caller: zapcore.EntryCaller{
			Defined:  true,
			Function: "github.com/akto-api-security/guardrails-service/pkg/session.(*Manager).Sync",
			File:     "/src/pkg/session/manager.go",
			Line:     239,
		},
		Message: "Synced sessions to TBS",
	}

	msg := formatDBLogMessage(ent, "Synced sessions to TBS")
	if want := "2026-05-26 06:31:02.158\tINFO\tsession/manager.go:239\tSynced sessions to TBS"; msg != want {
		t.Fatalf("got %q, want %q", msg, want)
	}
}

func TestFormatDBLogMessageTimestampSeconds(t *testing.T) {
	ent := zapcore.Entry{
		Time: time.Date(2026, 5, 26, 6, 31, 2, 500_000_000, time.UTC),
	}
	wholeSeconds := time.Date(2026, 5, 26, 6, 31, 2, 0, time.UTC).Unix()
	if ent.Time.Unix() != wholeSeconds {
		t.Fatalf("timestamp field must be whole seconds, got %d want %d", ent.Time.Unix(), wholeSeconds)
	}
	if ent.Time.Format(logTimeLayout) != "2026-05-26 06:31:02.500" {
		t.Fatalf("log text must include milliseconds")
	}
}
