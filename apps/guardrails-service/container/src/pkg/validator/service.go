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
	"github.com/akto-api-security/mcp-endpoint-shield/mcp"
	"github.com/akto-api-security/mcp-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

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
	config    *config.Config
	dbClient  *dbabstractor.Client
	processor mcp.RequestProcessor
	logger    *zap.Logger
	cache     *policyCache
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

	return &Service{
		config:    cfg,
		dbClient:  dbClient,
		processor: processor,
		logger:    logger,
		cache:     &policyCache{},
	}, nil
}

// getCachedPolicies returns cached policies if still valid, otherwise fetches fresh policies
func (s *Service) getCachedPolicies() ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, error) {
	refreshInterval := time.Duration(s.config.PolicyRefreshIntervalMin) * time.Minute

	// Check if cache is valid
	s.cache.mu.RLock()
	if !s.cache.lastFetched.IsZero() && time.Since(s.cache.lastFetched) < refreshInterval {
		policies := s.cache.policies
		auditPolicies := s.cache.auditPolicies
		compiledRules := s.cache.compiledRules
		hasAuditRules := s.cache.hasAuditRules
		s.cache.mu.RUnlock()
		s.logger.Debug("Using cached policies",
			zap.Time("lastFetched", s.cache.lastFetched),
			zap.Int("policiesCount", len(policies)))
		return policies, auditPolicies, compiledRules, hasAuditRules, nil
	}
	s.cache.mu.RUnlock()

	// Cache is stale or empty, fetch fresh policies
	return s.refreshPolicies()
}

// refreshPolicies fetches fresh policies from database and updates the cache
func (s *Service) refreshPolicies() ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, error) {
	s.cache.mu.Lock()
	defer s.cache.mu.Unlock()

	// Double-check: another goroutine might have refreshed while we waited for the lock
	refreshInterval := time.Duration(s.config.PolicyRefreshIntervalMin) * time.Minute
	if !s.cache.lastFetched.IsZero() && time.Since(s.cache.lastFetched) < refreshInterval {
		s.logger.Debug("Cache was refreshed by another goroutine, using cached policies")
		return s.cache.policies, s.cache.auditPolicies, s.cache.compiledRules, s.cache.hasAuditRules, nil
	}

	s.logger.Info("Refreshing policies cache")

	policies, auditPolicies, compiledRules, hasAuditRules, err := s.fetchAndParsePolicies()
	if err != nil {
		return nil, nil, nil, false, err
	}

	// Update cache
	s.cache.policies = policies
	s.cache.auditPolicies = auditPolicies
	s.cache.compiledRules = compiledRules
	s.cache.hasAuditRules = hasAuditRules
	s.cache.lastFetched = time.Now()

	s.logger.Info("Policy cache refreshed",
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Time("lastFetched", s.cache.lastFetched))

	return policies, auditPolicies, compiledRules, hasAuditRules, nil
}

// fetchAndParsePolicies fetches policies from database abstractor and parses them
func (s *Service) fetchAndParsePolicies() ([]types.Policy, map[string]*types.AuditPolicy, map[string]*regexp.Regexp, bool, error) {
	// Fetch guardrail policies from database abstractor
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

// ValidateRequest validates a request payload against guardrail policies
func (s *Service) ValidateRequest(ctx context.Context, payload string) (*mcp.ValidationResult, error) {
	s.logger.Info("Validating request payload")

	// Get cached policies (refreshes if stale)
	policies, auditPolicies, compiledRules, hasAuditRules, err := s.getCachedPolicies()
	if err != nil {
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	// Create validation context
	valCtx := &mcp.ValidationContext{
		// Add any context information needed
	}

	s.logger.Debug("Calling ProcessRequest",
		zap.Int("policiesCount", len(policies)),
		zap.Int("auditPoliciesCount", len(auditPolicies)),
		zap.Int("compiledRulesCount", len(compiledRules)),
		zap.Bool("hasAuditRules", hasAuditRules),
		zap.String("payload", payload))

	// Use processor's ProcessRequest method with external policies
	processResult, err := s.processor.ProcessRequest(ctx, payload, valCtx, policies, auditPolicies, hasAuditRules)
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

	// Convert ProcessResult to ValidationResult for backward compatibility
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != payload,
		ModifiedPayload: processResult.ModifiedPayload,
		Reason:          "", // Extract from BlockedResponse if needed
		Metadata:        processResult.ParsedData,
	}

	s.logger.Info("Request validation completed",
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("reason", result.Reason))

	return result, nil
}

// ValidateResponse validates a response payload against guardrail policies
func (s *Service) ValidateResponse(ctx context.Context, payload string) (*mcp.ValidationResult, error) {
	s.logger.Info("Validating response payload")

	// Get cached policies (refreshes if stale)
	policies, _, _, _, err := s.getCachedPolicies()
	if err != nil {
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

	// Create validation context
	valCtx := &mcp.ValidationContext{
		// Add any context information needed
	}

	// Use processor's ProcessResponse method with external policies
	processResult, err := s.processor.ProcessResponse(ctx, payload, valCtx, policies)
	if err != nil {
		return nil, fmt.Errorf("failed to process response: %w", err)
	}

	// Convert ProcessResult to ValidationResult for backward compatibility
	result := &mcp.ValidationResult{
		Allowed:         !processResult.IsBlocked,
		Modified:        processResult.ModifiedPayload != "" && processResult.ModifiedPayload != payload,
		ModifiedPayload: processResult.ModifiedPayload,
		Reason:          "", // Extract from BlockedResponse if needed
		Metadata:        processResult.ParsedData,
	}

	s.logger.Info("Response validation completed",
		zap.Bool("allowed", result.Allowed),
		zap.Bool("modified", result.Modified),
		zap.String("reason", result.Reason))

	return result, nil
}

// ValidateBatch validates a batch of request/response pairs
func (s *Service) ValidateBatch(ctx context.Context, batchData []models.IngestDataBatch) ([]ValidationBatchResult, error) {
	s.logger.Info("Validating batch data", zap.Int("count", len(batchData)))

	// Get cached policies (refreshes if stale)
	policies, auditPolicies, _, hasAuditRules, err := s.getCachedPolicies()
	if err != nil {
		return nil, fmt.Errorf("failed to load policies: %w", err)
	}

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
		}

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
					Reason:          "",
					Metadata:        processResult.ParsedData,
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
					Reason:          "",
					Metadata:        processResult.ParsedData,
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
