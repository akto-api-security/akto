package cache

import (
	"crypto/sha256"
	"encoding/hex"
	"sort"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
)

// ComputeRuleHash returns a 16-char hex fingerprint of the active policy rule content.
//
// Only rule content is hashed (type, pattern, action, topics, substrings).
// Policy names, IDs, created-by, and other metadata are deliberately excluded
// so two policies with identical rules but different metadata share cache entries.
//
// The direction string ("request" or "response") is prepended before hashing,
// guaranteeing that request and response entries in the same Redis index
// never collide even when the rule content is identical in both directions.
func ComputeRuleHash(policies []types.Policy, direction string) string {
	var rules []types.FilterRule
	for _, p := range policies {
		if direction == "request" {
			rules = append(rules, p.Filters.RequestPayload...)
		} else {
			rules = append(rules, p.Filters.ResponsePayload...)
		}
	}

	parts := make([]string, 0, len(rules))
	for _, r := range rules {
		parts = append(parts, strings.Join([]string{
			r.Type,
			r.Pattern,
			r.Action,
			strings.Join(r.Config.Topics, ","),
			strings.Join(r.Config.Substrings, ","),
		}, "|"))
	}
	sort.Strings(parts)

	content := direction + ":" + strings.Join(parts, ";")
	h := sha256.Sum256([]byte(content))
	return hex.EncodeToString(h[:8]) // 16-char hex
}
