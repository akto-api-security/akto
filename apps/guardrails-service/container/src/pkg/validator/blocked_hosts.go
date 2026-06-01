package validator

import (
	"encoding/json"
	"strings"
)

// browserLlmTag marks traffic coming from the browser LLM extension. Only this traffic is
// subject to the host blocklist for now; MCP / gen-AI sources are handled separately.
const browserLlmTag = "browser-llm"

// BlockedHostEntry mirrors the dashboard GuardrailPolicies.BlockedHostEntry DTO. Block-only
// (no allow semantics). Modeled as an object so it can be extended later (new match fields)
// without breaking the stored payload.
type BlockedHostEntry struct {
	Host              string   `json:"host"`
	Paths             []string `json:"paths,omitempty"` // empty = all paths on the host
	IncludeSubdomains bool     `json:"includeSubdomains,omitempty"`
}

// blockedHostRule is a single blocked-host entry together with the policy it came from, so a
// match can be attributed back to the originating policy.
type blockedHostRule struct {
	policyName string
	entry      BlockedHostEntry
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

// parseBlockedHostRules extracts blocked-host rules from the raw /fetchGuardrailPolicies
// response body. Only active policies with at least one valid host contribute rules.
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
			if normalizeBlockedHost(entry.Host) == "" {
				continue
			}
			rules = append(rules, blockedHostRule{
				policyName: p.Name,
				entry:      entry,
			})
		}
	}
	return rules
}

// matchBlockedHostRule returns the first rule whose host/path covers the request.
// Returns (rule, matchedHost, true) on a match.
func matchBlockedHostRule(host, endpoint string, rules []blockedHostRule) (blockedHostRule, string, bool) {
	reqHost := normalizeBlockedHost(host)
	if reqHost == "" {
		return blockedHostRule{}, "", false
	}
	for _, rule := range rules {
		entryHost := normalizeBlockedHost(rule.entry.Host)
		if entryHost == "" {
			continue
		}
		if !hostMatchesBlockedEntry(reqHost, entryHost, rule.entry.IncludeSubdomains) {
			continue
		}
		if !pathMatchesBlockedEntry(endpoint, rule.entry.Paths) {
			continue
		}
		return rule, entryHost, true
	}
	return blockedHostRule{}, "", false
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

// hostMatchesBlockedEntry reports whether reqHost is covered by entryHost, honouring
// sub-domain matching when includeSubdomains is set (e.g. "a.chatgpt.com" vs "chatgpt.com").
func hostMatchesBlockedEntry(reqHost, entryHost string, includeSubdomains bool) bool {
	if reqHost == entryHost {
		return true
	}
	if includeSubdomains && strings.HasSuffix(reqHost, "."+entryHost) {
		return true
	}
	return false
}

// pathMatchesBlockedEntry reports whether the request endpoint is covered by the entry's path
// list. An empty list (or a blank entry) means "all paths on the host". A trailing "*" is a
// prefix wildcard, otherwise the path must match exactly.
func pathMatchesBlockedEntry(endpoint string, paths []string) bool {
	if len(paths) == 0 {
		return true
	}
	for _, p := range paths {
		p = strings.TrimSpace(p)
		if p == "" {
			return true
		}
		if strings.HasSuffix(p, "*") {
			if strings.HasPrefix(endpoint, strings.TrimSuffix(p, "*")) {
				return true
			}
			continue
		}
		if endpoint == p {
			return true
		}
	}
	return false
}
