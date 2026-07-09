package sink

import (
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	shieldmcp "github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/utils"
	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
	"github.com/akto-api-security/otel-ingestion-service/pkg/strutil"
)

var slugSanitizer = regexp.MustCompile(`[^a-z0-9._~-]+`)

const (
	connectorClaudeCowork = "claude_cowork"
	guardrailModeObserve  = "observe"
	genAITagValue         = "Gen AI"
	coworkHookHeader      = "x-claude_cowork-hook"
	installerHeaderPrefix = "x-akto-installer-"
	maxDevicePrefixLen    = 12
	toolPathPrefix        = "/tool"
	llmMessagesPath       = "/v1/messages"
	mcpIngestPath         = "/mcp"
)

// ingestDataRecord extends shield ingest batch rows with guardrails routing fields.
type ingestDataRecord struct {
	shieldmcp.IngestDataBatch
	PublishToGuardrails bool   `json:"publishToGuardrails"`
	ContextSource       string `json:"contextSource,omitempty"`
}

type ingestDataRequest struct {
	BatchData []ingestDataRecord `json:"batchData"`
}

func toIngestDataRequest(batch Batch) ([]byte, error) {
	merged := mergePromptTurnEvents(batch.Events)
	records := make([]ingestDataRecord, 0, len(merged))
	for _, e := range merged {
		if skipsIngestDiscovery(hookNameFromEvent(e.EventName)) {
			continue
		}
		if skipsMcpToolDecisionIngest(e) {
			continue
		}
		rec, err := eventToIngestRecord(e)
		if err != nil {
			return nil, err
		}
		records = append(records, rec)
	}
	return json.Marshal(ingestDataRequest{BatchData: records})
}

// skipsIngestDiscovery returns true for OTLP events that must not become inventory
// endpoints (STI / api_info). plugin_loaded is a Claude plugin bundle signal — not an
// API call, skill, or tool. TBD: dedicated Plugins surface; until then, drop from ingest.
func skipsIngestDiscovery(hookName string) bool {
	switch hookName {
	case "plugin_loaded",
		"hook_execution_start",
		"hook_execution_complete",
		"api_request":
		return true
	default:
		return false
	}
}

// skipsMcpToolDecisionIngest drops MCP tool_decision rows. Cowork emits decision before
// result; shield only mirrors PostToolUse with tools/call + JSON-RPC result. Ingesting
// tool_decision with response "{}" wins sample dedup and leaves MCP calls blank in UI.
func skipsMcpToolDecisionIngest(e model.OtelIngestEvent) bool {
	hookName := hookNameFromEvent(e.EventName)
	if hookName != "tool_decision" {
		return false
	}
	toolName := toolNameFromEvent(e.EventName, e.Attributes)
	_, _, _, isMCP := resolveCoworkToolRouting(e.Attributes, toolName)
	return isMCP
}

// skipsPromptMerge returns true for events handled outside per-prompt LLM turn merging.
func skipsPromptMerge(hookName string) bool {
	switch hookName {
	case "plugin_loaded",
		"hook_execution_start",
		"hook_execution_complete",
		"tool_decision",
		"tool_result":
		return true
	default:
		return false
	}
}

// mergePromptTurnEvents collapses user_prompt + api_request + assistant_response for the
// same prompt.id into one /v1/messages ingest row. Internal hook/api_request telemetry
// must not become inventory endpoints or pollute LLM Observability sessions.
func mergePromptTurnEvents(events []model.OtelIngestEvent) []model.OtelIngestEvent {
	type promptTurn struct {
		base          model.OtelIngestEvent
		prompt        string
		response      string
		model         string
		inputTokens   int
		outputTokens  int
		hasUserPrompt bool
	}

	byPrompt := make(map[string]*promptTurn)
	var out []model.OtelIngestEvent

	for _, e := range events {
		hook := hookNameFromEvent(e.EventName)
		if skipsPromptMerge(hook) {
			out = append(out, e)
			continue
		}

		promptID := strutil.FirstNonEmpty(e.Attributes, "prompt.id", "prompt_id")
		if promptID == "" {
			promptID = e.CorrelationID
		}
		if promptID == "" {
			if hook == "user_prompt" || hook == "assistant_response" {
				out = append(out, e)
			}
			continue
		}

		turn := byPrompt[promptID]
		if turn == nil {
			turn = &promptTurn{base: e}
			byPrompt[promptID] = turn
		}

		switch hook {
		case "user_prompt":
			turn.hasUserPrompt = true
			if v := strutil.FirstNonEmpty(e.Attributes, "prompt"); v != "" {
				turn.prompt = v
			}
			if turn.base.Timestamp.IsZero() || e.Timestamp.Before(turn.base.Timestamp) {
				turn.base = e
			}
		case "assistant_response":
			if v := strutil.FirstNonEmpty(e.Attributes, "response"); v != "" {
				turn.response = v
			}
			if v := strutil.FirstNonEmpty(e.Attributes, "model"); v != "" {
				turn.model = v
			}
			if !e.Timestamp.IsZero() {
				turn.base = e
			}
		case "api_request":
			if v := strutil.FirstNonEmpty(e.Attributes, "model"); v != "" {
				turn.model = v
			}
			if v := intAttr(e.Attributes, "input_tokens"); v > 0 {
				turn.inputTokens = v
			}
			if v := intAttr(e.Attributes, "output_tokens"); v > 0 {
				turn.outputTokens = v
			}
			if !e.Timestamp.IsZero() {
				turn.base = e
			}
		default:
			continue
		}
	}

	for promptID, turn := range byPrompt {
		attrs := make(map[string]string, len(turn.base.Attributes)+8)
		for k, v := range turn.base.Attributes {
			attrs[k] = v
		}
		attrs["prompt.id"] = promptID
		if turn.prompt != "" {
			attrs["prompt"] = turn.prompt
		}
		if turn.response != "" {
			attrs["response"] = turn.response
		}
		if turn.model != "" {
			attrs["model"] = turn.model
		}
		if turn.inputTokens > 0 {
			attrs["input_tokens"] = strconv.Itoa(turn.inputTokens)
		}
		if turn.outputTokens > 0 {
			attrs["output_tokens"] = strconv.Itoa(turn.outputTokens)
		}

		eventName := "claude_code.user_prompt"
		if turn.response != "" || turn.model != "" || turn.inputTokens > 0 || turn.outputTokens > 0 {
			eventName = "claude_code.assistant_response"
		} else if !turn.hasUserPrompt {
			continue
		}

		out = append(out, model.OtelIngestEvent{
			AccountID:     turn.base.AccountID,
			Source:        turn.base.Source,
			SignalType:    turn.base.SignalType,
			EventName:     eventName,
			Timestamp:     turn.base.Timestamp,
			CorrelationID: turn.base.CorrelationID,
			Attributes:    attrs,
		})
	}

	return out
}

func eventToIngestRecord(e model.OtelIngestEvent) (ingestDataRecord, error) {
	devicePrefix := deriveDevicePrefix(e.Attributes)
	hookName := hookNameFromEvent(e.EventName)
	promptID := strutil.FirstNonEmpty(e.Attributes, "prompt.id", "prompt_id")
	sessionID := e.Attributes["session.id"]
	userEmail := strutil.FirstNonEmpty(e.Attributes, "user.email", "user_email")

	toolName := toolNameFromEvent(e.EventName, e.Attributes)
	mcpServer, mcpTool := "", ""
	isMCP := false
	if toolName != "" {
		toolName, mcpServer, mcpTool, isMCP = resolveCoworkToolRouting(e.Attributes, toolName)
	}

	// Shield parity: MCP tools land on {device}.{connector}.{mcpServer};
	// agent-owned traffic (prompts, built-in tools, etc.) on {device}.ai-agent.{connector}.
	host := deriveIngestHost(devicePrefix)
	if isMCP {
		host = deriveMCPHost(devicePrefix, mcpServer)
	}

	tagObj := map[string]string{
		utils.SourceTag:  utils.EndpointSource,
		"hook":           hookName,
		"akto_connector": connectorClaudeCowork,
		"mode":           guardrailModeObserve,
	}
	if isMCP {
		tagObj["mcp-server"] = "MCP Server"
		tagObj["mcp-client"] = connectorClaudeCowork
	} else {
		tagObj["gen-ai"] = genAITagValue
		tagObj[utils.AgentSource] = connectorClaudeCowork
	}
	if promptID != "" {
		tagObj["prompt_id"] = promptID
	}
	if sessionID != "" {
		tagObj["session_id"] = sessionID
	}
	// Username for Agentic UI: registered as collection envType tags via module heartbeat.
	if userEmail != "" {
		tagObj["username"] = userEmail
	}
	if osName := normalizeOsType(strutil.FirstNonEmpty(e.Attributes, "os.type", "os_type")); osName != "" {
		tagObj["os"] = osName
	}
	if toolName != "" {
		tagObj["tool_name"] = toolName
	}
	tagJSON, err := json.Marshal(tagObj)
	if err != nil {
		return ingestDataRecord{}, err
	}

	reqHeaders, err := buildAtlasRequestHeaders(host, hookName, e.EventName, e.Attributes, devicePrefix, promptID, sessionID, toolName, mcpServer)
	if err != nil {
		return ingestDataRecord{}, err
	}

	path, reqPayload, respPayload, err := buildPathAndPayloads(e, hookName, toolName, isMCP, mcpTool)
	if err != nil {
		return ingestDataRecord{}, err
	}

	ts := e.Timestamp
	if ts.IsZero() {
		ts = time.Now().UTC()
	}

	// Shield uses OS username in ip; Cowork has user.email — use it for identity parity.
	ip := userEmail
	if ip == "" {
		ip = "127.0.0.1"
	}

	return ingestDataRecord{
		IngestDataBatch: shieldmcp.IngestDataBatch{
			Path:            path,
			RequestHeaders:  string(reqHeaders),
			ResponseHeaders: "{}",
			Method:          "POST",
			RequestPayload:  reqPayload,
			ResponsePayload: respPayload,
			IP:              ip,
			Time:            strconv.FormatInt(ts.Unix(), 10),
			StatusCode:      "200",
			Type:            "HTTP/1.1",
			Status:          "OK",
			AktoAccountID:   strconv.Itoa(e.AccountID),
			AktoVxlanID:     devicePrefix,
			IsPending:       "false",
			Source:          "MIRRORING",
			Tag:             string(tagJSON),
		},
		PublishToGuardrails: true,
		ContextSource:       utils.EndpointSource,
	}, nil
}

func buildPathAndPayloads(
	e model.OtelIngestEvent,
	hookName, toolName string,
	isMCP bool,
	mcpTool string,
) (path, reqPayload, respPayload string, err error) {
	attrsJSON, err := json.Marshal(e.Attributes)
	if err != nil {
		return "", "", "", err
	}
	respPayload = "{}"

	switch hookName {
	case "user_prompt", "assistant_response":
		// Shield parity for LLM Observability — /v1/messages with Anthropic-shaped payloads.
		path = llmMessagesPath
		promptText := strutil.FirstNonEmpty(e.Attributes, "prompt")
		responseText := strutil.FirstNonEmpty(e.Attributes, "response")
		modelName := strutil.FirstNonEmpty(e.Attributes, "model")
		inputTok := intAttr(e.Attributes, "input_tokens")
		outputTok := intAttr(e.Attributes, "output_tokens")

		reqBody := promptText
		if reqBody == "" && hookName == "user_prompt" {
			reqBody = strutil.FirstNonEmpty(e.Attributes, "prompt_length")
		}
		reqBytes, err := json.Marshal(map[string]string{"body": reqBody})
		if err != nil {
			return "", "", "", err
		}
		reqPayload = string(reqBytes)

		if hookName == "assistant_response" || responseText != "" || modelName != "" || inputTok > 0 || outputTok > 0 {
			respPayload, err = anthropicMessagesResponse(modelName, responseText, inputTok, outputTok)
			if err != nil {
				return "", "", "", err
			}
		}
		return path, reqPayload, respPayload, nil

	case "tool_decision", "tool_result":
		// Shield: non-MCP → /tool/{name}; MCP → /mcp + JSON-RPC tools/call.
		if isMCP {
			path = mcpIngestPath
			rpc, mErr := json.Marshal(map[string]interface{}{
				"jsonrpc": "2.0",
				"method":  "tools/call",
				"params": map[string]interface{}{
					"name":      mcpTool,
					"arguments": mcpToolArguments(e.Attributes),
				},
				"id": 1,
			})
			if mErr != nil {
				return "", "", "", mErr
			}
			reqPayload = string(rpc)
			if hookName == "tool_result" {
				respPayload, mErr = marshalMcpToolsCallResponse(e.Attributes)
				if mErr != nil {
					return "", "", "", mErr
				}
			} else {
				respPayload = "{}"
			}
			return path, reqPayload, respPayload, nil
		}
		normalized := normalizeToolPathName(toolName)
		path = toolPathPrefix + "/" + normalized
		if hookName == "tool_decision" {
			reqPayload, respPayload, err = marshalToolDecisionPayloads(e.Attributes, toolName)
			if err != nil {
				return "", "", "", err
			}
			return path, reqPayload, respPayload, nil
		}
		toolBody := builtinToolPayloadBody(e.Attributes)
		payload, mErr := json.Marshal(map[string]interface{}{
			"body":     toolBody,
			"toolName": toolName,
		})
		if mErr != nil {
			return "", "", "", mErr
		}
		reqPayload = string(payload)
		respBody := builtinToolResponseBody(e.Attributes)
		respJSON, mErr := json.Marshal(map[string]interface{}{"body": map[string]interface{}{"result": respBody}})
		if mErr != nil {
			return "", "", "", mErr
		}
		return path, reqPayload, string(respJSON), nil

	default:
		path = fmt.Sprintf("/cowork/%s", e.EventName)
		return path, string(attrsJSON), respPayload, nil
	}
}

func buildAtlasRequestHeaders(
	host, hookName, eventName string,
	attrs map[string]string,
	devicePrefix, promptID, sessionID, toolName, mcpServer string,
) ([]byte, error) {
	headers := map[string]string{
		"host":                              host,
		"content-type":                      "application/json",
		"x-otel-event":                      eventName,
		coworkHookHeader:                    hookName,
		installerHeaderPrefix + "device_id": devicePrefix,
		installerHeaderPrefix + "akto_session_id": sessionID,
		installerHeaderPrefix + "session_id":      sessionID, // AgentQueryDataDao alias
		installerHeaderPrefix + "akto_message_id": promptID,
	}
	if email := strutil.FirstNonEmpty(attrs, "user.email", "user_email"); email != "" {
		headers[installerHeaderPrefix+"user_email"] = email
	}
	if osName := normalizeOsType(strutil.FirstNonEmpty(attrs, "os.type", "os_type")); osName != "" {
		headers[installerHeaderPrefix+"os"] = osName
	}
	if osVer := strutil.FirstNonEmpty(attrs, "os.version", "os_version"); osVer != "" {
		headers[installerHeaderPrefix+"os_version"] = osVer
	}
	if arch := strutil.FirstNonEmpty(attrs, "host.arch", "host_arch"); arch != "" {
		headers[installerHeaderPrefix+"arch"] = arch
	}
	if toolName != "" {
		headers["x-tool-name"] = toolName
	}
	if mcpServer != "" {
		headers["x-mcp-server"] = mcpServer
	}
	return json.Marshal(headers)
}

func toolNameFromEvent(eventName string, attrs map[string]string) string {
	hook := hookNameFromEvent(eventName)
	if hook != "tool_decision" && hook != "tool_result" {
		return ""
	}
	return strutil.FirstNonEmpty(attrs, "tool_name", "tool.name")
}

// resolveCoworkToolRouting classifies tool telemetry: external/user MCP (mcp__ prefix or
// tool_parameters server/name), vs direct builtins (Read, etc.) on /tool/*.
func resolveCoworkToolRouting(attrs map[string]string, displayName string) (string, string, string, bool) {
	if s, t := parseMCPToolName(displayName); s != "" && t != "" {
		return displayName, s, t, true
	}
	if s, t := mcpNamesFromToolParameters(attrs); s != "" && t != "" {
		return displayName, s, t, true
	}
	return displayName, "", "", false
}

func mcpNamesFromToolParameters(attrs map[string]string) (server, tool string) {
	raw := attrs["tool_parameters"]
	if raw == "" {
		return "", ""
	}
	var params map[string]interface{}
	if json.Unmarshal([]byte(raw), &params) != nil {
		return "", ""
	}
	server = stringParam(params, "mcp_server_name")
	tool = stringParam(params, "mcp_tool_name")
	return server, tool
}

func stringParam(params map[string]interface{}, key string) string {
	v, ok := params[key]
	if !ok || v == nil {
		return ""
	}
	s, ok := v.(string)
	if !ok {
		return ""
	}
	return strings.TrimSpace(s)
}

func mcpToolArguments(attrs map[string]string) map[string]interface{} {
	if raw := attrs["tool_parameters"]; raw != "" {
		var params map[string]interface{}
		if json.Unmarshal([]byte(raw), &params) == nil {
			args := make(map[string]interface{}, len(params))
			for k, v := range params {
				if k == "mcp_server_name" || k == "mcp_tool_name" {
					continue
				}
				args[k] = v
			}
			if len(args) > 0 {
				return args
			}
		}
	}
	if raw := strutil.FirstNonEmpty(attrs, "tool_input", "tool.input", "tool_args"); raw != "" {
		if v := jsonRawOrString(raw); v != nil {
			if m, ok := v.(map[string]interface{}); ok {
				return m
			}
		}
	}
	return map[string]interface{}{}
}

// builtinToolPayloadBody mirrors shield post-tool request shape for tool_result rows.
func builtinToolPayloadBody(attrs map[string]string) interface{} {
	if raw := strutil.FirstNonEmpty(attrs, "tool_input", "tool.input", "tool_args"); raw != "" {
		return jsonRawOrString(raw)
	}
	return attrs
}

// builtinToolResponseBody mirrors shield post-tool shape: {"body": {"result": <output>}}.
func builtinToolResponseBody(attrs map[string]string) interface{} {
	return toolResultBody(attrs)
}

// marshalMcpToolsCallResponse builds JSON-RPC 2.0 tools/call result for MCP tool_result rows.
func marshalMcpToolsCallResponse(attrs map[string]string) (string, error) {
	return buildToolsCallResultJSONRPC(mcpCallToolResult(attrs))
}

// buildToolsCallResultJSONRPC wraps an MCP CallToolResult-shaped body in JSON-RPC 2.0.
func buildToolsCallResultJSONRPC(resultBody map[string]interface{}) (string, error) {
	b, err := json.Marshal(map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      1,
		"result":  resultBody,
	})
	if err != nil {
		return "", err
	}
	return string(b), nil
}

// mcpCallToolResult uses MCP schema content[] for inventory/samples; Cowork OTLP often
// omits tool output text and only ships metadata (success, duration_ms, tool_result_size_bytes).
func mcpCallToolResult(attrs map[string]string) map[string]interface{} {
	if raw := strutil.FirstNonEmpty(attrs, "tool_response", "tool.response", "tool_output", "tool.result"); raw != "" {
		body := jsonRawOrString(raw)
		switch v := body.(type) {
		case string:
			return map[string]interface{}{
				"content": []map[string]interface{}{{"type": "text", "text": v}},
			}
		case map[string]interface{}:
			if _, ok := v["content"]; ok {
				return v
			}
			if b, err := json.Marshal(v); err == nil {
				return map[string]interface{}{
					"content": []map[string]interface{}{{"type": "text", "text": string(b)}},
				}
			}
		}
	}
	text := mcpResultSummaryText(attrs)
	if text == "" {
		text = "(no tool output in OTLP event)"
	}
	return map[string]interface{}{
		"content": []map[string]interface{}{{"type": "text", "text": text}},
	}
}

func mcpResultSummaryText(attrs map[string]string) string {
	var parts []string
	if v := attrs["success"]; v != "" {
		parts = append(parts, "success="+v)
	}
	if v := attrs["duration_ms"]; v != "" {
		parts = append(parts, "duration_ms="+v+"ms")
	}
	if v := attrs["tool_result_size_bytes"]; v != "" {
		parts = append(parts, "tool_result_size_bytes="+v)
	}
	if v := attrs["error"]; v != "" {
		parts = append(parts, "error="+v)
	}
	if raw := attrs["tool_input"]; raw != "" {
		parts = append(parts, "tool_input="+raw)
	}
	return strings.Join(parts, "\n")
}

// toolResultBody extracts tool output when present; Cowork OTLP often omits body text and
// only ships metadata (success, duration_ms, tool_result_size_bytes, tool_input).
func toolResultBody(attrs map[string]string) interface{} {
	if raw := strutil.FirstNonEmpty(attrs, "tool_response", "tool.response", "tool_output", "tool.result"); raw != "" {
		return jsonRawOrString(raw)
	}
	meta := map[string]interface{}{}
	if v := attrs["success"]; v != "" {
		meta["success"] = v == "true"
	}
	if v := attrs["error"]; v != "" {
		meta["error"] = v
	}
	if v := attrs["duration_ms"]; v != "" {
		meta["duration_ms"] = v
	}
	if v := attrs["tool_result_size_bytes"]; v != "" {
		meta["tool_result_size_bytes"] = v
	}
	if raw := attrs["tool_input"]; raw != "" {
		meta["tool_input"] = jsonRawOrString(raw)
	}
	if len(meta) > 0 {
		return meta
	}
	return map[string]interface{}{"output": ""}
}

// marshalToolDecisionPayloads splits Cowork tool_decision telemetry: parameters in the
// request, decision/metadata in the response (tool_result keeps input/result split).
func marshalToolDecisionPayloads(attrs map[string]string, toolName string) (string, string, error) {
	reqBody := interface{}(map[string]string{})
	if raw := attrs["tool_parameters"]; raw != "" {
		reqBody = jsonRawOrString(raw)
	}
	req := map[string]interface{}{
		"body":     reqBody,
		"toolName": toolName,
	}
	if id := attrs["tool_use_id"]; id != "" {
		req["tool_use_id"] = id
	}
	reqBytes, err := json.Marshal(req)
	if err != nil {
		return "", "", err
	}

	meta := make(map[string]string)
	for k, v := range attrs {
		if v == "" {
			continue
		}
		switch k {
		case "tool_parameters", "tool_use_id", "tool_name", "tool.name":
			continue
		}
		meta[k] = v
	}
	respBytes, err := json.Marshal(map[string]interface{}{"body": meta})
	if err != nil {
		return "", "", err
	}
	return string(reqBytes), string(respBytes), nil
}

func jsonRawOrString(s string) interface{} {
	var v interface{}
	if json.Unmarshal([]byte(s), &v) == nil {
		return v
	}
	return s
}

// parseMCPToolName recognizes Claude's mcp__{server}__{tool} convention.
func parseMCPToolName(toolName string) (server, tool string) {
	if !strings.HasPrefix(toolName, "mcp__") {
		return "", ""
	}
	parts := strings.Split(toolName, "__")
	if len(parts) < 3 {
		return "", ""
	}
	server = parts[1]
	tool = strings.Join(parts[2:], "__")
	if server == "" || tool == "" {
		return "", ""
	}
	return server, tool
}

func normalizeToolPathName(toolName string) string {
	s := strings.TrimSpace(toolName)
	if s == "" {
		s = "unknown"
	}
	s = slugSanitizer.ReplaceAllString(strings.ToLower(s), "-")
	s = strings.Trim(s, "-")
	if s == "" {
		s = "unknown"
	}
	return url.PathEscape(s)
}

func hookNameFromEvent(eventName string) string {
	const prefix = "claude_code."
	if strings.HasPrefix(eventName, prefix) {
		return strings.TrimPrefix(eventName, prefix)
	}
	return eventName
}

func deriveDevicePrefix(attrs map[string]string) string {
	if id := strutil.FirstNonEmpty(attrs, "user.account_uuid", "user_account_uuid"); id != "" {
		return truncateDevicePrefix(id)
	}
	if id := strutil.FirstNonEmpty(attrs, "user.account_id", "user_account_id"); id != "" {
		return truncateDevicePrefix(strings.TrimPrefix(id, "user_"))
	}
	if id := attrs["user.id"]; id != "" {
		return truncateDevicePrefix(id)
	}
	if sid := attrs["session.id"]; sid != "" {
		return truncateDevicePrefix(sid)
	}
	return "cowork"
}

func deriveIngestHost(devicePrefix string) string {
	return devicePrefix + ".ai-agent." + connectorClaudeCowork
}

// deriveMCPHost mirrors shield mcp_mirror_host: {device}.{connector}.{mcpServer}.
func deriveMCPHost(devicePrefix, mcpServer string) string {
	return devicePrefix + "." + connectorClaudeCowork + "." + mcpServer
}

func truncateDevicePrefix(id string) string {
	for _, sep := range []string{".", "-"} {
		if idx := strings.Index(id, sep); idx > 0 {
			seg := id[:idx]
			if len(seg) <= maxDevicePrefixLen {
				return seg
			}
		}
	}
	if len(id) > maxDevicePrefixLen {
		return id[:maxDevicePrefixLen]
	}
	return id
}

func intAttr(attrs map[string]string, key string) int {
	v := attrs[key]
	if v == "" {
		return 0
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0
	}
	return n
}

// anthropicMessagesResponse shapes ES/dashboard parsers (model, content, usage).
func anthropicMessagesResponse(model, text string, inputTokens, outputTokens int) (string, error) {
	payload := map[string]interface{}{}
	if model != "" {
		payload["model"] = model
	}
	if text != "" {
		payload["content"] = []map[string]string{{"type": "text", "text": text}}
	}
	usage := map[string]int{}
	if inputTokens > 0 {
		usage["input_tokens"] = inputTokens
	}
	if outputTokens > 0 {
		usage["output_tokens"] = outputTokens
	}
	if len(usage) > 0 {
		payload["usage"] = usage
	}
	if len(payload) == 0 {
		return "{}", nil
	}
	b, err := json.Marshal(payload)
	return string(b), err
}

// normalizeOsType maps Cowork OTLP os.type to shield module heartbeat values (mac/windows/linux).
func normalizeOsType(osType string) string {
	switch strings.ToLower(strings.TrimSpace(osType)) {
	case "darwin", "macos", "mac":
		return "mac"
	case "windows", "win32", "win":
		return "windows"
	case "linux":
		return "linux"
	default:
		return ""
	}
}
