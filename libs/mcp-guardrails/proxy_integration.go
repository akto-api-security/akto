package guardrails

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strconv"
	"time"
)

// MCPProxyIntegration provides a simplified interface for MCP proxy integration
type MCPProxyIntegration struct {
	engine *GuardrailEngine
}

// NewMCPProxyIntegration creates a new MCP proxy integration instance
// It reads configuration from environment variables:
// - GUARDRAIL_SERVICE_URL: URL of the database-abstractor service
// - GUARDRAIL_SERVICE_TOKEN: Authentication token (optional)
// - GUARDRAIL_REFRESH_INTERVAL: Template refresh interval in minutes (default: 10)
// - GUARDRAIL_ENABLE_SANITIZATION: Enable data sanitization (default: true)
// - GUARDRAIL_ENABLE_CONTENT_FILTERING: Enable content filtering (default: true)
// - GUARDRAIL_ENABLE_RATE_LIMITING: Enable rate limiting (default: true)
// - GUARDRAIL_ENABLE_INPUT_VALIDATION: Enable input validation (default: true)
// - GUARDRAIL_ENABLE_OUTPUT_FILTERING: Enable output filtering (default: true)
// - GUARDRAIL_ENABLE_LOGGING: Enable logging (default: true)
func NewMCPProxyIntegration() (*MCPProxyIntegration, error) {
	serviceURL := os.Getenv("GUARDRAIL_SERVICE_URL")
	if serviceURL == "" {
		return nil, fmt.Errorf("GUARDRAIL_SERVICE_URL environment variable is required")
	}

	// Create default configuration
	config := &GuardrailConfig{
		EnableDataSanitization: getEnvBool("GUARDRAIL_ENABLE_SANITIZATION", true),
		SensitiveFields:        []string{"password", "api_key", "secret", "token", "auth"},
		RedactionPatterns:      []string{},

		EnableContentFiltering: getEnvBool("GUARDRAIL_ENABLE_CONTENT_FILTERING", true),
		BlockedKeywords:        []string{"malicious", "dangerous", "exploit"},
		AllowedDomains:         []string{},

		EnableRateLimiting: getEnvBool("GUARDRAIL_ENABLE_RATE_LIMITING", true),
		RateLimitConfig: RateLimitConfig{
			RequestsPerMinute: 100,
			BurstSize:         10,
			WindowSize:        time.Minute,
		},

		EnableInputValidation: getEnvBool("GUARDRAIL_ENABLE_INPUT_VALIDATION", true),
		ValidationRules: map[string]string{
			"method": "required",
		},

		EnableOutputFiltering: getEnvBool("GUARDRAIL_ENABLE_OUTPUT_FILTERING", true),
		OutputFilters:         []string{"block_sensitive"},

		EnableLogging: getEnvBool("GUARDRAIL_ENABLE_LOGGING", true),
		LogLevel:      "INFO",
	}

	// Create template client with optional authentication
	serviceToken := os.Getenv("GUARDRAIL_SERVICE_TOKEN")
	var templateClient *TemplateClient
	if serviceToken != "" {
		templateClient = NewTemplateClientWithAuth(serviceURL, serviceToken)
	} else {
		templateClient = NewTemplateClient(serviceURL)
	}

	// Create guardrail engine
	engine := NewGuardrailEngineWithClient(config, templateClient)

	integration := &MCPProxyIntegration{
		engine: engine,
	}

	// Start template fetcher
	refreshInterval := getEnvDuration("GUARDRAIL_REFRESH_INTERVAL", 10*time.Minute)
	engine.StartTemplateFetcher(refreshInterval)

	log.Printf("MCP Proxy Integration initialized with service URL: %s", serviceURL)
	log.Printf("Template refresh interval: %v", refreshInterval)

	return integration, nil
}

// RequestGuardrail processes an incoming MCP request
// Returns true if the request should be blocked, false if allowed
// If blocked, the reason is provided in the error
func (m *MCPProxyIntegration) RequestGuardrail(requestData []byte) (bool, string, error) {
	result, err := m.engine.ProcessMCPRequest(requestData)
	if err != nil {
		return true, "Processing error", err
	}

	if result.Blocked {
		return true, result.BlockReason, nil
	}

	// Log warnings if any
	for _, warning := range result.Warnings {
		log.Printf("Request guardrail warning: %s", warning)
	}

	return false, "", nil
}

// ResponseGuardrail processes an outgoing MCP response
// Returns the processed response data and whether it was modified
func (m *MCPProxyIntegration) ResponseGuardrail(responseData []byte) ([]byte, bool, error) {
	result, err := m.engine.ProcessMCPResponse(responseData)
	if err != nil {
		return responseData, false, err
	}

	if result.Blocked {
		// Return error response instead of original response
		errorResponse := map[string]interface{}{
			"error": map[string]interface{}{
				"code":    -32603,
				"message": result.BlockReason,
			},
		}
		blockedData, _ := json.Marshal(errorResponse)
		return blockedData, true, nil
	}

	// Check if response was sanitized
	if result.SanitizedResponse != nil {
		sanitizedData, err := json.Marshal(result.SanitizedResponse)
		if err != nil {
			return responseData, false, err
		}
		return sanitizedData, true, nil
	}

	// Log warnings if any
	for _, warning := range result.Warnings {
		log.Printf("Response guardrail warning: %s", warning)
	}

	return responseData, false, nil
}

// GetStatus returns the current status of the guardrails system
func (m *MCPProxyIntegration) GetStatus() map[string]interface{} {
	templates := m.engine.GetAllTemplates()
	configs := m.engine.GetAllConfigs()

	return map[string]interface{}{
		"templates_loaded": len(templates),
		"configs_loaded":   len(configs),
		"engine_active":    true,
	}
}

// RefreshTemplates manually triggers a template refresh
func (m *MCPProxyIntegration) RefreshTemplates() error {
	return m.engine.RefreshTemplates()
}

// Helper functions

func getEnvBool(key string, defaultValue bool) bool {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	result, err := strconv.ParseBool(value)
	if err != nil {
		log.Printf("Invalid boolean value for %s: %s, using default: %v", key, value, defaultValue)
		return defaultValue
	}
	return result
}

func getEnvDuration(key string, defaultValue time.Duration) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}

	// Parse as minutes
	minutes, err := strconv.Atoi(value)
	if err != nil {
		log.Printf("Invalid duration value for %s: %s, using default: %v", key, value, defaultValue)
		return defaultValue
	}

	return time.Duration(minutes) * time.Minute
}
