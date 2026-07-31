package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

const (
	copilotErrCodeInvalidRequest = 4001
	copilotErrCodeInternal       = 5000

	// copilot studio's max wait time is 1000ms
	analyzeToolExecutionTimeout = 950 * time.Millisecond
)

type CopilotStudioHandler struct {
	validatorService *validator.Service
	logger           *zap.Logger
}

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

// AnalyzeToolExecution handles POST /api/v1/protection — evaluates a
// planned tool call and returns an allow/block verdict.
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

	params := buildValidateParamsFromEvaluationRequest(&req)
	applyAuthenticatedAccount(c, params)

	sessionID, requestID := session.ExtractSessionIDsFromRequest(c.Request, "")

	h.logger.Info("AnalyzeToolExecution - received request",
		zap.String("toolName", req.ToolDefinition.Name),
		zap.String("account", params.AktoAccountID),
		zap.String("conversationId", req.ConversationMetadata.ConversationID),
		zap.String("sessionID", sessionID))

	ctx, cancel := context.WithTimeout(c.Request.Context(), analyzeToolExecutionTimeout)
	defer cancel()

	result, err := h.validatorService.ValidateRequest(ctx, params, sessionID, requestID)
	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			// Mirrors Copilot Studio's own documented fallback: a missed
			// deadline is treated as "allow", so answer that way ourselves
			// rather than letting the caller's client-side timeout do it.
			h.logger.Warn("AnalyzeToolExecution - validation timed out, failing open",
				zap.String("toolName", req.ToolDefinition.Name),
				zap.Int64("latencyMs", time.Since(start).Milliseconds()))
			c.JSON(http.StatusOK, models.AnalyzeToolExecutionResponse{BlockAction: false})
			return
		}
		h.logger.Error("AnalyzeToolExecution - validation failed",
			zap.String("toolName", req.ToolDefinition.Name),
			zap.Error(err))
		c.JSON(http.StatusInternalServerError, models.WebhookErrorResponse{
			ErrorCode:  copilotErrCodeInternal,
			Message:    "Validation failed",
			HTTPStatus: http.StatusInternalServerError,
		})
		return
	}

	resp := mapValidationResultToResponse(result)
	h.logger.Info("AnalyzeToolExecution - completed",
		zap.String("toolName", req.ToolDefinition.Name),
		zap.Bool("blockAction", resp.BlockAction),
		zap.Int64("latencyMs", time.Since(start).Milliseconds()))

	c.JSON(http.StatusOK, resp)
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
	if json.Valid(body) {
		return zap.Any("body", json.RawMessage(body))
	}
	return zap.ByteString("body", body)
}

func buildValidateParamsFromEvaluationRequest(req *models.EvaluationRequest) *models.ValidateRequestParams {
	return &models.ValidateRequestParams{
		Path:           "/copilot/conversation/messages/" + req.ConversationMetadata.ConversationID,
		Method:         http.MethodPost,
		RequestHeaders: marshalHostHeader(copilotStudioHost(req)),
		RequestPayload: marshalPromptPayload(toolExecutionContent(req)),
		ContextSource:  string(types.ContextSourceEndpoint),
		Source:         "copilot-studio",
	}
}

// marshalHostHeader wraps a Host value in the same {"Host": ...} headers JSON
// shape handlers/file_validation.go already builds for validationContextFromParams
// to extract McpServerName from. Returns "" when host is empty.
func marshalHostHeader(host string) string {
	if host == "" {
		return ""
	}
	b, err := json.Marshal(map[string]string{"Host": host})
	if err != nil {
		return ""
	}
	return string(b)
}

var botNameJunkRegex = regexp.MustCompile(`[^\p{L}\p{N}-]+`)
var botNameHyphenRegex = regexp.MustCompile(`-+`)

func sanitizeBotName(name string) string {
	if name == "" {
		return ""
	}
	sanitized := botNameJunkRegex.ReplaceAllString(name, "-")
	sanitized = botNameHyphenRegex.ReplaceAllString(sanitized, "-")
	return strings.Trim(sanitized, "-")
}

func copilotStudioHost(req *models.EvaluationRequest) string {
	agentName := sanitizeBotName(req.ConversationMetadata.Agent.ID)
	if agentName == "" {
		return ""
	}

	host := agentName + ".copilot-studio"
	if envID := req.ConversationMetadata.Agent.EnvironmentID; envID != "" {
		envName := sanitizeBotName(envID)
		if runes := []rune(envName); len(runes) > 10 {
			envName = string(runes[:10])
		}
		if envName != "" {
			host += "-" + envName
		}
	}
	return host + ".microsoft.com"
}

func toolExecutionContent(req *models.EvaluationRequest) string {
	var b strings.Builder

	writeLine := func(s string) {
		if s == "" {
			return
		}
		if b.Len() > 0 {
			b.WriteByte('\n')
		}
		b.WriteString(s)
	}

	writeLine(req.PlannerContext.UserMessage)
	writeLine(req.PlannerContext.Thought)
	for _, msg := range req.PlannerContext.ChatHistory {
		writeLine(msg.Role + ": " + msg.Content)
	}
	for _, out := range req.PlannerContext.PreviousToolsOutputs {
		for _, o := range out.Outputs {
			if s, ok := o.Value.(string); ok {
				writeLine(s)
			}
		}
	}
	writeLine(req.ToolDefinition.Name + ": " + req.ToolDefinition.Description)
	for name, value := range req.InputValues {
		if s, ok := value.(string); ok {
			writeLine(name + ": " + s)
		}
	}

	return b.String()
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
