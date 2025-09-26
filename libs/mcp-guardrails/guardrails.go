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
	config         *GuardrailConfig
	patterns       []SensitiveDataPattern
	filters        []ContentFilter
	rateLimiter    *RateLimiter
	templateClient *TemplateClient
	templates      map[string]YamlTemplate
	configs        map[string]MCPGuardrailConfig
	mutex          sync.RWMutex
}

// NewGuardrailEngine creates a new guardrail engine with the given configuration
func NewGuardrailEngine(config *GuardrailConfig) *GuardrailEngine {
	engine := &GuardrailEngine{
		config:    config,
		patterns:  getDefaultSensitivePatterns(),
		filters:   getDefaultContentFilters(),
		templates: make(map[string]YamlTemplate),
		configs:   make(map[string]MCPGuardrailConfig),
	}

	if config.EnableRateLimiting {
		engine.rateLimiter = NewRateLimiter(config.RateLimitConfig)
	}

	return engine
}

// NewGuardrailEngineWithClient creates a new guardrail engine with a template client
func NewGuardrailEngineWithClient(config *GuardrailConfig, templateClient *TemplateClient) *GuardrailEngine {
	engine := NewGuardrailEngine(config)
	engine.templateClient = templateClient
	return engine
}

// ProcessResponse applies all configured guardrails to an MCP response
func (g *GuardrailEngine) ProcessResponse(response *MCPResponse) *GuardrailResult {
	result := &GuardrailResult{
		SanitizedResponse: response,
		Blocked:           false,
		Warnings:          []string{},
		Logs:              []LogEntry{},
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
			"blocked":      result.Blocked,
			"warnings":     result.Warnings,
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

// LoadTemplatesFromAPI loads guardrail templates from the database API
func (g *GuardrailEngine) LoadTemplatesFromAPI() error {
	if g.templateClient == nil {
		return fmt.Errorf("template client not configured")
	}

	g.mutex.Lock()
	defer g.mutex.Unlock()

	// Fetch all active templates
	templates, err := g.templateClient.FetchGuardrailTemplates(true)
	if err != nil {
		return fmt.Errorf("failed to fetch templates: %w", err)
	}

	// Update internal templates map
	g.templates = make(map[string]YamlTemplate)
	for _, template := range templates {
		g.templates[template.ID] = template
	}

	// Fetch parsed configurations
	configs, err := g.templateClient.FetchGuardrailConfigs(false)
	if err != nil {
		return fmt.Errorf("failed to fetch configurations: %w", err)
	}

	g.configs = configs

	log.Printf("Loaded %d templates and %d configurations from API", len(g.templates), len(g.configs))
	return nil
}

// RefreshTemplates refreshes templates from the API
func (g *GuardrailEngine) RefreshTemplates() error {
	return g.LoadTemplatesFromAPI()
}

// GetAllTemplates returns all loaded templates
func (g *GuardrailEngine) GetAllTemplates() map[string]YamlTemplate {
	g.mutex.RLock()
	defer g.mutex.RUnlock()

	// Return a copy to avoid concurrent access issues
	templates := make(map[string]YamlTemplate)
	for k, v := range g.templates {
		templates[k] = v
	}
	return templates
}

// GetAllConfigs returns all loaded configurations
func (g *GuardrailEngine) GetAllConfigs() map[string]MCPGuardrailConfig {
	g.mutex.RLock()
	defer g.mutex.RUnlock()

	// Return a copy to avoid concurrent access issues
	configs := make(map[string]MCPGuardrailConfig)
	for k, v := range g.configs {
		configs[k] = v
	}
	return configs
}

// StartTemplateFetcher starts the background service to fetch templates periodically
func (g *GuardrailEngine) StartTemplateFetcher(interval time.Duration) {
	if g.templateClient == nil {
		log.Println("Template client not configured, skipping template fetcher")
		return
	}

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		// Initial fetch
		if err := g.LoadTemplatesFromAPI(); err != nil {
			log.Printf("Initial template fetch failed: %v", err)
		} else {
			log.Printf("Initial templates loaded successfully")
		}

		// Periodic fetching
		for range ticker.C {
			if err := g.LoadTemplatesFromAPI(); err != nil {
				log.Printf("Template refresh failed: %v", err)
			} else {
				log.Printf("Templates refreshed successfully")
			}
		}
	}()
}

// ProcessMCPRequest is a convenience function for MCP proxy integration
// It processes an MCP request and returns a result that can be used to determine
// whether to allow, block, or modify the request
func (g *GuardrailEngine) ProcessMCPRequest(requestData []byte) (*GuardrailResult, error) {
	var request MCPRequest
	if err := json.Unmarshal(requestData, &request); err != nil {
		return &GuardrailResult{
			Blocked:     true,
			BlockReason: "Invalid request format",
			Warnings:    []string{fmt.Sprintf("Failed to parse request: %v", err)},
		}, nil
	}

	return g.ProcessRequest(&request), nil
}

// ProcessMCPResponse is a convenience function for MCP proxy integration
// It processes an MCP response and returns a result that can be used to determine
// whether to allow, block, or modify the response
func (g *GuardrailEngine) ProcessMCPResponse(responseData []byte) (*GuardrailResult, error) {
	var response MCPResponse
	if err := json.Unmarshal(responseData, &response); err != nil {
		return &GuardrailResult{
			Blocked:     true,
			BlockReason: "Invalid response format",
			Warnings:    []string{fmt.Sprintf("Failed to parse response: %v", err)},
		}, nil
	}

	return g.ProcessResponse(&response), nil
}
