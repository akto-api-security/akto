package endpointshield

import (
	"strings"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/nhi/extract"
)

// agentRoleTargets maps an agent rule's (agent, role), which the shield copies onto its results, to
// the parser. Agent rules are the per-agent paths from tests-library (Settings > File Inspection);
// the shield reads them, so they are not registered here. A role without an entry is not parsed yet.
// Agent names are matched ignoring case.
var agentRoleTargets = map[string]map[string]Target{
	"Claude Code": {
		"agent_login":     {SourceType: "claude_login", AgentType: "CODE_EDITOR", Login: extract.ClaudeCodeLogin},
		"mcp_credentials": {SourceType: "claude_credentials", AgentType: "CODE_EDITOR", Extractor: extract.ClaudeCredentials},
		"mcp_config":      {SourceType: "mcp_config", AgentType: "CODE_EDITOR", Extractor: extract.ClaudeCodeConfig},
	},
	"Codex": {
		"agent_login": {SourceType: "codex_auth", AgentType: "AI_AGENT", Extractor: extract.OpenAIAuth},
	},
	"Cursor CLI": {
		"mcp_config": {SourceType: "mcp_config", AgentType: "CODE_EDITOR", Extractor: extract.MCPConfig},
	},
}

// agentTarget returns the parser for an agent-rule result (all matches of one rule share its role).
func agentTarget(r dbabstractor.FileInspectionResult) (Target, bool) {
	if len(r.Matches) == 0 {
		return Target{}, false
	}
	for agent, roles := range agentRoleTargets {
		if !strings.EqualFold(agent, r.Agent) {
			continue
		}
		t, ok := roles[strings.ToLower(r.Matches[0].Role)]
		if !ok {
			return Target{}, false
		}
		t.AgentName = agent
		return t, true
	}
	return Target{}, false
}
