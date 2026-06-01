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
