package validator

import (
	"encoding/json"
	"sort"
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

// skillAgentAliases maps every spelling of a known agent to one key, so a skill row and a request host agree.
// Skill rows carry the endpoint agent's directory label ("claude", "codex", "cursor", ...) while the hook's
// host carries the client type ("claudecli", "codexcli", ...). Keys are canonicalServerToken form.
// To support a new agent or hook host, add its spellings here.
var skillAgentAliases = map[string]string{
	// Claude: skill rows say "claude" / "claude-desktop"; hooks say "claudecli" / "claude-agent-sdk".
	"claude": "claude", "claudecli": "claude", "claude-cli": "claude", "claude-cli-user": "claude",
	"claude-desktop": "claude", "claude-agent-sdk": "claude",
	// Codex: skill rows say "codex"; the hook says "codexcli".
	"codex": "codex", "codexcli": "codex", "codex-cli": "codex",
	// Agents whose row label and hook host are the same word.
	"cursor": "cursor", "opencode": "opencode", "amp": "amp",
	// Skill-row labels from the endpoint scanner; add their hook spellings here once confirmed.
	"vscode": "vscode", "copilot": "copilot", "kiro": "kiro", "windsurf": "windsurf", "antigravity": "antigravity",
	"geminicli": "gemini", "gemini": "gemini",
}

// skillAgentKey returns the canonical key of a known agent; any other name is returned as is
// (lowercased), so it only matches an identical name.
func skillAgentKey(agent string) string {
	agent = canonicalServerToken(strings.TrimSpace(agent))
	if key, ok := skillAgentAliases[agent]; ok {
		return key
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

// toolCallFromPayload returns the tool name of a hook tool-call payload and, for a Skill call, the skill name.
// The hook sends {"body":{"skill":"<name>"},"toolName":"Skill"}; both are "" for a payload with no toolName
// (prompts, MCP JSON-RPC, anything that is not a hook tool call).
func toolCallFromPayload(requestPayload string) (toolName, skill string) {
	if requestPayload == "" {
		return "", ""
	}
	var p struct {
		ToolName string `json:"toolName"`
		Body     struct {
			Skill string `json:"skill"`
		} `json:"body"`
	}
	if err := json.Unmarshal([]byte(requestPayload), &p); err != nil {
		return "", ""
	}
	toolName = strings.TrimSpace(p.ToolName)
	if strings.EqualFold(toolName, skillToolName) {
		skill = strings.TrimSpace(p.Body.Skill)
	}
	return toolName, skill
}

// skillNameFromPayload returns the skill name when the hook payload is a Skill tool call, else "".
func skillNameFromPayload(requestPayload string) string {
	_, skill := toolCallFromPayload(requestPayload)
	return skill
}

// maxLoggedSkillNames caps how many rejected skill names a log line lists.
const maxLoggedSkillNames = 20

// rejectedSkillNames lists the rejected skill names for logging, sorted and capped.
func rejectedSkillNames(skills map[string][]rejectedSkill) []string {
	names := make([]string, 0, len(skills))
	for name := range skills {
		names = append(names, name)
	}
	sort.Strings(names)
	if len(names) > maxLoggedSkillNames {
		names = names[:maxLoggedSkillNames]
	}
	return names
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

// checkRejectedSkill blocks a Skill tool call for a skill rejected in Audit Data. It logs only when it blocks.
func (s *Service) checkRejectedSkill(params *models.ValidateRequestParams, valCtx *mcp.ValidationContext, payloadToValidate, sessionID, requestID string) *mcp.ValidationResult {
	toolName, name := toolCallFromPayload(params.RequestPayload)
	if !strings.EqualFold(toolName, skillToolName) || name == "" {
		return nil // not a Skill tool call (prompt, MCP JSON-RPC, other tool, ...)
	}

	s.cache.mu.RLock()
	skills := s.cache.rejectedSkills
	s.cache.mu.RUnlock()

	host := valCtx.McpServerName
	agent := agentFromHost(host)
	row, rejected := matchRejectedSkill(skills, name, agent, deviceLabelFromMcpServerName(host), requestUserEmail(valCtx))
	if !rejected {
		return nil
	}

	s.logger.Warn("ValidateRequest - blocking skill rejected in audit data",
		zap.String("skill", name),
		zap.String("host", host),
		zap.String("agent", agent),
		zap.String("rowAgent", row.agent),
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
