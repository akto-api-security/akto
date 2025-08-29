package config

import (
	"fmt"
	"os"
	"strconv"

	"mcp-threat-detection/mcp-threat/constants"
	"mcp-threat-detection/mcp-threat/types"
)

// LoadConfigFromEnv loads configuration from environment variables
func LoadConfigFromEnv() (*types.AppConfig, error) {
	config := &types.AppConfig{
		LLM: types.LLMConfig{
			ProviderType: getEnvOrDefault("MCP_LLM_PROVIDER", constants.ProviderOpenAI),
			Model:        getEnvOrDefault("MCP_LLM_MODEL", constants.DefaultModel),
			Timeout:      getEnvIntOrDefault("MCP_LLM_TIMEOUT", constants.DefaultTimeout),
			Temperature:  getEnvFloatOrDefault("MCP_LLM_TEMPERATURE", constants.DefaultTemperature),
		},
		Debug: getEnvBoolOrDefault("MCP_DEBUG", false),
	}

	// Load API key (check both MCP_LLM_API_KEY and provider-specific keys)
	if apiKey := os.Getenv("MCP_LLM_API_KEY"); apiKey != "" {
		config.LLM.APIKey = &apiKey
	} else {
		// Check provider-specific keys
		switch config.LLM.ProviderType {
		case constants.ProviderOpenAI:
			if apiKey := os.Getenv("OPENAI_API_KEY"); apiKey != "" {
				config.LLM.APIKey = &apiKey
			}
		}
	}

	// Load base URL for self-hosted providers
	if baseURL := os.Getenv("MCP_LLM_BASE_URL"); baseURL != "" {
		config.LLM.BaseURL = &baseURL
	}

	// Validate configuration
	if err := validateConfig(config); err != nil {
		return nil, fmt.Errorf("invalid configuration: %w", err)
	}

	return config, nil
}

// GetProviderConfig returns provider-specific configuration
func GetProviderConfig(config *types.AppConfig) map[string]interface{} {
	providerConfig := map[string]interface{}{
		"timeout":     config.LLM.Timeout,
		"temperature": config.LLM.Temperature,
	}

	switch config.LLM.ProviderType {
	case constants.ProviderOpenAI:
		if config.LLM.APIKey != nil {
			providerConfig["api_key"] = *config.LLM.APIKey
		}
		providerConfig["model"] = config.LLM.Model
		if config.LLM.BaseURL != nil {
			providerConfig["base_url"] = *config.LLM.BaseURL
		}

	case constants.ProviderSelfHosted:
		if config.LLM.BaseURL != nil {
			providerConfig["endpoint"] = *config.LLM.BaseURL
		}
		if config.LLM.APIKey != nil {
			providerConfig["api_key"] = *config.LLM.APIKey
		}
		providerConfig["model"] = config.LLM.Model
	}

	return providerConfig
}

// GetEnvOrDefault gets an environment variable or returns a default value
func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// getEnvIntOrDefault gets an environment variable as int or returns a default value
func getEnvIntOrDefault(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}

// getEnvFloatOrDefault gets an environment variable as float64 or returns a default value
func getEnvFloatOrDefault(key string, defaultValue float64) float64 {
	if value := os.Getenv(key); value != "" {
		if floatValue, err := strconv.ParseFloat(value, 64); err == nil {
			return floatValue
		}
	}
	return defaultValue
}

// getEnvBoolOrDefault gets an environment variable as bool or returns a default value
func getEnvBoolOrDefault(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		switch value {
		case "true", "1", "yes", "on":
			return true
		case "false", "0", "no", "off":
			return false
		}
	}
	return defaultValue
}

// validateConfig validates the configuration
func validateConfig(config *types.AppConfig) error {
	// Validate LLM configuration
	if config.LLM.ProviderType == "" {
		return fmt.Errorf("LLM provider type is required")
	}

	if config.LLM.APIKey == nil || *config.LLM.APIKey == "" {
		return fmt.Errorf("API key is required for provider: %s", config.LLM.ProviderType)
	}

	if config.LLM.Model == "" {
		return fmt.Errorf("model name is required")
	}

	if config.LLM.Timeout <= 0 {
		return fmt.Errorf("timeout must be positive")
	}

	if config.LLM.Temperature < 0 || config.LLM.Temperature > 2 {
		return fmt.Errorf("temperature must be between 0 and 2")
	}

	// Validate provider-specific configuration
	switch config.LLM.ProviderType {
	case constants.ProviderOpenAI:
		// OpenAI is valid
	case constants.ProviderSelfHosted:
		if config.LLM.BaseURL == nil || *config.LLM.BaseURL == "" {
			return fmt.Errorf("base URL is required for self-hosted provider")
		}
	default:
		return fmt.Errorf("unsupported provider type: %s", config.LLM.ProviderType)
	}

	return nil
}

// GetDefaultConfig returns the default configuration
func GetDefaultConfig() *types.AppConfig {
	return &types.AppConfig{
		LLM: types.LLMConfig{
			ProviderType: constants.ProviderOpenAI,
			Model:        constants.DefaultModel,
			Timeout:      constants.DefaultTimeout,
			Temperature:  constants.DefaultTemperature,
		},
		Debug: false,
	}
}
