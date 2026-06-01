package validator

import (
	"encoding/json"
	"regexp"
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
// match can be attributed back to the originating policy. The pattern is compiled once into an
// anchored regex (re) so matching is robust for patterns such as "*.*/v1/chat/*".
type blockedHostRule struct {
	policyName string
	pattern    string
	re         *regexp.Regexp
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
			re, err := compileBlockedPattern(pattern)
			if err != nil {
				continue // skip patterns that fail to compile rather than blocking everything
			}
			rules = append(rules, blockedHostRule{policyName: p.Name, pattern: pattern, re: re})
		}
	}
	return rules
}

// blockedHostPatterns returns the raw patterns of the rules, for logging/diagnostics.
func blockedHostPatterns(rules []blockedHostRule) []string {
	patterns := make([]string, 0, len(rules))
	for _, r := range rules {
		patterns = append(patterns, r.pattern)
	}
	return patterns
}

// matchBlockedHostRule matches the request host+path against each rule's compiled pattern.
// Returns (rule, matchedPattern, true) on the first match.
func matchBlockedHostRule(host, endpoint string, rules []blockedHostRule) (blockedHostRule, string, bool) {
	normHost := normalizeBlockedHost(host)
	if normHost == "" {
		return blockedHostRule{}, "", false
	}
	// Drop any query string / fragment so path patterns match regardless of query params.
	if i := strings.IndexAny(endpoint, "?#"); i >= 0 {
		endpoint = endpoint[:i]
	}
	target := normHost + strings.ToLower(endpoint)
	for _, rule := range rules {
		if rule.re != nil && rule.re.MatchString(target) {
			return rule, rule.pattern, true
		}
	}
	return blockedHostRule{}, "", false
}

// compileBlockedPattern turns a (normalized) glob pattern into an anchored regex matched against
// the request's "host+path". "*" becomes ".*" (matches any sequence, including "/"); all other
// characters are taken literally. A bare host (no "/" and no "*") matches the host and every
// path under it, e.g. "chatgpt.com" -> ^chatgpt\.com(/.*)?$. This handles compound wildcard
// patterns such as "*.*/v1/chat/*" correctly.
func compileBlockedPattern(pattern string) (*regexp.Regexp, error) {
	if !strings.ContainsAny(pattern, "*/") {
		return regexp.Compile("^" + regexp.QuoteMeta(pattern) + "(/.*)?$")
	}

	var b strings.Builder
	b.WriteString("^")
	for i := 0; i < len(pattern); i++ {
		if pattern[i] == '*' {
			b.WriteString(".*")
		} else {
			b.WriteString(regexp.QuoteMeta(string(pattern[i])))
		}
	}
	b.WriteString("$")
	return regexp.Compile(b.String())
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
