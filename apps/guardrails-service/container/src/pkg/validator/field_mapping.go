package validator

import (
	"encoding/json"
	"os"
	"regexp"
	"strings"
	"sync"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

const (
	// EnvFieldMapping: per-endpoint request/response JSON paths.
	// Format: semicolon-separated "METHOD:PATH:requestPath,responsePath"
	// Multiple paths per side are pipe-separated (first match wins).
	// Example: POST:/v1/chat/completions:messages.role=user.content|messages.role=user.content.0.text,choices.0.message.content
	EnvFieldMapping = "GUARDRAIL_FIELD_MAPPING"
)

type endpointPaths struct {
	RequestPaths  []string
	ResponsePaths []string
}

type endpointPattern struct {
	method string
	regex  *regexp.Regexp
	paths  *endpointPaths
}

// FieldMapping holds env-configured per-endpoint JSON paths (exact + wildcard).
type FieldMapping struct {
	mu               sync.RWMutex
	byEndpoint       map[string]*endpointPaths
	wildcardPatterns []endpointPattern
	loadReport       fieldMappingLoadReport
}

// fieldMappingLoadReport captures how GUARDRAIL_FIELD_MAPPING was parsed at startup.
type fieldMappingLoadReport struct {
	envSet            bool
	entryCount        int
	exactEndpoints    []endpointMappingSummary
	wildcardEndpoints []endpointMappingSummary
	skippedEntries    []string
}

type endpointMappingSummary struct {
	endpointKey   string
	requestPaths  []string
	responsePaths []string
}

var (
	globalFieldMapping     *FieldMapping
	globalFieldMappingOnce sync.Once

	fieldMappingStartupLogger     *zap.Logger
	fieldMappingStartupLoggerOnce sync.Once
	logFieldMappingStartupOnce    sync.Once
)

// fieldMappingStartupLogger returns a logger that always emits Info+ to stderr,
// independent of LOG_LEVEL, so field-mapping config is visible at startup.
func getFieldMappingStartupLogger() *zap.Logger {
	fieldMappingStartupLoggerOnce.Do(func() {
		encCfg := zap.NewDevelopmentEncoderConfig()
		encCfg.EncodeTime = zapcore.TimeEncoderOfLayout("2006-01-02 15:04:05.000")
		encCfg.EncodeLevel = zapcore.CapitalColorLevelEncoder
		core := zapcore.NewCore(
			zapcore.NewConsoleEncoder(encCfg),
			zapcore.AddSync(os.Stderr),
			zap.LevelEnablerFunc(func(l zapcore.Level) bool { return l >= zapcore.InfoLevel }),
		)
		fieldMappingStartupLogger = zap.New(core)
	})
	return fieldMappingStartupLogger
}

func isWildcardPath(path string) bool {
	return strings.Contains(path, "*")
}

func wildcardToRegex(path string) string {
	escaped := regexp.QuoteMeta(path)
	escaped = strings.ReplaceAll(escaped, `\*\*`, `.*`)
	escaped = strings.ReplaceAll(escaped, `\*`, `[^/]+`)
	return `^` + escaped + `$`
}

// GlobalFieldMapping returns the process-wide env field mapping singleton.
func GlobalFieldMapping() *FieldMapping {
	globalFieldMappingOnce.Do(func() {
		globalFieldMapping = &FieldMapping{
			byEndpoint: make(map[string]*endpointPaths),
		}
		globalFieldMapping.LoadFromEnv()
	})
	return globalFieldMapping
}

func (fm *FieldMapping) matchPattern(endpointKey string) *endpointPaths {
	colonIdx := strings.IndexByte(endpointKey, ':')
	if colonIdx < 0 {
		return nil
	}
	method := endpointKey[:colonIdx]
	path := endpointKey[colonIdx+1:]
	for i := range fm.wildcardPatterns {
		p := &fm.wildcardPatterns[i]
		if p.method == method && p.regex.MatchString(path) {
			return p.paths
		}
	}
	return nil
}

// LogFieldMappingStartup logs whether GUARDRAIL_FIELD_MAPPING was parsed successfully.
// Emitted once per process to stderr, bypassing LOG_LEVEL so it is visible even when LOG_LEVEL=error.
func LogFieldMappingStartup() {
	logFieldMappingStartupOnce.Do(func() {
		logger := getFieldMappingStartupLogger()
		fm := GlobalFieldMapping()
		fm.mu.RLock()
		report := fm.loadReport
		fm.mu.RUnlock()

		if !report.envSet {
			logger.Info("[FieldMapping] GUARDRAIL_FIELD_MAPPING not set; env field extraction disabled (dashboard schema or raw payload fallback)")
			return
		}

		parsedCount := len(report.exactEndpoints) + len(report.wildcardEndpoints)
		if parsedCount == 0 {
			logger.Warn("[FieldMapping] GUARDRAIL_FIELD_MAPPING is set but no endpoints were parsed",
				zap.Int("entryCount", report.entryCount),
				zap.Strings("skippedEntries", report.skippedEntries))
			return
		}

		logger.Info("[FieldMapping] GUARDRAIL_FIELD_MAPPING parsed successfully",
			zap.Int("entryCount", report.entryCount),
			zap.Int("exactEndpoints", len(report.exactEndpoints)),
			zap.Int("wildcardEndpoints", len(report.wildcardEndpoints)))

		for _, ep := range report.exactEndpoints {
			logger.Info("[FieldMapping] exact endpoint configured",
				zap.String("endpoint", ep.endpointKey),
				zap.Strings("requestPaths", ep.requestPaths),
				zap.Strings("responsePaths", ep.responsePaths))
		}
		for _, ep := range report.wildcardEndpoints {
			logger.Info("[FieldMapping] wildcard endpoint configured",
				zap.String("endpoint", ep.endpointKey),
				zap.Strings("requestPaths", ep.requestPaths),
				zap.Strings("responsePaths", ep.responsePaths))
		}
		if len(report.skippedEntries) > 0 {
			logger.Warn("[FieldMapping] some GUARDRAIL_FIELD_MAPPING entries were skipped",
				zap.Strings("skippedEntries", report.skippedEntries))
		}
	})
}

// GetPaths returns request/response field paths for endpointKey from env mapping.
func (fm *FieldMapping) GetPaths(endpointKey string) (requestPaths, responsePaths []string, ok bool) {
	fm.mu.RLock()
	defer fm.mu.RUnlock()

	if ep, found := fm.byEndpoint[endpointKey]; found && ep != nil {
		return ep.RequestPaths, ep.ResponsePaths, len(ep.RequestPaths) > 0 || len(ep.ResponsePaths) > 0
	}
	if ep := fm.matchPattern(endpointKey); ep != nil {
		return ep.RequestPaths, ep.ResponsePaths, len(ep.RequestPaths) > 0 || len(ep.ResponsePaths) > 0
	}
	return nil, nil, false
}

func splitFieldPaths(raw string) []string {
	if raw == "" {
		return nil
	}
	var paths []string
	for _, part := range strings.Split(raw, "|") {
		part = strings.TrimSpace(part)
		if part != "" {
			paths = append(paths, part)
		}
	}
	return paths
}

// LoadFromEnv parses GUARDRAIL_FIELD_MAPPING into the registry.
func (fm *FieldMapping) LoadFromEnv() {
	raw := os.Getenv(EnvFieldMapping)
	report := fieldMappingLoadReport{envSet: raw != ""}

	fm.mu.Lock()
	defer fm.mu.Unlock()

	fm.byEndpoint = make(map[string]*endpointPaths)
	fm.wildcardPatterns = nil
	fm.loadReport = report

	if raw == "" {
		return
	}

	for _, entry := range strings.Split(raw, ";") {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		report.entryCount++

		// METHOD:PATH:requestPath,responsePath
		parts := strings.SplitN(entry, ":", 3)
		if len(parts) < 3 {
			report.skippedEntries = append(report.skippedEntries, entry+` (invalid format: expected METHOD:PATH:requestPath[,responsePath])`)
			continue
		}
		method := strings.TrimSpace(strings.ToUpper(parts[0]))
		if method == "" {
			report.skippedEntries = append(report.skippedEntries, entry+` (missing HTTP method)`)
			continue
		}

		path := strings.TrimSpace(parts[1])
		path = strings.ReplaceAll(path, "__", "/")
		path = strings.TrimSuffix(path, "/")
		if path == "" {
			path = "/"
		}
		key := method + ":" + path

		fieldPart := strings.TrimSpace(parts[2])
		reqPaths, respPaths := []string(nil), []string(nil)
		if fieldPart != "" {
			reqResp := strings.SplitN(fieldPart, ",", 2)
			reqPaths = splitFieldPaths(reqResp[0])
			if len(reqResp) > 1 {
				respPaths = splitFieldPaths(reqResp[1])
			}
		}
		if len(reqPaths) == 0 && len(respPaths) == 0 {
			report.skippedEntries = append(report.skippedEntries, entry+` (no request or response field paths)`)
			continue
		}

		ep := &endpointPaths{RequestPaths: reqPaths, ResponsePaths: respPaths}
		summary := endpointMappingSummary{
			endpointKey:   key,
			requestPaths:  append([]string(nil), reqPaths...),
			responsePaths: append([]string(nil), respPaths...),
		}

		if isWildcardPath(path) {
			regexStr := wildcardToRegex(path)
			compiled, err := regexp.Compile(regexStr)
			if err != nil {
				report.skippedEntries = append(report.skippedEntries, entry+` (invalid wildcard path: `+err.Error()+`)`)
				continue
			}
			fm.wildcardPatterns = append(fm.wildcardPatterns, endpointPattern{
				method: method,
				regex:  compiled,
				paths:  ep,
			})
			report.wildcardEndpoints = append(report.wildcardEndpoints, summary)
			continue
		}
		fm.byEndpoint[key] = ep
		report.exactEndpoints = append(report.exactEndpoints, summary)
	}

	fm.loadReport = report
}

// resolveFieldsForEndpoint returns message field entries from dashboard schema or env fallback.
func resolveFieldsForEndpoint(method, path string, isRequest bool) []MessageFieldEntry {
	key := EndpointKey(method, path)

	if gs, ok := GlobalGuardrailSchemaRegistry().Get(key); ok && gs != nil {
		var fields []MessageFieldEntry
		if isRequest {
			fields = gs.RequestMessageFields
		} else {
			fields = gs.ResponseMessageFields
		}
		if len(fields) > 0 {
			return fields
		}
	}

	reqPaths, respPaths, ok := GlobalFieldMapping().GetPaths(key)
	if !ok {
		return nil
	}
	fieldPaths := reqPaths
	if !isRequest {
		fieldPaths = respPaths
	}
	if len(fieldPaths) == 0 {
		return nil
	}
	entries := make([]MessageFieldEntry, 0, len(fieldPaths))
	for _, fieldPath := range fieldPaths {
		entries = append(entries, MessageFieldEntry{FieldPath: fieldPath})
	}
	return entries
}

// GetValueAtPathFromJSON returns the string at dot path in JSON payload.
// Supports bracket notation, negative indices, and messages.role=user style filters.
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
	return coerceValueToString(v)
}

func coerceValueToString(v any) (string, bool) {
	switch val := v.(type) {
	case string:
		if val == "" {
			return "", false
		}
		return val, true
	case []any:
		// Multimodal content blocks: [{ "type": "text", "text": "..." }]
		var texts []string
		for _, item := range val {
			if m, ok := item.(map[string]any); ok {
				if t, ok := m["text"].(string); ok && t != "" {
					texts = append(texts, t)
				}
			}
		}
		if len(texts) == 0 {
			return "", false
		}
		return strings.Join(texts, "\n"), true
	default:
		return "", false
	}
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
		if strings.HasPrefix(key, "role=") {
			role := strings.TrimPrefix(key, "role=")
			var found any
			for _, elem := range m {
				if obj, ok := elem.(map[string]any); ok {
					if r, _ := obj["role"].(string); r == role {
						found = elem
					}
				}
			}
			if found == nil {
				return nil
			}
			if len(rest) == 0 {
				return found
			}
			return valueAtPath(found, rest)
		}
		idx, ok := parseArrayIndex(key, len(m))
		if !ok {
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

func parseArrayIndex(key string, length int) (int, bool) {
	if length == 0 {
		return 0, false
	}
	negative := false
	n := 0
	hasDigit := false
	for _, c := range key {
		if c == '-' {
			negative = true
			continue
		}
		if c >= '0' && c <= '9' {
			hasDigit = true
			n = n*10 + int(c-'0')
			continue
		}
		return 0, false
	}
	if !hasDigit {
		return 0, false
	}
	idx := n
	if negative {
		idx = length - n
	}
	if idx < 0 || idx >= length {
		return 0, false
	}
	return idx, true
}
