package guardrails

import (
	"encoding/json"
	"fmt"
	"log"
	"regexp"
	"strings"
	"sync"
	"time"
	"unicode"
)

// GuardrailEngine is the main engine that applies all guardrails
type GuardrailEngine struct {
	config     *GuardrailConfig
	patterns   []SensitiveDataPattern
	filters    []ContentFilter
	rateLimiter *RateLimiter
	mutex      sync.RWMutex
}

// NewGuardrailEngine creates a new guardrail engine with the given configuration
func NewGuardrailEngine(config *GuardrailConfig) *GuardrailEngine {
	engine := &GuardrailEngine{
		config:   config,
		patterns: getDefaultSensitivePatterns(),
		filters:  getDefaultContentFilters(),
	}

	if config.EnableRateLimiting {
		engine.rateLimiter = NewRateLimiter(config.RateLimitConfig)
	}

	return engine
}

// ProcessResponse applies all configured guardrails to an MCP response
func (g *GuardrailEngine) ProcessResponse(response *MCPResponse) *GuardrailResult {
	result := &GuardrailResult{
		SanitizedResponse: response,
		Blocked:          false,
		Warnings:         []string{},
		Logs:             []LogEntry{},
	}

	g.mutex.RLock()
	defer g.mutex.RUnlock()

	// Apply data sanitization
	if g.config.EnableDataSanitization {
		g.applyDataSanitization(result)
	}

	// Apply content filtering
	if g.config.EnableContentFiltering {
		g.applyContentFiltering(result)
	}

	// Apply output filtering
	if g.config.EnableOutputFiltering {
		g.applyOutputFiltering(result)
	}

	// Log the operation if enabled
	if g.config.EnableLogging {
		g.logOperation(result)
	}

	return result
}

// ProcessRequest applies guardrails to an MCP request
func (g *GuardrailEngine) ProcessRequest(request *MCPRequest) *GuardrailResult {
	result := &GuardrailResult{
		Blocked:  false,
		Warnings: []string{},
		Logs:     []LogEntry{},
	}

	g.mutex.RLock()
	defer g.mutex.RUnlock()

	// Apply rate limiting
	if g.config.EnableRateLimiting && g.rateLimiter != nil {
		if !g.rateLimiter.Allow() {
			result.Blocked = true
			result.BlockReason = "Rate limit exceeded"
			return result
		}
	}

	// Apply input validation
	if g.config.EnableInputValidation {
		g.applyInputValidation(request, result)
	}

	// Apply content filtering to request
	if g.config.EnableContentFiltering {
		g.applyRequestContentFiltering(request, result)
	}

	// Log the operation if enabled
	if g.config.EnableLogging {
		g.logOperation(result)
	}

	return result
}

// applyDataSanitization sanitizes sensitive data in the response
func (g *GuardrailEngine) applyDataSanitization(result *GuardrailResult) {
	if result.SanitizedResponse == nil {
		return
	}

	// Convert response to string for processing
	responseBytes, err := json.Marshal(result.SanitizedResponse)
	if err != nil {
		result.Warnings = append(result.Warnings, "Failed to marshal response for sanitization")
		return
	}

	responseStr := string(responseBytes)

	// Apply sensitive data patterns
	for _, pattern := range g.patterns {
		re, err := regexp.Compile(pattern.Pattern)
		if err != nil {
			result.Warnings = append(result.Warnings, fmt.Sprintf("Invalid pattern %s: %v", pattern.Name, err))
			continue
		}

		responseStr = re.ReplaceAllString(responseStr, pattern.Replacement)
	}

	// Apply custom sensitive fields
	for _, field := range g.config.SensitiveFields {
		fieldPattern := fmt.Sprintf(`"%s"\s*:\s*"([^"]*)"`, regexp.QuoteMeta(field))
		re, err := regexp.Compile(fieldPattern)
		if err != nil {
			continue
		}
		responseStr = re.ReplaceAllString(responseStr, fmt.Sprintf(`"%s": "***REDACTED***"`, field))
	}

	// Unmarshal back to response
	var sanitizedResponse MCPResponse
	if err := json.Unmarshal([]byte(responseStr), &sanitizedResponse); err != nil {
		result.Warnings = append(result.Warnings, "Failed to unmarshal sanitized response")
		return
	}

	result.SanitizedResponse = &sanitizedResponse
	result.Logs = append(result.Logs, LogEntry{
		Timestamp: time.Now(),
		Level:     "INFO",
		Message:   "Data sanitization applied",
	})
}

// applyContentFiltering filters content based on configured rules
func (g *GuardrailEngine) applyContentFiltering(result *GuardrailResult) {
	if result.SanitizedResponse == nil {
		return
	}

	responseBytes, err := json.Marshal(result.SanitizedResponse)
	if err != nil {
		return
	}

	responseStr := string(responseBytes)

	for _, filter := range g.filters {
		var matched bool
		switch filter.Type {
		case "keyword":
			matched = strings.Contains(strings.ToLower(responseStr), strings.ToLower(filter.Pattern))
		case "regex":
			re, err := regexp.Compile(filter.Pattern)
			if err != nil {
				continue
			}
			matched = re.MatchString(responseStr)
		}

		if matched {
			switch filter.Action {
			case "block":
				result.Blocked = true
				result.BlockReason = fmt.Sprintf("Content blocked: %s", filter.Description)
				return
			case "warn":
				result.Warnings = append(result.Warnings, fmt.Sprintf("Content warning: %s", filter.Description))
			case "sanitize":
				// Apply sanitization based on filter
				responseStr = g.sanitizeContent(responseStr, filter)
			}
		}
	}

	// Update response if content was sanitized
	if responseStr != string(responseBytes) {
		var sanitizedResponse MCPResponse
		if err := json.Unmarshal([]byte(responseStr), &sanitizedResponse); err == nil {
			result.SanitizedResponse = &sanitizedResponse
		}
	}
}

// applyRequestContentFiltering filters content in requests
func (g *GuardrailEngine) applyRequestContentFiltering(request *MCPRequest, result *GuardrailResult) {
	requestBytes, err := json.Marshal(request)
	if err != nil {
		return
	}

	requestStr := string(requestBytes)

	for _, filter := range g.filters {
		var matched bool
		switch filter.Type {
		case "keyword":
			matched = strings.Contains(strings.ToLower(requestStr), strings.ToLower(filter.Pattern))
		case "regex":
			re, err := regexp.Compile(filter.Pattern)
			if err != nil {
				continue
			}
			matched = re.MatchString(requestStr)
		}

		if matched {
			switch filter.Action {
			case "block":
				result.Blocked = true
				result.BlockReason = fmt.Sprintf("Request blocked: %s", filter.Description)
				return
			case "warn":
				result.Warnings = append(result.Warnings, fmt.Sprintf("Request warning: %s", filter.Description))
			}
		}
	}
}

// applyInputValidation validates input parameters
func (g *GuardrailEngine) applyInputValidation(request *MCPRequest, result *GuardrailResult) {
	// Validate method
	if request.Method == "" {
		result.Blocked = true
		result.BlockReason = "Empty method not allowed"
		return
	}

	// Apply custom validation rules
	for field, rule := range g.config.ValidationRules {
		if err := g.validateField(request, field, rule); err != nil {
			result.Blocked = true
			result.BlockReason = fmt.Sprintf("Validation failed for %s: %v", field, err)
			return
		}
	}
}

// applyOutputFiltering filters output based on configured rules
func (g *GuardrailEngine) applyOutputFiltering(result *GuardrailResult) {
	if result.SanitizedResponse == nil {
		return
	}

	for _, filter := range g.config.OutputFilters {
		// Apply output filters (simplified implementation)
		if strings.Contains(strings.ToLower(filter), "block") {
			result.Warnings = append(result.Warnings, fmt.Sprintf("Output filter applied: %s", filter))
		}
	}
}

// sanitizeContent applies content sanitization based on filter rules
func (g *GuardrailEngine) sanitizeContent(content string, filter ContentFilter) string {
	switch filter.Type {
	case "keyword":
		return strings.ReplaceAll(content, filter.Pattern, "***SANITIZED***")
	case "regex":
		re, err := regexp.Compile(filter.Pattern)
		if err != nil {
			return content
		}
		return re.ReplaceAllString(content, "***SANITIZED***")
	default:
		return content
	}
}

// validateField validates a specific field based on the given rule
func (g *GuardrailEngine) validateField(request *MCPRequest, field, rule string) error {
	// Simplified validation - in a real implementation, you'd have more sophisticated validation
	switch rule {
	case "required":
		if request.Method == "" {
			return fmt.Errorf("field %s is required", field)
		}
	case "alphanumeric":
		if !isAlphanumeric(request.Method) {
			return fmt.Errorf("field %s must be alphanumeric", field)
		}
	}
	return nil
}

// isAlphanumeric checks if a string contains only alphanumeric characters
func isAlphanumeric(s string) bool {
	for _, r := range s {
		if !unicode.IsLetter(r) && !unicode.IsDigit(r) {
			return false
		}
	}
	return true
}

// logOperation logs the guardrail operation
func (g *GuardrailEngine) logOperation(result *GuardrailResult) {
	logEntry := LogEntry{
		Timestamp: time.Now(),
		Level:     g.config.LogLevel,
		Message:   "Guardrail operation completed",
		Data: map[string]interface{}{
			"blocked":     result.Blocked,
			"warnings":    result.Warnings,
			"block_reason": result.BlockReason,
		},
	}

	result.Logs = append(result.Logs, logEntry)

	// Also log to standard logger if configured
	if g.config.LogLevel == "DEBUG" {
		log.Printf("Guardrail: %+v", logEntry)
	}
}

// AddSensitivePattern adds a new sensitive data pattern
func (g *GuardrailEngine) AddSensitivePattern(pattern SensitiveDataPattern) {
	g.mutex.Lock()
	defer g.mutex.Unlock()
	g.patterns = append(g.patterns, pattern)
}

// AddContentFilter adds a new content filter
func (g *GuardrailEngine) AddContentFilter(filter ContentFilter) {
	g.mutex.Lock()
	defer g.mutex.Unlock()
	g.filters = append(g.filters, filter)
}

// UpdateConfig updates the guardrail configuration
func (g *GuardrailEngine) UpdateConfig(config *GuardrailConfig) {
	g.mutex.Lock()
	defer g.mutex.Unlock()
	g.config = config
} 