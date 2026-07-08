package validator

import (
	"regexp"
	"strconv"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// replacement is a single placeholder -> original-text substitution made during a
// redact pass. It's kept only for the duration of one request so the real text can be
// restored before anything is forwarded downstream — never persisted, never encoded
// into the payload itself.
type replacement struct {
	placeholder string
	original    string
}

// ignorePhraseMatcher redacts a fixed set of phrases (literal or regex) from text using
// a single compiled, linear-time (RE2) regex.
type ignorePhraseMatcher struct {
	re *regexp.Regexp
}

// compileIgnorePhraseMatcher builds one compiled matcher for a single policy's ignore
// phrases. Returns nil if there's nothing to match (empty list, or every phrase is blank).
func compileIgnorePhraseMatcher(phrases []types.IgnorePhrase) *ignorePhraseMatcher {
	var caseSensitiveParts, caseInsensitiveParts []string
	for _, phrase := range phrases {
		if strings.TrimSpace(phrase.Phrase) == "" {
			continue
		}
		pattern := phrase.Phrase
		if !phrase.IsRegex {
			// Literal phrases match whole words only, so a short/common phrase (e.g.
			// "Acme") doesn't also strip a substring out of an unrelated larger word
			// (e.g. "AcmeCorp2024"). Regex phrases are used as-is — the author controls
			// their own boundaries.
			//
			// Treat a literal escape-sequence pair as an additional valid
			// boundary alongside \b so this common case (test payloads/logs with escaped
			// newlines pasted verbatim) doesn't silently defeat redaction.
			boundary := `(?:\\[nrtbf"\\/]|\b)`
			pattern = boundary + regexp.QuoteMeta(phrase.Phrase) + boundary
		}
		if phrase.IsRegex {
			if _, err := regexp.Compile(pattern); err != nil {
				continue
			}
		}
		if phrase.CaseSensitive {
			caseSensitiveParts = append(caseSensitiveParts, pattern)
		} else {
			caseInsensitiveParts = append(caseInsensitiveParts, pattern)
		}
	}
	if len(caseSensitiveParts) == 0 && len(caseInsensitiveParts) == 0 {
		return nil
	}

	var combinedParts []string
	if len(caseSensitiveParts) > 0 {
		combinedParts = append(combinedParts, "("+strings.Join(caseSensitiveParts, "|")+")")
	}
	if len(caseInsensitiveParts) > 0 {
		combinedParts = append(combinedParts, "(?i:"+strings.Join(caseInsensitiveParts, "|")+")")
	}

	compiled, err := regexp.Compile(strings.Join(combinedParts, "|"))
	if err != nil {
		return nil
	}
	return &ignorePhraseMatcher{re: compiled}
}

// compileIgnorePhraseMatchersByPolicy builds one compiled matcher per policy name, once,
// straight off each policy's own IgnorePhrases (populated natively by
// mcp.ConvertGuardrailsToPolicy). Called from fetchAndParsePolicies at cache refresh
// time, not per request — steady-state request handling is a map lookup, never a regex
// compile. Mirrors how compiledRules is already derived from policies in this file.
func compileIgnorePhraseMatchersByPolicy(policies []types.Policy) map[string]*ignorePhraseMatcher {
	matchersByPolicy := make(map[string]*ignorePhraseMatcher)
	for _, policy := range policies {
		if len(policy.IgnorePhrases) == 0 {
			continue
		}
		if matcher := compileIgnorePhraseMatcher(policy.IgnorePhrases); matcher != nil {
			matchersByPolicy[policy.Info.Name] = matcher
		}
	}
	return matchersByPolicy
}

// redact replaces every match with a plain sequential placeholder, starting from
// startPlaceholderIndex so placeholders stay unique when multiple matchers run against
// the same payload in one request. It returns the substitutions made so the real text
// can be restored later, and the next free index for a subsequent matcher to continue from.
func (m *ignorePhraseMatcher) redact(payload string, startPlaceholderIndex int) (redacted string, restore []replacement, nextPlaceholderIndex int) {
	if m == nil || m.re == nil {
		return payload, nil, startPlaceholderIndex
	}
	placeholderIndex := startPlaceholderIndex
	redacted = m.re.ReplaceAllStringFunc(payload, func(match string) string {
		placeholder := "\x00IGNP" + strconv.Itoa(placeholderIndex) + "\x00"
		restore = append(restore, replacement{placeholder: placeholder, original: match})
		placeholderIndex++
		return placeholder
	})
	return redacted, restore, placeholderIndex
}

// redactIgnorePhrasesForPolicies applies every applicable policy's own ignore-phrase
// matcher to the same shared payload, in sequence, since the enforcement library takes
// exactly one payload string for the whole policies batch handed to it in a single call
// — there's no per-policy channel to keep one policy's redaction from being visible to
// another's detectors in that same call. A phrase ignored by Policy A is therefore
// invisible to every other policy evaluated alongside it in this request, not just
// Policy A itself; this is a deliberate simplicity trade-off (see plan doc).
func redactIgnorePhrasesForPolicies(payload string, policies []types.Policy, matchersByPolicy map[string]*ignorePhraseMatcher) (redacted string, restore []replacement, changed bool) {
	if len(matchersByPolicy) == 0 {
		return payload, nil, false
	}
	redacted = payload
	placeholderIndex := 0
	for _, policy := range policies {
		matcher, ok := matchersByPolicy[policy.Info.Name]
		if !ok {
			continue
		}
		var policyRestore []replacement
		redacted, policyRestore, placeholderIndex = matcher.redact(redacted, placeholderIndex)
		restore = append(restore, policyRestore...)
	}
	return redacted, restore, len(restore) > 0
}

// restoreIgnorePhrases puts the real text back wherever a placeholder survived into
// the (possibly further masked) result the enforcement library returned. Plain
// strings.ReplaceAll, same primitive already used elsewhere in this service — no
// encoding/decoding involved.
func restoreIgnorePhrases(payload string, restore []replacement) string {
	for _, r := range restore {
		payload = strings.ReplaceAll(payload, r.placeholder, r.original)
	}
	return payload
}

// redactIgnorePhrasesForEvaluation is the shared "before the enforcement-library call"
// half of ignore-phrase handling for ValidateRequest and ValidateResponse: look up the
// cached per-policy matchers (refreshed alongside the rest of the policy cache) and
// delegate to redactIgnorePhrasesForEvaluationWithMatchers.
func (s *Service) redactIgnorePhrasesForEvaluation(originalPayload string, policies []types.Policy, logPrefix, sessionID string) (payloadForEvaluation, preRedactionPayload string, restore []replacement) {
	s.cache.mu.RLock()
	matchersByPolicy := s.cache.ignorePhraseMatchersByPolicy
	s.cache.mu.RUnlock()
	return s.redactIgnorePhrasesForEvaluationWithMatchers(originalPayload, policies, matchersByPolicy, logPrefix, sessionID)
}

// redactIgnorePhrasesForEvaluationWithMatchers is the cache-independent core: given
// matchers the caller already has in hand, redact the payload before it's evaluated if
// any of the given policies has one. Returns the payload to send for evaluation, the
// untouched pre-redaction payload, and the substitutions made (nil if nothing changed)
// — pass all three to reconcileIgnorePhraseRedaction afterward. Used directly by
// ValidateRequestWithPolicy (the guardrail playground), which evaluates a single
// one-off provided policy that never goes through the policy cache at all.
func (s *Service) redactIgnorePhrasesForEvaluationWithMatchers(originalPayload string, policies []types.Policy, matchersByPolicy map[string]*ignorePhraseMatcher, logPrefix, sessionID string) (payloadForEvaluation, preRedactionPayload string, restore []replacement) {
	if len(matchersByPolicy) == 0 {
		return originalPayload, originalPayload, nil
	}
	redacted, restore, changed := redactIgnorePhrasesForPolicies(originalPayload, policies, matchersByPolicy)
	if !changed {
		return originalPayload, originalPayload, nil
	}
	s.logger.Info(logPrefix+" - ignore-phrase redaction applied for evaluation",
		zap.String("sessionID", sessionID),
		zap.Int("matchCount", len(restore)))
	return redacted, originalPayload, restore
}

// reconcileIgnorePhraseRedaction is the shared "after the enforcement-library call" half:
// the real origin must never see an ignore-phrase placeholder. If the library modified
// the payload on top of what was sent for evaluation, the real phrases are restored into
// that modified result; otherwise the untouched pre-redaction payload is returned instead
// of the eval-only redacted copy.
func (s *Service) reconcileIgnorePhraseRedaction(modifiedPayload, payloadForEvaluation, preRedactionPayload string, restore []replacement, logPrefix, sessionID string) string {
	if len(restore) == 0 {
		return modifiedPayload
	}
	if modifiedPayload != "" && modifiedPayload != payloadForEvaluation {
		s.logger.Info(logPrefix+" - restored ignore-phrase placeholders after masking",
			zap.String("sessionID", sessionID))
		return restoreIgnorePhrases(modifiedPayload, restore)
	}
	return preRedactionPayload
}
