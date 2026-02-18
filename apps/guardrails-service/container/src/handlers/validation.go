package handlers

import (
	"net/http"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/session"
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

	result, err := h.validatorService.ValidateRequest(c.Request.Context(), req.Payload, req.ContextSource, sessionID, requestID, skipThreat)
	if err != nil {
		h.logger.Error("Failed to validate request", zap.Error(err))
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
