package validator

import (
	"encoding/json"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
)

// auditTypeAgentSkill is the mcp_audit_info type the dashboard and the endpoint agent write for skills.
const auditTypeAgentSkill = "AGENT_SKILL"

// skillToolName is the tool name Claude Code reports when it runs a skill.
const skillToolName = "skill"

// rejectedSkill is one Rejected AGENT_SKILL row: the agent it was recorded for and who the block applies to.
type rejectedSkill struct {
	agent string // canonical agent key; empty means every agent
	scope auditScope
}

// skillAgentKey maps an agent name to the key shared by skill rows and request hosts. Skill rows carry the
// endpoint agent's name ("claude") while the hook's host carries the client type ("claudecli",
// "claude-cli-user"), so every claude* spelling collapses to "claude".
func skillAgentKey(agent string) string {
	agent = canonicalServerToken(strings.TrimSpace(agent))
	if strings.HasPrefix(agent, "claude") {
		return "claude"
	}
	return agent
}

// agentFromHost returns the agent segment of a "{device}.ai-agent.{agent}" or "{device}.{agent}.{server}" host.
func agentFromHost(host string) string {
	host = canonicalServerToken(strings.TrimSpace(host))
	parts := strings.SplitN(host, ".", 3)
	if len(parts) < 3 {
		return ""
	}
	if parts[1] == auditTypeAIAgent {
		return parts[2]
	}
	return parts[1]
}

// skillNameFromPayload returns the skill name when the hook payload is a Skill tool call, else "".
// The hook sends {"body":{"skill":"<name>"},"toolName":"Skill"}.
func skillNameFromPayload(requestPayload string) string {
	if requestPayload == "" {
		return ""
	}
	var p struct {
		ToolName string `json:"toolName"`
		Body     struct {
			Skill string `json:"skill"`
		} `json:"body"`
	}
	if err := json.Unmarshal([]byte(requestPayload), &p); err != nil {
		return ""
	}
	if !strings.EqualFold(strings.TrimSpace(p.ToolName), skillToolName) {
		return ""
	}
	return strings.TrimSpace(p.Body.Skill)
}

// skillLookupKeys are the names to try for a skill: the full name, and the part after a "plugin:" prefix.
func skillLookupKeys(name string) []string {
	name = strings.ToLower(strings.TrimSpace(name))
	if name == "" {
		return nil
	}
	keys := []string{name}
	if i := strings.LastIndex(name, ":"); i >= 0 && i < len(name)-1 {
		keys = append(keys, name[i+1:])
	}
	return keys
}

// matchRejectedSkill reports whether the skill is rejected for this agent, device and user.
func matchRejectedSkill(skills map[string][]rejectedSkill, name, agent, deviceLabel, email string) (rejectedSkill, bool) {
	agent = skillAgentKey(agent)
	for _, key := range skillLookupKeys(name) {
		for _, row := range skills[key] {
			if row.agent != "" && row.agent != agent {
				continue
			}
			if row.scope.matches(deviceLabel, email) {
				return row, true
			}
		}
	}
	return rejectedSkill{}, false
}

// checkRejectedSkill blocks a Skill tool call for a skill rejected in Audit Data.
func (s *Service) checkRejectedSkill(params *models.ValidateRequestParams, valCtx *mcp.ValidationContext, payloadToValidate, sessionID, requestID string) *mcp.ValidationResult {
	name := skillNameFromPayload(params.RequestPayload)
	if name == "" {
		return nil
	}
	s.cache.mu.RLock()
	skills := s.cache.rejectedSkills
	s.cache.mu.RUnlock()
	if len(skills) == 0 {
		return nil
	}

	host := valCtx.McpServerName
	if _, rejected := matchRejectedSkill(skills, name, agentFromHost(host), deviceLabelFromMcpServerName(host), requestUserEmail(valCtx)); !rejected {
		return nil
	}

	s.logger.Warn("ValidateRequest - blocking skill rejected in audit data",
		zap.String("host", host),
		zap.String("skill", name),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))
	metadata := types.ThreatMetadata{
		PolicyName:   mcp.AuditPolicyCategory,
		RuleViolated: "SkillAccessRejected",
		Severity:     "MEDIUM",
		Reason:       "Skill access has been rejected by Audit Policy",
	}
	return s.reportAndBlock(params, valCtx, payloadToValidate, sessionID, requestID, metadata, "block")
}
