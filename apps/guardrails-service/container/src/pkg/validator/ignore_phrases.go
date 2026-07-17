package validator

import (
	"regexp"
	"strings"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// ignorePhraseMatcher redacts a set of phrases (literal or regex) via one compiled RE2 regex.
type ignorePhraseMatcher struct {
	re *regexp.Regexp
}

// compileIgnorePhraseMatcher compiles one matcher for a policy's ignore phrases.
// Returns nil if there's nothing to match.
func compileIgnorePhraseMatcher(phrases []types.IgnorePhrase) *ignorePhraseMatcher {
	var caseSensitiveParts, caseInsensitiveParts []string
	for _, phrase := range phrases {
		if strings.TrimSpace(phrase.Phrase) == "" {
			continue
		}
		pattern := phrase.Phrase
		if !phrase.IsRegex {
			// Whole-word match so "Acme" doesn't also strip "AcmeCorp2024". \b alone
			// misses a phrase glued to a literal "\n" in escaped text, so also accept
			// a JSON escape-sequence pair as a boundary.
			boundary := `(?:\\[nrtbf"\\/]|\b)`
			pattern = boundary + regexp.QuoteMeta(phrase.Phrase) + boundary
		}
		if phrase.IsRegex {
			// Java's regex engine (save-time validation) accepts things RE2 doesn't
			// (backreferences, lookaround) — skip just this phrase, not the whole policy.
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

// compileIgnorePhraseMatchersByPolicy compiles one matcher per policy, once, at cache
// refresh time — steady-state requests only do a map lookup.
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

// redact deletes every match outright. A marker/placeholder would itself be
// unusual-looking text a prompt-injection scanner can flag — deletion leaves no
// artifact. No restore mapping needed: deletion always shortens the payload, so
// "did anything change" is just a != check against the original.
func (m *ignorePhraseMatcher) redact(payload string) string {
	if m == nil || m.re == nil {
		return payload
	}
	return m.re.ReplaceAllString(payload, "")
}

// redactIgnorePhrasesForPolicies redacts every applicable policy's phrases from one
// shared payload, since the enforcement library evaluates all policies against a single
// string per call. A phrase ignored by Policy A is therefore hidden from every policy in
// this request, not just Policy A (deliberate simplicity trade-off — see plan doc).
func redactIgnorePhrasesForPolicies(payload string, policies []types.Policy, matchersByPolicy map[string]*ignorePhraseMatcher) string {
	if len(matchersByPolicy) == 0 {
		return payload
	}
	redacted := payload
	for _, policy := range policies {
		matcher, ok := matchersByPolicy[policy.Info.Name]
		if !ok {
			continue
		}
		redacted = matcher.redact(redacted)
	}
	return redacted
}

// redactIgnorePhrasesForEvaluation looks up cached matchers and delegates to
// redactIgnorePhrasesForEvaluationWithMatchers, for ValidateRequest/ValidateResponse.
func (s *Service) redactIgnorePhrasesForEvaluation(originalPayload string, policies []types.Policy, logPrefix, sessionID string) (payloadForEvaluation, preRedactionPayload string) {
	s.cache.mu.RLock()
	matchersByPolicy := s.cache.ignorePhraseMatchersByPolicy
	s.cache.mu.RUnlock()
	return s.redactIgnorePhrasesForEvaluationWithMatchers(originalPayload, policies, matchersByPolicy, logPrefix, sessionID)
}

// redactIgnorePhrasesForEvaluationWithMatchers is the cache-independent core, used
// directly by ValidateRequestWithPolicy (playground), which never goes through the
// policy cache. Returns the payload to evaluate and the untouched original — pass both
// to reconcileIgnorePhraseRedaction afterward.
func (s *Service) redactIgnorePhrasesForEvaluationWithMatchers(originalPayload string, policies []types.Policy, matchersByPolicy map[string]*ignorePhraseMatcher, logPrefix, sessionID string) (payloadForEvaluation, preRedactionPayload string) {
	if len(matchersByPolicy) == 0 {
		return originalPayload, originalPayload
	}
	redacted := redactIgnorePhrasesForPolicies(originalPayload, policies, matchersByPolicy)
	if redacted == originalPayload {
		return originalPayload, originalPayload
	}
	s.logger.Info(logPrefix+" - ignore-phrase redaction applied for evaluation",
		zap.String("sessionID", sessionID))
	return redacted, originalPayload
}

// reconcileIgnorePhraseRedaction ensures the real origin gets the real text. If nothing
// was redacted, modifiedPayload passes through untouched. Otherwise the untouched
// original is returned, unless another detector's own masking also changed the payload
// — then that modified copy is forwarded as-is (no marker left to restore just our span).
func (s *Service) reconcileIgnorePhraseRedaction(modifiedPayload, payloadForEvaluation, preRedactionPayload, logPrefix, sessionID string) string {
	if payloadForEvaluation == preRedactionPayload {
		return modifiedPayload
	}
	if modifiedPayload != "" && modifiedPayload != payloadForEvaluation {
		s.logger.Info(logPrefix+" - another detector modified the payload on top of ignore-phrase redaction; forwarding that modified copy as-is",
			zap.String("sessionID", sessionID))
		return modifiedPayload
	}
	return preRedactionPayload
}
