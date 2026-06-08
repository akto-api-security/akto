package validator

import (
	"encoding/json"
	"regexp"
	"strings"
)

// browserLlmTag marks traffic coming from the browser LLM extension. Such traffic carries a real
// host header and is matched with the glob matcher (matchBlockedHostRule).
const browserLlmTag = "browser-llm"

// Tags / context-source values that identify endpoint & MCP-server traffic. These come from the
// gateway shields and the agent prompt hooks (e.g. ~/.claude/hooks/akto_ingestion_utility.py sends
// tag {"source":"ENDPOINT",...} with contextSource "ENDPOINT"). Such traffic carries a *synthetic*
// host (deviceLabel.ai-agent.<tool>) instead of a real domain, so it uses the tool-token matcher.
const (
	sourceTagKey       = "source"
	endpointSourceTag  = "ENDPOINT"
	mcpServerTag       = "mcp-server"
	aiAgentTagKey      = "ai-agent"
	mcpClientTagKey    = "mcp-client"
	minVendorAliasSize = 3 // ignore aliases shorter than this to avoid pathological prefix matches
)

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
	policyName    string
	contextSource string // policy-level contextSource ("AGENTIC", "ENDPOINT", or "")
	pattern       string
	re            *regexp.Regexp
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

// isEndpointOrMcpRequest reports whether the request originates from endpoint / MCP-server traffic,
// identified by contextSource "ENDPOINT", or a Tag whose "source" is "ENDPOINT", or a "mcp-server"
// key. These carry a synthetic host and are matched with the tool-token matcher, not the glob one.
func isEndpointOrMcpRequest(tag, contextSource string) bool {
	if strings.EqualFold(contextSource, endpointSourceTag) {
		return true
	}
	if tag == "" {
		return false
	}
	var tagsMap map[string]string
	if err := json.Unmarshal([]byte(tag), &tagsMap); err != nil {
		return false
	}
	if v, ok := tagsMap[sourceTagKey]; ok && strings.EqualFold(v, endpointSourceTag) {
		return true
	}
	_, ok := tagsMap[mcpServerTag]
	return ok
}

// vendorAliases maps a canonical vendor to the tool-token *prefixes* seen in synthetic hosts / tags.
// A host/tag token matches an alias when it equals or starts-with the alias, so "claude" covers
// claude / claudecli / claude-desktop / claude-code, and "codex" covers codex / codexcli. This is
// the one place to extend when a new AI tool appears; later it can be sourced from config without
// touching call sites (only resolveVendorAliases would change).
var vendorAliases = map[string][]string{
	"claude":  {"claude", "anthropic"},
	"chatgpt": {"codex", "chatgpt", "openai", "gpt"},
	"gemini":  {"gemini"},
	"cursor":  {"cursor"},
}

// commonTLDs are skipped when deriving vendor labels from a pattern, so a TLD like "ai" or "co"
// never becomes an alias that spuriously prefix-matches a tool token (e.g. "co" -> "codex").
var commonTLDs = map[string]struct{}{
	"com": {}, "ai": {}, "io": {}, "net": {}, "org": {}, "dev": {}, "co": {}, "app": {}, "cloud": {}, "inc": {},
}

// resolveVendorAliases returns the alias prefixes usable for a single pattern label. The label
// itself is always returned; if it matches any vendor group (as the group key or one of its
// aliases) the whole group is returned too. Unknown labels resolve to just themselves.
func resolveVendorAliases(label string) []string {
	label = strings.ToLower(strings.TrimSpace(label))
	out := []string{label}
	if label == "" {
		return out
	}
	for key, aliases := range vendorAliases {
		match := label == key
		if !match {
			for _, a := range aliases {
				if label == a {
					match = true
					break
				}
			}
		}
		if match {
			out = append(out, key)
			out = append(out, aliases...)
		}
	}
	return out
}

// patternVendorAliases derives the set of alias prefixes for a (normalized) blocked-host pattern by
// resolving every non-wildcard, non-TLD label. "claude.com/*" -> {claude, anthropic};
// "chatgpt.com" -> {chatgpt, codex, openai, gpt}; bare "codex" -> {codex, chatgpt, openai, gpt}.
func patternVendorAliases(pattern string) []string {
	hostPart := pattern
	if i := strings.IndexByte(hostPart, '/'); i >= 0 {
		hostPart = hostPart[:i]
	}
	set := make(map[string]struct{})
	for _, lbl := range strings.Split(hostPart, ".") {
		lbl = strings.ToLower(strings.TrimSpace(lbl))
		if lbl == "" || strings.Contains(lbl, "*") {
			continue
		}
		if _, isTLD := commonTLDs[lbl]; isTLD {
			continue
		}
		for _, a := range resolveVendorAliases(lbl) {
			if len(a) >= minVendorAliasSize {
				set[a] = struct{}{}
			}
		}
	}
	aliases := make([]string, 0, len(set))
	for a := range set {
		aliases = append(aliases, a)
	}
	return aliases
}

// agentToolTokens collects candidate AI-tool tokens for endpoint / MCP-server traffic, preferring
// the canonical name in the Tag ("ai-agent" / "mcp-client") and falling back to the synthetic host.
// The host's first segment is the device label and is dropped; remaining segments (and their "-"
// sub-tokens) are kept. e.g. "<device>.ai-agent.claude-desktop" -> {claude-desktop, claude, desktop,
// ai-agent, ai, agent}.
func agentToolTokens(tag, host string) []string {
	set := make(map[string]struct{})
	add := func(s string) {
		s = strings.ToLower(strings.TrimSpace(s))
		if s != "" {
			set[s] = struct{}{}
		}
	}
	addWithSubtokens := func(s string) {
		add(s)
		if strings.Contains(s, "-") {
			for _, sub := range strings.Split(s, "-") {
				add(sub)
			}
		}
	}

	if tag != "" {
		var tagsMap map[string]string
		if err := json.Unmarshal([]byte(tag), &tagsMap); err == nil {
			for _, k := range []string{aiAgentTagKey, mcpClientTagKey} {
				if v, ok := tagsMap[k]; ok {
					addWithSubtokens(v)
				}
			}
		}
	}

	h := normalizeBlockedHost(host)
	if h != "" {
		segs := strings.Split(h, ".")
		if len(segs) > 1 {
			segs = segs[1:] // drop the device-label segment
		}
		for _, seg := range segs {
			addWithSubtokens(seg)
		}
	}

	tokens := make([]string, 0, len(set))
	for t := range set {
		tokens = append(tokens, t)
	}
	return tokens
}

// matchBlockedToolRule matches endpoint / MCP-server traffic against blocked-host rules using the
// AI-tool token (from Tag / synthetic host) and the rule's vendor aliases. A token matches an alias
// when it equals or starts-with it (so "codexcli" matches alias "codex"). Returns the first match.
func matchBlockedToolRule(tag, host string, rules []blockedHostRule) (blockedHostRule, string, bool) {
	tokens := agentToolTokens(tag, host)
	if len(tokens) == 0 {
		return blockedHostRule{}, "", false
	}
	for _, rule := range rules {
		for _, alias := range patternVendorAliases(rule.pattern) {
			for _, tok := range tokens {
				if tok == alias || strings.HasPrefix(tok, alias) {
					return rule, rule.pattern, true
				}
			}
		}
	}
	return blockedHostRule{}, "", false
}

// parseBlockedHostRules extracts blocked-host patterns from the raw /fetchGuardrailPolicies
// response body. Only active policies with at least one non-empty pattern contribute rules.
func parseBlockedHostRules(raw []byte) []blockedHostRule {
	var response struct {
		GuardrailPolicies []struct {
			Name          string             `json:"name"`
			Active        bool               `json:"active"`
			ContextSource string             `json:"contextSource"`
			BlockedHosts  []BlockedHostEntry `json:"blockedHosts"`
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
			rules = append(rules, blockedHostRule{
				policyName:    p.Name,
				contextSource: strings.ToUpper(p.ContextSource),
				pattern:       pattern,
				re:            re,
			})
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
