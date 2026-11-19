package validator

import (
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
)

// auditTypeAIAgent is the mcp_audit_info type the dashboard writes for AI agents.
const auditTypeAIAgent = "ai-agent"

// matchRejectedAgent exact-matches the host's agent segment, or a bare host, against rejected agents.
func matchRejectedAgent(host string, agents map[string]rejectedAgent) (rejectedAgent, bool) {
	host = canonicalServerToken(strings.TrimSpace(host))
	if host == "" {
		return rejectedAgent{}, false
	}
	if row, ok := agents[host]; ok {
		return row, true
	}
	parts := strings.SplitN(host, ".", 3)
	if len(parts) < 3 {
		return rejectedAgent{}, false
	}
	agent := parts[1]
	if agent == auditTypeAIAgent {
		agent = parts[2]
	}
	row, ok := agents[agent]
	return row, ok
}

// checkRejectedAgent blocks all traffic (prompts and MCP calls) from an AI agent rejected in Audit Data.
func (s *Service) checkRejectedAgent(params *models.ValidateRequestParams, valCtx *mcp.ValidationContext, payloadToValidate, sessionID, requestID string) *mcp.ValidationResult {
	if mcp.IsBrowserExtensionRequest(valCtx.Tag) {
		return nil
	}
	s.cache.mu.RLock()
	agents := s.cache.rejectedAIAgents
	s.cache.mu.RUnlock()
	if len(agents) == 0 {
		return nil
	}

	agent, rejected := matchRejectedAgent(valCtx.McpServerName, agents)
	if !rejected || !agent.scope.matches(deviceLabelFromMcpServerName(valCtx.McpServerName), requestUserEmail(valCtx)) {
		return nil
	}

	s.logger.Warn("ValidateRequest - blocking request from AI agent rejected in audit data",
		zap.String("host", valCtx.McpServerName),
		zap.String("agent", agent.row),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))
	metadata := types.ThreatMetadata{
		PolicyName:   mcp.AuditPolicyCategory,
		RuleViolated: "AIAgentAccessRejected",
		Severity:     "MEDIUM",
		Reason:       "AI agent access has been rejected by Audit Policy",
	}
	return s.reportAndBlock(params, valCtx, payloadToValidate, sessionID, requestID, metadata, "block")
}
