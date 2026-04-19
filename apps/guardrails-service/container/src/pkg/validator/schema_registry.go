package validator

import (
	"encoding/json"
	"regexp"
	"strings"
	"sync"
)

// aktoParamPattern matches Akto-style path parameters: INTEGER, STRING, FLOAT, BOOLEAN
var aktoParamPattern = regexp.MustCompile(`\b(INTEGER|STRING|FLOAT|BOOLEAN)\b`)

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
			pattern := regexp.QuoteMeta(key)
			pattern = aktoParamPattern.ReplaceAllLiteralString(pattern, `[^/]+`)
			if re, err := regexp.Compile("^" + pattern + "$"); err == nil {
				newTemplates[key] = re
			}
		}
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.schemas = newSchemas
	r.compiledTemplates = newTemplates
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
