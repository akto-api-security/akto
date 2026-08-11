package handlers

import (
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/metrics"
	"github.com/akto-api-security/guardrails-service/pkg/promptguard"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

// referenceIDMaxLen mirrors the spec's cap on a verdict reference_id.
const referenceIDMaxLen = 50

// PromptGuardHandler serves the inbound pre-inference guardrail webhook
// (POST /api/v1/webhooks/:provider/guardrail): an AI provider server (Claude,
// ChatGPT, …) posts a signed frame carrying the conversation transcript, we
// validate it with the shared guardrail validator, and return an allow/deny
// verdict in that provider's format. The frame's own event type (prompt today,
// response-side later) is read by the provider adapter, so one URL serves all
// event types.
//
// Verdicts always go back as HTTP 200 with a JSON body — the provider treats any
// non-200 as a delivery failure (handled by its own failure policy), never as a
// deny — with one exception: a request that fails signature verification is
// rejected with 401 so it is never acted upon.
type PromptGuardHandler struct {
	validatorService *validator.Service
	logger           *zap.Logger
	cfg              *config.Config
	registry         *promptguard.Registry
	metrics          *metrics.Accumulator
}

// NewPromptGuardHandler creates a PromptGuardHandler.
func NewPromptGuardHandler(validatorService *validator.Service, logger *zap.Logger, cfg *config.Config, registry *promptguard.Registry, acc *metrics.Accumulator) *PromptGuardHandler {
	return &PromptGuardHandler{
		validatorService: validatorService,
		logger:           logger,
		cfg:              cfg,
		registry:         registry,
		metrics:          acc,
	}
}

// Guard handles POST /api/v1/webhooks/:provider/guardrail.
func (h *PromptGuardHandler) Guard(c *gin.Context) {
	start := time.Now()
	providerName := c.Param("provider")

	provider, ok := h.registry.Get(providerName)
	if !ok {
		// Unknown :provider is our own routing concern (a misconfigured URL), not
		// a provider event, so a 404 is appropriate rather than a verdict.
		h.logger.Warn("PromptGuard - unknown provider", zap.String("provider", providerName))
		c.JSON(http.StatusNotFound, gin.H{"error": "unknown provider"})
		return
	}

	body, err := h.readBody(c)
	if err != nil {
		h.logger.Error("PromptGuard - failed to read body",
			zap.String("provider", providerName), zap.Error(err))
		c.JSON(http.StatusBadRequest, gin.H{"error": "unable to read request body"})
		return
	}

	// Authenticity check over the raw bytes. A configured-but-invalid or missing
	// signature is a hard reject; an unconfigured secret means we cannot check
	// (local/dev or the pre-first-save connection test) and we fail open.
	switch provider.Verify(c.Request.Header, body) {
	case promptguard.SigInvalid, promptguard.SigUnsigned:
		h.logger.Warn("PromptGuard - signature verification failed",
			zap.String("provider", providerName))
		c.JSON(http.StatusUnauthorized, gin.H{"error": "signature verification failed"})
		return
	case promptguard.SigNotConfigured:
		h.logger.Warn("PromptGuard - no signing secret configured; accepting unverified request",
			zap.String("provider", providerName))
	}

	input, allowShortCircuit, err := provider.Parse(body)
	if err != nil {
		// Body we can't understand: fail open with an explicit allow rather than a
		// non-200 (which the provider would treat as a delivery failure).
		h.logger.Error("PromptGuard - failed to parse body; allowing",
			zap.String("provider", providerName), zap.Error(err))
		c.JSON(http.StatusOK, provider.Verdict(promptguard.Decision{Allow: true}))
		return
	}
	if allowShortCircuit {
		h.logger.Info("PromptGuard - non-inspectable event; allowing",
			zap.String("provider", providerName))
		c.JSON(http.StatusOK, provider.Verdict(promptguard.Decision{Allow: true}))
		return
	}

	decision := h.evaluate(c, providerName, input)

	h.logger.Info("PromptGuard - completed",
		zap.String("provider", providerName),
		zap.String("requestID", input.RequestID),
		zap.String("sessionID", input.SessionID),
		zap.String("model", input.Model),
		zap.String("sourceApp", input.SourceApp),
		zap.Bool("allow", decision.Allow),
		zap.Int64("latencyMs", time.Since(start).Milliseconds()))

	c.JSON(http.StatusOK, provider.Verdict(decision))
}

// evaluate runs the flattened transcript through the guardrail validator and
// maps the result to a neutral Decision. A validator error fails open (allow),
// since a deny must reflect a real policy hit, not an internal fault.
func (h *PromptGuardHandler) evaluate(c *gin.Context, providerName string, input *promptguard.GuardInput) promptguard.Decision {
	if strings.TrimSpace(input.FlatText) == "" {
		return promptguard.Decision{Allow: true, ReferenceID: sanitizeReferenceID(input.RequestID)}
	}

	// A prompt is agentic LLM traffic, so it validates under the AGENTIC context
	// source — the same bucket data-ingestion resolves non-endpoint traffic to. A
	// made-up context source would match no policy and allow everything
	// (filterPoliciesByContextSource matches exactly). Source carries the provider
	// name so this traffic stays traceable.
	params := &models.ValidateRequestParams{
		Path:           "/guardrail",
		Method:         "POST",
		RequestPayload: input.FlatText,
		ContextSource:  string(types.ContextSourceAgentic),
		Source:         providerName,
	}
	// Tenant comes from the admin-configured Authorization: Bearer <Akto JWT>
	// (verified by the auth middleware), falling back to the service-token
	// account — the same resolution the other validate endpoints use.
	applyAuthenticatedAccount(c, params)

	valStart := time.Now()
	result, err := h.validatorService.ValidateRequest(c.Request.Context(), params, input.SessionID, input.RequestID)
	if h.metrics != nil && params.AktoAccountID != "" {
		h.metrics.RecordRequest(params.AktoAccountID, time.Since(valStart).Nanoseconds())
	}
	if err != nil {
		h.logger.Error("PromptGuard - validation failed; allowing",
			zap.String("provider", providerName),
			zap.String("account", params.AktoAccountID),
			zap.String("requestID", input.RequestID),
			zap.Error(err))
		return promptguard.Decision{Allow: true, ReferenceID: sanitizeReferenceID(input.RequestID)}
	}

	return promptguard.Decision{
		Allow:       result.Allowed,
		Reason:      result.Reason,
		ReferenceID: sanitizeReferenceID(input.RequestID),
	}
}

// readBody reads the raw request body, bounded by the configured maximum so an
// oversized frame is rejected rather than buffered without limit.
func (h *PromptGuardHandler) readBody(c *gin.Context) ([]byte, error) {
	limit := h.cfg.PromptGuardMaxBodyBytes
	reader := io.Reader(c.Request.Body)
	if limit > 0 {
		reader = io.LimitReader(c.Request.Body, int64(limit))
	}
	return io.ReadAll(reader)
}

// sanitizeReferenceID reduces an opaque request id to the character set a verdict
// reference_id allows ([A-Za-z0-9._:/-]) and caps its length. The provider
// records it for correlation and never shows it to the end user.
func sanitizeReferenceID(id string) string {
	if id == "" {
		return ""
	}
	var b strings.Builder
	for _, r := range id {
		switch {
		case r >= 'A' && r <= 'Z', r >= 'a' && r <= 'z', r >= '0' && r <= '9',
			r == '.', r == '_', r == ':', r == '/', r == '-':
			b.WriteRune(r)
		}
		if b.Len() >= referenceIDMaxLen {
			break
		}
	}
	return b.String()
}
