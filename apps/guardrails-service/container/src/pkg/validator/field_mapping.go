package validator

import (
	"encoding/json"
	"os"
	"regexp"
	"strings"
	"sync"
)

const (
	// EnvFieldMapping: per-endpoint request/response JSON paths.
	// Format: semicolon-separated "METHOD:PATH:requestPath,responsePath"
	// Example: POST:/v1/chat/completions:messages.role=user.content.0.text,choices.0.message.content
	EnvFieldMapping = "GUARDRAIL_FIELD_MAPPING"
)

type endpointPaths struct {
	RequestPath  string
	ResponsePath string
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
}

var (
	globalFieldMapping     *FieldMapping
	globalFieldMappingOnce sync.Once
)

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

// GetPaths returns request/response field paths for endpointKey from env mapping.
func (fm *FieldMapping) GetPaths(endpointKey string) (requestPath, responsePath string, ok bool) {
	fm.mu.RLock()
	defer fm.mu.RUnlock()

	if ep, found := fm.byEndpoint[endpointKey]; found && ep != nil {
		return ep.RequestPath, ep.ResponsePath, ep.RequestPath != "" || ep.ResponsePath != ""
	}
	if ep := fm.matchPattern(endpointKey); ep != nil {
		return ep.RequestPath, ep.ResponsePath, ep.RequestPath != "" || ep.ResponsePath != ""
	}
	return "", "", false
}

// LoadFromEnv parses GUARDRAIL_FIELD_MAPPING into the registry.
func (fm *FieldMapping) LoadFromEnv() {
	raw := os.Getenv(EnvFieldMapping)
	if raw == "" {
		return
	}

	fm.mu.Lock()
	defer fm.mu.Unlock()

	fm.byEndpoint = make(map[string]*endpointPaths)
	fm.wildcardPatterns = nil

	for _, entry := range strings.Split(raw, ";") {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		// METHOD:PATH:requestPath,responsePath
		parts := strings.SplitN(entry, ":", 3)
		if len(parts) < 3 {
			continue
		}
		method := strings.TrimSpace(strings.ToUpper(parts[0]))
		path := strings.TrimSpace(parts[1])
		path = strings.ReplaceAll(path, "__", "/")
		path = strings.TrimSuffix(path, "/")
		if path == "" {
			path = "/"
		}
		key := method + ":" + path

		fieldPart := strings.TrimSpace(parts[2])
		reqPath, respPath := "", ""
		if fieldPart != "" {
			reqResp := strings.SplitN(fieldPart, ",", 2)
			reqPath = strings.TrimSpace(reqResp[0])
			if len(reqResp) > 1 {
				respPath = strings.TrimSpace(reqResp[1])
			}
		}
		ep := &endpointPaths{RequestPath: reqPath, ResponsePath: respPath}
		if isWildcardPath(path) {
			regexStr := wildcardToRegex(path)
			if compiled, err := regexp.Compile(regexStr); err == nil {
				fm.wildcardPatterns = append(fm.wildcardPatterns, endpointPattern{
					method: method,
					regex:  compiled,
					paths:  ep,
				})
			}
			continue
		}
		fm.byEndpoint[key] = ep
	}
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

	reqPath, respPath, ok := GlobalFieldMapping().GetPaths(key)
	if !ok {
		return nil
	}
	fieldPath := reqPath
	if !isRequest {
		fieldPath = respPath
	}
	if fieldPath == "" {
		return nil
	}
	return []MessageFieldEntry{{FieldPath: fieldPath}}
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
