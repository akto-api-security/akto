package handlers

import (
	"errors"
	"net/http"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

const (
	maxRedactIDLen   = 128
	maxRedactHostLen = 255
	maxRedactPathLen = 4096
	// redactBodyOverhead is the allowance for JSON framing and non-payload fields on top of the
	// payloads themselves when sizing the request body cap.
	redactBodyOverhead = 1 << 20
)

// RedactPayloads handles POST /api/redact/payloads: it masks the payloads of a batch of endpoint
// calls with the account's guardrail redact rules and returns one explicit status per payload
// side. See validator.Service.RedactPayloads for the statuses.
func (h *ValidationHandler) RedactPayloads(c *gin.Context) {
	// Sized from the configured caps so one call cannot make the service buffer more than
	// RedactMaxItems payload pairs.
	bodyLimit := int64(h.cfg.RedactMaxItems)*int64(2*h.cfg.RedactMaxPayloadBytes) + redactBodyOverhead
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, bodyLimit)

	var req models.RedactPayloadsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "Request body too large"})
			return
		}
		h.logger.Warn("RedactPayloads - invalid request format", zap.Error(err))
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request format"})
		return
	}
	if msg := h.validateRedactRequest(&req); msg != "" {
		h.logger.Warn("RedactPayloads - rejected request", zap.String("reason", msg), zap.Int("items", len(req.Items)))
		c.JSON(http.StatusBadRequest, gin.H{"error": msg})
		return
	}
	if h.validatorService == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "Redaction unavailable"})
		return
	}

	c.JSON(http.StatusOK, models.RedactPayloadsResponse{
		Items: h.validatorService.RedactPayloads(c.Request.Context(), &req),
	})
}

// validateRedactRequest returns why the request is unacceptable, or "" when it is fine. An
// oversize payload is not a rejection: that side is answered UNAVAILABLE.
func (h *ValidationHandler) validateRedactRequest(req *models.RedactPayloadsRequest) string {
	if len(req.Items) == 0 {
		return "items must not be empty"
	}
	if len(req.Items) > h.cfg.RedactMaxItems {
		return "too many items"
	}
	seen := make(map[string]struct{}, len(req.Items))
	for _, item := range req.Items {
		switch {
		case item.ID == "" || len(item.ID) > maxRedactIDLen:
			return "every item needs an id of at most 128 characters"
		case len(item.Host) > maxRedactHostLen:
			return "host too long"
		case len(item.Path) > maxRedactPathLen:
			return "path too long"
		}
		if _, dup := seen[item.ID]; dup {
			return "item ids must be unique"
		}
		seen[item.ID] = struct{}{}
	}
	return ""
}
