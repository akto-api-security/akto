package validator

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/auth"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
	"golang.org/x/sync/singleflight"
)

// policyRefreshResult bundles refreshed policy cache fields for singleflight callers.
type policyRefreshResult struct {
	policies      []types.Policy
	auditPolicies map[string]*types.AuditPolicy
	compiledRules map[string]*regexp.Regexp
	hasAuditRules bool
}

// policyCache holds cached policies and their metadata
type policyCache struct {
	policies                     []types.Policy
	auditPolicies                map[string]*types.AuditPolicy
	compiledRules                map[string]*regexp.Regexp
	hasAuditRules                bool
	blockedHosts                 []mcp.BlockedHostRule
	ignorePhraseMatchersByPolicy map[string]*ignorePhraseMatcher // keyed by policy name, pre-compiled
	lastFetched                  time.Time
	mu                           sync.RWMutex
}

// collectionTag represents a single key-value tag on a collection
type collectionTag struct {
	KeyName string `json:"keyName"`
	Value   string `json:"value"`
}

// collectionTagsCache caches collection tags keyed by host name
type collectionTagsCache struct {
	byHostName  map[string]map[string]string // hostName -> tagKey -> tagValue
	lastFetched time.Time
	mu          sync.RWMutex
}

type mcpListCache struct {
	mcpAllowedList []types.McpAllowedList
	lastFetched    time.Time
	mu             sync.RWMutex
}

// Service handles payload validation using akto-gateway library
type Service struct {
	config                *config.Config
	dbClient              *dbabstractor.Client
	processor             mcp.RequestProcessor // Default processor (skipThreat=false)
	logger                *zap.Logger
	cache                 *policyCache
	mcpListCache          *mcpListCache
	collectionTagsCache   *collectionTagsCache
	sessionMgr            *session.SessionManager // Our session manager implementation for session tracking
	schemaFetcher         *SchemaFetcher
	policyRefreshGroup    singleflight.Group
	allowlistRefreshGroup singleflight.Group
}

// NewService creates a new validator service
func NewService(cfg *config.Config, logger *zap.Logger) (*Service, error) {
	// Create database abstractor client
	dbClient := dbabstractor.NewClient(logger)

	// Create validator
	validator := mcp.NewPolicyValidator()

	// Create ingestor (can be nil if not using ingestion)
	var ingestor mcp.DataIngestor = nil

	// Create session manager (can be nil if not using sessions)
	var sessionMgr mcp.SessionManagerInterface = nil

	// Create default processor with validator, ingestor, and sessionManager
	// Note: skipThreat is now controlled per-request via API parameter, not at service level
	// Default processor created with skipThreat=false (will report threats)
	// Per-request processors will be created on-demand with the requested skipThreat value
	defaultProcessor := mcp.NewCommonMCPProcessor(
		validator,
		ingestor,
		sessionMgr,
		"",    // sessionID - empty for our use case
		"",    // projectName - empty for our use case
		false, // skipThreat - default to false, will be overridden per-request if needed
	)

	// Initialize session manager if enabled
	var sessionManager *session.SessionManager
	if cfg.SessionEnabled && cfg.DatabaseAbstractorURL != "" && cfg.ThreatBackendURL != "" {
		syncInterval := time.Duration(cfg.SessionSyncIntervalMin) * time.Minute
		var err error
		sessionManager, err = session.NewSessionManager(
			cfg.DatabaseAbstractorURL,
			cfg.DatabaseAbstractorToken,
			cfg.ThreatBackendURL,
			cfg.ThreatBackendToken,
			syncInterval,
			logger,
		)
		if err != nil {
			logger.Warn("Failed to initialize session manager", zap.Error(err))
			sessionManager = nil
		} else {
			sessionManager.Start()
			logger.Info("Session manager started",
				zap.Duration("syncInterval", syncInterval),
				zap.String("cyborgURL", cfg.DatabaseAbstractorURL),
				zap.String("tbsURL", cfg.ThreatBackendURL))
		}
	}

	schemaFetcher := NewSchemaFetcher(dbClient, time.Duration(cfg.PolicyRefreshIntervalMin)*time.Minute, logger)

	LogFieldMappingStartup()

	svc := &Service{
		config:              cfg,
		dbClient:            dbClient,
		processor:           defaultProcessor,
		logger:              logger,
		cache:               &policyCache{},
		mcpListCache:        &mcpListCache{},
		collectionTagsCache: &collectionTagsCache{byHostName: make(map[string]map[string]string)},
		sessionMgr:          sessionManager,
		schemaFetcher:       schemaFetcher,
	}
	bp := mcp.GetScanBackpressureSnapshot()
	logger.Info("Scan backpressure breaker active (mcp processor, remote-scanner boundary)",
		zap.Bool("enabled", bp.Enabled),
		zap.Float64("thresholdMs", bp.ThresholdMs),
		zap.Int("minSamples", bp.MinSamples),
		zap.Float64("ttlSeconds", bp.TTLSeconds))
	go func() {
		if _, err := svc.getMcpAllowedHostList(); err != nil {
			logger.Warn("Warm MCP allowlist cache failed", zap.Error(err))
		}
		if _, _, _, _, err := svc.getCachedPolicies(""); err != nil {
			logger.Warn("Warm policy cache failed", zap.Error(err))
		}
	}()
	return svc, nil
}

// policyNames extracts policy names for debug logging
func policyNames(policies []types.Policy) []string {
	names := make([]string, 0, len(policies))
	for _, p := range policies {
		name := p.Info.Name
		if name == "" {
			name = "(unnamed)"
		}
		names = append(names, name)
	}
	return names
}

func (s *Service) filterPoliciesByContextSource(policies []types.Policy, contextSource string) []types.Policy {
	s.logger.Debug("filterPoliciesByContextSource - input",
		zap.String("contextSource", contextSource),
		zap.Int("totalPolicies", len(policies)))

	if contextSource == "" {
		s.logger.Debug("filterPoliciesByContextSource - contextSource empty, returning all policies")
		return policies
	}

	if contextSource == string(types.ContextSourceAgentic) {
		filtered := make([]types.Policy, 0)
		for _, policy := range policies {
			if policy.ContextSource == "" || policy.ContextSource == "AGENTIC" {
				filtered = append(filtered, policy)
			}
		}
		s.logger.Debug("filterPoliciesByContextSource - filtered for AGENTIC",
			zap.Int("filteredCount", len(filtered)),
			zap.Strings("policyNames", policyNames(filtered)))
		return filtered
	}

	filtered := make([]types.Policy, 0)
	for _, policy := range policies {
		if policy.ContextSource == contextSource {
			filtered = append(filtered, policy)
		}
	}
	s.logger.Debug("filterPoliciesByContextSource - filtered for specific context",
		zap.String("contextSource", contextSource),
		zap.Int("filteredCount", len(filtered)),
		zap.Strings("policyNames", policyNames(filtered)))
	return filtered
}

// filterPoliciesByMcpServer filters policies by MCP server name.
// Mirrors the logic in PolicyManager.GetPoliciesForMcpServer from the mcp-endpoint-shield library:
// policies with an empty server map are skipped (not configured for any server).
// filterPoliciesByMcpServer filters policies to those applicable to the given MCP server name.
// YAML policies always pass through. If mcpServerName is empty, all policies are returned unchanged.
func (s *Service) filterPoliciesByMcpServer(policies []types.Policy, mcpServerName string) []types.Policy {
	if mcpServerName == "" {
		return policies
	}
	mcpServerNameLower := strings.ToLower(mcpServerName)
	filtered := make([]types.Policy, 0, len(policies))
	for _, policy := range policies {
		if policy.IsYamlPolicy {
			filtered = append(filtered, policy)
			continue
		}
		if policy.ApplyToAllServers {
			filtered = append(filtered, policy)
			continue
			// if policy.Behaviour == "block" && (policy.ContextSource == "" || policy.ContextSource == "AGENTIC") {
			// 	// block+applyToAll in AGENTIC context: Java resolves the server list — fall through to match
			// } else {
			// 	filtered = append(filtered, policy)
			// 	continue
			// }
		}
		combinedServers := make(map[string]struct{}, len(policy.SelectedMcpServers)+len(policy.SelectedAgentServers))
		for k, v := range policy.SelectedMcpServers {
			combinedServers[k] = v
		}
		for k, v := range policy.SelectedAgentServers {
			combinedServers[k] = v
		}
		if len(combinedServers) == 0 {
			continue // not configured for any server
		}
		for serverName := range combinedServers {
			storedLower := strings.ToLower(serverName)
			// Exact match: full hostname stored (old Argus) or short key equals incoming.
			if storedLower == mcpServerNameLower {
				filtered = append(filtered, policy)
				break
			}
			// Suffix match: stored value is a trailing dot-segment — covers service names
			// ('filesystem'), multi-segment LLM domains ('chatgpt.com'), and old device-stripped
			// Atlas keys ('cursor.filesystem') since ".cursor.filesystem" is a valid suffix.
			if strings.HasSuffix(mcpServerNameLower, "."+storedLower) {
				filtered = append(filtered, policy)
				break
			}
			// Middle-segment match: stored agent platform key sits between device-id and service name.
			// e.g. stored='cursor' matches 'device.cursor.filesystem'.
			if strings.Contains(mcpServerNameLower, "."+storedLower+".") {
				filtered = append(filtered, policy)
				break
			}
		}
	}
	return filtered
}

// filterPoliciesByDeviceId filters policies by the device label embedded in the MCP server name.
// The device label is the first dot-delimited segment of "{deviceLabel}.{clientType}.{host}".
// Policies with an empty ApplyToDeviceIds list apply to all devices and always pass through.
// If mcpServerName is empty or has no device prefix, all policies are returned unchanged.
func (s *Service) filterPoliciesByDeviceId(policies []types.Policy, mcpServerName string) []types.Policy {
	if mcpServerName == "" {
		return policies
	}
	deviceLabel := ""
	if i := strings.IndexByte(mcpServerName, '.'); i > 0 {
		deviceLabel = mcpServerName[:i]
	}
	if deviceLabel == "" {
		return policies
	}
	filtered := make([]types.Policy, 0, len(policies))
	for _, p := range policies {
		if len(p.ApplyToDeviceIds) == 0 {
			filtered = append(filtered, p)
			continue
		}
		for _, id := range p.ApplyToDeviceIds {
			if id == deviceLabel {
				filtered = append(filtered, p)
				break
			}
		}
	}
	return filtered
}

func (s *Service) getMcpAllowedHostList() ([]types.McpAllowedList, error) {
	refreshInterval := time.Duration(s.config.McpAllowedListRefreshIntervalMin) * time.Minute

	s.mcpListCache.mu.RLock()
	if !s.mcpListCache.lastFetched.IsZero() && time.Since(s.mcpListCache.lastFetched) < refreshInterval {
		list := s.mcpListCache.mcpAllowedList
		s.mcpListCache.mu.RUnlock()
		s.logger.Debug("Using cached MCP allowlist", zap.Int("count", len(list)))
		return list, nil
	}
	s.mcpListCache.mu.RUnlock()

	v, err, _ := s.allowlistRefreshGroup.Do("mcp-allowlist", func() (interface{}, error) {
		s.mcpListCache.mu.Lock()
		defer s.mcpListCache.mu.Unlock()

		if !s.mcpListCache.lastFetched.IsZero() && time.Since(s.mcpListCache.lastFetched) < refreshInterval {
			return s.mcpListCache.mcpAllowedList, nil
		}

		rawResp, fetchErr := s.dbClient.FetchMcpAllowedHostList()
		if fetchErr != nil {
			return nil, fetchErr
		}

		var mcpHostAllowedList struct {
			McpAllowlist []types.McpAllowedList `json:"mcpAllowlist"`
		}
		if unmarshalErr := json.Unmarshal(rawResp, &mcpHostAllowedList); unmarshalErr != nil {
			return nil, fmt.Errorf("failed to unmarshal outer response: %w", unmarshalErr)
		}

		s.mcpListCache.mcpAllowedList = mcpHostAllowedList.McpAllowlist
		s.mcpListCache.lastFetched = time.Now()
		s.logger.Info("MCP allowlist cache refreshed", zap.Int("count", len(mcpHostAllowedList.McpAllowlist)))
		return mcpHostAllowedList.McpAllowlist, nil
	})
	if err != nil {
		s.mcpListCache.mu.RLock()
		if len(s.mcpListCache.mcpAllowedList) > 0 {
			stale := s.mcpListCache.mcpAllowedList
			s.mcpListCache.mu.RUnlock()
			s.logger.Warn("MCP allowlist refresh failed, using stale cache", zap.Error(err))
			return stale, nil
		}
		s.mcpListCache.mu.RUnlock()
		s.logger.Error("Failed to fetch MCP allowlist from database-abstractor", zap.Error(err))
		return nil, err
	}
	return v.([]types.McpAllowedList), nil
}

func (s *Service) getCachedPolicies(contextSource string) ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, error) {
	refreshInterval := time.Duration(s.config.PolicyRefreshIntervalMin) * time.Minute

	// Check if cache is valid
	s.cache.mu.RLock()
	if !s.cache.lastFetched.IsZero() && time.Since(s.cache.lastFetched) < refreshInterval {
		// Filter policies by contextSource from cached data
		filteredPolicies := s.filterPoliciesByContextSource(s.cache.policies, contextSource)
		auditPolicies := s.cache.auditPolicies
		compiledRules := s.cache.compiledRules
		hasAuditRules := s.cache.hasAuditRules
		// RUnlock so that multiple goroutines can read the cached policies
		s.cache.mu.RUnlock()
		s.logger.Debug("Using cached policies",
			zap.Time("lastFetched", s.cache.lastFetched),
			zap.String("contextSource", contextSource),
			zap.Int("totalPoliciesCount", len(s.cache.policies)),
			zap.Int("filteredPoliciesCount", len(filteredPolicies)))
		return filteredPolicies, auditPolicies, compiledRules, hasAuditRules, nil
	}
	s.cache.mu.RUnlock()

	// Cache is stale or empty, coalesce refresh across concurrent requests.
	v, refreshErr, _ := s.policyRefreshGroup.Do("guardrail-policies", func() (interface{}, error) {
		policies, auditPolicies, compiledRules, hasAuditRules, err := s.refreshPolicies()
		if err != nil {
			return nil, err
		}
		return &policyRefreshResult{
			policies:      policies,
			auditPolicies: auditPolicies,
			compiledRules: compiledRules,
			hasAuditRules: hasAuditRules,
		}, nil
	})
	if refreshErr != nil {
		s.cache.mu.RLock()
		hasStale := !s.cache.lastFetched.IsZero() && len(s.cache.policies) > 0
		if hasStale {
			filteredPolicies := s.filterPoliciesByContextSource(s.cache.policies, contextSource)
			staleAudit := s.cache.auditPolicies
			staleRules := s.cache.compiledRules
			staleHasAudit := s.cache.hasAuditRules
			lastFetched := s.cache.lastFetched
			s.cache.mu.RUnlock()
			s.logger.Warn("Policy refresh failed, using stale cache",
				zap.String("contextSource", contextSource),
				zap.Time("lastFetched", lastFetched),
				zap.Error(refreshErr))
			return filteredPolicies, staleAudit, staleRules, staleHasAudit, nil
		}
		s.cache.mu.RUnlock()
		return nil, nil, nil, false, refreshErr
	}

	bundle := v.(*policyRefreshResult)
	filteredPolicies := s.filterPoliciesByContextSource(bundle.policies, contextSource)
	return filteredPolicies, bundle.auditPolicies, bundle.compiledRules, bundle.hasAuditRules, nil
}

// refreshPolicies fetches fresh policies from database and updates the cache
// Fetches ALL policies without filtering by contextSource
func (s *Service) refreshPolicies() ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, error) {
	s.cache.mu.Lock()
	defer s.cache.mu.Unlock()

	// Double-check: another goroutine might have refreshed while we waited for the lock
	refreshInterval := time.Duration(s.config.PolicyRefreshIntervalMin) * time.Minute
	if !s.cache.lastFetched.IsZero() && time.Since(s.cache.lastFetched) < refreshInterval {
		s.logger.Debug("Cache was refreshed by another goroutine, using cached policies")
		return s.cache.policies, s.cache.auditPolicies, s.cache.compiledRules, s.cache.hasAuditRules, nil
	}

	s.logger.Info("Refreshing policies cache - fetching ALL policies")

	policies, auditPolicies, compiledRules, hasAuditRules, blockedHostRules, ignorePhraseMatchersByPolicy, err := s.fetchAndParsePolicies()
	if err != nil {
		return nil, nil, nil, false, err
	}

	// Update cache with ALL policies
	s.cache.policies = policies
	s.cache.auditPolicies = auditPolicies
	s.cache.compiledRules = compiledRules
	s.cache.hasAuditRules = hasAuditRules
	s.cache.blockedHosts = blockedHostRules
	s.cache.ignorePhraseMatchersByPolicy = ignorePhraseMatchersByPolicy
	s.cache.lastFetched = time.Now()

	s.logger.Info("Policy cache refreshed with ALL policies",
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Time("lastFetched", s.cache.lastFetched),
		zap.Any("policies", policies))

	return policies, auditPolicies, compiledRules, hasAuditRules, nil
}

// blockPersonalAccountPolicyName returns the name of the first policy that has
// BlockPersonalAccounts enabled, or ("", false) if none.
func blockPersonalAccountPolicyName(policies []types.Policy) (string, bool) {
	for _, p := range policies {
		if p.BlockPersonalAccounts {
			return p.Info.Name, true
		}
	}
	return "", false
}

// refreshCollectionTagsIfNeeded fetches all collections from the database abstractor and
// rebuilds the in-memory map of vxlan_id → tag key → tag value.
func (s *Service) refreshCollectionTagsIfNeeded() {
	refreshInterval := time.Duration(s.config.CollectionRefreshIntervalMin) * time.Minute

	s.collectionTagsCache.mu.RLock()
	fresh := !s.collectionTagsCache.lastFetched.IsZero() && time.Since(s.collectionTagsCache.lastFetched) < refreshInterval
	s.collectionTagsCache.mu.RUnlock()
	if fresh {
		return
	}

	s.collectionTagsCache.mu.Lock()
	defer s.collectionTagsCache.mu.Unlock()

	// Double-check after acquiring write lock
	if !s.collectionTagsCache.lastFetched.IsZero() && time.Since(s.collectionTagsCache.lastFetched) < refreshInterval {
		return
	}

	raw, err := s.dbClient.FetchApiCollections()
	if err != nil {
		s.logger.Warn("Failed to fetch API collections for tag cache", zap.Error(err))
		s.collectionTagsCache.lastFetched = time.Now()
		return
	}

	var response struct {
		ApiCollections []struct {
			HostName string          `json:"hostName"`
			TagsList []collectionTag `json:"tagsList"`
		} `json:"apiCollections"`
	}
	if err := json.Unmarshal(raw, &response); err != nil {
		s.logger.Warn("Failed to parse API collections response", zap.Error(err))
		s.collectionTagsCache.lastFetched = time.Now()
		return
	}

	byHostName := make(map[string]map[string]string, len(response.ApiCollections))
	for _, c := range response.ApiCollections {
		if c.HostName == "" {
			continue
		}
		tags := make(map[string]string, len(c.TagsList))
		for _, t := range c.TagsList {
			if t.KeyName != "" {
				tags[t.KeyName] = t.Value
			}
		}
		byHostName[c.HostName] = tags
	}

	s.collectionTagsCache.byHostName = byHostName
	s.collectionTagsCache.lastFetched = time.Now()
	s.logger.Info("Collection tag cache refreshed", zap.Int("collectionsCount", len(byHostName)))
}

// getLoginUserEmailType looks up the host from request headers in the collection tag cache
// and returns login-user-email-type, falling back to browser-llm-account-type.
// The account type is resolved ONLY from the collection's tags; if the host is not
// found in the collection cache, it returns "" (no fallback to the request tag).
func (s *Service) getLoginUserEmailType(reqHeaders map[string]string) string {
	host := extractHostHeader(reqHeaders)
	s.logger.Info("getLoginUserEmailType - host extracted", zap.String("host", host))
	if host == "" {
		return ""
	}

	s.collectionTagsCache.mu.RLock()
	tags, ok := s.collectionTagsCache.byHostName[host]
	cacheSize := len(s.collectionTagsCache.byHostName)
	s.collectionTagsCache.mu.RUnlock()

	s.logger.Info("getLoginUserEmailType - cache lookup",
		zap.String("host", host),
		zap.Bool("found", ok),
		zap.Int("cacheSize", cacheSize),
		zap.Any("tags", tags))

	if !ok {
		// Host has no matching collection — do not fall back to the request tag.
		s.logger.Info("getLoginUserEmailType - collection not found, returning empty", zap.String("host", host))
		return ""
	}

	if v := tags["login-user-email-type"]; v != "" {
		s.logger.Info("getLoginUserEmailType - returning login-user-email-type", zap.String("value", v))
		return v
	}
	if v := tags["browser-llm-account-type"]; v != "" {
		s.logger.Info("getLoginUserEmailType - returning browser-llm-account-type (cache)", zap.String("value", v))
		return v
	}

	return ""
}

// TODO: move reportAndBlockPersonalAccount to mcp library so threat reporting
// and validation live in one place alongside other policy enforcement.
func (s *Service) reportAndBlockPersonalAccount(_ context.Context, params *models.ValidateRequestParams, payloadToValidate, sessionID, requestID, policyName string) *mcp.ValidationResult {
	blockReason := "Blocked: personal accounts are not permitted by guardrail policy"

	if s.sessionMgr != nil && sessionID != "" {
		s.sessionMgr.TrackResponse(sessionID, requestID, blockReason, true)
		s.sessionMgr.UpdateBlockedReason(sessionID, blockReason)
	}

	if !params.EffectiveSkipThreat() {
		reqHeaders := make(map[string]string)
		if params.RequestHeaders != "" {
			json.Unmarshal([]byte(params.RequestHeaders), &reqHeaders)
		}
		statusCode := 0
		if params.StatusCode != "" {
			fmt.Sscanf(params.StatusCode, "%d", &statusCode)
		}
		go func() {
			if err := mcp.ReportThreat(
				context.Background(),
				payloadToValidate,
				"",
				types.ThreatMetadata{
					PolicyName:   policyName,
					RuleViolated: "BlockPersonalAccounts",
					Severity:     "MEDIUM",
					Reason:       blockReason,
				},
				params.IP,
				params.Path,
				params.Method,
				reqHeaders,
				nil,
				statusCode,
				types.ContextSource(params.ContextSource),
				extractHostHeader(reqHeaders),
				sessionID,
				"block",
			); err != nil {
				s.logger.Warn("Failed to report threat for personal account block", zap.String("policyName", policyName), zap.Error(err))
			}
		}()
	}

	return &mcp.ValidationResult{
		Allowed:   false,
		Reason:    blockReason,
		Behaviour: "block",
		Metadata: types.ThreatMetadata{
			PolicyName:   policyName,
			RuleViolated: "BlockPersonalAccounts",
			Severity:     "MEDIUM",
			Reason:       blockReason,
		},
	}
}

// checkBlockedHost evaluates the request against cached blocked-host rules via the mcp library.
// Returns a block ValidationResult on a match, or nil to allow.
func (s *Service) checkBlockedHost(params *models.ValidateRequestParams, valCtx *mcp.ValidationContext, payloadToValidate, sessionID, requestID string, policies []types.Policy) *mcp.ValidationResult {
	s.cache.mu.RLock()
	allRules := s.cache.blockedHosts
	s.cache.mu.RUnlock()
	if len(allRules) == 0 {
		return nil
	}

	// Narrow to rules from policies applicable to this server.
	activePolicyNames := make(map[string]struct{}, len(policies))
	for _, p := range policies {
		activePolicyNames[p.Info.Name] = struct{}{}
	}
	var scoped []mcp.BlockedHostRule
	for _, r := range allRules {
		if _, ok := activePolicyNames[r.PolicyName]; ok {
			scoped = append(scoped, r)
		}
	}
	if len(scoped) == 0 {
		return nil
	}

	reqHost := extractHostHeader(valCtx.RequestHeaders)
	s.logger.Info("[BLOCKED_HOST] running blocked-host check",
		zap.String("feature", "blocked-host-v1"),
		zap.String("host", reqHost),
		zap.String("path", params.Path),
		zap.String("contextSource", params.ContextSource),
		zap.Int("ruleCount", len(scoped)),
		zap.Strings("patterns", mcp.BlockedHostPatterns(scoped)))

	policyName, matchedPattern, blocked := mcp.CheckBlockedHosts(valCtx.Tag, reqHost, params.Path, params.ContextSource, scoped)
	if !blocked {
		return nil
	}

	s.logger.Warn("ValidateRequest - blocking request via blocked-host policy",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("host", reqHost),
		zap.String("pattern", matchedPattern),
		zap.String("policy", policyName),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))
	return s.reportAndBlockHost(params, valCtx, payloadToValidate, sessionID, requestID, policyName, matchedPattern)
}

func (s *Service) reportAndBlockHost(params *models.ValidateRequestParams, valCtx *mcp.ValidationContext, payloadToValidate, sessionID, requestID, policyName, matchedPattern string) *mcp.ValidationResult {
	reason := "Request blocked by guardrail policy (blocked host pattern: " + matchedPattern + ")"
	metadata := types.ThreatMetadata{
		PolicyName:   policyName,
		RuleViolated: "BlockedHost",
		Severity:     "HIGH",
		Reason:       reason,
	}

	if s.sessionMgr != nil && sessionID != "" {
		s.sessionMgr.TrackResponse(sessionID, requestID, reason, true)
		s.sessionMgr.UpdateBlockedReason(sessionID, reason)
	}

	if !params.EffectiveSkipThreat() {
		go func() {
			if err := mcp.ReportThreat(
				context.Background(),
				payloadToValidate,
				"",
				metadata,
				params.IP,
				params.Path,
				params.Method,
				valCtx.RequestHeaders,
				nil,
				valCtx.StatusCode,
				types.ContextSource(params.ContextSource),
				extractHostHeader(valCtx.RequestHeaders),
				sessionID,
				"block",
			); err != nil {
				s.logger.Warn("Failed to report threat for blocked host", zap.Error(err))
			}
		}()
	}

	return &mcp.ValidationResult{
		Allowed:         false,
		Modified:        false,
		ModifiedPayload: payloadToValidate,
		Reason:          reason,
		Metadata:        metadata,
		Behaviour:       "block",
	}
}

// extractHostHeader extracts the host header value from request headers
func extractHostHeader(headers map[string]string) string {
	if host, ok := headers["Host"]; ok {
		return host
	}
	if host, ok := headers["host"]; ok {
		return host
	}
	return ""
}

// fetchAndParsePolicies fetches policies from database abstractor and parses them
func (s *Service) fetchAndParsePolicies() ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, []mcp.BlockedHostRule, map[string]*ignorePhraseMatcher, error) {
	var rawGuardrailPolicies, rawAuditPolicies []byte

	g := new(errgroup.Group)
	g.Go(func() error {
		var err error
		rawGuardrailPolicies, err = s.dbClient.FetchGuardrailPolicies()
		if err != nil {
			return fmt.Errorf("failed to fetch guardrail policies: %w", err)
		}
		return nil
	})
	g.Go(func() error {
		var err error
		rawAuditPolicies, err = s.dbClient.FetchMcpAuditInfo()
		if err != nil {
			s.logger.Warn("Failed to fetch MCP audit info", zap.Error(err))
			rawAuditPolicies = []byte("{}")
		}
		return nil
	})
	if err := g.Wait(); err != nil {
		return nil, nil, nil, false, nil, nil, err
	}

	s.logger.Debug("Raw guardrail policies response",
		zap.Int("size", len(rawGuardrailPolicies)),
		zap.String("raw", string(rawGuardrailPolicies)))

	// Parse the wrapper object containing guardrailPolicies array
	var response struct {
		GuardrailPolicies []*mcp.GuardrailsPolicy `json:"guardrailPolicies"`
	}
	if err := json.Unmarshal(rawGuardrailPolicies, &response); err != nil {
		s.logger.Error("Failed to parse guardrail policies",
			zap.Error(err),
			zap.String("rawResponse", string(rawGuardrailPolicies)))
		return nil, nil, nil, false, nil, nil, fmt.Errorf("failed to parse guardrail policies: %w", err)
	}

	s.logger.Info("Parsed guardrail policies",
		zap.Int("count", len(response.GuardrailPolicies)))

	// Convert GuardrailsPolicy to Policy using library function
	var policies []types.Policy
	for _, gp := range response.GuardrailPolicies {
		if gp.Active { // Only include active policies
			policy := mcp.ConvertGuardrailsToPolicy(gp)
			policies = append(policies, policy)
		}
	}

	// Compile blocked-host rules from the converted policies (uses policy.BlockedHosts patterns).
	blockedHostRules := mcp.CompileBlockedHostRules(policies)
	s.logger.Info("[BLOCKED_HOST] compiled blocked-host rules from policies",
		zap.String("feature", "blocked-host-v1"),
		zap.Int("ruleCount", len(blockedHostRules)),
		zap.Strings("patterns", mcp.BlockedHostPatterns(blockedHostRules)))

	s.logger.Debug("Raw audit policies response",
		zap.Int("size", len(rawAuditPolicies)),
		zap.String("raw", string(rawAuditPolicies)))

	// Parse the wrapper object containing mcpAuditInfoList array
	var auditResponse struct {
		McpAuditInfoList []*types.AuditPolicy `json:"mcpAuditInfoList"`
	}
	if err := json.Unmarshal(rawAuditPolicies, &auditResponse); err != nil {
		s.logger.Warn("Failed to parse MCP audit info",
			zap.Error(err),
			zap.String("rawResponse", string(rawAuditPolicies)))
		auditResponse.McpAuditInfoList = []*types.AuditPolicy{}
	}

	// Convert array to map keyed by (resourceName, serverIdentity). Each
	// row is indexed under both its raw mcpHost and its device-stripped
	// form so a single probe at validation time matches both legacy
	// device-prefixed rows and already-canonical rows. See
	// mcp.IndexAuditPolicy for the rationale.
	auditPolicies := make(map[string]*types.AuditPolicy)
	for _, policy := range auditResponse.McpAuditInfoList {
		mcp.IndexAuditPolicy(auditPolicies, policy)
	}

	s.logger.Info("Parsed audit policies",
		zap.Int("count", len(auditPolicies)))

	// Compile regex rules from policies
	compiledRules := make(map[string]*regexp.Regexp)
	for _, policy := range policies {
		// Compile request payload rules
		for _, rule := range policy.Filters.RequestPayload {
			if rule.Type == "regex" && rule.Pattern != "" {
				if compiled, err := regexp.Compile(rule.Pattern); err == nil {
					compiledRules[rule.Pattern] = compiled
				} else {
					s.logger.Warn("Failed to compile regex pattern",
						zap.String("pattern", rule.Pattern),
						zap.Error(err))
				}
			}
		}
		// Compile response payload rules
		for _, rule := range policy.Filters.ResponsePayload {
			if rule.Type == "regex" && rule.Pattern != "" {
				if compiled, err := regexp.Compile(rule.Pattern); err == nil {
					compiledRules[rule.Pattern] = compiled
				} else {
					s.logger.Warn("Failed to compile regex pattern",
						zap.String("pattern", rule.Pattern),
						zap.Error(err))
				}
			}
		}
	}

	hasAuditRules := len(auditPolicies) > 0

	// Compile the ignore-phrase matchers once here (refresh time), not per request —
	// see compileIgnorePhraseMatchersByPolicy and groupPoliciesForIsolatedRedaction.
	ignorePhraseMatchersByPolicy := compileIgnorePhraseMatchersByPolicy(policies)

	s.logger.Info("Successfully fetched and parsed policies",
		zap.Int("guardrailPolicies", len(policies)),
		zap.Int("auditPolicies", len(auditPolicies)),
		zap.Int("compiledRules", len(compiledRules)),
		zap.Int("ignorePhrasePolicies", len(ignorePhraseMatchersByPolicy)))

	return policies, auditPolicies, compiledRules, hasAuditRules, blockedHostRules, ignorePhraseMatchersByPolicy, nil
}

// validationContextFromParams builds mcp.ValidationContext from traffic params and explicit payload strings for the context.
// Callers pass requestPayloadForCtx / responsePayloadForCtx as the strings stored on the context (e.g. summary-injected request body for ProcessRequest).
func (s *Service) validationContextFromParams(
	params *models.ValidateRequestParams,
	sessionID string,
	requestPayloadForCtx string,
	responsePayloadForCtx string,
	logPrefix string,
	mcpAllowedHostList []types.McpAllowedList,
	compiledRules map[string]*regexp.Regexp,
) *mcp.ValidationContext {
	// Parse headers and status code
	reqHeaders := make(map[string]string)
	respHeaders := make(map[string]string)
	if params.RequestHeaders != "" {
		if err := json.Unmarshal([]byte(params.RequestHeaders), &reqHeaders); err != nil {
			s.logger.Warn(logPrefix+" - failed to parse request headers, proceeding with empty headers",
				zap.String("sessionID", sessionID),
				zap.Error(err))
		}
	}
	if params.ResponseHeaders != "" {
		if err := json.Unmarshal([]byte(params.ResponseHeaders), &respHeaders); err != nil {
			s.logger.Warn(logPrefix+" - failed to parse response headers, proceeding with empty headers",
				zap.String("sessionID", sessionID),
				zap.Error(err))
		}
	}

	statusCode := 0
	if params.StatusCode != "" {
		fmt.Sscanf(params.StatusCode, "%d", &statusCode)
	}

	// Extract host header for McpServerName
	mcpServerName := extractHostHeader(reqHeaders)
	contextSource := params.ContextSource

	return &mcp.ValidationContext{
		IP:                 params.IP,
		DestIP:             params.DestIP,
		Endpoint:           params.Path,
		Method:             params.Method,
		RequestHeaders:     reqHeaders,
		ResponseHeaders:    respHeaders,
		StatusCode:         statusCode,
		RequestPayload:     requestPayloadForCtx,
		ResponsePayload:    responsePayloadForCtx,
		ContextSource:      types.ContextSource(contextSource),
		McpServerName:      mcpServerName,
		SessionID:          sessionID,
		AktoAccountID:      params.AktoAccountID,
		SkipThreat:         params.EffectiveSkipThreat(), // Set skipThreat directly in context
		Tag:                params.Tag,
		AllowedLists:       mcpAllowedHostList,
		CompiledRegexRules: compiledRules,
	}
}

// extractPayloadForValidation extracts configured JSON fields for guardrail evaluation.
// Priority: dashboard guardrailSchema → GUARDRAIL_FIELD_MAPPING env → raw payload.
func (s *Service) extractPayloadForValidation(payload, method, path string, isRequest bool) string {
	key := EndpointKey(method, path)

	fields := resolveFieldsForEndpoint(method, path, isRequest)
	if len(fields) == 0 {
		s.logger.Debug("[SchemaExtract] no field mapping for endpoint, using raw payload",
			zap.String("endpoint", key),
			zap.Bool("isRequest", isRequest))
		return payload
	}

	source := "dashboard"
	if gs, ok := GlobalGuardrailSchemaRegistry().Get(key); !ok || gs == nil ||
		(isRequest && !gs.HasRequestFields()) || (!isRequest && !gs.HasResponseFields()) {
		source = "env"
	}

	s.logger.Info("[SchemaExtract] attempting field extraction",
		zap.String("endpoint", key),
		zap.Bool("isRequest", isRequest),
		zap.String("source", source),
		zap.Int("fieldCount", len(fields)))

	var extracted string
	if source == "env" {
		extracted = ExtractContentFirst(payload, fields)
	} else {
		extracted = ExtractContent(payload, fields)
	}
	if extracted == "" {
		s.logger.Warn("[SchemaExtract] schema present but no fields matched payload, falling back to raw payload",
			zap.String("endpoint", key),
			zap.Bool("isRequest", isRequest),
			zap.Int("fieldCount", len(fields)),
			zap.Int("payloadLength", len(payload)))
		return payload
	}

	finalPayload := extracted
	if !strings.HasPrefix(strings.TrimSpace(extracted), "{") {
		if b, err := json.Marshal(map[string]string{"text": extracted}); err == nil {
			finalPayload = string(b)
		}
	}

	s.logger.Info("[SchemaExtract] extraction successful",
		zap.String("endpoint", key),
		zap.Bool("isRequest", isRequest),
		zap.Int("fieldCount", len(fields)),
		zap.Int("extractedLength", len(extracted)),
		zap.Int("finalPayloadLength", len(finalPayload)))

	return finalPayload
}

// BackpressureSnapshot exposes the scan breaker's current state for diagnostics.
// The breaker lives in the mcp processor (remote-scanner boundary); guardrails-
// service just surfaces its snapshot on GET /backpressure.
func (s *Service) BackpressureSnapshot() mcp.ScanBackpressureSnapshot {
	return mcp.GetScanBackpressureSnapshot()
}

// withValidationDeadline caps the synchronous validate path with a single
// absolute deadline (config.ValidationTimeoutMs). It cascades to every parallel
// policy goroutine, async scanner wait, and /scan call at once — because
// executeAsyncTasks honors a parent deadline and context.WithTimeout takes the
// earlier of parent/child — so the whole request fails open before the caller's
// client timeout fires. The returned cancel must always be called. Returns the
// ctx unchanged (with a no-op cancel) when disabled (<=0).
func (s *Service) withValidationDeadline(ctx context.Context) (context.Context, context.CancelFunc) {
	if s.config == nil || s.config.ValidationTimeoutMs <= 0 {
		return ctx, func() {}
	}
	return context.WithTimeout(ctx, time.Duration(s.config.ValidationTimeoutMs)*time.Millisecond)
}

// ValidateRequest validates a request payload against guardrail policies with session tracking
func (s *Service) ValidateRequest(ctx context.Context, params *models.ValidateRequestParams, sessionID string, requestID string) (*mcp.ValidationResult, error) {
	start := time.Now()
	payload := params.RequestPayload
	contextSource := params.ContextSource

	s.logger.Info("ValidateRequest - starting",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("account", params.AktoAccountID),
		zap.String("contextSource", contextSource),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.String("ip", params.IP),
		zap.String("destIp", params.DestIP),
		zap.String("statusCode", params.StatusCode),
		zap.String("source", params.Source),
		zap.String("direction", params.Direction),
		zap.String("tag", params.Tag),
		zap.String("aktoVxlanId", params.AktoVxlanID),
		zap.Bool("skipThreat", params.EffectiveSkipThreat()))

	if result, isMalicious := session.CheckAndHandleMaliciousSession(s.sessionMgr, s.logger, sessionID, requestID, payload); isMalicious {
		s.logger.Info("ValidateRequest - session already malicious, blocking request",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("sessionID", sessionID),
			zap.String("requestID", requestID))
		return result, nil
	}

	// Track request and generate summary asynchronously
	session.TrackRequestAndGenerateSummary(s.sessionMgr, s.logger, sessionID, requestID, payload)

	// Inject session summary into payload if available
	payloadToValidate := session.GetModifiedPayloadWithSummary(s.sessionMgr, s.logger, payload, sessionID)
	if payloadToValidate != payload {
		s.logger.Info("ValidateRequest - payload modified by session summary injection",
			zap.String("sessionID", sessionID))
	}

	s.schemaFetcher.RefreshIfNeeded()

	payloadToValidate = s.extractPayloadForValidation(payloadToValidate, params.Method, params.Path, true)
	s.logger.Info("ValidateRequest - payload prepared for validation",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("payloadToValidate", payloadToValidate))

	// Get cached policies (refreshes if stale)
	policiesStart := time.Now()
	policies, auditPolicies, compiledRules, hasAuditRules, err := s.getCachedPolicies(contextSource)
	if err != nil {
		s.logger.Error("ValidateRequest - failed to load policies",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("contextSource", contextSource),
			zap.String("sessionID", sessionID),
			zap.Int64("latencyMs", time.Since(policiesStart).Milliseconds()),
			zap.Error(err))
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	mcpAllowedHostList, err := s.getMcpAllowedHostList()
	if err != nil {
		s.logger.Error("ValidateRequest - failed to get MCP allowed host list",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("contextSource", contextSource),
			zap.String("sessionID", sessionID),
			zap.Error(err))
		return nil, fmt.Errorf("failed to get MCP allowed host list: %w", err)
	}
	s.logger.Info("ValidateRequest - loaded policies",
		zap.String("contextSource", contextSource),
		zap.Int("policiesCount", len(policies)),
		zap.Strings("policyNames", policyNames(policies)),
		zap.Any("policies", policies),
		zap.Int64("latencyMs", time.Since(policiesStart).Milliseconds()))

	// Create validation context with full request metadata (matching batch flow)
	valCtx := s.validationContextFromParams(params, sessionID, payloadToValidate, params.ResponsePayload, "ValidateRequest", mcpAllowedHostList, compiledRules)

	// Filter policies by MCP server name so all subsequent checks only fire for
	// rules that belong to policies applicable to this server.
	policies = s.filterPoliciesByMcpServer(policies, valCtx.McpServerName)
	policies = s.filterPoliciesByDeviceId(policies, valCtx.McpServerName)

	// Check account-type guardrail after server filtering so the policy's server
	// selection is respected (a personal-account policy scoped to server A should
	// not block requests arriving on server B).
	if policyName, ok := blockPersonalAccountPolicyName(policies); ok {
		s.refreshCollectionTagsIfNeeded()
		accountType := s.getLoginUserEmailType(valCtx.RequestHeaders)
		s.logger.Info("ValidateRequest - account type check",
			zap.String("path", params.Path),
			zap.String("sessionID", sessionID),
			zap.String("accountType", accountType))
		if accountType != "" && accountType != "enterprise" {
			s.logger.Warn("ValidateRequest - blocking non-enterprise account",
				zap.String("path", params.Path),
				zap.String("method", params.Method),
				zap.String("account", params.AktoAccountID),
				zap.String("accountType", accountType),
				zap.String("policyName", policyName),
				zap.String("sessionID", sessionID))
			return s.reportAndBlockPersonalAccount(ctx, params, payloadToValidate, sessionID, requestID, policyName), nil
		}
	}

	// Host blocklist (block-only). Evaluated after server filtering so only rules from
	// policies scoped to this server are considered.
	if blockResult := s.checkBlockedHost(params, valCtx, payloadToValidate, sessionID, requestID, policies); blockResult != nil {
		return blockResult, nil
	}

	s.logger.Info("ValidateRequest - calling ProcessRequest",
		zap.String("contextSource", contextSource),
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("sessionID", sessionID),
		zap.String("mcpServerName", valCtx.McpServerName),
		zap.Int("policiesCount", len(policies)),
		zap.Strings("policyNames", policyNames(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Int("compiledRulesCount", len(compiledRules)),
		zap.Bool("hasAuditRules", hasAuditRules),
		zap.Bool("skipThreat", params.EffectiveSkipThreat()),
		zap.Int("reqHeadersCount", len(valCtx.RequestHeaders)),
		zap.Int("respHeadersCount", len(valCtx.ResponseHeaders)))

	// Use the default processor - skipThreat is passed via ValidationContext.
	// Scan backpressure lives inside the mcp processor at the remote-scanner
	// boundary (executeSingleScannerTask), so only the agent-guard /scan fan-out
	// is shed when degraded — local PII/regex/token-limit filters always run.
	// Redact any configured ignore-phrases before the enforcement library ever sees the
	// text — see ValidateRequest's plan-doc note on the shared-payload trade-off. Shared
	// with ValidateResponse via redactIgnorePhrasesForEvaluation/reconcileIgnorePhraseRedaction.
	payloadForEvaluation, preRedactionPayload, restore := s.redactIgnorePhrasesForEvaluation(payloadToValidate, policies, "ValidateRequest", sessionID)
	payloadToValidate = payloadForEvaluation

	// Use the default processor - skipThreat is passed via ValidationContext
	processStart := time.Now()
	procCtx, cancelProc := s.withValidationDeadline(ctx)
	defer cancelProc()
	processResult, err := s.processor.ProcessRequestParallel(procCtx, payloadToValidate, valCtx, policies, auditPolicies, hasAuditRules)
	if err != nil {
		s.logger.Error("ValidateRequest - ProcessRequestParallel failed",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("sessionID", sessionID),
			zap.Int64("latencyMs", time.Since(processStart).Milliseconds()),
			zap.Error(err))
		return nil, fmt.Errorf("failed to process request: %w", err)
	}

	// Reconcile ignore-phrase redaction: the real origin must never see a placeholder.
	finalPayload := s.reconcileIgnorePhraseRedaction(processResult.ModifiedPayload, payloadToValidate, preRedactionPayload, restore, "ValidateRequest", sessionID)

	s.logger.Info("ValidateRequest - ProcessRequestParallel result",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("sessionID", sessionID),
		zap.Bool("isBlocked", processResult.IsBlocked),
		zap.Bool("shouldForward", processResult.ShouldForward),
		zap.Bool("payloadModified", finalPayload != "" && finalPayload != payload),
		zap.Int64("latencyMs", time.Since(processStart).Milliseconds()))

	if processResult.IsBlocked {
		s.logger.Warn("ValidateRequest - request blocked by guardrails",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("contextSource", contextSource),
			zap.String("sessionID", sessionID),
			zap.Any("blockedResponse", processResult.BlockedResponse))
	}

	// Track blocked response if request was blocked
	session.TrackBlockedResponse(s.sessionMgr, s.logger, sessionID, requestID, processResult)

	// Convert ProcessResult to ValidationResult. finalPayload is processResult.ModifiedPayload
	// with any ignore-phrase placeholders reconciled back to real text (or discarded in
	// favor of the untouched pre-redaction payload) — see reconciliation above.
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        finalPayload != "" && finalPayload != payload,
		ModifiedPayload: finalPayload,
		Reason:          extractReasonFromBlockedResponse(processResult.BlockedResponse),
		Metadata:        types.ThreatMetadata{},
		Behaviour:       processResult.Behaviour,
	}

	s.logger.Info("ValidateRequest - completed",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("account", params.AktoAccountID),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("behaviour", result.Behaviour),
		zap.String("reason", result.Reason),
		zap.Int("policyCount", len(policies)),
		zap.Int64("policiesMs", time.Since(policiesStart).Milliseconds()),
		zap.Int64("processMs", time.Since(processStart).Milliseconds()),
		zap.Int64("totalLatencyMs", time.Since(start).Milliseconds()))

	totalMs := time.Since(start).Milliseconds()
	if totalMs >= 1500 {
		s.logger.Warn("ValidateRequest - slow",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("sessionID", sessionID),
			zap.Int("policyCount", len(policies)),
			zap.Int64("policiesMs", time.Since(policiesStart).Milliseconds()),
			zap.Int64("processMs", time.Since(processStart).Milliseconds()),
			zap.Int64("totalLatencyMs", totalMs),
			zap.Bool("allowed", result.Allowed))
	}

	return result, nil
}

// ValidateResponse validates a response payload against guardrail policies with session tracking
func (s *Service) ValidateResponse(ctx context.Context, params *models.ValidateRequestParams, responseBody string, sessionID string, requestID string) (*mcp.ValidationResult, error) {
	start := time.Now()
	contextSource := params.ContextSource

	s.logger.Info("ValidateResponse - starting",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("account", params.AktoAccountID),
		zap.String("contextSource", contextSource),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.String("ip", params.IP),
		zap.String("destIp", params.DestIP),
		zap.String("statusCode", params.StatusCode),
		zap.String("source", params.Source),
		zap.String("direction", params.Direction),
		zap.String("tag", params.Tag),
		zap.String("aktoVxlanId", params.AktoVxlanID),
		zap.Bool("skipThreat", params.EffectiveSkipThreat()))

	s.schemaFetcher.RefreshIfNeeded()

	// Get cached policies (refreshes if stale)
	policiesStart := time.Now()
	policies, _, compiledRules, _, err := s.getCachedPolicies(contextSource)
	if err != nil {
		s.logger.Error("ValidateResponse - failed to load policies",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("contextSource", contextSource),
			zap.String("sessionID", sessionID),
			zap.Int64("latencyMs", time.Since(policiesStart).Milliseconds()),
			zap.Error(err))
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	s.logger.Info("ValidateResponse - loaded policies",
		zap.String("contextSource", contextSource),
		zap.Int("policiesCount", len(policies)),
		zap.Strings("policyNames", policyNames(policies)),
		zap.Int64("latencyMs", time.Since(policiesStart).Milliseconds()))

	mcpAllowedHostList, err := s.getMcpAllowedHostList()
	if err != nil {
		s.logger.Error("ValidateResponse - failed to get MCP allowed host list",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("contextSource", contextSource),
			zap.String("sessionID", sessionID),
			zap.Error(err))
		return nil, fmt.Errorf("failed to get MCP allowed host list: %w", err)
	}

	// Create validation context with full request metadata (matching batch flow)
	valCtx := s.validationContextFromParams(params, sessionID, params.RequestPayload, responseBody, "ValidateResponse", mcpAllowedHostList, compiledRules)

	// Filter policies by MCP server name — policies with no server configured are skipped
	policies = s.filterPoliciesByMcpServer(policies, valCtx.McpServerName)
	policies = s.filterPoliciesByDeviceId(policies, valCtx.McpServerName)

	s.logger.Info("ValidateResponse - calling ProcessResponse",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("sessionID", sessionID),
		zap.String("mcpServerName", valCtx.McpServerName),
		zap.Int("policiesCount", len(policies)),
		zap.Strings("policyNames", policyNames(policies)),
		zap.Bool("skipThreat", params.EffectiveSkipThreat()),
		zap.Int("reqHeadersCount", len(valCtx.RequestHeaders)),
		zap.Int("respHeadersCount", len(valCtx.ResponseHeaders)))

	responseBodyForValidation := s.extractPayloadForValidation(responseBody, params.Method, params.Path, false)
	s.logger.Info("ValidateResponse - payload prepared for validation",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("payloadToValidate", responseBodyForValidation))

	// Use processor's ProcessResponse method with external policies. Scan
	// backpressure is applied inside the mcp processor at the remote-scanner
	// boundary, so only the agent-guard /scan call is shed when degraded.
	// Redact any configured ignore-phrases before the enforcement library ever sees the
	// text — see ValidateRequest for the full rationale and the shared-payload trade-off.
	payloadForEvaluation, preRedactionPayload, restore := s.redactIgnorePhrasesForEvaluation(responseBodyForValidation, policies, "ValidateResponse", sessionID)
	responseBodyForValidation = payloadForEvaluation

	// Use processor's ProcessResponse method with external policies
	processStart := time.Now()
	procCtx, cancelProc := s.withValidationDeadline(ctx)
	defer cancelProc()
	processResult, err := s.processor.ProcessResponseParallel(procCtx, responseBodyForValidation, valCtx, policies)
	if err != nil {
		s.logger.Error("ValidateResponse - ProcessResponseParallel failed",
			zap.String("path", params.Path),
			zap.String("method", params.Method),
			zap.String("account", params.AktoAccountID),
			zap.String("sessionID", sessionID),
			zap.Int64("latencyMs", time.Since(processStart).Milliseconds()),
			zap.Error(err))
		return nil, fmt.Errorf("failed to process response: %w", err)
	}

	// Reconcile ignore-phrase redaction: the real origin must never see a placeholder.
	finalResponsePayload := s.reconcileIgnorePhraseRedaction(processResult.ModifiedPayload, responseBodyForValidation, preRedactionPayload, restore, "ValidateResponse", sessionID)

	s.logger.Info("ValidateResponse - ProcessResponseParallel result",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("sessionID", sessionID),
		zap.Bool("isBlocked", processResult.IsBlocked),
		zap.Int64("latencyMs", time.Since(processStart).Milliseconds()))

	// Track response and generate summary
	isMalicious := processResult.IsBlocked
	session.TrackResponseAndGenerateSummary(s.sessionMgr, s.logger, sessionID, requestID, responseBody, isMalicious)

	// Convert ProcessResult to ValidationResult for backward compatibility. finalResponsePayload
	// is processResult.ModifiedPayload with any ignore-phrase placeholders reconciled back to
	// real text (or discarded in favor of the untouched pre-redaction payload).
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        finalResponsePayload != "" && finalResponsePayload != responseBody,
		ModifiedPayload: finalResponsePayload,
		Reason:          extractReasonFromBlockedResponse(processResult.BlockedResponse),
		Metadata:        types.ThreatMetadata{},
		Behaviour:       processResult.Behaviour,
	}

	s.logger.Info("ValidateResponse - completed",
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("account", params.AktoAccountID),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("behaviour", result.Behaviour),
		zap.String("reason", result.Reason),
		zap.Int64("totalLatencyMs", time.Since(start).Milliseconds()))

	return result, nil
}

// ValidateRequestWithPolicy validates a request payload with an optional provided policy
// The provided policy is merged with cached policies (provided policy takes precedence)
func (s *Service) ValidateRequestWithPolicy(
	ctx context.Context,
	payload string,
	contextSource string,
	sessionID string,
	requestID string,
	skipThreat bool,
	providedPolicy *mcp.GuardrailsPolicy,
) (*mcp.ValidationResult, error) {
	s.logger.Info("Validating request payload with provided policy",
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.String("policyName", providedPolicy.Name))

	if result, isMalicious := session.CheckAndHandleMaliciousSession(s.sessionMgr, s.logger, sessionID, requestID, payload); isMalicious {
		s.logger.Info("ValidateRequestWithPolicy - session already malicious, blocking request",
			zap.String("sessionID", sessionID),
			zap.String("requestID", requestID))
		return result, nil
	}

	// Track request and generate summary asynchronously
	session.TrackRequestAndGenerateSummary(s.sessionMgr, s.logger, sessionID, requestID, payload)

	// Inject session summary into payload if available
	payloadToValidate := session.GetModifiedPayloadWithSummary(s.sessionMgr, s.logger, payload, sessionID)

	// Log the provided policy structure before conversion
	policyJSON, _ := json.Marshal(providedPolicy)
	s.logger.Info("Provided policy before conversion",
		zap.String("policyName", providedPolicy.Name),
		zap.String("policyJSON", string(policyJSON)),
		zap.Any("piiTypes", providedPolicy.PIITypes),
		zap.Bool("active", providedPolicy.Active),
		zap.Bool("applyOnRequest", providedPolicy.ApplyOnRequest),
		zap.Bool("applyOnResponse", providedPolicy.ApplyOnResponse))

	// Convert provided policy to types.Policy
	providedPolicyConverted := mcp.ConvertGuardrailsToPolicy(providedPolicy)

	// Log the converted policy structure
	convertedPolicyJSON, _ := json.Marshal(providedPolicyConverted)
	s.logger.Info("Converted policy after conversion",
		zap.String("policyName", providedPolicy.Name),
		zap.String("convertedPolicyJSON", string(convertedPolicyJSON)),
		zap.Int("requestPayloadFilters", len(providedPolicyConverted.Filters.RequestPayload)),
		zap.Int("responsePayloadFilters", len(providedPolicyConverted.Filters.ResponsePayload)))

	// Use only the provided policy (no cached policies)
	policies := []types.Policy{providedPolicyConverted}
	auditPolicies := make(map[string]*types.AuditPolicy)
	hasAuditRules := false

	s.logger.Info("Using only provided policy (no cached policies)",
		zap.String("policyName", providedPolicy.Name),
		zap.Int("totalPolicies", len(policies)))

	mcpAllowedHostList, err := s.getMcpAllowedHostList()
	if err != nil {
		s.logger.Error("Failed to get MCP allowed host list", zap.Error(err))
		return nil, fmt.Errorf("failed to get MCP allowed host list: %w", err)
	}

	// Create validation context with skipThreat flag
	valCtx := &mcp.ValidationContext{
		ContextSource: types.ContextSource(contextSource),
		SessionID:     sessionID,
		SkipThreat:    skipThreat,
		AllowedLists:  mcpAllowedHostList,
	}

	// Log detailed filter information
	if len(providedPolicyConverted.Filters.RequestPayload) > 0 {
		s.logger.Info("Request payload filters in converted policy",
			zap.Int("count", len(providedPolicyConverted.Filters.RequestPayload)))
		for i, filter := range providedPolicyConverted.Filters.RequestPayload {
			// Log full filter details including Config
			filterJSON, _ := json.Marshal(filter)
			s.logger.Info("Request filter details",
				zap.Int("index", i),
				zap.String("type", filter.Type),
				zap.String("pattern", filter.Pattern),
				zap.String("action", filter.Action),
				zap.String("description", filter.Description),
				zap.Bool("caseSensitive", filter.CaseSensitive),
				zap.String("replacement", filter.Replacement),
				zap.Any("config", filter.Config),
				zap.String("fullFilterJSON", string(filterJSON)))
		}
	} else {
		s.logger.Warn("No request payload filters found in converted policy - this might be why guardrails aren't working!")
	}

	s.logger.Info("Calling ProcessRequest with provided policy only",
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Bool("hasAuditRules", hasAuditRules),
		zap.Bool("skipThreat", skipThreat),
		zap.String("payload", payloadToValidate),
		zap.Int("requestFiltersCount", len(providedPolicyConverted.Filters.RequestPayload)),
		zap.Int("responseFiltersCount", len(providedPolicyConverted.Filters.ResponsePayload)))

	// Redact any configured ignore-phrases before the enforcement library ever sees the
	// text — same rationale as ValidateRequest, but the playground evaluates a single
	// one-off provided policy that never goes through the policy cache, so matchers are
	// compiled fresh for just this one policy rather than looked up from it.
	matchersByPolicy := compileIgnorePhraseMatchersByPolicy(policies)
	payloadForEvaluation, preRedactionPayload, restore := s.redactIgnorePhrasesForEvaluationWithMatchers(payloadToValidate, policies, matchersByPolicy, "ValidateRequestWithPolicy", sessionID)
	payloadToValidate = payloadForEvaluation

	// Use the default processor - skipThreat is passed via ValidationContext
	processResult, err := s.processor.ProcessRequest(ctx, payloadToValidate, valCtx, policies, auditPolicies, hasAuditRules)
	if err != nil {
		s.logger.Error("ProcessRequest failed", zap.Error(err))
		return nil, fmt.Errorf("failed to process request: %w", err)
	}

	// Log detailed ProcessRequest result
	processResultJSON, _ := json.Marshal(processResult)
	s.logger.Info("ProcessRequest result",
		zap.Bool("isBlocked", processResult.IsBlocked),
		zap.Bool("shouldForward", processResult.ShouldForward),
		zap.String("modifiedPayload", processResult.ModifiedPayload),
		zap.Any("blockedResponse", processResult.BlockedResponse),
		zap.String("behaviour", processResult.Behaviour),
		zap.String("fullProcessResultJSON", string(processResultJSON)))

	// Reconcile ignore-phrase redaction: the real origin must never see a placeholder.
	finalPayload := s.reconcileIgnorePhraseRedaction(processResult.ModifiedPayload, payloadToValidate, preRedactionPayload, restore, "ValidateRequestWithPolicy", sessionID)

	// Convert ProcessResult to ValidationResult
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        finalPayload != "" && finalPayload != preRedactionPayload,
		ModifiedPayload: finalPayload,
		Reason:          "",                     // TODO: Extract from BlockedResponse when library is updated
		Metadata:        types.ThreatMetadata{}, // Empty for now - library will populate later
		Behaviour:       processResult.Behaviour,
	}

	s.logger.Info("Request validation completed with provided policy",
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("behaviour", result.Behaviour),
		zap.String("sessionID", sessionID))

	return result, nil
}

// ValidateBatch validates a batch of request/response pairs
func (s *Service) ValidateBatch(ctx context.Context, batchData []models.IngestDataBatch, contextSource string, skipThreat bool) ([]ValidationBatchResult, error) {
	s.logger.Debug("ValidateBatch - starting batch validation",
		zap.String("contextSource", contextSource),
		zap.Int("batchSize", len(batchData)))

	s.logger.Info("Validating batch data",
		zap.Int("count", len(batchData)),
		zap.String("contextSource", contextSource),
		zap.Bool("skipThreat", skipThreat))

	s.schemaFetcher.RefreshIfNeeded()

	policies, auditPolicies, compiledRules, hasAuditRules, err := s.getCachedPolicies(string(contextSource))
	if err != nil {
		s.logger.Error("ValidateBatch - failed to load policies",
			zap.String("contextSource", contextSource),
			zap.Error(err))
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	s.logger.Debug("ValidateBatch - loaded policies for contextSource",
		zap.String("contextSource", contextSource),
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Strings("policyNames", policyNames(policies)))

	results := make([]ValidationBatchResult, 0, len(batchData))

	for i, data := range batchData {
		result := ValidationBatchResult{
			Index:  i,
			Method: data.Method,
			Path:   data.Path,
		}

		// Parse status code
		statusCode := 0
		if data.StatusCode != "" {
			fmt.Sscanf(data.StatusCode, "%d", &statusCode)
		}

		// Parse headers (simplified - assumes JSON format)
		reqHeaders := make(map[string]string)
		respHeaders := make(map[string]string)
		if data.RequestHeaders != "" {
			json.Unmarshal([]byte(data.RequestHeaders), &reqHeaders)
		}
		if data.ResponseHeaders != "" {
			json.Unmarshal([]byte(data.ResponseHeaders), &respHeaders)
		}

		// Extract host header for McpServerName
		mcpServerName := extractHostHeader(reqHeaders)

		mcpAllowedHostList, err := s.getMcpAllowedHostList()
		if err != nil {
			s.logger.Error("Failed to get MCP allowed host list", zap.Error(err))
			return nil, fmt.Errorf("failed to get MCP allowed host list: %w", err)
		}

		// Create validation context with actual data and skipThreat flag
		valCtx := &mcp.ValidationContext{
			IP:                 data.IP,
			Endpoint:           data.Path,
			Method:             data.Method,
			RequestHeaders:     reqHeaders,
			ResponseHeaders:    respHeaders,
			StatusCode:         statusCode,
			RequestPayload:     data.RequestPayload,
			ResponsePayload:    data.ResponsePayload,
			ContextSource:      types.ContextSource(contextSource),
			McpServerName:      mcpServerName,
			AktoAccountID:      auth.AccountIDFromServiceToken(),
			SkipThreat:         skipThreat, // Set skipThreat directly in context
			AllowedLists:       mcpAllowedHostList,
			CompiledRegexRules: compiledRules,
		}

		s.logger.Debug("ValidateBatch - created validation context for batch item",
			zap.Int("index", i),
			zap.String("contextSource", string(valCtx.ContextSource)),
			zap.String("method", data.Method),
			zap.String("path", data.Path))

		// Filter policies by MCP server name for this specific batch item
		itemPolicies := s.filterPoliciesByMcpServer(policies, mcpServerName)
		itemPolicies = s.filterPoliciesByDeviceId(itemPolicies, mcpServerName)
		s.logger.Debug("ValidateBatch - applicable policies for server",
			zap.Int("index", i),
			zap.String("mcpServerName", mcpServerName),
			zap.Int("policiesCount", len(itemPolicies)),
			zap.Strings("policyNames", policyNames(itemPolicies)))

		var reqResult, respResult *mcp.ValidationResult

		// Validate request payload if present
		if data.RequestPayload != "" {
			s.logger.Debug("Processing request",
				zap.Int("index", i),
				zap.String("method", data.Method),
				zap.String("path", data.Path),
				zap.String("payload", data.RequestPayload))

			reqPayload := s.extractPayloadForValidation(data.RequestPayload, data.Method, data.Path, true)
			processResult, err := s.processor.ProcessRequest(ctx, reqPayload, valCtx, itemPolicies, auditPolicies, hasAuditRules)
			if err != nil {
				s.logger.Error("Failed to validate request",
					zap.Int("index", i),
					zap.Error(err))
				result.RequestError = err.Error()
			} else {
				s.logger.Debug("ProcessRequest result",
					zap.Int("index", i),
					zap.Bool("isBlocked", processResult.IsBlocked),
					zap.String("behaviour", processResult.Behaviour),
					zap.String("modifiedPayload", processResult.ModifiedPayload))

				reqResult = &mcp.ValidationResult{
					Allowed:         !processResult.IsBlocked,
					Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != data.RequestPayload,
					ModifiedPayload: processResult.ModifiedPayload,
					Reason:          extractReasonFromBlockedResponse(processResult.BlockedResponse),
					Metadata:        types.ThreatMetadata{},
					Behaviour:       processResult.Behaviour,
				}
				result.RequestAllowed = reqResult.Allowed
				result.RequestModified = reqResult.Modified
				result.RequestModifiedPayload = reqResult.ModifiedPayload
				result.RequestReason = reqResult.Reason
				result.RequestBehaviour = reqResult.Behaviour
			}
		}

		// Validate response payload if present
		if data.ResponsePayload != "" {
			respPayload := s.extractPayloadForValidation(data.ResponsePayload, data.Method, data.Path, false)
			processResult, err := s.processor.ProcessResponse(ctx, respPayload, valCtx, itemPolicies)
			if err != nil {
				s.logger.Error("Failed to validate response", zap.Error(err))
				result.ResponseError = err.Error()
			} else {
				respResult = &mcp.ValidationResult{
					Allowed:         !processResult.IsBlocked,
					Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != data.ResponsePayload,
					ModifiedPayload: processResult.ModifiedPayload,
					Reason:          extractReasonFromBlockedResponse(processResult.BlockedResponse),
					Metadata:        types.ThreatMetadata{},
					Behaviour:       processResult.Behaviour,
				}
				result.ResponseAllowed = respResult.Allowed
				result.ResponseModified = respResult.Modified
				result.ResponseModifiedPayload = respResult.ModifiedPayload
				result.ResponseReason = respResult.Reason
				result.ResponseBehaviour = respResult.Behaviour
			}
		}

		results = append(results, result)

		// Report threats to dashboard if detected
		shouldReport := false
		if reqResult != nil && (!reqResult.Allowed || reqResult.Modified) {
			shouldReport = true

			s.logger.Warn("Request blocked or modified by guardrails",
				zap.Int("index", i),
				zap.String("method", data.Method),
				zap.String("path", data.Path),
				zap.Bool("allowed", reqResult.Allowed),
				zap.Bool("modified", reqResult.Modified),
				zap.String("behaviour", reqResult.Behaviour),
				zap.String("reason", result.RequestReason))
		}

		if respResult != nil && (!respResult.Allowed || respResult.Modified) {
			shouldReport = true
			s.logger.Warn("Response blocked or modified by guardrails",
				zap.Int("index", i),
				zap.String("method", data.Method),
				zap.String("path", data.Path),
				zap.Bool("allowed", respResult.Allowed),
				zap.Bool("modified", respResult.Modified),
				zap.String("behaviour", respResult.Behaviour),
				zap.String("reason", result.ResponseReason))
		}

		if shouldReport {
			s.logger.Info("Threat detected",
				zap.String("method", data.Method),
				zap.String("path", data.Path))
		}
	}

	blockedRequests, blockedResponses := 0, 0
	for _, r := range results {
		if !r.RequestAllowed {
			blockedRequests++
		}
		if !r.ResponseAllowed {
			blockedResponses++
		}
	}
	s.logger.Info("ValidateBatch - completed",
		zap.Int("batchSize", len(batchData)),
		zap.String("contextSource", contextSource),
		zap.Int("blockedRequests", blockedRequests),
		zap.Int("blockedResponses", blockedResponses))

	return results, nil
}

// FetchPolicies fetches guardrail policies from database-abstractor
func (s *Service) FetchPolicies() error {
	s.logger.Info("Fetching guardrail policies from database-abstractor")

	policies, err := s.dbClient.FetchGuardrailPolicies()
	if err != nil {
		return err
	}

	s.logger.Info("Fetched guardrail policies", zap.Int("size", len(policies)))

	// TODO: Store or process policies as needed
	// The akto-gateway library might already handle policy loading from files
	// You may need to write policies to a file or configure the library differently

	return nil
}

// ValidationBatchResult represents the validation result for a single batch item
type ValidationBatchResult struct {
	Index                   int    `json:"index"`
	Method                  string `json:"method"`
	Path                    string `json:"path"`
	RequestBehaviour        string `json:"requestBehaviour,omitempty"`
	RequestAllowed          bool   `json:"requestAllowed"`
	RequestModified         bool   `json:"requestModified"`
	RequestModifiedPayload  string `json:"requestModifiedPayload,omitempty"`
	RequestReason           string `json:"requestReason,omitempty"`
	RequestError            string `json:"requestError,omitempty"`
	ResponseAllowed         bool   `json:"responseAllowed"`
	ResponseBehaviour       string `json:"responseBehaviour,omitempty"`
	ResponseModified        bool   `json:"responseModified"`
	ResponseModifiedPayload string `json:"responseModifiedPayload,omitempty"`
	ResponseReason          string `json:"responseReason,omitempty"`
	ResponseError           string `json:"responseError,omitempty"`
}

// extractReasonFromBlockedResponse drills into the BlockedResponse map
// produced by mcp-endpoint-shield's CreateBlockedResponse to retrieve the
// human-readable reason string (e.g. "PII detected", "Blocked by regex policy").
// Returns "" when the response is nil or the path doesn't exist.
func extractReasonFromBlockedResponse(blocked map[string]any) string {
	if blocked == nil {
		return ""
	}
	errObj, ok := blocked["error"].(map[string]any)
	if !ok {
		return ""
	}
	if msg, ok := errObj["message"].(string); ok && msg != "" {
		return msg
	}
	data, ok := errObj["data"].(map[string]any)
	if !ok {
		return ""
	}
	if reason, ok := data["reason"].(string); ok {
		return reason
	}
	return ""
}
