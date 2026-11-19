package validator

import "testing"

func TestRejectedAIAgents(t *testing.T) {
	raw := []byte(`{"mcpAuditInfoList":[
		{"type":"ai-agent","resourceName":"claude2","remarks":"Rejected","rawValues":["claude","claudecli","claude-cli-user"]},
		{"type":"ai-agent","resourceName":"github_cli","remarks":"Rejected","rawValues":["github_cli"]},
		{"type":"ai-agent","resourceName":"caveman-stats","remarks":"Rejected","rawValues":["caveman-stats"]},
		{"type":"ai-agent","resourceName":"akto.io","remarks":"Rejected","rawValues":["akto.io"]},
		{"type":"ai-agent","resourceName":"cursor","remarks":"Approved","rawValues":["cursor"]},
		{"type":"browser-llm","resourceName":"chatgpt.com","remarks":"Rejected"}
	]}`)
	agents := rejectedAuditDecisions(raw).agents
	if len(agents) != 6 {
		t.Fatalf("expected 6 rejected names, got %v", agents)
	}

	cases := []struct {
		host string
		want string
	}{
		{"janedoe-mac.ai-agent.claudecli", "claude2"},
		{"janedoe-mac.ai-agent.claude-cli-user", "claude2"},
		{"janedoe-mac.claude.github", "claude2"}, // MCP call made by a rejected agent
		{"JANEDOE-MAC.AI-AGENT.CLAUDE", "claude2"},
		{"janedoe-mac.ai_agent.github-cli", "github_cli"}, // "_" and "-" are the same token
		{"janedoe-mac.ai-agent.caveman-stats", "caveman-stats"},
		{"akto.io", "akto.io"},
		{"janedoe-mac.cursor.claude", ""},   // server segment never matches
		{"janedoe-mac.ai-agent.cursor", ""}, // approved agent
		{"janedoe-mac.ai-agent.claudecode", ""},
		{"janedoe.chrome.chatgpt.com", ""},
		{"claude", "claude2"},
		{"", ""},
	}
	for _, c := range cases {
		got, ok := matchRejectedAgent(c.host, agents)
		if got.row != c.want || ok != (c.want != "") {
			t.Errorf("matchRejectedAgent(%q) = %q, %v; want %q", c.host, got.row, ok, c.want)
		}
	}

	if got := rejectedAuditDecisions([]byte("not json")).agents; len(got) != 0 {
		t.Errorf("expected empty map on bad json, got %v", got)
	}
}
