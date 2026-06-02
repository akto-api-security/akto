package validator

import "testing"

func TestMatchBlockedHostRule(t *testing.T) {
	patterns := []string{
		"chatgpt.com",            // bare host -> host + all paths
		"chatgpt.com/*",          // all paths on host
		"*/v1/chat/completions",  // path on any host
		"deepseek.com/api/v1/*",  // path prefix
		"*.*/v1/chat/*",          // compound wildcards
		"*.openai.com/*",         // subdomains + paths
		"claude.ai/api/share",    // exact path (no wildcard)
	}

	var rules []blockedHostRule
	for _, p := range patterns {
		re, err := compileBlockedPattern(normalizePattern(p))
		if err != nil {
			t.Fatalf("compile %q: %v", p, err)
		}
		rules = append(rules, blockedHostRule{policyName: "test", pattern: p, re: re})
	}

	cases := []struct {
		host    string
		path    string
		want    bool
		pattern string // expected matched pattern (when want==true)
	}{
		{"chatgpt.com", "/", true, "chatgpt.com"},
		{"chatgpt.com", "/backend-api/conversation", true, "chatgpt.com"},
		{"api.openai.com", "/v1/chat/completions", true, "*/v1/chat/completions"},
		{"deepseek.com", "/api/v1/chat", true, "deepseek.com/api/v1/*"},
		{"api.anthropic.com", "/v1/chat/x", true, "*.*/v1/chat/*"},
		{"api.openai.com", "/v1/models", true, "*.openai.com/*"},
		{"claude.ai", "/api/share", true, "claude.ai/api/share"},
		{"claude.ai", "/api/share/extra", false, ""}, // exact path must not prefix-match
		{"evilchatgpt.com", "/x", false, ""},          // must not match chatgpt.com/*
		{"example.com", "/health", false, ""},          // unrelated
		{"CHATGPT.com", "/V1/Chat", true, "chatgpt.com"}, // case-insensitive
	}

	for _, c := range cases {
		rule, matched, ok := matchBlockedHostRule(c.host, c.path, rules)
		if ok != c.want {
			t.Errorf("host=%q path=%q: got blocked=%v, want %v (matched=%q)", c.host, c.path, ok, c.want, matched)
			continue
		}
		if c.want && rule.pattern != c.pattern {
			t.Errorf("host=%q path=%q: matched pattern=%q, want %q", c.host, c.path, rule.pattern, c.pattern)
		}
	}
}

// TestBlockedPatternEdgeCases covers regex-sensitive and URL edge cases: query strings,
// regex metacharacters (which must be treated as literals), ports, trailing slashes and case.
func TestBlockedPatternEdgeCases(t *testing.T) {
	mk := func(pattern string) []blockedHostRule {
		re, err := compileBlockedPattern(normalizePattern(pattern))
		if err != nil {
			t.Fatalf("compile %q: %v", pattern, err)
		}
		return []blockedHostRule{{policyName: "t", pattern: pattern, re: re}}
	}

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
		rules := mk(c.pattern)
		_, _, ok := matchBlockedHostRule(c.host, c.path, rules)
		if ok != c.want {
			t.Errorf("%s: pattern=%q host=%q path=%q -> got %v, want %v", c.name, c.pattern, c.host, c.path, ok, c.want)
		}
	}
}

// Policies without a blockedHosts key (or empty / malformed responses) must not break the
// service — they simply yield no rules and never block.
func TestParseBlockedHostRulesNoData(t *testing.T) {
	cases := []struct {
		name string
		raw  string
	}{
		{"no blockedHosts key", `{"guardrailPolicies":[{"name":"p1","active":true}]}`},
		{"empty blockedHosts", `{"guardrailPolicies":[{"name":"p1","active":true,"blockedHosts":[]}]}`},
		{"null blockedHosts", `{"guardrailPolicies":[{"name":"p1","active":true,"blockedHosts":null}]}`},
		{"empty policies", `{"guardrailPolicies":[]}`},
		{"empty object", `{}`},
		{"empty bytes", ``},
		{"malformed json", `{not json`},
		{"inactive policy with hosts", `{"guardrailPolicies":[{"name":"p1","active":false,"blockedHosts":[{"pattern":"chatgpt.com/*"}]}]}`},
		{"blank pattern", `{"guardrailPolicies":[{"name":"p1","active":true,"blockedHosts":[{"pattern":"  "}]}]}`},
	}
	for _, c := range cases {
		rules := parseBlockedHostRules([]byte(c.raw))
		if len(rules) != 0 {
			t.Errorf("%s: expected 0 rules, got %d", c.name, len(rules))
		}
		// Matching against no rules must never block and must not panic.
		if _, _, ok := matchBlockedHostRule("chatgpt.com", "/v1/chat", rules); ok {
			t.Errorf("%s: expected no block with empty rules", c.name)
		}
	}

	// nil rules slice is also safe.
	if _, _, ok := matchBlockedHostRule("chatgpt.com", "/v1/chat", nil); ok {
		t.Errorf("nil rules: expected no block")
	}
}

// TestMatchBlockedToolRule covers the endpoint / MCP-server matcher against synthetic hosts. The
// AI-tool token lives in a dotted segment after the device label and carries connector-specific
// suffixes (claudecli, codexcli), so matching is prefix/alias based, and "chatgpt" must block
// "codex"/"codexcli" via the vendor alias map.
func TestMatchBlockedToolRule(t *testing.T) {
	mk := func(patterns ...string) []blockedHostRule {
		var rules []blockedHostRule
		for _, p := range patterns {
			np := normalizePattern(p)
			re, err := compileBlockedPattern(np)
			if err != nil {
				t.Fatalf("compile %q: %v", p, err)
			}
			rules = append(rules, blockedHostRule{policyName: "test", pattern: np, re: re})
		}
		return rules
	}

	dev := "rahul-s-macbook-pro--2--be4f9b91"
	cases := []struct {
		name    string
		pattern string
		tag     string
		host    string
		want    bool
	}{
		// claude family — prefix match covers cli suffix and "-desktop".
		{"claude blocks claudecli (hook host)", "claude.com/*", "", dev + ".ai-agent.claudecli", true},
		{"claude blocks claude (discovery host)", "claude.com/*", "", dev + ".ai-agent.claude", true},
		{"claude blocks claude-desktop", "claude.com", "", dev + ".ai-agent.claude-desktop", true},
		{"claude via ai-agent tag value", "claude.com/*", `{"source":"ENDPOINT","ai-agent":"claudecli"}`, dev + ".ai-agent.claudecli", true},
		{"claude via mcp-client tag value", "claude.com", `{"mcp-client":"claude-desktop"}`, dev + ".ai-agent.claude-desktop", true},
		// chatgpt -> codex alias.
		{"chatgpt blocks codexcli", "chatgpt.com", "", dev + ".ai-agent.codexcli", true},
		{"chatgpt blocks codex", "chatgpt.com", "", dev + ".ai-agent.codex", true},
		{"bare codex pattern blocks codexcli", "codex", "", dev + ".ai-agent.codexcli", true},
		// other vendors.
		{"cursor blocks cursor", "cursor.com", "", dev + ".ai-agent.cursor", true},
		{"gemini blocks geminicli", "gemini.com", "", dev + ".ai-agent.geminicli", true},
		// negatives — no cross-vendor leakage, device label ignored, TLD not an alias.
		{"claude does not block codexcli", "claude.com/*", "", dev + ".ai-agent.codexcli", false},
		{"claude does not block cursor", "claude.com", "", dev + ".ai-agent.cursor", false},
		{"chatgpt does not block claude", "chatgpt.com", "", dev + ".ai-agent.claude", false},
		{"device label is not matched", "claude.com", "", "claude-laptop-abc.ai-agent.codexcli", false},
		{"unrelated vendor not blocked", "claude.com", "", dev + ".ai-agent.vscode", false},
	}

	for _, c := range cases {
		rules := mk(c.pattern)
		rule, matched, ok := matchBlockedToolRule(c.tag, c.host, rules)
		if ok != c.want {
			t.Errorf("%s: pattern=%q tag=%q host=%q -> got %v, want %v (matched=%q)", c.name, c.pattern, c.tag, c.host, ok, c.want, matched)
			continue
		}
		if c.want && rule.pattern != normalizePattern(c.pattern) {
			t.Errorf("%s: matched pattern=%q, want %q", c.name, rule.pattern, normalizePattern(c.pattern))
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
		{"", "endpoint", true}, // case-insensitive
		{`{"gen-ai":"Gen AI"}`, "", false},
		{`{"browser-llm":"Browser LLM"}`, "", false},
		{"", "", false},
		{"not-json", "", false},
	}
	for _, c := range cases {
		if got := isEndpointOrMcpRequest(c.tag, c.contextSource); got != c.want {
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
		if got := isBrowserExtensionRequest(c.tag); got != c.want {
			t.Errorf("tag=%q: got %v, want %v", c.tag, got, c.want)
		}
	}
}
