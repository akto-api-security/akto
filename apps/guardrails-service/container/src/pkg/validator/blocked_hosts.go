package validator

import (
	"encoding/json"
	"strings"
)

// browserLlmTag marks traffic coming from the browser LLM extension. Only this traffic is
// subject to the host blocklist for now; MCP / gen-AI sources are handled separately.
const browserLlmTag = "browser-llm"

// BlockedHostEntry mirrors the dashboard GuardrailPolicies.BlockedHostEntry DTO. Block-only
// (no allow semantics). Each entry is a glob pattern matched against "host+path", e.g.
// "chatgpt.com/*", "*/v1/chat/completions", "deepseek.com/api/v1/*". Modeled as an object so
// it can be extended later without breaking the stored payload.
type BlockedHostEntry struct {
	Pattern string `json:"pattern"`
}

// blockedHostRule is a single blocked-host pattern together with the policy it came from, so a
// match can be attributed back to the originating policy.
type blockedHostRule struct {
	policyName string
	pattern    string
}

// isBrowserExtensionRequest reports whether the request originates from the browser LLM
// extension, identified by the "browser-llm" key in the request tag metadata. MCP / gen-AI
// traffic is intentionally excluded — it will be handled separately.
func isBrowserExtensionRequest(tag string) bool {
	if tag == "" {
		return false
	}
	var tagsMap map[string]string
	if err := json.Unmarshal([]byte(tag), &tagsMap); err != nil {
		return false
	}
	_, ok := tagsMap[browserLlmTag]
	return ok
}

// parseBlockedHostRules extracts blocked-host patterns from the raw /fetchGuardrailPolicies
// response body. Only active policies with at least one non-empty pattern contribute rules.
func parseBlockedHostRules(raw []byte) []blockedHostRule {
	var response struct {
		GuardrailPolicies []struct {
			Name         string             `json:"name"`
			Active       bool               `json:"active"`
			BlockedHosts []BlockedHostEntry `json:"blockedHosts"`
		} `json:"guardrailPolicies"`
	}
	if err := json.Unmarshal(raw, &response); err != nil {
		return nil
	}

	var rules []blockedHostRule
	for _, p := range response.GuardrailPolicies {
		if !p.Active {
			continue
		}
		for _, entry := range p.BlockedHosts {
			pattern := normalizePattern(entry.Pattern)
			if pattern == "" {
				continue
			}
			rules = append(rules, blockedHostRule{policyName: p.Name, pattern: pattern})
		}
	}
	return rules
}

// matchBlockedHostRule matches the request host+path against each rule pattern.
// Returns (rule, matchedPattern, true) on the first match.
func matchBlockedHostRule(host, endpoint string, rules []blockedHostRule) (blockedHostRule, string, bool) {
	normHost := normalizeBlockedHost(host)
	if normHost == "" {
		return blockedHostRule{}, "", false
	}
	target := normHost + strings.ToLower(endpoint)
	for _, rule := range rules {
		if patternMatches(rule.pattern, normHost, target) {
			return rule, rule.pattern, true
		}
	}
	return blockedHostRule{}, "", false
}

// patternMatches decides whether a blocked pattern covers the request. A bare host (no "/" and
// no "*") blocks every path on that host; otherwise the pattern is glob-matched against the
// full "host+path" target with "*" matching any sequence of characters (including "/").
func patternMatches(pattern, host, target string) bool {
	if pattern == "" {
		return false
	}
	if !strings.ContainsAny(pattern, "*/") {
		return host == pattern
	}
	return globMatch(pattern, target)
}

// globMatch reports whether s matches pattern, where "*" matches any (possibly empty) sequence
// of characters. Classic two-pointer wildcard match; only "*" is special.
func globMatch(pattern, s string) bool {
	star, ss, p, sp := -1, 0, 0, 0
	for sp < len(s) {
		if p < len(pattern) && pattern[p] == s[sp] {
			p++
			sp++
		} else if p < len(pattern) && pattern[p] == '*' {
			star = p
			ss = sp
			p++
		} else if star != -1 {
			p = star + 1
			ss++
			sp = ss
		} else {
			return false
		}
	}
	for p < len(pattern) && pattern[p] == '*' {
		p++
	}
	return p == len(pattern)
}

// normalizeBlockedHost lowercases/trims and strips scheme, path and :port so values like
// "https://ChatGPT.com/foo:443" normalise to "chatgpt.com".
func normalizeBlockedHost(host string) string {
	host = strings.ToLower(strings.TrimSpace(host))
	host = strings.TrimPrefix(host, "http://")
	host = strings.TrimPrefix(host, "https://")
	if idx := strings.IndexByte(host, '/'); idx >= 0 {
		host = host[:idx]
	}
	if idx := strings.IndexByte(host, ':'); idx >= 0 {
		host = host[:idx]
	}
	return host
}

// normalizePattern lowercases/trims a pattern and strips any scheme prefix, preserving "*" and
// "/" so glob matching against "host+path" works.
func normalizePattern(pattern string) string {
	pattern = strings.ToLower(strings.TrimSpace(pattern))
	pattern = strings.TrimPrefix(pattern, "http://")
	pattern = strings.TrimPrefix(pattern, "https://")
	return pattern
}
