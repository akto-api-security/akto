package sink

import (
	"encoding/json"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/model"
)

var slugSanitizer = regexp.MustCompile(`[^a-z0-9._~-]+`)

const (
	connectorClaudeCowork = "claude_cowork"
	guardrailModeObserve  = "observe"
	ingestSourceMirroring = "MIRRORING"
	atlasSourceEndpoint   = "ENDPOINT"
	aiAgentTagKey         = "ai-agent"
	coworkHookHeader      = "x-claude_cowork-hook"
	installerHeaderPrefix = "x-akto-installer-"
	maxDevicePrefixLen    = 12
	toolPathPrefix        = "/tool"
	llmMessagesPath       = "/v1/messages"
	mcpIngestPath         = "/mcp"
)

type ingestDataRecord struct {
	Path                string `json:"path"`
	RequestHeaders      string `json:"requestHeaders"`
	ResponseHeaders     string `json:"responseHeaders"`
	Method              string `json:"method"`
	RequestPayload      string `json:"requestPayload"`
	ResponsePayload     string `json:"responsePayload"`
	IP                  string `json:"ip"`
	DestIP              string `json:"destIp"`
	Time                string `json:"time"`
	StatusCode          string `json:"statusCode"`
	Type                string `json:"type"`
	Status              string `json:"status"`
	AktoAccountID       string `json:"akto_account_id"`
	AktoVXLANID         string `json:"akto_vxlan_id"`
	IsPending           string `json:"is_pending"`
	Source              string `json:"source"`
	Direction           string `json:"direction,omitempty"`
	ProcessID           string `json:"process_id,omitempty"`
	SocketID            string `json:"socket_id,omitempty"`
	DaemonsetID         string `json:"daemonset_id,omitempty"`
	EnabledGraph        string `json:"enabled_graph,omitempty"`
	Tag                 string `json:"tag"`
	PublishToGuardrails bool   `json:"publishToGuardrails"`
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

		promptID := firstNonEmptyAttr(e.Attributes, "prompt.id", "prompt_id")
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
			if v := firstNonEmptyAttr(e.Attributes, "prompt"); v != "" {
				turn.prompt = v
			}
			if turn.base.Timestamp.IsZero() || e.Timestamp.Before(turn.base.Timestamp) {
				turn.base = e
			}
		case "assistant_response":
			if v := firstNonEmptyAttr(e.Attributes, "response"); v != "" {
				turn.response = v
			}
			if v := firstNonEmptyAttr(e.Attributes, "model"); v != "" {
				turn.model = v
			}
			if !e.Timestamp.IsZero() {
				turn.base = e
			}
		case "api_request":
			if v := firstNonEmptyAttr(e.Attributes, "model"); v != "" {
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
	promptID := firstNonEmptyAttr(e.Attributes, "prompt.id", "prompt_id")
	sessionID := e.Attributes["session.id"]
	userEmail := firstNonEmptyAttr(e.Attributes, "user.email", "user_email")

	toolName := toolNameFromEvent(e.EventName, e.Attributes)
	mcpServer, mcpTool := parseMCPToolName(toolName)
	isMCP := mcpServer != "" && mcpTool != ""

	// Shield parity: MCP tools land on {device}.{connector}.{mcpServer};
	// agent-owned traffic (prompts, built-in tools, etc.) on {device}.ai-agent.{connector}.
	host := deriveIngestHost(devicePrefix)
	if isMCP {
		host = deriveMCPHost(devicePrefix, mcpServer)
	}

	tagObj := map[string]string{
		"gen-ai":         "Gen AI",
		"source":         atlasSourceEndpoint,
		"hook":           hookName,
		"akto_connector": connectorClaudeCowork,
		"mode":           guardrailModeObserve,
	}
	if isMCP {
		tagObj["mcp-server"] = "MCP Server"
		tagObj["mcp-client"] = connectorClaudeCowork
	} else {
		tagObj[aiAgentTagKey] = connectorClaudeCowork
	}
	if promptID != "" {
		tagObj["prompt_id"] = promptID
	}
	if sessionID != "" {
		tagObj["session_id"] = sessionID
	}
	// Username for Agentic UI: also registered as collection envType tags via register_cowork_collections.py
	if userEmail != "" {
		tagObj["username"] = userEmail
	}
	if osName := normalizeOsType(firstNonEmptyAttr(e.Attributes, "os.type", "os_type")); osName != "" {
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
		Path:                path,
		RequestHeaders:      string(reqHeaders),
		ResponseHeaders:     "{}",
		Method:              "POST",
		RequestPayload:      reqPayload,
		ResponsePayload:     respPayload,
		IP:                  ip,
		DestIP:              "",
		Time:                strconv.FormatInt(ts.Unix(), 10),
		StatusCode:          "200",
		Type:                "HTTP/1.1",
		Status:              "OK",
		AktoAccountID:       strconv.Itoa(e.AccountID),
		AktoVXLANID:         devicePrefix,
		IsPending:           "false",
		Source:              ingestSourceMirroring,
		Tag:                 string(tagJSON),
		PublishToGuardrails: true,
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
		promptText := firstNonEmptyAttr(e.Attributes, "prompt")
		responseText := firstNonEmptyAttr(e.Attributes, "response")
		model := firstNonEmptyAttr(e.Attributes, "model")
		inputTok := intAttr(e.Attributes, "input_tokens")
		outputTok := intAttr(e.Attributes, "output_tokens")

		reqBody := promptText
		if reqBody == "" && hookName == "user_prompt" {
			reqBody = firstNonEmptyAttr(e.Attributes, "prompt_length")
		}
		reqBytes, err := json.Marshal(map[string]string{"body": reqBody})
		if err != nil {
			return "", "", "", err
		}
		reqPayload = string(reqBytes)

		if hookName == "assistant_response" || responseText != "" || model != "" || inputTok > 0 || outputTok > 0 {
			respPayload, err = anthropicMessagesResponse(model, responseText, inputTok, outputTok)
			if err != nil {
				return "", "", "", err
			}
		}
		return path, string(reqPayload), respPayload, nil

	case "tool_decision", "tool_result":
		// Shield: non-MCP → /tool/{name}; MCP → /mcp + JSON-RPC tools/call.
		if isMCP {
			path = mcpIngestPath
			rpc, mErr := json.Marshal(map[string]interface{}{
				"jsonrpc": "2.0",
				"method":  "tools/call",
				"params": map[string]interface{}{
					"name":      mcpTool,
					"arguments": e.Attributes,
				},
				"id": 1,
			})
			if mErr != nil {
				return "", "", "", mErr
			}
			return path, string(rpc), respPayload, nil
		}
		normalized := normalizeToolPathName(toolName)
		path = toolPathPrefix + "/" + normalized
		toolBody := builtinToolPayloadBody(e.Attributes)
		payload, mErr := json.Marshal(map[string]interface{}{
			"body":     toolBody,
			"toolName": toolName,
		})
		if mErr != nil {
			return "", "", "", mErr
		}
		reqPayload = string(payload)
		if hookName == "tool_result" {
			respBody := builtinToolResponseBody(e.Attributes)
			respJSON, mErr := json.Marshal(map[string]interface{}{"body": map[string]interface{}{"result": respBody}})
			if mErr != nil {
				return "", "", "", mErr
			}
			return path, reqPayload, string(respJSON), nil
		}
		return path, reqPayload, respPayload, nil

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
	if email := firstNonEmptyAttr(attrs, "user.email", "user_email"); email != "" {
		headers[installerHeaderPrefix+"user_email"] = email
	}
	if osName := normalizeOsType(firstNonEmptyAttr(attrs, "os.type", "os_type")); osName != "" {
		headers[installerHeaderPrefix+"os"] = osName
	}
	if osVer := firstNonEmptyAttr(attrs, "os.version", "os_version"); osVer != "" {
		headers[installerHeaderPrefix+"os_version"] = osVer
	}
	if arch := firstNonEmptyAttr(attrs, "host.arch", "host_arch"); arch != "" {
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
	return firstNonEmptyAttr(attrs, "tool_name", "tool.name")
}

// builtinToolPayloadBody mirrors shield pre-tool shape: {"body": <input>, "toolName": ...}.
func builtinToolPayloadBody(attrs map[string]string) interface{} {
	if raw := firstNonEmptyAttr(attrs, "tool_input", "tool.input", "tool_args"); raw != "" {
		return jsonRawOrString(raw)
	}
	return attrs
}

// builtinToolResponseBody mirrors shield post-tool shape: {"body": {"result": <output>}}.
func builtinToolResponseBody(attrs map[string]string) interface{} {
	if raw := firstNonEmptyAttr(attrs, "tool_response", "tool.response", "tool_output"); raw != "" {
		return jsonRawOrString(raw)
	}
	return attrs
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

func firstNonEmptyAttr(attrs map[string]string, keys ...string) string {
	for _, k := range keys {
		if v := attrs[k]; v != "" {
			return v
		}
	}
	return ""
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
