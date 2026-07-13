package decode

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"
)

func TestDecodeCoworkJSONFixture(t *testing.T) {
	fixture := coworkLogsFixturePath(t)
	body, err := os.ReadFile(fixture)
	if err != nil {
		t.Fatal(err)
	}

	logs, err := DecodeLogs("application/json", body)
	if err != nil {
		t.Fatal(err)
	}
	if logs.ResourceLogs().Len() != 1 {
		t.Fatalf("expected 1 resourceLogs, got %d", logs.ResourceLogs().Len())
	}
	records := logs.ResourceLogs().At(0).ScopeLogs().At(0).LogRecords()
	if records.Len() != 3 {
		t.Fatalf("expected 3 log records, got %d", records.Len())
	}
	if records.At(0).EventName() != "user_prompt" {
		t.Fatalf("expected user_prompt, got %q", records.At(0).EventName())
	}
}

func coworkLogsFixturePath(t *testing.T) string {
	t.Helper()
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("runtime.Caller failed")
	}
	// container/src/pkg/decode -> ../../../../scripts/fixtures/cowork_logs.json
	return filepath.Join(filepath.Dir(file), "..", "..", "..", "..", "scripts", "fixtures", "cowork_logs.json")
}
