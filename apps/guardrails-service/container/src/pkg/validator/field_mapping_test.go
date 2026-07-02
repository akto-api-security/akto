package validator

import (
	"os"
	"sync"
	"testing"

	"go.uber.org/zap"
)

func resetFieldMappingEnv(t *testing.T) {
	t.Helper()
	t.Setenv(EnvFieldMapping, "")
	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}
}

func resetGuardrailSchemaRegistry(t *testing.T) {
	t.Helper()
	GlobalGuardrailSchemaRegistry().Replace(map[string]*GuardrailSchema{})
}

func testValidatorService() *Service {
	return &Service{logger: zap.NewNop()}
}

func TestSplitFieldPaths(t *testing.T) {
	t.Run("multiple paths", func(t *testing.T) {
		got := splitFieldPaths("messages.role=user.content|messages.role=user.content.0.text")
		if len(got) != 2 || got[0] != "messages.role=user.content" || got[1] != "messages.role=user.content.0.text" {
			t.Fatalf("splitFieldPaths() = %#v", got)
		}
	})

	t.Run("single path", func(t *testing.T) {
		got := splitFieldPaths("messages.role=user.content.0.text")
		if len(got) != 1 || got[0] != "messages.role=user.content.0.text" {
			t.Fatalf("splitFieldPaths() = %#v", got)
		}
	})

	t.Run("empty", func(t *testing.T) {
		if got := splitFieldPaths(""); got != nil {
			t.Fatalf("splitFieldPaths() = %#v, want nil", got)
		}
	})
}

func TestFieldMappingLoadFromEnv_singlePath(t *testing.T) {
	t.Setenv(EnvFieldMapping, "POST:/v1/chat/completions:messages.role=user.content.0.text,choices.0.message.content")

	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}

	reqPaths, respPaths, ok := GlobalFieldMapping().GetPaths("POST:/v1/chat/completions")
	if !ok {
		t.Fatal("GetPaths() ok = false")
	}
	if len(reqPaths) != 1 || reqPaths[0] != "messages.role=user.content.0.text" {
		t.Fatalf("request paths = %#v", reqPaths)
	}
	if len(respPaths) != 1 || respPaths[0] != "choices.0.message.content" {
		t.Fatalf("response paths = %#v", respPaths)
	}
}

func TestFieldMappingLoadFromEnv_multiplePaths(t *testing.T) {
	t.Setenv(EnvFieldMapping, "POST:/v1/chat/completions:messages.role=user.content|messages.role=user.content.0.text,choices.0.message.content")

	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}

	reqPaths, respPaths, ok := GlobalFieldMapping().GetPaths("POST:/v1/chat/completions")
	if !ok {
		t.Fatal("GetPaths() ok = false")
	}
	if len(reqPaths) != 2 {
		t.Fatalf("request paths = %#v, want 2 entries", reqPaths)
	}
	if len(respPaths) != 1 || respPaths[0] != "choices.0.message.content" {
		t.Fatalf("response paths = %#v", respPaths)
	}
}

func TestGetValueAtPathFromJSON_singlePaths(t *testing.T) {
	tests := []struct {
		name    string
		path    string
		payload string
		want    string
	}{
		{
			name: "string content via content path",
			path: "messages.role=user.content",
			payload: `{
				"messages": [
					{"role": "system", "content": "ignore me"},
					{"role": "user", "content": "scan this prompt"}
				]
			}`,
			want: "scan this prompt",
		},
		{
			name: "multimodal content via content.0.text path",
			path: "messages.role=user.content.0.text",
			payload: `{
				"messages": [
					{"role": "user", "content": [{"type": "text", "text": "scan multimodal prompt"}]}
				]
			}`,
			want: "scan multimodal prompt",
		},
		{
			name: "response content path",
			path: "choices.0.message.content",
			payload: `{
				"choices": [
					{"message": {"role": "assistant", "content": "assistant reply"}}
				]
			}`,
			want: "assistant reply",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, ok := GetValueAtPathFromJSON(tt.payload, tt.path)
			if !ok {
				t.Fatal("GetValueAtPathFromJSON() ok = false, want true")
			}
			if got != tt.want {
				t.Fatalf("GetValueAtPathFromJSON() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestExtractContentFirst_singlePath(t *testing.T) {
	fields := []MessageFieldEntry{{FieldPath: "messages.role=user.content"}}
	payload := `{"messages":[{"role":"user","content":"single path prompt"}]}`

	got := ExtractContentFirst(payload, fields)
	if got != "single path prompt" {
		t.Fatalf("ExtractContentFirst() = %q, want %q", got, "single path prompt")
	}
}

func TestExtractContentFirst_chatCompletionsFormats(t *testing.T) {
	fields := []MessageFieldEntry{
		{FieldPath: "messages.role=user.content"},
		{FieldPath: "messages.role=user.content.0.text"},
	}

	tests := []struct {
		name    string
		payload string
		want    string
	}{
		{
			name: "string content with system and user messages",
			payload: `{
				"messages": [
					{"role": "system", "content": "system instructions"},
					{"role": "user", "content": "Retrieve all Notion pages tagged with status 'In Review'."}
				]
			}`,
			want: "Retrieve all Notion pages tagged with status 'In Review'.",
		},
		{
			name: "string content with only user message",
			payload: `{
				"messages": [
					{"role": "user", "content": "Find all CRM activity related to Grid Dynamics."}
				]
			}`,
			want: "Find all CRM activity related to Grid Dynamics.",
		},
		{
			name: "multimodal array content",
			payload: `{
				"messages": [
					{"role": "user", "content": [{"type": "text", "text": "hello from multimodal"}]}
				]
			}`,
			want: "hello from multimodal",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := ExtractContentFirst(tt.payload, fields)
			if got != tt.want {
				t.Fatalf("ExtractContentFirst() = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestResolveFieldsForEndpoint_envSinglePath(t *testing.T) {
	os.Setenv(EnvFieldMapping, "POST:/v1/chat/completions:messages.role=user.content.0.text,choices.0.message.content")
	t.Cleanup(func() { os.Unsetenv(EnvFieldMapping) })

	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}

	reqFields := resolveFieldsForEndpoint("POST", "/v1/chat/completions", true)
	if len(reqFields) != 1 || reqFields[0].FieldPath != "messages.role=user.content.0.text" {
		t.Fatalf("request fields = %#v", reqFields)
	}

	respFields := resolveFieldsForEndpoint("POST", "/v1/chat/completions", false)
	if len(respFields) != 1 || respFields[0].FieldPath != "choices.0.message.content" {
		t.Fatalf("response fields = %#v", respFields)
	}
}

func TestFieldMappingLoadFromEnv_reportsSkippedEntries(t *testing.T) {
	t.Setenv(EnvFieldMapping, "BADENTRY;POST:/v1/chat/completions:messages.role=user.content,")

	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}

	fm := GlobalFieldMapping()
	fm.mu.RLock()
	report := fm.loadReport
	fm.mu.RUnlock()

	if report.entryCount != 2 {
		t.Fatalf("entryCount = %d, want 2", report.entryCount)
	}
	if len(report.exactEndpoints) != 1 {
		t.Fatalf("exactEndpoints = %d, want 1", len(report.exactEndpoints))
	}
	if len(report.skippedEntries) != 1 {
		t.Fatalf("skippedEntries = %#v, want 1 skipped entry", report.skippedEntries)
	}
}

func TestResolveFieldsForEndpoint_envMultiplePaths(t *testing.T) {
	os.Setenv(EnvFieldMapping, "POST:/v1/chat/completions:messages.role=user.content|messages.role=user.content.0.text")
	t.Cleanup(func() { os.Unsetenv(EnvFieldMapping) })

	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}

	fields := resolveFieldsForEndpoint("POST", "/v1/chat/completions", true)
	if len(fields) != 2 {
		t.Fatalf("resolveFieldsForEndpoint() = %#v, want 2 fields", fields)
	}
}

func TestResolveFieldsForEndpoint_noMapping(t *testing.T) {
	resetFieldMappingEnv(t)
	resetGuardrailSchemaRegistry(t)

	fields := resolveFieldsForEndpoint("POST", "/v1/chat/completions", true)
	if fields != nil {
		t.Fatalf("resolveFieldsForEndpoint() = %#v, want nil", fields)
	}
}

func TestResolveFieldsForEndpoint_dashboardSchemaPreferredOverEnv(t *testing.T) {
	t.Setenv(EnvFieldMapping, "POST:/v1/chat/completions:messages.role=user.content")
	t.Cleanup(func() { os.Unsetenv(EnvFieldMapping) })
	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}

	GlobalGuardrailSchemaRegistry().Replace(map[string]*GuardrailSchema{
		"POST:/v1/chat/completions": {
			RequestMessageFields: []MessageFieldEntry{
				{FieldPath: "messages.role=system.content"},
			},
		},
	})
	t.Cleanup(func() { resetGuardrailSchemaRegistry(t) })

	fields := resolveFieldsForEndpoint("POST", "/v1/chat/completions", true)
	if len(fields) != 1 || fields[0].FieldPath != "messages.role=system.content" {
		t.Fatalf("resolveFieldsForEndpoint() = %#v, want dashboard schema field", fields)
	}
}

func TestExtractPayloadForValidation_noMappingReturnsRawPayload(t *testing.T) {
	resetFieldMappingEnv(t)
	resetGuardrailSchemaRegistry(t)

	svc := testValidatorService()
	raw := `{"messages":[{"role":"user","content":"validate entire payload"}]}`

	got := svc.extractPayloadForValidation(raw, "POST", "/v1/chat/completions", true)
	if got != raw {
		t.Fatalf("extractPayloadForValidation() = %q, want raw payload %q", got, raw)
	}
}

func TestExtractPayloadForValidation_dashboardSchemaExtracts(t *testing.T) {
	resetFieldMappingEnv(t)
	GlobalGuardrailSchemaRegistry().Replace(map[string]*GuardrailSchema{
		"POST:/v1/chat/completions": {
			RequestMessageFields: []MessageFieldEntry{
				{FieldPath: "messages.role=user.content"},
			},
		},
	})
	t.Cleanup(func() { resetGuardrailSchemaRegistry(t) })

	svc := testValidatorService()
	raw := `{"messages":[{"role":"user","content":"dashboard extracted prompt"}]}`

	got := svc.extractPayloadForValidation(raw, "POST", "/v1/chat/completions", true)
	want := `{"text":"dashboard extracted prompt"}`
	if got != want {
		t.Fatalf("extractPayloadForValidation() = %q, want %q", got, want)
	}
}

func TestExtractPayloadForValidation_envExtractionFailureFallsBackToRawPayload(t *testing.T) {
	t.Setenv(EnvFieldMapping, "POST:/v1/chat/completions:messages.role=user.content.0.text")
	t.Cleanup(func() { os.Unsetenv(EnvFieldMapping) })
	globalFieldMapping = nil
	globalFieldMappingOnce = sync.Once{}
	resetGuardrailSchemaRegistry(t)

	svc := testValidatorService()
	raw := `{"messages":[{"role":"user","content":"plain string content"}]}`

	got := svc.extractPayloadForValidation(raw, "POST", "/v1/chat/completions", true)
	if got != raw {
		t.Fatalf("extractPayloadForValidation() = %q, want raw payload %q", got, raw)
	}
}
