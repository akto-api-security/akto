package validator

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"sync"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"go.uber.org/zap"
)

// SchemaFetcher fetches and caches selected endpoints and GuardrailSchemas from db_abstractor.
// Mirrors EndpointSelectorFetcher in ai-agent-shield.
type SchemaFetcher struct {
	dbClient        *dbabstractor.Client
	refreshInterval time.Duration
	lastFetchTime   time.Time
	mu              sync.RWMutex
	logger          *zap.Logger
	endpoints       map[string]map[string]bool // method -> path -> true
}

// NewSchemaFetcher creates a new SchemaFetcher and performs an initial fetch.
// On fetch error, logs a warning and continues with empty state.
func NewSchemaFetcher(dbClient *dbabstractor.Client, refreshInterval time.Duration, logger *zap.Logger) *SchemaFetcher {
	sf := &SchemaFetcher{
		dbClient:        dbClient,
		refreshInterval: refreshInterval,
		logger:          logger,
		endpoints:       make(map[string]map[string]bool),
	}
	if err := sf.FetchAndUpdate(); err != nil {
		logger.Warn("SchemaFetcher: initial fetch failed, proceeding with empty state", zap.Error(err))
	}
	return sf
}

// RefreshIfNeeded fetches and updates if the refresh interval has elapsed.
func (sf *SchemaFetcher) RefreshIfNeeded() {
	sf.mu.RLock()
	elapsed := time.Since(sf.lastFetchTime)
	sf.mu.RUnlock()

	if elapsed >= sf.refreshInterval {
		if err := sf.FetchAndUpdate(); err != nil {
			sf.logger.Warn("SchemaFetcher: refresh failed, using stale state", zap.Error(err))
		}
	}
}

// IsEndpointSelected checks if an endpoint has guardrails enabled in the dashboard.
// Supports parameterized paths (e.g. stored as /api/v1/INTEGER/price matches /api/v1/42/price).
func (sf *SchemaFetcher) IsEndpointSelected(method, path string) bool {
	sf.mu.RLock()
	defer sf.mu.RUnlock()

	method = strings.ToUpper(method)
	paths, exists := sf.endpoints[method]
	if !exists {
		return false
	}

	// Exact match
	if paths[path] {
		return true
	}

	// Parameterized match — stored path may contain INTEGER/STRING/FLOAT/BOOLEAN
	for storedPath := range paths {
		if aktoParamPattern.MatchString(storedPath) {
			if matchesParamTemplate(storedPath, path) {
				return true
			}
		}
	}
	return false
}

// matchesParamTemplate returns true if storedPath (with Akto placeholders) matches actualPath.
func matchesParamTemplate(storedPath, actualPath string) bool {
	pattern := "^" + aktoParamPattern.ReplaceAllLiteralString(regexp.QuoteMeta(storedPath), `[^/]+`) + "$"
	re, err := regexp.Compile(pattern)
	if err != nil {
		return false
	}
	return re.MatchString(actualPath)
}

// FetchAndUpdate fetches endpoint selections and schemas from db_abstractor.
// On error, existing state is left unchanged.
func (sf *SchemaFetcher) FetchAndUpdate() error {
	sf.logger.Info("SchemaFetcher: fetching guardrail endpoint selections and schemas")

	body, err := sf.dbClient.FetchGuardrailEndpoints()
	if err != nil {
		return fmt.Errorf("failed to fetch guardrail endpoints: %w", err)
	}

	var resp struct {
		Endpoints []map[string]any `json:"agentProxyGuardrailEndpoints"`
	}
	if err := json.Unmarshal(body, &resp); err != nil {
		return fmt.Errorf("failed to parse guardrail endpoints response: %w", err)
	}

	newEndpoints := make(map[string]map[string]bool)
	newSchemas := make(map[string]*GuardrailSchema)

	for _, record := range resp.Endpoints {
		if isDeleted, ok := record["isDeleted"].(bool); ok && isDeleted {
			continue
		}

		method, _ := record["method"].(string)
		path, _ := record["url"].(string)
		if path == "" {
			path, _ = record["path"].(string)
		}

		if method == "" || path == "" {
			sf.logger.Warn("SchemaFetcher: skipping record with missing method or url",
				zap.String("method", method), zap.String("path", path))
			continue
		}

		method = strings.ToUpper(method)
		epKey := EndpointKey(method, path)

		// Track selected endpoint (all records, regardless of schema)
		if newEndpoints[method] == nil {
			newEndpoints[method] = make(map[string]bool)
		}
		newEndpoints[method][path] = true

		// Extract guardrailSchema if present
		schemaRaw, ok := record["guardrailSchema"]
		if !ok || schemaRaw == nil {
			sf.logger.Debug("SchemaFetcher: endpoint selected but no guardrailSchema",
				zap.String("endpoint", epKey))
			continue
		}

		b, err := json.Marshal(schemaRaw)
		if err != nil {
			sf.logger.Warn("SchemaFetcher: failed to marshal guardrailSchema",
				zap.String("endpoint", epKey), zap.Error(err))
			continue
		}

		var gs GuardrailSchema
		if err := json.Unmarshal(b, &gs); err != nil {
			sf.logger.Warn("SchemaFetcher: failed to unmarshal guardrailSchema",
				zap.String("endpoint", epKey), zap.Error(err))
			continue
		}

		newSchemas[epKey] = &gs
		sf.logger.Info("SchemaFetcher: loaded guardrailSchema",
			zap.String("endpoint", epKey),
			zap.Int("requestFields", len(gs.RequestMessageFields)),
			zap.Int("responseFields", len(gs.ResponseMessageFields)))
	}

	GlobalGuardrailSchemaRegistry().Replace(newSchemas)

	sf.mu.Lock()
	sf.endpoints = newEndpoints
	sf.lastFetchTime = time.Now()
	sf.mu.Unlock()

	totalCount := 0
	for _, paths := range newEndpoints {
		totalCount += len(paths)
	}
	sf.logger.Info("SchemaFetcher: state updated",
		zap.Int("selectedEndpoints", totalCount),
		zap.Int("schemasLoaded", len(newSchemas)))

	return nil
}
