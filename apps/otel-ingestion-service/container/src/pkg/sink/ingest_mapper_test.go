package sink

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	shieldmcp "github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/utils"
	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
)

func TestEventToIngestRecord(t *testing.T) {
	e := model.OtelIngestEvent{
		AccountID:     1726615470,
		Source:        "claude_code",
		SignalType:    "logs",
		EventName:     "claude_code.user_prompt",
		Timestamp:     time.Unix(1700000000, 0).UTC(),
		CorrelationID: "prompt-abc",
		Attributes: map[string]string{
			"service.name": "cowork",
			"prompt.id":    "prompt-abc",
			"prompt":       "hello",
			"session.id":   "529ccd89-30b4-4f2a-9c1d-abcdef123456",
			"user.id":      "0571cbc5-1234-5678-90ab-cdef12345678",
			"user.email":   "user@example.com",
			"os.type":      "darwin",
			"os.version":   "26.5.2",
			"host.arch":    "arm64",
		},
	}

	rec, err := eventToIngestRecord(e)
	if err != nil {
		t.Fatal(err)
	}
	if rec.Path != "/v1/messages" {
		t.Fatalf("unexpected path %q", rec.Path)
	}
	if rec.AktoAccountID != "1726615470" {
		t.Fatalf("unexpected account id %q", rec.AktoAccountID)
	}
	if rec.AktoVxlanID != "0571cbc5" {
		t.Fatalf("unexpected vxlan id %q", rec.AktoVxlanID)
	}
	if rec.IP != "user@example.com" {
		t.Fatalf("unexpected ip %q", rec.IP)
	}
	if !rec.PublishToGuardrails {
		t.Fatal("expected publishToGuardrails true")
	}

	var tag map[string]string
	if err := json.Unmarshal([]byte(rec.Tag), &tag); err != nil {
		t.Fatal(err)
	}
	if tag["akto_connector"] != "claude_cowork" {
		t.Fatalf("unexpected connector %q", tag["akto_connector"])
	}
	if tag["ai-agent"] != "claude_cowork" {
		t.Fatalf("unexpected ai-agent %q", tag["ai-agent"])
	}
	if tag["source"] != utils.EndpointSource {
		t.Fatalf("expected ENDPOINT source, got %q", tag["source"])
	}
	if rec.ContextSource != utils.EndpointSource {
		t.Fatalf("expected contextSource ENDPOINT, got %q", rec.ContextSource)
	}
	if tag["hook"] != "user_prompt" {
		t.Fatalf("unexpected hook %q", tag["hook"])
	}
	if tag["prompt_id"] != "prompt-abc" {
		t.Fatalf("unexpected prompt_id %q", tag["prompt_id"])
	}
	if tag["session_id"] != "529ccd89-30b4-4f2a-9c1d-abcdef123456" {
		t.Fatalf("unexpected session_id %q", tag["session_id"])
	}
	if tag["mode"] != "observe" {
		t.Fatalf("expected observe mode, got %q", tag["mode"])
	}
	if tag["username"] != "user@example.com" {
		t.Fatalf("unexpected username tag %q", tag["username"])
	}
	if tag["os"] != "mac" {
		t.Fatalf("unexpected os tag %q", tag["os"])
	}

	var headers map[string]string
	if err := json.Unmarshal([]byte(rec.RequestHeaders), &headers); err != nil {
		t.Fatal(err)
	}
	wantHost := "0571cbc5.ai-agent.claude_cowork"
	if headers["host"] != wantHost {
		t.Fatalf("unexpected host %q, want %q", headers["host"], wantHost)
	}
	if headers["x-claude_cowork-hook"] != "user_prompt" {
		t.Fatalf("unexpected hook header %q", headers["x-claude_cowork-hook"])
	}
	if headers["x-akto-installer-device_id"] != "0571cbc5" {
		t.Fatalf("unexpected device_id header %q", headers["x-akto-installer-device_id"])
	}
	if headers["x-akto-installer-user_email"] != "user@example.com" {
		t.Fatalf("unexpected user_email header %q", headers["x-akto-installer-user_email"])
	}
	if headers["x-akto-installer-os"] != "mac" {
		t.Fatalf("unexpected os header %q", headers["x-akto-installer-os"])
	}
	if headers["x-akto-installer-akto_session_id"] != "529ccd89-30b4-4f2a-9c1d-abcdef123456" {
		t.Fatalf("unexpected session header %q", headers["x-akto-installer-akto_session_id"])
	}
	if headers["x-akto-installer-session_id"] != "529ccd89-30b4-4f2a-9c1d-abcdef123456" {
		t.Fatalf("unexpected session_id alias %q", headers["x-akto-installer-session_id"])
	}
	if headers["x-akto-installer-akto_message_id"] != "prompt-abc" {
		t.Fatalf("unexpected message id header %q", headers["x-akto-installer-akto_message_id"])
	}

	var body map[string]string
	if err := json.Unmarshal([]byte(rec.RequestPayload), &body); err != nil {
		t.Fatal(err)
	}
	if body["body"] != "hello" {
		t.Fatalf("unexpected request body %q", body["body"])
	}
}

func TestEventToIngestRecordToolResult(t *testing.T) {
	e := model.OtelIngestEvent{
		AccountID: 1000000,
		EventName: "claude_code.tool_result",
		Timestamp: time.Unix(1783427985, 0).UTC(),
		Attributes: map[string]string{
			"tool_name":  "Bash",
			"user.id":    "0571cbc5b6d43849d7f66f42ae1e72d1bce2df54783dc57b581306ffb584b5e0",
			"user.email": "rohan@akto.io",
			"session.id": "529ccd89-30b4-4f2a-9c1d-abcdef123456",
		},
	}

	rec, err := eventToIngestRecord(e)
	if err != nil {
		t.Fatal(err)
	}
	if rec.Path != "/tool/bash" {
		t.Fatalf("unexpected path %q", rec.Path)
	}

	var tag map[string]string
	if err := json.Unmarshal([]byte(rec.Tag), &tag); err != nil {
		t.Fatal(err)
	}
	if tag["tool_name"] != "Bash" {
		t.Fatalf("unexpected tool_name tag %q", tag["tool_name"])
	}
	if tag["ai-agent"] != "claude_cowork" {
		t.Fatalf("expected ai-agent on built-in tool, got %q", tag["ai-agent"])
	}

	var headers map[string]string
	if err := json.Unmarshal([]byte(rec.RequestHeaders), &headers); err != nil {
		t.Fatal(err)
	}
	if headers["host"] != "0571cbc5b6d4.ai-agent.claude_cowork" {
		t.Fatalf("unexpected host %q", headers["host"])
	}

	var payload map[string]interface{}
	if err := json.Unmarshal([]byte(rec.RequestPayload), &payload); err != nil {
		t.Fatal(err)
	}
	if payload["toolName"] != "Bash" {
		t.Fatalf("unexpected toolName in payload %v", payload["toolName"])
	}
}

func TestEventToIngestRecordMCPTool(t *testing.T) {
	e := model.OtelIngestEvent{
		AccountID: 1000000,
		EventName: "claude_code.tool_decision",
		Timestamp: time.Unix(1783427985, 0).UTC(),
		Attributes: map[string]string{
			"tool_name": "mcp__github__search_repos",
			"user.id":   "0571cbc5b6d43849",
		},
	}

	rec, err := eventToIngestRecord(e)
	if err != nil {
		t.Fatal(err)
	}
	if rec.Path != "/mcp" {
		t.Fatalf("unexpected path %q", rec.Path)
	}

	var tag map[string]string
	if err := json.Unmarshal([]byte(rec.Tag), &tag); err != nil {
		t.Fatal(err)
	}
	if tag["mcp-server"] != "MCP Server" {
		t.Fatalf("expected mcp-server tag, got %q", tag["mcp-server"])
	}
	if tag["ai-agent"] != "" {
		t.Fatalf("MCP traffic should not set ai-agent, got %q", tag["ai-agent"])
	}

	var headers map[string]string
	if err := json.Unmarshal([]byte(rec.RequestHeaders), &headers); err != nil {
		t.Fatal(err)
	}
	if headers["host"] != "0571cbc5b6d4.claude_cowork.github" {
		t.Fatalf("unexpected MCP host %q", headers["host"])
	}
	if headers["x-mcp-server"] != "github" {
		t.Fatalf("unexpected x-mcp-server %q", headers["x-mcp-server"])
	}
}

func TestToIngestDataRequestSkipsPluginLoaded(t *testing.T) {
	batch := Batch{
		AccountID: 1000000,
		Events: []model.OtelIngestEvent{
			{
				EventName: "claude_code.user_prompt",
				Attributes: map[string]string{
					"prompt":     "hi",
					"user.id":    "0571cbc5",
					"user.email": "user@example.com",
				},
			},
			{
				EventName: "claude_code.plugin_loaded",
				Attributes: map[string]string{
					"plugin.name": "anthropic-skills",
					"user.id":     "0571cbc5",
				},
			},
		},
	}
	body, err := toIngestDataRequest(batch)
	if err != nil {
		t.Fatal(err)
	}
	var req ingestDataRequest
	if err := json.Unmarshal(body, &req); err != nil {
		t.Fatal(err)
	}
	if len(req.BatchData) != 1 {
		t.Fatalf("expected 1 record (plugin_loaded dropped), got %d", len(req.BatchData))
	}
	if req.BatchData[0].Path != "/v1/messages" {
		t.Fatalf("unexpected path %q", req.BatchData[0].Path)
	}
}

func TestSkipsIngestDiscovery(t *testing.T) {
	for _, hook := range []string{"plugin_loaded", "hook_execution_start", "hook_execution_complete", "api_request"} {
		if !skipsIngestDiscovery(hook) {
			t.Fatalf("%s must skip ingest", hook)
		}
	}
	if skipsIngestDiscovery("user_prompt") {
		t.Fatal("user_prompt must not skip ingest")
	}
}

func TestMergePromptTurnEvents(t *testing.T) {
	batch := Batch{
		AccountID: 1000000,
		Events: []model.OtelIngestEvent{
			{
				EventName: "claude_code.user_prompt",
				Timestamp: time.Unix(100, 0).UTC(),
				Attributes: map[string]string{
					"prompt.id": "p1",
					"prompt":    "hello",
					"user.id":   "0571cbc5",
					"user.email": "user@example.com",
					"session.id": "sess-1",
				},
			},
			{
				EventName: "claude_code.api_request",
				Timestamp: time.Unix(101, 0).UTC(),
				Attributes: map[string]string{
					"prompt.id":     "p1",
					"model":         "claude-haiku-4-5-20251001",
					"input_tokens":  "2",
					"output_tokens": "19",
					"user.id":       "0571cbc5",
					"user.email":    "user@example.com",
					"session.id":    "sess-1",
				},
			},
			{
				EventName: "claude_code.assistant_response",
				Timestamp: time.Unix(102, 0).UTC(),
				Attributes: map[string]string{
					"prompt.id": "p1",
					"response":  "hi there",
					"model":     "claude-haiku-4-5-20251001",
					"user.id":   "0571cbc5",
					"user.email": "user@example.com",
					"session.id": "sess-1",
				},
			},
			{
				EventName: "claude_code.hook_execution_start",
				Attributes: map[string]string{
					"prompt.id": "p1",
					"user.id":   "0571cbc5",
				},
			},
			{
				EventName: "claude_code.tool_decision",
				Attributes: map[string]string{
					"tool_name": "Bash",
					"user.id":   "0571cbc5",
				},
			},
		},
	}

	body, err := toIngestDataRequest(batch)
	if err != nil {
		t.Fatal(err)
	}
	var req ingestDataRequest
	if err := json.Unmarshal(body, &req); err != nil {
		t.Fatal(err)
	}
	if len(req.BatchData) != 2 {
		t.Fatalf("expected merged LLM + tool rows, got %d", len(req.BatchData))
	}

	var llm ingestDataRecord
	for _, rec := range req.BatchData {
		if rec.Path == "/v1/messages" {
			llm = rec
		}
		if strings.HasPrefix(rec.Path, "/cowork/") {
			t.Fatalf("unexpected internal path %q", rec.Path)
		}
	}
	if llm.Path != "/v1/messages" {
		t.Fatal("missing merged /v1/messages row")
	}

	var reqBody map[string]string
	if err := json.Unmarshal([]byte(llm.RequestPayload), &reqBody); err != nil {
		t.Fatal(err)
	}
	if reqBody["body"] != "hello" {
		t.Fatalf("unexpected prompt body %q", reqBody["body"])
	}

	var respBody map[string]interface{}
	if err := json.Unmarshal([]byte(llm.ResponsePayload), &respBody); err != nil {
		t.Fatal(err)
	}
	if respBody["model"] != "claude-haiku-4-5-20251001" {
		t.Fatalf("unexpected model %v", respBody["model"])
	}
	usage, ok := respBody["usage"].(map[string]interface{})
	if !ok || int(usage["input_tokens"].(float64)) != 2 {
		t.Fatalf("unexpected usage %v", respBody["usage"])
	}
}

func TestNormalizeOsType(t *testing.T) {
	if normalizeOsType("darwin") != "mac" {
		t.Fatal("darwin → mac")
	}
	if normalizeOsType("windows") != "windows" {
		t.Fatal("windows")
	}
	if normalizeOsType("nope") != "" {
		t.Fatal("unknown → empty")
	}
}

func TestDeriveDevicePrefixFallsBackToSession(t *testing.T) {
	prefix := deriveDevicePrefix(map[string]string{"session.id": "529ccd89-30b4-4f2a"})
	if prefix != "529ccd89" {
		t.Fatalf("expected session prefix 529ccd89, got %q", prefix)
	}
}

func TestDeriveDevicePrefixFallbackCowork(t *testing.T) {
	if deriveDevicePrefix(map[string]string{}) != "cowork" {
		t.Fatal("expected cowork fallback")
	}
}

func TestParseMCPToolName(t *testing.T) {
	s, tool := parseMCPToolName("mcp__github__search_repos")
	if s != "github" || tool != "search_repos" {
		t.Fatalf("got server=%q tool=%q", s, tool)
	}
	if s2, t2 := parseMCPToolName("Bash"); s2 != "" || t2 != "" {
		t.Fatal("Bash should not parse as MCP")
	}
}

func TestNormalizeToolPathName(t *testing.T) {
	if got := normalizeToolPathName("Bash"); got != "bash" {
		t.Fatalf("got %q", got)
	}
	if !strings.Contains(normalizeToolPathName("Read File"), "read") {
		t.Fatal("expected sanitized tool path")
	}
}

func TestCoworkTagPassesIsEndpointOrMcpRequest(t *testing.T) {
	rec, err := eventToIngestRecord(model.OtelIngestEvent{
		AccountID: 1000000,
		EventName: "claude_code.user_prompt",
		Timestamp: time.Unix(1700000000, 0).UTC(),
		Attributes: map[string]string{
			"prompt":     "hello",
			"user.id":    "0571cbc5",
			"user.email": "user@example.com",
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if !shieldmcp.IsEndpointOrMcpRequest(rec.Tag, "") {
		t.Fatalf("Cowork tag should resolve as ENDPOINT traffic: %s", rec.Tag)
	}
}
