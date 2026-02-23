package handlers

import (
	"fmt"
	"net/http"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"github.com/akto-api-security/guardrails-service/pkg/slack"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/akto-api-security/mcp-endpoint-shield/mcp"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

// ValidationHandler handles validation requests
type ValidationHandler struct {
	validatorService *validator.Service
	logger           *zap.Logger
}

// NewValidationHandler creates a new validation handler
func NewValidationHandler(validatorService *validator.Service, logger *zap.Logger) *ValidationHandler {
	return &ValidationHandler{
		validatorService: validatorService,
		logger:           logger,
	}
}

// IngestData handles batch data ingestion and validation
// Similar to IngestionAction.ingestData() in mini-runtime-service
func (h *ValidationHandler) IngestData(c *gin.Context) {
	var req models.ValidationRequest

	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.Error("Failed to parse request", zap.Error(err))
		c.JSON(http.StatusBadRequest, models.ValidationResponse{
			Success: false,
			Result:  "ERROR",
			Errors:  []string{"Invalid request format: " + err.Error()},
		})
		return
	}

	h.logger.Debug("IngestData - received contextSource from request",
		zap.String("contextSource", req.ContextSource),
		zap.Int("batchSize", len(req.BatchData)))

	h.logger.Info("Received batch data",
		zap.Int("size", len(req.BatchData)),
		zap.String("contextSource", req.ContextSource))

	// Default skipThreat to false if not provided
	skipThreat := false
	if req.SkipThreat != nil {
		skipThreat = *req.SkipThreat
	}

	// Validate the batch with optional contextSource and skipThreat
	results, err := h.validatorService.ValidateBatch(c.Request.Context(), req.BatchData, req.ContextSource, skipThreat)
	if err != nil {
		h.logger.Error("Failed to validate batch", zap.Error(err))
		c.JSON(http.StatusInternalServerError, models.ValidationResponse{
			Success: false,
			Result:  "ERROR",
			Errors:  []string{"Validation failed: " + err.Error()},
		})
		return
	}

	// Check if any validation failed
	hasBlockedRequests := false
	hasBlockedResponses := false
	for _, result := range results {
		if !result.RequestAllowed {
			hasBlockedRequests = true
		}
		if !result.ResponseAllowed {
			hasBlockedResponses = true
		}
	}

	if hasBlockedRequests || hasBlockedResponses {
		h.logger.Warn("Some payloads were blocked",
			zap.Bool("hasBlockedRequests", hasBlockedRequests),
			zap.Bool("hasBlockedResponses", hasBlockedResponses))
	}

	// Return success response with validation results
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"result":  "SUCCESS",
		"results": results,
	})
}

// ValidateRequest validates a single request payload
func (h *ValidationHandler) ValidateRequest(c *gin.Context) {
	var req models.ValidateRequestParams

	if err := c.ShouldBindJSON(&req); err != nil {
		h.logger.Error("ValidateRequest - invalid request format", zap.Error(err))
		alertMsg := fmt.Sprintf("[guardrails] /validate/request - invalid request format: %s", err.Error())
		slack.SendAlert(h.logger, alertMsg)
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid request format",
		})
		return
	}

	// Extract session and request IDs from headers
	sessionID, requestID := session.ExtractSessionIDsFromRequest(c.Request)

	h.logger.Debug("ValidateRequest - received request params",
		zap.String("contextSource", req.ContextSource),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.String("ip", req.IP),
		zap.String("destIp", req.DestIP),
		zap.String("path", req.Path),
		zap.String("method", req.Method),
		zap.String("statusCode", req.StatusCode),
		zap.String("source", req.Source),
		zap.String("direction", req.Direction),
		zap.String("tag", req.Tag),
		zap.String("aktoAccountId", req.AktoAccountID),
		zap.String("aktoVxlanId", req.AktoVxlanID),
		zap.Bool("hasRequestHeaders", req.RequestHeaders != ""),
		zap.Bool("hasResponseHeaders", req.ResponseHeaders != ""),
		zap.Bool("hasResponsePayload", req.ResponsePayload != ""),
		zap.Bool("hasMetadata", req.Metadata != ""))

	result, err := h.validatorService.ValidateRequest(c.Request.Context(), &req, sessionID, requestID)
	if err != nil {
		h.logger.Error("ValidateRequest failed",
			zap.String("path", req.Path),
			zap.String("method", req.Method),
			zap.String("account", req.AktoAccountID),
			zap.String("contextSource", req.ContextSource),
			zap.String("sessionID", sessionID),
			zap.Error(err))
		alertMsg := fmt.Sprintf("[guardrails] /validate/request failed - path: %s, method: %s, account: %s, contextSource: %s, sessionID: %s, error: %s",
			req.Path, req.Method, req.AktoAccountID, req.ContextSource, sessionID, err.Error())
		slack.SendAlert(h.logger, alertMsg)
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Validation failed",
		})
		return
	}

	c.JSON(http.StatusOK, result)
}

// ValidateRequestWithPolicy validates a single request payload with a provided policy (for playground testing)
func (h *ValidationHandler) ValidateRequestWithPolicy(c *gin.Context) {
	var req struct {
		Payload       string                 `json:"payload" binding:"required"`
		ContextSource string                 `json:"contextSource,omitempty"` // Optional context source
		SkipThreat    *bool                  `json:"skipThreat,omitempty"`    // Optional: skip threat reporting to TBS (default: false)
		Policy        *mcp.GuardrailsPolicy  `json:"policy" binding:"required"` // Required: policy for playground testing
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid request format",
		})
		return
	}

	// Extract session and request IDs from headers
	sessionID, requestID := session.ExtractSessionIDsFromRequest(c.Request)

	// Default skipThreat to false if not provided
	skipThreat := false
	if req.SkipThreat != nil {
		skipThreat = *req.SkipThreat
	}

	result, err := h.validatorService.ValidateRequestWithPolicy(
		c.Request.Context(),
		req.Payload,
		req.ContextSource,
		sessionID,
		requestID,
		skipThreat,
		req.Policy,
	)
	if err != nil {
		h.logger.Error("Failed to validate request with policy", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Validation failed",
		})
		return
	}

	c.JSON(http.StatusOK, result)
}

// ValidateResponse validates a single response payload
func (h *ValidationHandler) ValidateResponse(c *gin.Context) {
	var req struct {
		Payload       string `json:"payload" binding:"required"`
		ContextSource string `json:"contextSource,omitempty"` // Optional context source
		SkipThreat    *bool  `json:"skipThreat,omitempty"`    // Optional: skip threat reporting to TBS (default: false)
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "Invalid request format",
		})
		return
	}

	// Extract session and request IDs from headers
	sessionID, requestID := session.ExtractSessionIDsFromRequest(c.Request)

	// Default skipThreat to false if not provided
	skipThreat := false
	if req.SkipThreat != nil {
		skipThreat = *req.SkipThreat
	}

	h.logger.Debug("ValidateResponse - received contextSource from request",
		zap.String("contextSource", req.ContextSource),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))

	result, err := h.validatorService.ValidateResponse(c.Request.Context(), req.Payload, req.ContextSource, sessionID, requestID, skipThreat)
	if err != nil {
		h.logger.Error("Failed to validate response", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "Validation failed",
		})
		return
	}

	c.JSON(http.StatusOK, result)
}

// HealthCheck handles health check requests
func (h *ValidationHandler) HealthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"success": true,
		"status":  "healthy",
	})
}
