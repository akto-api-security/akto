package endpointshield

import (
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/nhi/extract"
)

// agentRoleTargets maps an agent rule (agent, role) to its parser. Agent rules are the per-agent
// paths from tests-library (Settings > File Inspection); the shield reads them, so they are not
// registered here. A role without an entry is not parsed yet.
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

func agentTarget(r dbabstractor.FileInspectionRule) (Target, bool) {
	t, ok := agentRoleTargets[r.Agent][r.Role]
	if !ok {
		return Target{}, false
	}
	t.Path = r.Path
	t.AgentName = r.Agent
	return t, true
}
