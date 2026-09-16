package validator

import (
	"encoding/json"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
)

// tagKeyBrowserLLMAgent names the browser LLM the extension is attached to ("claude",
// "chatgpt", ...). Written by the extension onto the request tag, never by this service.
const tagKeyBrowserLLMAgent = "browser-llm-agent"

// browserAttachmentBlockReason names the guardrail for a verdict that carried no reason of
// its own. A masked verdict never builds a blocked response, so Reason is empty on exactly
// the case this upgrade exists to catch — the same gap chunkBlockReason fills for
// /api/validate/file.
const browserAttachmentBlockReason = "file attachment contains content blocked by guardrail policy"

// carriesBrowserAttachment reports whether this request is browser-extension traffic that
// inlined file-attachment content into its chat payload.
//
// All three checks matter. The marker alone is ordinary user-visible text, so a plain API
// caller echoing that string must not trip the guardrail; mcp.IsBrowserExtensionRequest (a
// "browser-llm" key on the request tag, already used by resolvePersonalAccountBlock) scopes
// it to the extension. browser-llm-agent then narrows it to the agents that actually need
// the workaround — only claude.ai bypasses the upload endpoint /api/validate/file
// intercepts, and an agent that still reaches that endpoint must keep its redaction verdicts.
// Neither tag can be the trigger on its own: both are on every extension request, including
// attachment-free chat.
//
// The raw RequestPayload is searched rather than the extracted payload: the marker is part of
// the extension's wire contract, and extractPayloadForValidation may narrow the payload to a
// mapped field once a schema exists for the chat endpoint.
func (s *Service) carriesBrowserAttachment(params *models.ValidateRequestParams) bool {
	if s.config == nil || params == nil {
		return false
	}
	cfg := s.config.BrowserAttachment
	if !cfg.Enabled || cfg.Marker == "" {
		return false
	}
	if !mcp.IsBrowserExtensionRequest(params.Tag) {
		return false
	}
	if !matchesBrowserAttachmentAgent(params.Tag, cfg.Agents) {
		return false
	}
	return strings.Contains(params.RequestPayload, cfg.Marker)
}

// matchesBrowserAttachmentAgent reports whether the request's browser-llm-agent is one the
// guardrail covers. An empty agents list means no agent filter. A request whose tag carries
// no agent is never matched by a non-empty list: an unidentified agent is not known to need
// the claude.ai workaround, so it keeps the engine's own verdict.
func matchesBrowserAttachmentAgent(tag string, agents []string) bool {
	if len(agents) == 0 {
		return true
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(tag), &m); err != nil {
		return false
	}
	agent := strings.TrimSpace(m[tagKeyBrowserLLMAgent])
	if agent == "" {
		return false
	}
	for _, want := range agents {
		if strings.EqualFold(agent, want) {
			return true
		}
	}
	return false
}

// upgradeBrowserAttachmentVerdict turns a mask-or-alert verdict into a hard block when the
// request carried inlined attachment content. Mutates result in place; a no-op for every
// request that is not browser-extension attachment traffic.
//
// Only the enforcement action changes — detection is untouched, because the inlined content
// already reaches the engine as part of the request payload. See BrowserAttachmentConfig for
// why mask and alert are unenforceable on this shape.
func (s *Service) upgradeBrowserAttachmentVerdict(result *mcp.ValidationResult, params *models.ValidateRequestParams, sessionID string) {
	if result == nil {
		return
	}

	// A clean request stays clean: carrying an attachment is not itself a violation.
	if result.Allowed && !result.Modified {
		return
	}

	// An approval verdict holds the request for a human rather than redacting or alerting,
	// and pendingIfHumanApproval has already shaped the response around it. Overwriting its
	// behaviour would strand the caller's poll, so approvals pass through untouched.
	switch mcp.ParseBehaviour(result.Behaviour) {
	case mcp.BehaviourApproval, mcp.BehaviourHumanApproval:
		return
	}

	if !s.carriesBrowserAttachment(params) {
		return
	}

	// An alert-mode policy never blocks, whatever its rules asked for, so it is never
	// upgraded. Behaviour alone settles that for a block/warn rule, which reports the
	// policy's mode; a redact/mask rule always reports "alert" to describe what it did to
	// the payload (piiReportBehaviour) and never consults policy.Behaviour, so the policy
	// itself is asked whenever a rewritten payload is present.
	if mcp.ParseBehaviour(result.Behaviour) == mcp.BehaviourAlert &&
		(!result.Modified || s.PolicyIsAlertMode(params.ContextSource, result.Metadata.PolicyName)) {
		s.logger.Info("Browser attachment guardrail - alert-mode policy allowed",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("sessionID", sessionID),
			zap.Bool("modified", result.Modified),
			zap.String("policyName", result.Metadata.PolicyName))
		result.Allowed = true
		// The masked text is in the extension's flattened shape, not claude.ai's real
		// request body, so it can never be applied here — drop it rather than hand the
		// caller something it would map onto the wrong payload.
		result.ModifiedPayload = ""
		return
	}

	previousAllowed, previousBehaviour := result.Allowed, result.Behaviour
	if mcp.ParseBehaviour(previousBehaviour) == mcp.BehaviourBlock && !previousAllowed {
		return // already the verdict this upgrade produces
	}

	if result.Reason == "" {
		result.Reason = browserAttachmentBlockReason
	}
	result.Allowed = false
	result.Behaviour = string(mcp.BehaviourBlock)
	// Drop the rewritten payload rather than hand the caller something it cannot apply: the
	// masked text is in the flattened shape the extension synthesised, not claude.ai's real
	// request body. Modified is left as the engine reported it, matching fileVerdict, which
	// also reports Modified on a block while never returning content.
	result.ModifiedPayload = ""

	s.logger.Warn("Browser attachment guardrail - upgraded verdict to block",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("account", params.AktoAccountID),
		zap.String("sessionID", sessionID),
		zap.Bool("previousAllowed", previousAllowed),
		zap.String("previousBehaviour", previousBehaviour),
		zap.String("reason", result.Reason),
		zap.String("policyName", result.Metadata.PolicyName))
}
