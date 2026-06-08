package validator

import (
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

// buildRules is a test helper that creates compiled BlockedHostRules from raw patterns via a
// synthetic policy, mirroring what CompileBlockedHostRules does in production.
func buildRules(t *testing.T, patterns ...string) []mcp.BlockedHostRule {
	t.Helper()
	policy := types.Policy{
		Info:         types.PolicyInfo{Name: "test"},
		Inactive:     false,
		BlockedHosts: patterns,
	}
	return mcp.CompileBlockedHostRules([]types.Policy{policy})
}

// browserTag returns a tag JSON string that looks like a browser-extension request.
const browserTag = `{"browser-llm":"Browser LLM"}`

// endpointTag returns a tag JSON for an endpoint/MCP-server request.
func endpointTag(aiAgent string) string {
	return `{"source":"ENDPOINT","ai-agent":"` + aiAgent + `"}`
}

func TestMatchBlockedHostRule(t *testing.T) {
	rules := buildRules(t,
		"chatgpt.com",
		"chatgpt.com/*",
		"*/v1/chat/completions",
		"deepseek.com/api/v1/*",
		"*.*/v1/chat/*",
		"*.openai.com/*",
		"claude.ai/api/share",
	)

	cases := []struct {
		host    string
		path    string
		want    bool
		pattern string
	}{
		{"chatgpt.com", "/", true, "chatgpt.com"},
		{"chatgpt.com", "/backend-api/conversation", true, "chatgpt.com"},
		{"api.openai.com", "/v1/chat/completions", true, "*/v1/chat/completions"},
		{"deepseek.com", "/api/v1/chat", true, "deepseek.com/api/v1/*"},
		{"api.anthropic.com", "/v1/chat/x", true, "*.*/v1/chat/*"},
		{"api.openai.com", "/v1/models", true, "*.openai.com/*"},
		{"claude.ai", "/api/share", true, "claude.ai/api/share"},
		{"claude.ai", "/api/share/extra", false, ""},
		{"evilchatgpt.com", "/x", false, ""},
		{"example.com", "/health", false, ""},
		{"CHATGPT.com", "/V1/Chat", true, "chatgpt.com"},
	}

	for _, c := range cases {
		_, matched, ok := mcp.CheckBlockedHosts(browserTag, c.host, c.path, "", rules)
		if ok != c.want {
			t.Errorf("host=%q path=%q: got blocked=%v, want %v (matched=%q)", c.host, c.path, ok, c.want, matched)
			continue
		}
		if c.want && matched != c.pattern {
			t.Errorf("host=%q path=%q: matched pattern=%q, want %q", c.host, c.path, matched, c.pattern)
		}
	}
}

func TestBlockedPatternEdgeCases(t *testing.T) {
	cases := []struct {
		name    string
		pattern string
		host    string
		path    string
		want    bool
	}{
		{"query stripped, exact path matches", "chatgpt.com/v1/chat", "chatgpt.com", "/v1/chat?model=x&t=1", true},
		{"fragment stripped", "chatgpt.com/v1/chat", "chatgpt.com", "/v1/chat#frag", true},
		{"query does not falsely extend prefix", "deepseek.com/api/v1/*", "deepseek.com", "/api/v1/chat?a=b", true},
		{"dot is literal not any-char", "chatgpt.com/*", "chatgptxcom", "/v1", false},
		{"regex meta in literal path is escaped", "site.com/a+b", "site.com", "/a+b", true},
		{"regex meta literal does not match as quantifier", "site.com/a+b", "site.com", "/aaab", false},
		{"port on request host is stripped", "chatgpt.com/*", "chatgpt.com:8443", "/v1", true},
		{"uppercase host and path", "chatgpt.com/v1/*", "CHATGPT.COM", "/V1/Chat", true},
		{"scheme in request host stripped", "chatgpt.com/*", "https://chatgpt.com", "/v1", true},
		{"bare host matches root", "chatgpt.com", "chatgpt.com", "/", true},
		{"bare host matches deep path", "chatgpt.com", "chatgpt.com", "/a/b/c", true},
		{"host/* needs a slash", "chatgpt.com/*", "chatgpt.com", "", false},
		{"trailing-slash pattern exact", "chatgpt.com/v1/", "chatgpt.com", "/v1/", true},
		{"middle wildcard", "*.openai.com/v1/*", "api.openai.com", "/v1/chat", true},
		{"middle wildcard host mismatch", "*.openai.com/v1/*", "openai.com", "/v1/chat", false},
	}

	for _, c := range cases {
		rules := buildRules(t, c.pattern)
		_, _, ok := mcp.CheckBlockedHosts(browserTag, c.host, c.path, "", rules)
		if ok != c.want {
			t.Errorf("%s: pattern=%q host=%q path=%q -> got %v, want %v", c.name, c.pattern, c.host, c.path, ok, c.want)
		}
	}
}

func TestCompileBlockedHostRulesNoData(t *testing.T) {
	cases := []struct {
		name   string
		policy types.Policy
	}{
		{"no BlockedHosts", types.Policy{Info: types.PolicyInfo{Name: "p1"}, Inactive: false}},
		{"empty BlockedHosts", types.Policy{Info: types.PolicyInfo{Name: "p1"}, Inactive: false, BlockedHosts: []string{}}},
		{"nil BlockedHosts", types.Policy{Info: types.PolicyInfo{Name: "p1"}, Inactive: false, BlockedHosts: nil}},
		{"blank pattern", types.Policy{Info: types.PolicyInfo{Name: "p1"}, Inactive: false, BlockedHosts: []string{"  "}}},
		{"inactive policy", types.Policy{Info: types.PolicyInfo{Name: "p1"}, Inactive: true, BlockedHosts: []string{"chatgpt.com/*"}}},
	}
	for _, c := range cases {
		rules := mcp.CompileBlockedHostRules([]types.Policy{c.policy})
		if len(rules) != 0 {
			t.Errorf("%s: expected 0 rules, got %d", c.name, len(rules))
		}
		if _, _, ok := mcp.CheckBlockedHosts(browserTag, "chatgpt.com", "/v1/chat", "", rules); ok {
			t.Errorf("%s: expected no block with empty rules", c.name)
		}
	}

	if _, _, ok := mcp.CheckBlockedHosts(browserTag, "chatgpt.com", "/v1/chat", "", nil); ok {
		t.Errorf("nil rules: expected no block")
	}
}

func TestMatchBlockedToolRule(t *testing.T) {
	dev := "rahul-s-macbook-pro--2--be4f9b91"
	cases := []struct {
		name    string
		pattern string
		tag     string
		host    string
		want    bool
	}{
		{"claude blocks claudecli (hook host)", "claude.com/*", endpointTag("claudecli"), dev + ".ai-agent.claudecli", true},
		{"claude blocks claude (discovery host)", "claude.com/*", endpointTag("claude"), dev + ".ai-agent.claude", true},
		{"claude blocks claude-desktop", "claude.com", endpointTag("claude-desktop"), dev + ".ai-agent.claude-desktop", true},
		{"claude via mcp-client tag value", "claude.com", `{"mcp-client":"claude-desktop"}`, dev + ".ai-agent.claude-desktop", true},
		{"chatgpt blocks codexcli", "chatgpt.com", endpointTag("codexcli"), dev + ".ai-agent.codexcli", true},
		{"chatgpt blocks codex", "chatgpt.com", endpointTag("codex"), dev + ".ai-agent.codex", true},
		{"bare codex pattern blocks codexcli", "codex", endpointTag("codexcli"), dev + ".ai-agent.codexcli", true},
		{"cursor blocks cursor", "cursor.com", endpointTag("cursor"), dev + ".ai-agent.cursor", true},
		{"gemini blocks geminicli", "gemini.com", endpointTag("geminicli"), dev + ".ai-agent.geminicli", true},
		{"claude does not block codexcli", "claude.com/*", endpointTag("codexcli"), dev + ".ai-agent.codexcli", false},
		{"claude does not block cursor", "claude.com", endpointTag("cursor"), dev + ".ai-agent.cursor", false},
		{"chatgpt does not block claude", "chatgpt.com", endpointTag("claude"), dev + ".ai-agent.claude", false},
		{"device label is not matched", "claude.com", endpointTag("codexcli"), "claude-laptop-abc.ai-agent.codexcli", false},
		{"unrelated vendor not blocked", "claude.com", endpointTag("vscode"), dev + ".ai-agent.vscode", false},
	}

	for _, c := range cases {
		rules := buildRules(t, c.pattern)
		_, _, ok := mcp.CheckBlockedHosts(c.tag, c.host, "/", "ENDPOINT", rules)
		if ok != c.want {
			t.Errorf("%s: pattern=%q tag=%q host=%q -> got %v, want %v", c.name, c.pattern, c.tag, c.host, ok, c.want)
		}
	}
}

func TestIsEndpointOrMcpRequest(t *testing.T) {
	cases := []struct {
		tag           string
		contextSource string
		want          bool
	}{
		{`{"gen-ai":"Gen AI","hook":"UserPromptSubmit","ai-agent":"claudecli","source":"ENDPOINT"}`, "ENDPOINT", true},
		{`{"source":"ENDPOINT"}`, "", true},
		{`{"mcp-server":"x"}`, "", true},
		{"", "ENDPOINT", true},
		{"", "endpoint", true},
		{`{"gen-ai":"Gen AI"}`, "", false},
		{`{"browser-llm":"Browser LLM"}`, "", false},
		{"", "", false},
		{"not-json", "", false},
	}
	for _, c := range cases {
		if got := mcp.IsEndpointOrMcpRequest(c.tag, c.contextSource); got != c.want {
			t.Errorf("tag=%q contextSource=%q: got %v, want %v", c.tag, c.contextSource, got, c.want)
		}
	}
}

func TestIsBrowserExtensionRequest(t *testing.T) {
	cases := []struct {
		tag  string
		want bool
	}{
		{`{"gen-ai":"Gen AI","browser-llm":"Browser LLM","browser-llm-account-type":"personal"}`, true},
		{`{"gen-ai":"Gen AI"}`, false},
		{`{"mcp-server":"x"}`, false},
		{"", false},
		{"not-json", false},
	}
	for _, c := range cases {
		if got := mcp.IsBrowserExtensionRequest(c.tag); got != c.want {
			t.Errorf("tag=%q: got %v, want %v", c.tag, got, c.want)
		}
	}
}
