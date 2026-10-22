package validator

import (
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
)

// auditTypeBrowserLLM is the mcp_audit_info type the dashboard writes for browser LLM sites.
const auditTypeBrowserLLM = "browser-llm"

// matchRejectedLLMSite exact-matches the extension's host against rejected sites. Discovery names a row either
// <site> (host minus <user>.<browser>) or, for hosts it keeps whole, the host itself, so both are checked;
// a blocked site never blocks its parent or subdomains.
func matchRejectedLLMSite(host string, sites map[string]auditScope) (string, bool) {
	host = strings.ToLower(strings.TrimSpace(host))
	if host == "" {
		return "", false
	}
	if parts := strings.SplitN(host, ".", 3); len(parts) == 3 {
		if _, ok := sites[parts[2]]; ok {
			return parts[2], true
		}
	}
	if _, ok := sites[host]; ok {
		return host, true
	}
	return "", false
}

// checkRejectedLLM blocks browser extension traffic to an LLM site rejected in Audit Data.
func (s *Service) checkRejectedLLM(params *models.ValidateRequestParams, valCtx *mcp.ValidationContext, payloadToValidate, sessionID, requestID string) *mcp.ValidationResult {
	if !mcp.IsBrowserExtensionRequest(valCtx.Tag) {
		return nil
	}
	s.cache.mu.RLock()
	sites := s.cache.rejectedLLMSites
	s.cache.mu.RUnlock()
	if len(sites) == 0 {
		return nil
	}

	host := extractHostHeader(valCtx.RequestHeaders)
	site, rejected := matchRejectedLLMSite(host, sites)
	if !rejected || !sites[site].matches(deviceLabelFromMcpServerName(host), requestUserEmail(valCtx)) {
		return nil
	}

	s.logger.Warn("ValidateRequest - blocking request to LLM rejected in audit data",
		zap.String("host", host),
		zap.String("site", site),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))
	reason := "LLM access has been rejected by Audit Policy"
	metadata := types.ThreatMetadata{
		PolicyName:   mcp.AuditPolicyCategory,
		RuleViolated: "LLMAccessRejected",
		Severity:     "MEDIUM",
		Reason:       reason,
	}
	return s.reportAndBlock(params, valCtx, payloadToValidate, sessionID, requestID, metadata, "block")
}
