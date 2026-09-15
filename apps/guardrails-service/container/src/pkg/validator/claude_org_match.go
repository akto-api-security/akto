package validator

import (
	"regexp"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"go.uber.org/zap"
)

// Org-scoped guardrail targeting for Claude surfaces.
//
// A policy's UserMetadata rows are matched by email, but one person's email is the same in every
// org they belong to — so an email-only match fires a policy scoped to their work org while they
// are using a personal one, and vice versa. Where the row's UserId encodes an org, this narrows
// the match to requests actually coming from that org.

// claudeSurfaces maps a host substring to the agentType key holding that surface's login.
//
// Three host labels resolve to two logins: Cowork runs inside Claude Desktop and shares its
// session, so the agent reports no separate claude-cowork login. The CLI's login lives under
// "claude-cli-user" (claude-cli-local / -project / -enterprise are config scopes, not logins).
//
// This table is duplicated in com.akto.utils.elasticsearch.AgentQueryRecord on the ingestion side,
// which stamps the same org onto serviceId. Neither can import the other; keep them in step, and
// see TestClaudeSurfaceMappingMatchesIngestion for the pin.
// Both "claude-cli" and "claudecli" appear as host labels depending on how the collection was
// registered; they are mutually exclusive substrings, so order here is irrelevant.
var claudeSurfaces = [][2]string{
	{"claude-desktop", "claude-desktop"},
	{"claude-cowork", "claude-desktop"},
	{"claude-cli", "claude-cli-user"},
	{"claudecli", "claude-cli-user"},
}

// claudeLoginKeyForHost returns the agentLogins key for whichever Claude surface the host names,
// or "" when the host is not a Claude surface at all.
//
// This is the gate for the whole feature: org scoping exists because one Claude account spans
// several orgs, which is not true of the other agents, so anything that is not a Claude host is
// left on plain email matching.
func claudeLoginKeyForHost(host string) string {
	if host == "" {
		return ""
	}
	for _, surface := range claudeSurfaces {
		if strings.Contains(host, surface[0]) {
			return surface[1]
		}
	}
	return ""
}

// uuidRE is the 8-4-4-4-12 shape. Used to decide whether a UserId's trailing segment really is an
// org uuid, so an unrelated id like "okta_12345" is not mistaken for one.
var uuidRE = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// orgUUIDFromUserID pulls the org uuid off a composite id of the form "<email>_<orgUuid>",
// returning "" when the id carries no org.
//
// Split on the LAST underscore, never the first: email local-parts legally contain underscores
// ("first_last@corp.com_<uuid>"), and a first-underscore split would silently yield a wrong org —
// a policy that never fires, with nothing in the logs to say why. A uuid contains no underscore,
// so the last one is always the separator.
func orgUUIDFromUserID(userID string) string {
	i := strings.LastIndex(userID, "_")
	if i < 0 {
		return ""
	}
	candidate := userID[i+1:]
	if !uuidRE.MatchString(candidate) {
		return ""
	}
	return candidate
}

// claudeOrgForHost returns the org the given device is currently working in on whichever Claude
// surface the host names, or "" when that cannot be determined.
func claudeOrgForHost(host, deviceLabel string, infoMap map[string]map[string]dbabstractor.ClaudeDesktopInfo) string {
	loginKey := claudeLoginKeyForHost(host)
	if loginKey == "" || deviceLabel == "" || infoMap == nil {
		return ""
	}
	byAgentType, ok := infoMap[deviceLabel]
	if !ok {
		return ""
	}
	return byAgentType[loginKey].OrganizationUUID
}

// rowsMatchOrg reports whether any UserMetadata row encodes liveOrg.
//
// Split out from orgMatchesAny so the comparison is testable without standing up a Service and its
// caches. Rows carrying no org (every policy written before org scoping, and every identity that
// exists only via module_info reporting) simply do not contribute a match.
func rowsMatchOrg(rows []types.AgenticUsers, liveOrg string) bool {
	if liveOrg == "" {
		return false
	}
	for i := range rows {
		if policyOrg := orgUUIDFromUserID(rows[i].UserId); policyOrg != "" &&
			strings.EqualFold(policyOrg, liveOrg) {
			return true
		}
	}
	return false
}

// orgMatchesAny reports whether the request's live Claude org is one of the orgs this policy's
// UserMetadata rows name — an independent way for a policy to match, ORed alongside device label
// and user email.
//
// Note the polarity, which is the opposite of a narrowing check: as an OR term, an undeterminable
// org must return false. Returning true would make every policy match every request the moment the
// device map went missing — so a non-Claude host, an unresolvable device and a failed fetch all
// contribute nothing here, leaving device and email to decide on their own.
//
// Because this ORs rather than narrows, a policy naming one user in an org applies to that whole
// org's Claude traffic, not only to that person.
func (s *Service) orgMatchesAny(rows []types.AgenticUsers, host, deviceLabel string) bool {
	// Gate on the host first. Every other agent (Cursor, Copilot, Codex, ...) has one identity per
	// account with no org dimension, so there is nothing to scope by — and checking here means a
	// non-Claude request never parses an id or touches the device-info cache at all.
	if claudeLoginKeyForHost(host) == "" {
		return false
	}

	infoMap, err := s.getDeviceClaudeInfoMap()
	if err != nil {
		s.logger.Warn("claude org match: device info map unavailable, org term contributes no match",
			zap.String("deviceLabel", deviceLabel), zap.Error(err))
		return false
	}

	liveOrg := claudeOrgForHost(host, deviceLabel, infoMap)
	if liveOrg == "" {
		s.logger.Debug("claude org match: no live org for device/surface",
			zap.String("deviceLabel", deviceLabel), zap.String("host", host))
		return false
	}

	matched := rowsMatchOrg(rows, liveOrg)
	s.logger.Debug("claude org match",
		zap.String("deviceLabel", deviceLabel),
		zap.String("host", host),
		zap.String("liveOrg", liveOrg),
		zap.Bool("matched", matched))
	return matched
}

// getDeviceClaudeInfoMap returns the cached device -> surface -> login map, refreshing it when
// stale. Cached because it is consulted on the request path; refreshed roughly every minute
// (CLAUDE_INFO_REFRESH_INTERVAL_SEC) because switching org in Claude Desktop changes which
// user-targeted policies apply, and that should not wait on the policy cache's slower clock.
//
// A failed refresh with a populated cache serves the stale copy rather than failing the match —
// same trade-off getMcpAllowedHostList makes.
func (s *Service) getDeviceClaudeInfoMap() (map[string]map[string]dbabstractor.ClaudeDesktopInfo, error) {
	refreshInterval := time.Duration(s.config.ClaudeInfoRefreshIntervalSec) * time.Second

	s.claudeInfoCache.mu.RLock()
	if !s.claudeInfoCache.lastFetched.IsZero() && time.Since(s.claudeInfoCache.lastFetched) < refreshInterval {
		m := s.claudeInfoCache.byDevice
		s.claudeInfoCache.mu.RUnlock()
		return m, nil
	}
	s.claudeInfoCache.mu.RUnlock()

	v, err, _ := s.claudeInfoRefreshGroup.Do("claude-info-map", func() (interface{}, error) {
		s.claudeInfoCache.mu.Lock()
		defer s.claudeInfoCache.mu.Unlock()

		if !s.claudeInfoCache.lastFetched.IsZero() && time.Since(s.claudeInfoCache.lastFetched) < refreshInterval {
			return s.claudeInfoCache.byDevice, nil
		}

		fetched, fetchErr := s.dbClient.FetchDeviceClaudeInfoMap()
		if fetchErr != nil {
			return nil, fetchErr
		}
		s.claudeInfoCache.byDevice = fetched
		s.claudeInfoCache.lastFetched = time.Now()
		s.logger.Info("Claude device info cache refreshed", zap.Int("devices", len(fetched)))
		return fetched, nil
	})
	if err != nil {
		s.claudeInfoCache.mu.RLock()
		stale := s.claudeInfoCache.byDevice
		s.claudeInfoCache.mu.RUnlock()
		if len(stale) > 0 {
			s.logger.Warn("Claude device info refresh failed, using stale cache", zap.Error(err))
			return stale, nil
		}
		return nil, err
	}
	return v.(map[string]map[string]dbabstractor.ClaudeDesktopInfo), nil
}
