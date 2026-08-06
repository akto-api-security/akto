package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/akto-endpoint-shield/utils"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

const copilotErrCodeInvalidRequest = 4001

type CopilotStudioHandler struct {
	validatorService *validator.Service
	logger           *zap.Logger
}

var botNameJunkRegex = regexp.MustCompile(`[^\p{L}\p{N}-]+`)
var botNameHyphenRegex = regexp.MustCompile(`-+`)
var nonAlphanumericRegex = regexp.MustCompile(`[^a-zA-Z0-9]`)

func NewCopilotStudioHandler(validatorService *validator.Service, logger *zap.Logger) *CopilotStudioHandler {
	return &CopilotStudioHandler{
		validatorService: validatorService,
		logger:           logger,
	}
}

// Validate handles POST /api/v1/health — Copilot Studio's setup/readiness check.
func (h *CopilotStudioHandler) Validate(c *gin.Context) {
	logRawRequest(c, h.logger, "Validate")

	c.JSON(http.StatusOK, models.CopilotValidationResponse{
		IsSuccessful: true,
		Status:       "OK",
	})
}

// AnalyzeToolExecution handles POST /api/v1/protection — validates the user
// message and the planned tool invocation concurrently, and returns a merged
// allow/block verdict.
func (h *CopilotStudioHandler) AnalyzeToolExecution(c *gin.Context) {
	logRawRequest(c, h.logger, "AnalyzeToolExecution")

	start := time.Now()

	var req models.EvaluationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.Warn("AnalyzeToolExecution - invalid request format", zap.Error(err))
		c.JSON(http.StatusBadRequest, models.WebhookErrorResponse{
			ErrorCode:  copilotErrCodeInvalidRequest,
			Message:    "Invalid request format: " + err.Error(),
			HTTPStatus: http.StatusBadRequest,
		})
		return
	}

	userMsgParams := buildUserMessageParams(&req, c.Request.Header)
	toolParams, err := buildToolInvocationParams(&req, c.Request.Header)
	if err != nil {
		h.logger.Error("AnalyzeToolExecution - failed to build tool invocation payload, failing open",
			zap.String("toolName", req.ToolDefinition.Name), zap.Error(err))
		c.JSON(http.StatusOK, &models.AnalyzeToolExecutionResponse{BlockAction: false})
		return
	}

	applyAuthenticatedAccount(c, userMsgParams)
	applyAuthenticatedAccount(c, toolParams)

	// sessionID, requestID := session.ExtractSessionIDsFromRequest(c.Request, userMsgParams.RequestHeaders)
	sessionID := ""
	requestID := ""
	h.logger.Info("AnalyzeToolExecution - received request",
		zap.String("toolName", req.ToolDefinition.Name),
		zap.String("account", userMsgParams.AktoAccountID),
		zap.String("conversationId", req.ConversationMetadata.ConversationID),
		zap.String("sessionID", sessionID))

	ctx, cancel := context.WithCancel(c.Request.Context())
	defer cancel()

	results := make(chan validationOutcome, 2)
	go func() {
		resp, err := h.runValidation(ctx, userMsgParams, sessionID, requestID)
		results <- validationOutcome{label: "userMessage", resp: resp, err: err}
	}()
	go func() {
		resp, err := h.runValidation(ctx, toolParams, sessionID, requestID)
		results <- validationOutcome{label: "toolInvocation", resp: resp, err: err}
	}()

	for i := 0; i < 2; i++ {
		out := <-results
		if out.err != nil {
			cancel()
			h.logger.Error("AnalyzeToolExecution - validation failed, failing open",
				zap.String("toolName", req.ToolDefinition.Name), zap.Error(out.err))
			c.JSON(http.StatusOK, &models.AnalyzeToolExecutionResponse{BlockAction: false})
			return
		}
		if out.resp.BlockAction {
			cancel()
			resp := labelBlockedResponse(out.label, out.resp)
			h.logger.Info("AnalyzeToolExecution - completed",
				zap.String("toolName", req.ToolDefinition.Name),
				zap.Bool("blockAction", true),
				zap.Int64("latencyMs", time.Since(start).Milliseconds()))
			c.JSON(http.StatusOK, resp)
			return
		}
	}

	h.logger.Info("AnalyzeToolExecution - completed",
		zap.String("toolName", req.ToolDefinition.Name),
		zap.Bool("blockAction", false),
		zap.Int64("latencyMs", time.Since(start).Milliseconds()))
	c.JSON(http.StatusOK, &models.AnalyzeToolExecutionResponse{BlockAction: false})
}

type validationOutcome struct {
	label string
	resp  *models.AnalyzeToolExecutionResponse
	err   error
}

func (h *CopilotStudioHandler) runValidation(ctx context.Context, params *models.ValidateRequestParams, sessionID, requestID string) (*models.AnalyzeToolExecutionResponse, error) {
	h.logger.Info("runValidation - params", zap.Any("params", params))
	result, err := h.validatorService.ValidateRequest(ctx, params, sessionID, requestID)
	if err != nil {
		return nil, err
	}
	return mapValidationResultToResponse(result), nil
}

func labelBlockedResponse(label string, resp *models.AnalyzeToolExecutionResponse) *models.AnalyzeToolExecutionResponse {
	labeled := &models.AnalyzeToolExecutionResponse{
		BlockAction: true,
		Reason:      label + ": " + resp.Reason,
	}
	if resp.Diagnostics != "" {
		diagnostics := map[string]json.RawMessage{label: json.RawMessage(resp.Diagnostics)}
		if b, err := json.Marshal(diagnostics); err == nil {
			labeled.Diagnostics = string(b)
		}
	}
	return labeled
}

func logRawRequest(c *gin.Context, logger *zap.Logger, label string) {
	body, _ := io.ReadAll(c.Request.Body)
	c.Request.Body = io.NopCloser(bytes.NewReader(body))

	headers := make(map[string][]string, len(c.Request.Header))
	for k, v := range c.Request.Header {
		if strings.EqualFold(k, "Authorization") {
			headers[k] = []string{"REDACTED"}
			continue
		}
		headers[k] = v
	}

	logger.Info(label+" - raw request",
		zap.String("method", c.Request.Method),
		zap.String("path", c.Request.URL.Path),
		zap.String("query", c.Request.URL.RawQuery),
		zap.Any("headers", headers),
		rawBodyField(body))
}

func rawBodyField(body []byte) zap.Field {
	var parsed interface{}
	if err := json.Unmarshal(body, &parsed); err == nil {
		return zap.Any("body", parsed)
	}
	return zap.ByteString("body", body)
}

// buildUserMessageParams validates plannerContext.userMessage alone.
func buildUserMessageParams(req *models.EvaluationRequest, header http.Header) *models.ValidateRequestParams {
	agent := req.ConversationMetadata.Agent
	agentName := agentDisplayName(agent)

	return &models.ValidateRequestParams{
		Path:           "/copilot/conversation/messages/" + req.ConversationMetadata.ConversationID,
		Method:         http.MethodPost,
		RequestHeaders: buildRequestHeaders(header, copilotStudioHost(req), req.ConversationMetadata.ConversationID),
		RequestPayload: marshalPromptPayload(req.PlannerContext.UserMessage),
		ContextSource:  string(types.ContextSourceEndpoint),
		Source:         "copilot-studio",
		IP:             clientIPFromHeaders(header),
		Tag:            buildCopilotStudioTag(agentName, agent.EnvironmentID, false),
	}
}

// buildToolInvocationParams validates the tool call itself: name + actual
// arguments, as an MCP tools/call request when the tool is MCP-backed,
// otherwise the raw inputValues JSON.
func buildToolInvocationParams(req *models.EvaluationRequest, header http.Header) (*models.ValidateRequestParams, error) {
	agent := req.ConversationMetadata.Agent
	agentName := agentDisplayName(agent)
	isMcp := isMcpTool(req.ToolDefinition)

	host := copilotStudioHost(req)
	if isMcp {
		host = copilotStudioMcpHost(req, mcpServerNameFromToolDefinition(req.ToolDefinition))
	}

	payload, err := toolInvocationPayload(req.ToolDefinition, req.InputValues, isMcp)
	if err != nil {
		return nil, err
	}

	path := "/copilot/tool/" + url.PathEscape(strings.ToLower(req.ToolDefinition.Name))
	if isMcp {
		path = "/copilot/mcp"
	}

	return &models.ValidateRequestParams{
		Path:           path,
		Method:         http.MethodPost,
		RequestHeaders: buildRequestHeaders(header, host, req.ConversationMetadata.ConversationID),
		RequestPayload: payload,
		ContextSource:  string(types.ContextSourceEndpoint),
		Source:         "copilot-studio",
		IP:             clientIPFromHeaders(header),
		Tag:            buildCopilotStudioTag(agentName, agent.EnvironmentID, isMcp),
	}, nil
}

// toolInvocationPayload builds the RequestPayload for the tool-invocation
// check: an MCP tools/call JSON-RPC request when the tool is MCP-backed
// (params.name/params.arguments, matching the wire shape the vendored mcp
// package's own extractFromParams already expects), otherwise the raw
// inputValues JSON directly (structured data, not the free-text "prompt"
// convention).
func toolInvocationPayload(t models.ToolDefinition, inputValues map[string]interface{}, isMcp bool) (string, error) {
	if !isMcp {
		b, err := json.Marshal(inputValues)
		return string(b), err
	}

	payload := map[string]interface{}{
		"jsonrpc":       "2.0",
		"id":            1,
		utils.MCPMethod: utils.MCPToolCall,
		utils.MCPParams: map[string]interface{}{
			"name":      mcpOperationName(t),
			"arguments": inputValues,
		},
	}
	b, err := json.Marshal(payload)
	return string(b), err
}

func isMcpTool(t models.ToolDefinition) bool {
	return t.Type == "DynamicServerToolDefinition"
}

func splitMcpToolID(id string) (server, operation string, ok bool) {
	idx := strings.Index(id, "~")
	if idx < 0 {
		return "", "", false
	}
	return id[:idx], id[idx+1:], true
}

func mcpOperationName(t models.ToolDefinition) string {
	if _, operation, ok := splitMcpToolID(t.ID); ok && operation != "" {
		return operation
	}
	return t.Name
}

func mcpServerNameFromToolDefinition(t models.ToolDefinition) string {
	if _, operation, ok := splitMcpToolID(t.ID); ok && operation != "" && strings.HasSuffix(t.Name, "-"+operation) {
		return strings.TrimSuffix(t.Name, "-"+operation)
	}
	return t.Name
}

func buildRequestHeaders(header http.Header, host, conversationID string) string {
	headers := flattenHeaders(header)
	if host != "" {
		headers["Host"] = host
	}
	if conversationID != "" {
		headers["X-Conversation-Id"] = conversationID
	}
	b, err := json.Marshal(headers)
	if err != nil {
		return ""
	}
	return string(b)
}

// flattenHeaders takes the first value per header key. Authorization is
// redacted: it carries a live Entra ID bearer token, and this value can end
// up embedded in a stored/reported malicious-event payload downstream, not
// just a log line.
func flattenHeaders(h http.Header) map[string]string {
	flat := make(map[string]string, len(h))
	for k, v := range h {
		if len(v) == 0 {
			continue
		}
		if strings.EqualFold(k, "Authorization") {
			flat[k] = "REDACTED"
			continue
		}
		flat[k] = v[0]
	}
	return flat
}

// clientIPFromHeaders extracts the originating client IP from proxy headers:
// the first X-Forwarded-For entry, else X-Real-Ip.
func clientIPFromHeaders(h http.Header) string {
	if xff := h.Get("X-Forwarded-For"); xff != "" {
		if first := strings.TrimSpace(strings.Split(xff, ",")[0]); first != "" {
			return normalizeIP(first)
		}
	}
	return normalizeIP(h.Get("X-Real-Ip"))
}

// normalizeIP unwraps an IPv4-mapped IPv6 address (e.g. "::ffff:14.143.179.162")
// down to its plain IPv4 form. Non-IP or genuinely IPv6 input is returned unchanged.
func normalizeIP(ip string) string {
	parsed := net.ParseIP(ip)
	if parsed == nil {
		return ip
	}
	if v4 := parsed.To4(); v4 != nil {
		return v4.String()
	}
	return ip
}

// buildCopilotStudioTag mirrors the same mutually-exclusive tag convention
// the Claude CLI hook uses (apps/mcp-endpoint-shield/claude-cli-hooks/
// akto-validate-mcp-request.py, build_hook_tags): mcp-server/mcp-client for
// MCP calls, gen-ai/ai-agent otherwise. Not cosmetic — mcp/policy_validator.go's
// allowlistAppliesToRequest checks for the "mcp-server" key to decide whether
// MCP-server-scoped allowlist policies apply.
func buildCopilotStudioTag(agentName, environmentID string, isMcp bool) string {
	tags := map[string]string{
		utils.SourceTag: utils.EndpointSource,
		// "bot-name":           agentName,
		"bot-environment-id": environmentID,
		"ai-agent":           "copilot-studio",
	}
	if isMcp {
		tags["mcp-server"] = "MCP Server"
		tags["mcp-client"] = "copilot-studio"
	} else {
		tags["gen-ai"] = "Gen AI"
		tags[utils.AgentSource] = "copilot-studio"
	}
	b, err := json.Marshal(tags)
	if err != nil {
		return ""
	}
	return string(b)
}

func sanitizeBotName(name string) string {
	if name == "" {
		return ""
	}
	sanitized := botNameJunkRegex.ReplaceAllString(name, "-")
	sanitized = botNameHyphenRegex.ReplaceAllString(sanitized, "-")
	return strings.ToLower(strings.Trim(sanitized, "-"))
}

// agentDisplayName prefers the agent's display name (present on real
// requests, not in Microsoft's documented schema) over its id, sanitized for
// use in a hostname.
func agentDisplayName(agent models.AgentContext) string {
	if name := sanitizeBotName(agent.Name); name != "" {
		return name
	}
	return sanitizeBotName(agent.ID)
}

func copilotStudioHost(req *models.EvaluationRequest) string {
	agent := req.ConversationMetadata.Agent
	agentName := agentDisplayName(agent)
	if agentName == "" {
		return ""
	}

	host := agentName + ".copilot-studio"
	if envSuffix := sanitizeEnvironmentID(agent.EnvironmentID); envSuffix != "" {
		host += "-" + envSuffix
	}
	return host + ".microsoft.com"
}

// copilotStudioMcpHost builds a synthetic Host for MCP tool-invocation
// checks that encodes the specific MCP server instead of the environment,
// mirroring the Claude CLI hook's mcp_mirror_host (device/agent identity +
// connector + the specific server) so McpServerName-scoped policies can
// target this one MCP server.
func copilotStudioMcpHost(req *models.EvaluationRequest, mcpServerName string) string {
	agentName := agentDisplayName(req.ConversationMetadata.Agent)
	if agentName == "" {
		return ""
	}

	host := agentName + ".copilot-studio"
	if serverSuffix := sanitizeBotName(mcpServerName); serverSuffix != "" {
		host += "." + serverSuffix
	}
	return host
}

func sanitizeEnvironmentID(environmentID string) string {
	suffix := nonAlphanumericRegex.ReplaceAllString(environmentID, "")
	if len(suffix) > 10 {
		suffix = suffix[:10]
	}
	return strings.ToLower(suffix)
}

func mapValidationResultToResponse(result *mcp.ValidationResult) *models.AnalyzeToolExecutionResponse {
	resp := &models.AnalyzeToolExecutionResponse{
		BlockAction: !result.Allowed,
	}
	if resp.BlockAction {
		resp.Reason = result.Reason
		if result.Metadata.PolicyName != "" || result.Metadata.RuleViolated != "" {
			if diagnostics, err := json.Marshal(result.Metadata); err == nil {
				resp.Diagnostics = string(diagnostics)
			}
		}
	}
	return resp
}
