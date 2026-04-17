package validator

import (
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"go.uber.org/zap"
)

// SchemaFetcher fetches and caches GuardrailSchema entries from db_abstractor.
type SchemaFetcher struct {
	dbClient        *dbabstractor.Client
	refreshInterval time.Duration
	lastFetchTime   time.Time
	mu              sync.RWMutex
	logger          *zap.Logger
}

// NewSchemaFetcher creates a new SchemaFetcher and performs an initial fetch.
// On fetch error, logs a warning and continues with an empty registry.
func NewSchemaFetcher(dbClient *dbabstractor.Client, refreshInterval time.Duration, logger *zap.Logger) *SchemaFetcher {
	sf := &SchemaFetcher{
		dbClient:        dbClient,
		refreshInterval: refreshInterval,
		logger:          logger,
	}
	if err := sf.FetchAndUpdate(); err != nil {
		logger.Warn("SchemaFetcher: initial fetch failed, proceeding with empty registry", zap.Error(err))
	}
	return sf
}

// RefreshIfNeeded fetches and updates schemas if the refresh interval has elapsed.
func (sf *SchemaFetcher) RefreshIfNeeded() {
	sf.mu.RLock()
	elapsed := time.Since(sf.lastFetchTime)
	sf.mu.RUnlock()

	if elapsed >= sf.refreshInterval {
		if err := sf.FetchAndUpdate(); err != nil {
			sf.logger.Warn("SchemaFetcher: refresh failed, using stale registry", zap.Error(err))
		}
	}
}

// FetchAndUpdate fetches endpoint schemas from db_abstractor and replaces the registry.
// On error, the existing registry is left unchanged.
func (sf *SchemaFetcher) FetchAndUpdate() error {
	sf.logger.Info("SchemaFetcher: fetching guardrail endpoint schemas")

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

		schemaRaw, ok := record["guardrailSchema"]
		if !ok || schemaRaw == nil {
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
	sf.lastFetchTime = time.Now()
	sf.mu.Unlock()

	sf.logger.Info("SchemaFetcher: registry updated", zap.Int("schemasLoaded", len(newSchemas)))
	return nil
}
