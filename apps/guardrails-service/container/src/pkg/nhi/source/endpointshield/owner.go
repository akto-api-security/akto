package endpointshield

import (
	"strings"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
)

// Owner pairing: an agent's credentials and its login usually live in different files (Claude Code:
// Keychain or ~/.claude/.credentials.json vs ~/.claude.json). Both are uploaded by the same OS user's
// agent, so the login found for (device, home, agent) owns that agent's identities there.

const keychainPathPrefix = "keychain:"

func loginKey(deviceID, home, agent string) string { return deviceID + "|" + home + "|" + agent }

func (s *Source) setLogin(deviceID, home, agent, email string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.logins[loginKey(deviceID, home, agent)] = email
}

func (s *Source) login(deviceID, home, agent string) string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.logins[loginKey(deviceID, home, agent)]
}

// loginsFirst orders a page so login files are parsed before the credentials they own.
func (s *Source) loginsFirst(results []dbabstractor.FileInspectionResult) []dbabstractor.FileInspectionResult {
	ordered := make([]dbabstractor.FileInspectionResult, 0, len(results))
	var rest []dbabstractor.FileInspectionResult
	for _, r := range results {
		if t, ok := s.lookupTarget(r.RuleID); ok && t.Login != nil {
			ordered = append(ordered, r)
		} else {
			rest = append(rest, r)
		}
	}
	return append(ordered, rest...)
}

// homeOf is the home of the OS user whose agent produced the result: reported by the shield, or
// (older shields) derived from a "~/..." target's expanded path.
func homeOf(r dbabstractor.FileInspectionResult, t Target, m dbabstractor.FileInspectionMatch) string {
	if r.UserHome != "" {
		return r.UserHome
	}
	if rel, ok := strings.CutPrefix(t.Path, "~"); ok && strings.HasSuffix(m.Path, rel) {
		return strings.TrimSuffix(m.Path, rel)
	}
	return ""
}

func (s *Source) logInfo(msg string, keysAndValues ...any) {
	s.logger.Sugar().Infow(msg, keysAndValues...)
}
