package handlers

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

const (
	copilotErrCodeInvalidRequest = 4001
	copilotErrCodeInternal       = 5000

	// analyzeToolExecutionTimeout stays comfortably inside Copilot Studio's
	// documented ~1000ms budget so this handler always answers before the
	// caller's own timeout, rather than racing it.
	analyzeToolExecutionTimeout = 950 * time.Millisecond

	// copilotStudioContextSource reuses the existing "ENDPOINT" content-safety
	// policy set — no new policy type for this integration.
	copilotStudioContextSource = "ENDPOINT"
)

// CopilotStudioHandler implements the Microsoft Copilot Studio external
// security webhook contract (validate + analyze-tool-execution), adapting it
// onto the same validator.Service core the /api/validate/* endpoints use.
// https://learn.microsoft.com/en-us/microsoft-copilot-studio/external-security-webhooks-interface-developers
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
	c.JSON(http.StatusOK, models.CopilotValidationResponse{
		IsSuccessful: true,
		Status:       "OK",
	})
}

// AnalyzeToolExecution handles POST /api/v1/protection — evaluates a
// planned tool call and returns an allow/block verdict.
func (h *CopilotStudioHandler) AnalyzeToolExecution(c *gin.Context) {
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

// buildValidateParamsFromEvaluationRequest adapts Copilot Studio's tool-execution
// shape onto the same models.ValidateRequestParams the rest of this service
// validates — the free-text content Copilot Studio sent is collapsed into a
// single "prompt" payload field (the same convention handlers/file_validation.go
// uses via marshalPromptPayload) so existing content-safety policies apply
// unchanged.
func buildValidateParamsFromEvaluationRequest(req *models.EvaluationRequest) *models.ValidateRequestParams {
	return &models.ValidateRequestParams{
		Path:           req.ToolDefinition.Name,
		Method:         http.MethodPost,
		RequestPayload: marshalPromptPayload(toolExecutionContent(req)),
		ContextSource:  copilotStudioContextSource,
		Source:         "copilot-studio",
	}
}

// toolExecutionContent collapses the free-text fields Copilot Studio sends
// (planner reasoning, chat history, prior tool outputs, and the tool's own
// input values) into a single string for content-safety scanning.
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

// mapValidationResultToResponse narrows the internal engine's verdict down to
// Copilot Studio's small external contract — the same pattern
// handlers/file_validation.go already uses to shrink mcp.ValidationResult down
// to its own {allowed, reason} response, just a different target shape.
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
