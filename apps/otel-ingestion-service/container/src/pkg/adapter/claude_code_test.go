package adapter

import (
	"testing"
	"time"

	"go.opentelemetry.io/collector/pdata/pcommon"
	"go.opentelemetry.io/collector/pdata/plog"
)

func TestClaudeCodeAdapterMatch(t *testing.T) {
	adapter := &ClaudeCodeAdapter{}

	if !adapter.Match(map[string]string{"service.name": "cowork"}, "", "user_prompt") {
		t.Fatal("expected cowork user_prompt to match")
	}
	if !adapter.Match(map[string]string{}, "", "claude_code.tool_result") {
		t.Fatal("expected claude_code prefix to match")
	}
	if adapter.Match(map[string]string{"service.name": "other"}, "", "unknown_event") {
		t.Fatal("expected unrelated event to not match")
	}
}

func TestRegistryProcessLogs(t *testing.T) {
	logs := plog.NewLogs()
	rl := logs.ResourceLogs().AppendEmpty()
	rl.Resource().Attributes().PutStr("service.name", "cowork")
	sl := rl.ScopeLogs().AppendEmpty()
	record := sl.LogRecords().AppendEmpty()
	record.SetEventName("user_prompt")
	record.SetTimestamp(pcommon.NewTimestampFromTime(time.Unix(1_700_000_000, 0)))
	record.Attributes().PutStr("prompt.id", "prompt-abc")
	record.Attributes().PutStr("user.email", "user@example.com")

	events := NewRegistry().ProcessLogs(logs, 99)
	if len(events) != 1 {
		t.Fatalf("expected 1 event, got %d", len(events))
	}
	ev := events[0]
	if ev.AccountID != 99 {
		t.Fatalf("expected account 99, got %d", ev.AccountID)
	}
	if ev.Source != "claude_code" {
		t.Fatalf("expected claude_code source, got %q", ev.Source)
	}
	if ev.EventName != "claude_code.user_prompt" {
		t.Fatalf("expected prefixed event name, got %q", ev.EventName)
	}
	if ev.CorrelationID != "prompt-abc" {
		t.Fatalf("expected prompt-abc correlation, got %q", ev.CorrelationID)
	}
}
