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

// aktoParamPattern matches Akto-style path parameters: INTEGER, STRING, FLOAT, BOOLEAN
var aktoParamPattern = regexp.MustCompile(`\b(INTEGER|STRING|FLOAT|BOOLEAN)\b`)

// aktoParamReplacements maps each Akto placeholder to the regex segment it should match.
var aktoParamReplacements = map[string]string{
	"INTEGER": `[0-9]+`,
	"FLOAT":   `[0-9]+\.[0-9]+`,
	"BOOLEAN": `(true|false)`,
	"STRING":  `[^/]+`,
}

// MessageFieldEntry mirrors Java's ApiInfo.GuardrailSchema.MessageFieldEntry.
// FieldPath is a dot-notation JSON path (e.g. "messages.0.content").
type MessageFieldEntry struct {
	FieldPath   string `json:"fieldPath"`
	Description string `json:"description"`
}

// GuardrailSchema mirrors Java's ApiInfo.GuardrailSchema.
type GuardrailSchema struct {
	RequestMessageFields       []MessageFieldEntry `json:"requestMessageFields"`
	ResponseMessageFields      []MessageFieldEntry `json:"responseMessageFields"`
	BlockedResponseCode        *int                `json:"blockedResponseCode"`
	BlockedResponseBody        string              `json:"blockedResponseBody"`
	BlockedResponseContentType string              `json:"blockedResponseContentType"`
}

// HasRequestFields returns true when at least one request field is configured.
func (gs *GuardrailSchema) HasRequestFields() bool {
	return gs != nil && len(gs.RequestMessageFields) > 0
}

// HasResponseFields returns true when at least one response field is configured.
func (gs *GuardrailSchema) HasResponseFields() bool {
	return gs != nil && len(gs.ResponseMessageFields) > 0
}

// GuardrailSchemaRegistry holds per-endpoint schemas fetched from api_info.
type GuardrailSchemaRegistry struct {
	mu                sync.RWMutex
	schemas           map[string]*GuardrailSchema
	compiledTemplates map[string]*regexp.Regexp // storedKey → compiled regex for parameterized keys
}

var (
	globalGuardrailSchemaRegistry     *GuardrailSchemaRegistry
	globalGuardrailSchemaRegistryOnce sync.Once
)

// GlobalGuardrailSchemaRegistry returns the process-wide schema registry singleton.
func GlobalGuardrailSchemaRegistry() *GuardrailSchemaRegistry {
	globalGuardrailSchemaRegistryOnce.Do(func() {
		globalGuardrailSchemaRegistry = &GuardrailSchemaRegistry{
			schemas:           make(map[string]*GuardrailSchema),
			compiledTemplates: make(map[string]*regexp.Regexp),
		}
	})
	return globalGuardrailSchemaRegistry
}

// Get returns the GuardrailSchema for the given endpoint key, if one is registered.
// First tries exact match, then falls back to parameterized template matching
// (e.g. stored key "POST:/api/conversations/INTEGER/messages" matches "POST:/api/conversations/46/messages").
func (r *GuardrailSchemaRegistry) Get(endpointKey string) (*GuardrailSchema, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	// 1. Exact match
	if s, ok := r.schemas[endpointKey]; ok && s != nil {
		return s, true
	}

	// 2. Parameterized template match
	for storedKey, re := range r.compiledTemplates {
		if re.MatchString(endpointKey) {
			if s := r.schemas[storedKey]; s != nil {
				return s, true
			}
		}
	}
	return nil, false
}

// Replace atomically replaces the entire registry and pre-compiles regexes for parameterized keys.
func (r *GuardrailSchemaRegistry) Replace(newSchemas map[string]*GuardrailSchema) {
	newTemplates := make(map[string]*regexp.Regexp)
	for key := range newSchemas {
		if aktoParamPattern.MatchString(key) {
			if re := compileParamTemplate(key); re != nil {
				newTemplates[key] = re
			}
		}
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.schemas = newSchemas
	r.compiledTemplates = newTemplates
}

// compileParamTemplate builds a type-aware regex from a parameterized endpoint key.
// INTEGER → [0-9]+, FLOAT → [0-9]+\.[0-9]+, BOOLEAN → (true|false), STRING → [^/]+
func compileParamTemplate(key string) *regexp.Regexp {
	pattern := regexp.QuoteMeta(key)
	pattern = aktoParamPattern.ReplaceAllStringFunc(pattern, func(match string) string {
		if seg, ok := aktoParamReplacements[match]; ok {
			return seg
		}
		return `[^/]+`
	})
	re, err := regexp.Compile("^" + pattern + "$")
	if err != nil {
		return nil
	}
	return re
}

// EndpointKey formats method and path as "METHOD:PATH" (normalized).
func EndpointKey(method, path string) string {
	method = strings.TrimSpace(strings.ToUpper(method))
	path = strings.TrimSuffix(strings.TrimSpace(path), "/")
	if path == "" {
		path = "/"
	}
	return method + ":" + path
}

// normalizePath converts bracket array notation to dot notation.
// e.g. "messages[-1].content" → "messages.-1.content"
func normalizePath(path string) string {
	result := strings.ReplaceAll(path, "[", ".")
	result = strings.ReplaceAll(result, "]", "")
	for strings.Contains(result, "..") {
		result = strings.ReplaceAll(result, "..", ".")
	}
	return strings.TrimPrefix(result, ".")
}

// GetValueAtPathFromJSON returns the string at dot path in JSON payload.
// Supports both dot notation and bracket notation.
func GetValueAtPathFromJSON(payload string, path string) (string, bool) {
	if path == "" {
		return "", false
	}
	var data any
	if err := json.Unmarshal([]byte(payload), &data); err != nil {
		return "", false
	}
	v := valueAtPath(data, strings.Split(normalizePath(path), "."))
	if v == nil {
		return "", false
	}
	s, ok := v.(string)
	return s, ok
}

func valueAtPath(data any, parts []string) any {
	if data == nil || len(parts) == 0 {
		return data
	}
	key := parts[0]
	rest := parts[1:]
	switch m := data.(type) {
	case map[string]any:
		next, ok := m[key]
		if !ok {
			return nil
		}
		if len(rest) == 0 {
			return next
		}
		return valueAtPath(next, rest)
	case []any:
		idx := 0
		n := 0
		for _, c := range key {
			if c >= '0' && c <= '9' {
				n = n*10 + int(c-'0')
			} else if c == '-' {
				idx = -1
				continue
			} else {
				return nil
			}
		}
		if idx < 0 {
			idx = len(m) - n
		} else {
			idx = n
		}
		if idx < 0 || idx >= len(m) {
			return nil
		}
		next := m[idx]
		if len(rest) == 0 {
			return next
		}
		return valueAtPath(next, rest)
	}
	return nil
}

// ExtractContent extracts and concatenates values from all provided fields in the JSON payload.
// Values are prefixed with "[description]\n" if a description is set, joined with "\n---\n".
// Returns "" if no fields match (caller should fall through to raw payload).
func ExtractContent(payload string, fields []MessageFieldEntry) string {
	if len(fields) == 0 || payload == "" {
		return ""
	}
	var parts []string
	for _, f := range fields {
		val, ok := GetValueAtPathFromJSON(payload, f.FieldPath)
		if !ok || val == "" {
			continue
		}
		if f.Description != "" {
			parts = append(parts, "["+f.Description+"]\n"+val)
		} else {
			parts = append(parts, val)
		}
	}
	if len(parts) == 0 {
		return ""
	}
	return strings.Join(parts, "\n---\n")
}

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
	re := compileParamTemplate(storedPath)
	if re == nil {
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
