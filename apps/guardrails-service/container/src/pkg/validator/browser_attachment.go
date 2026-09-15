package validator

import (
	"encoding/json"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
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
// scannedPayload is what the verdict was measured against — the baseline Modified compares
// to — and supplies the coordinate system for locating the detection.
func (s *Service) upgradeBrowserAttachmentVerdict(result *mcp.ValidationResult, params *models.ValidateRequestParams, scannedPayload, sessionID string) {
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

	// A detection confined to the prompt keeps the engine's own verdict: the extension can
	// rewrite the prompt it owns, so a mask there is still enforceable. Only the attachment
	// half has no writable counterpart in claude.ai's request.
	markerIdx := strings.Index(scannedPayload, s.config.BrowserAttachment.Marker)
	if promptOnlyViolation(scannedPayload, result.ModifiedPayload, result.Metadata, markerIdx) {
		s.logger.Info("Browser attachment guardrail - detection is prompt-only, keeping engine verdict",
			zap.String("path", params.Path),
			zap.String("sessionID", sessionID),
			zap.Bool("allowed", result.Allowed),
			zap.String("behaviour", result.Behaviour))
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

// promptOnlyViolation reports whether every detection provably landed in the user's own
// prompt rather than in attachment content.
//
// The prompt and the attachments arrive merged into one flattened payload, so the engine
// answers with a single verdict for both. Upgrading that verdict unconditionally turns a
// redactable prompt detection — "my ssn is 123-45-6789" typed next to a clean text file —
// into a hard block, which is not what the attachment guardrail is for. A prompt detection
// stays maskable because the extension owns the prompt field and can rewrite it; only the
// attachment half is unmappable.
//
// scanned is the payload the verdict was measured against, and markerIdx the offset where
// attachment content begins within it. Localisation uses whichever evidence the verdict
// carries:
//
//   - A mask verdict carries no SchemaErrors at all (the processor's redaction branch sets
//     ModifiedPayload and Behaviour but never Metadata), so its masked payload is diffed
//     against the original and the hull of the changes is what gets located.
//   - A block or alert verdict carries Metadata lifted off the enforced violation, whose
//     SchemaErrors hold byte offsets into the scanned payload.
//
// Returns false whenever neither is usable: with no evidence of where a detection landed,
// the verdict keeps the upgrade rather than silently letting attachment content through.
func promptOnlyViolation(scanned, modified string, meta types.ThreatMetadata, markerIdx int) bool {
	if markerIdx < 0 {
		return false // no attachment boundary in the scanned payload; nothing to localise against
	}

	if modified != "" && modified != scanned {
		_, end := changedSpan(scanned, modified)
		return end <= markerIdx
	}

	localizable := 0
	for _, se := range meta.SchemaErrors {
		// valueSchemaErrors emits a zero-offset entry when it cannot find the value in the
		// payload; it carries a message but no position, so it localises nothing.
		if se.End <= 0 {
			continue
		}
		localizable++
		if se.End > markerIdx {
			return false
		}
	}
	return localizable > 0
}

// changedSpan returns the half-open byte range [start, end) of original that differs from
// modified, as the hull of every change. Equal strings give (0, 0).
//
// The hull, not each individual edit: masking rewrites spans to placeholders of a different
// length, so positions after the first edit no longer line up between the two strings and
// per-edit alignment would need a real diff. The hull spans from the first change to the
// last, which errs toward treating a detection as attachment-side — the safe direction for
// a guardrail, and exact for the case that matters, where every change sits on one side.
func changedSpan(original, modified string) (int, int) {
	if original == modified {
		return 0, 0
	}
	shortest := len(original)
	if len(modified) < shortest {
		shortest = len(modified)
	}

	prefix := 0
	for prefix < shortest && original[prefix] == modified[prefix] {
		prefix++
	}
	suffix := 0
	for suffix < shortest-prefix && original[len(original)-1-suffix] == modified[len(modified)-1-suffix] {
		suffix++
	}
	return prefix, len(original) - suffix
}
