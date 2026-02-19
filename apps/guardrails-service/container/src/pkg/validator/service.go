package validator

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"sync"
	"time"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"github.com/akto-api-security/mcp-endpoint-shield/mcp"
	"github.com/akto-api-security/mcp-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// const (
// 	ContextSource = types.ContextSourceAgentic
// )

// policyCache holds cached policies and their metadata
type policyCache struct {
	policies      []types.Policy
	auditPolicies map[string]*types.AuditPolicy
	compiledRules map[string]*regexp.Regexp
	hasAuditRules bool
	lastFetched   time.Time
	mu            sync.RWMutex
}

// Service handles payload validation using akto-gateway library
type Service struct {
	config     *config.Config
	dbClient   *dbabstractor.Client
	processor  mcp.RequestProcessor
	logger     *zap.Logger
	cache      *policyCache
	sessionMgr *session.SessionManager
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

	// Create processor with validator, ingestor, and sessionManager
	processor := mcp.NewCommonMCPProcessor(
		validator,
		ingestor,
		sessionMgr,
		"",    // sessionID - empty for our use case
		"",    // projectName - empty for our use case
		false, // skipThreat - false to enable threat reporting
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

	return &Service{
		config:     cfg,
		dbClient:   dbClient,
		processor:  processor,
		logger:     logger,
		cache:      &policyCache{},
		sessionMgr: sessionManager,
	}, nil
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

	// Cache is stale or empty, fetch fresh policies
	allPolicies, auditPolicies, compiledRules, hasAuditRules, err := s.refreshPolicies()
	if err != nil {
		return nil, nil, nil, false, err
	}

	// Filter by contextSource before returning
	filteredPolicies := s.filterPoliciesByContextSource(allPolicies, contextSource)
	return filteredPolicies, auditPolicies, compiledRules, hasAuditRules, nil
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

	policies, auditPolicies, compiledRules, hasAuditRules, err := s.fetchAndParsePolicies()
	if err != nil {
		return nil, nil, nil, false, err
	}

	// Update cache with ALL policies
	s.cache.policies = policies
	s.cache.auditPolicies = auditPolicies
	s.cache.compiledRules = compiledRules
	s.cache.hasAuditRules = hasAuditRules
	s.cache.lastFetched = time.Now()

	s.logger.Info("Policy cache refreshed with ALL policies",
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Time("lastFetched", s.cache.lastFetched))

	return policies, auditPolicies, compiledRules, hasAuditRules, nil
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
func (s *Service) fetchAndParsePolicies() ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, error) {
	rawGuardrailPolicies, err := s.dbClient.FetchGuardrailPolicies()
	if err != nil {
		return nil, nil, nil, false, fmt.Errorf("failed to fetch guardrail policies: %w", err)
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
		return nil, nil, nil, false, fmt.Errorf("failed to parse guardrail policies: %w", err)
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

	// Fetch MCP audit info from database abstractor
	rawAuditPolicies, err := s.dbClient.FetchMcpAuditInfo()
	if err != nil {
		s.logger.Warn("Failed to fetch MCP audit info", zap.Error(err))
		// Continue without audit policies rather than failing completely
		rawAuditPolicies = []byte("{}")
	}

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

	// Convert array to map keyed by resourceName
	auditPolicies := make(map[string]*types.AuditPolicy)
	for _, policy := range auditResponse.McpAuditInfoList {
		if policy != nil && policy.ResourceName != "" {
			auditPolicies[policy.ResourceName] = policy
		}
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

	s.logger.Info("Successfully fetched and parsed policies",
		zap.Int("guardrailPolicies", len(policies)),
		zap.Int("auditPolicies", len(auditPolicies)),
		zap.Int("compiledRules", len(compiledRules)))

	return policies, auditPolicies, compiledRules, hasAuditRules, nil
}

// ValidateRequest validates a request payload against guardrail policies with session tracking
func (s *Service) ValidateRequest(ctx context.Context, params *models.ValidateRequestParams, sessionID string, requestID string) (*mcp.ValidationResult, error) {
	payload := params.RequestPayload
	contextSource := params.ContextSource

	s.logger.Debug("ValidateRequest - starting validation",
		zap.String("contextSource", contextSource),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.String("ip", params.IP),
		zap.String("destIp", params.DestIP),
		zap.String("path", params.Path),
		zap.String("method", params.Method),
		zap.String("statusCode", params.StatusCode),
		zap.String("source", params.Source),
		zap.String("direction", params.Direction),
		zap.String("tag", params.Tag),
		zap.String("aktoAccountId", params.AktoAccountID),
		zap.String("aktoVxlanId", params.AktoVxlanID))

	s.logger.Info("Validating request payload",
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))

	// Check if session is already malicious
	if result, isMalicious := session.CheckAndHandleMaliciousSession(s.sessionMgr, s.logger, sessionID, requestID, payload); isMalicious {
		return result, nil
	}

	// Track request and generate summary asynchronously
	session.TrackRequestAndGenerateSummary(s.sessionMgr, s.logger, sessionID, requestID, payload)

	// Inject session summary into payload if available
	payloadToValidate := session.GetModifiedPayloadWithSummary(s.sessionMgr, s.logger, payload, sessionID)

	// Get cached policies (refreshes if stale)
	policies, auditPolicies, compiledRules, hasAuditRules, err := s.getCachedPolicies(contextSource)
	if err != nil {
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	s.logger.Debug("ValidateRequest - loaded policies for contextSource",
		zap.String("contextSource", contextSource),
		zap.Int("policiesCount", len(policies)),
		zap.Strings("policyNames", policyNames(policies)))

	// Parse headers and status code
	reqHeaders := make(map[string]string)
	respHeaders := make(map[string]string)
	if params.RequestHeaders != "" {
		json.Unmarshal([]byte(params.RequestHeaders), &reqHeaders)
	}
	if params.ResponseHeaders != "" {
		json.Unmarshal([]byte(params.ResponseHeaders), &respHeaders)
	}

	statusCode := 0
	if params.StatusCode != "" {
		fmt.Sscanf(params.StatusCode, "%d", &statusCode)
	}

	// Extract host header for McpServerName
	mcpServerName := extractHostHeader(reqHeaders)

	// Create validation context with full request metadata (matching batch flow)
	valCtx := &mcp.ValidationContext{
		IP:              params.IP,
		DestIP:          params.DestIP,
		Endpoint:        params.Path,
		Method:          params.Method,
		RequestHeaders:  reqHeaders,
		ResponseHeaders: respHeaders,
		StatusCode:      statusCode,
		RequestPayload:  payloadToValidate,
		ResponsePayload: params.ResponsePayload,
		ContextSource:   types.ContextSource(contextSource),
		McpServerName:   mcpServerName,
		SessionID:       sessionID,
	}

	s.logger.Debug("ValidateRequest - created validation context",
		zap.String("contextSource", string(valCtx.ContextSource)),
		zap.String("sessionID", valCtx.SessionID),
		zap.String("ip", valCtx.IP),
		zap.String("destIp", valCtx.DestIP),
		zap.String("endpoint", valCtx.Endpoint),
		zap.String("method", valCtx.Method),
		zap.Int("statusCode", valCtx.StatusCode),
		zap.String("mcpServerName", valCtx.McpServerName),
		zap.Int("reqHeadersCount", len(valCtx.RequestHeaders)),
		zap.Int("respHeadersCount", len(valCtx.ResponseHeaders)),
		zap.Bool("hasRequestPayload", valCtx.RequestPayload != ""))

	s.logger.Debug("Calling ProcessRequest",
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Int("compiledRulesCount", len(compiledRules)),
		zap.Bool("hasAuditRules", hasAuditRules),
		zap.String("payload", payloadToValidate))

	// Use processor's ProcessRequest method with external policies (using modified payload)
	processResult, err := s.processor.ProcessRequest(ctx, payloadToValidate, valCtx, policies, auditPolicies, hasAuditRules)
	if err != nil {
		s.logger.Error("ProcessRequest failed", zap.Error(err))
		return nil, fmt.Errorf("failed to process request: %w", err)
	}

	s.logger.Debug("ProcessRequest result",
		zap.Bool("isBlocked", processResult.IsBlocked),
		zap.Bool("shouldForward", processResult.ShouldForward),
		zap.String("modifiedPayload", processResult.ModifiedPayload),
		zap.Any("blockedResponse", processResult.BlockedResponse),
		zap.Any("parsedData", processResult.ParsedData))

	// Track blocked response if request was blocked
	session.TrackBlockedResponse(s.sessionMgr, s.logger, sessionID, requestID, processResult)

	// Convert ProcessResult to ValidationResult
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != payload,
		ModifiedPayload: processResult.ModifiedPayload,
		Reason:          "",                     // TODO: Extract from BlockedResponse when library is updated
		Metadata:        types.ThreatMetadata{}, // Empty for now - library will populate later
	}

	s.logger.Info("Request validation completed",
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("sessionID", sessionID))

	return result, nil
}

// ValidateResponse validates a response payload against guardrail policies with session tracking
func (s *Service) ValidateResponse(ctx context.Context, payload string, contextSource string, sessionID string, requestID string) (*mcp.ValidationResult, error) {
	s.logger.Debug("ValidateResponse - starting validation",
		zap.String("contextSource", contextSource),
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))

	s.logger.Info("Validating response payload",
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))

	// Get cached policies (refreshes if stale)
	policies, _, _, _, err := s.getCachedPolicies(contextSource)
	if err != nil {
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	s.logger.Debug("ValidateResponse - loaded policies for contextSource",
		zap.String("contextSource", contextSource),
		zap.Int("policiesCount", len(policies)),
		zap.Strings("policyNames", policyNames(policies)))

	// Create validation context
	valCtx := &mcp.ValidationContext{
		ContextSource: types.ContextSource(contextSource),
		SessionID:     sessionID,
	}

	s.logger.Debug("ValidateResponse - created validation context",
		zap.String("contextSource", string(valCtx.ContextSource)),
		zap.String("sessionID", valCtx.SessionID))

	// Use processor's ProcessResponse method with external policies
	processResult, err := s.processor.ProcessResponse(ctx, payload, valCtx, policies)
	if err != nil {
		return nil, fmt.Errorf("failed to process response: %w", err)
	}

	// Track response and generate summary
	isMalicious := processResult.IsBlocked
	session.TrackResponseAndGenerateSummary(s.sessionMgr, s.logger, sessionID, requestID, payload, isMalicious)

	// Convert ProcessResult to ValidationResult for backward compatibility
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != payload,
		ModifiedPayload: processResult.ModifiedPayload,
		Reason:          "",                     // TODO: Extract from BlockedResponse when library is updated
		Metadata:        types.ThreatMetadata{}, // Empty for now - library will populate later
	}

	s.logger.Info("Response validation completed",
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("sessionID", sessionID))

	return result, nil
}

// ValidateBatch validates a batch of request/response pairs
func (s *Service) ValidateBatch(ctx context.Context, batchData []models.IngestDataBatch, contextSource string) ([]ValidationBatchResult, error) {
	s.logger.Debug("ValidateBatch - starting batch validation",
		zap.String("contextSource", contextSource),
		zap.Int("batchSize", len(batchData)))

	s.logger.Info("Validating batch data", zap.Int("count", len(batchData)))

	// Get cached policies (refreshes if stale)
	policies, auditPolicies, _, hasAuditRules, err := s.getCachedPolicies(string(contextSource))
	if err != nil {
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

		// Create validation context with actual data
		valCtx := &mcp.ValidationContext{
			IP:              data.IP,
			Endpoint:        data.Path,
			Method:          data.Method,
			RequestHeaders:  reqHeaders,
			ResponseHeaders: respHeaders,
			StatusCode:      statusCode,
			RequestPayload:  data.RequestPayload,
			ResponsePayload: data.ResponsePayload,
			ContextSource:   types.ContextSource(contextSource),
			McpServerName:   mcpServerName,
		}

		s.logger.Debug("ValidateBatch - created validation context for batch item",
			zap.Int("index", i),
			zap.String("contextSource", string(valCtx.ContextSource)),
			zap.String("method", data.Method),
			zap.String("path", data.Path))

		var reqResult, respResult *mcp.ValidationResult

		// Validate request payload if present
		if data.RequestPayload != "" {
			s.logger.Debug("Processing request",
				zap.Int("index", i),
				zap.String("method", data.Method),
				zap.String("path", data.Path),
				zap.String("payload", data.RequestPayload))

			processResult, err := s.processor.ProcessRequest(ctx, data.RequestPayload, valCtx, policies, auditPolicies, hasAuditRules)
			if err != nil {
				s.logger.Error("Failed to validate request",
					zap.Int("index", i),
					zap.Error(err))
				result.RequestError = err.Error()
			} else {
				s.logger.Debug("ProcessRequest result",
					zap.Int("index", i),
					zap.Bool("isBlocked", processResult.IsBlocked),
					zap.String("modifiedPayload", processResult.ModifiedPayload))

				reqResult = &mcp.ValidationResult{
					Allowed:         !processResult.IsBlocked,
					Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != data.RequestPayload,
					ModifiedPayload: processResult.ModifiedPayload,
					Reason:          "",                     // TODO: Extract from BlockedResponse when library is updated
					Metadata:        types.ThreatMetadata{}, // Empty for now - library will populate later
				}
				result.RequestAllowed = reqResult.Allowed
				result.RequestModified = reqResult.Modified
				result.RequestModifiedPayload = reqResult.ModifiedPayload
				result.RequestReason = reqResult.Reason
			}
		}

		// Validate response payload if present
		if data.ResponsePayload != "" {
			processResult, err := s.processor.ProcessResponse(ctx, data.ResponsePayload, valCtx, policies)
			if err != nil {
				s.logger.Error("Failed to validate response", zap.Error(err))
				result.ResponseError = err.Error()
			} else {
				respResult = &mcp.ValidationResult{
					Allowed:         !processResult.IsBlocked,
					Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != data.ResponsePayload,
					ModifiedPayload: processResult.ModifiedPayload,
					Reason:          "",                     // TODO: Extract from BlockedResponse when library is updated
					Metadata:        types.ThreatMetadata{}, // Empty for now - library will populate later
				}
				result.ResponseAllowed = respResult.Allowed
				result.ResponseModified = respResult.Modified
				result.ResponseModifiedPayload = respResult.ModifiedPayload
				result.ResponseReason = respResult.Reason
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
				zap.String("reason", result.ResponseReason))
		}

		if shouldReport {
			s.logger.Info("Threat detected",
				zap.String("method", data.Method),
				zap.String("path", data.Path))
		}
	}

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
	RequestAllowed          bool   `json:"requestAllowed"`
	RequestModified         bool   `json:"requestModified"`
	RequestModifiedPayload  string `json:"requestModifiedPayload,omitempty"`
	RequestReason           string `json:"requestReason,omitempty"`
	RequestError            string `json:"requestError,omitempty"`
	ResponseAllowed         bool   `json:"responseAllowed"`
	ResponseModified        bool   `json:"responseModified"`
	ResponseModifiedPayload string `json:"responseModifiedPayload,omitempty"`
	ResponseReason          string `json:"responseReason,omitempty"`
	ResponseError           string `json:"responseError,omitempty"`
}
