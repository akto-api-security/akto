package client

import (
	"context"
	"encoding/json"
	"fmt"
	"log"

	"mcp-threat-detection/mcp-threat/constants"
	"mcp-threat-detection/mcp-threat/providers"
	"mcp-threat-detection/mcp-threat/types"
	"mcp-threat-detection/mcp-threat/validators"
)

// MCPValidator is the main client for MCP validation
type MCPValidator struct {
	providerType      string
	provider          providers.LLMProvider
	keywordDetector   *validators.KeywordDetector
	requestValidator  *validators.RequestValidator
	responseValidator *validators.ResponseValidator
}

// NewMCPValidator creates a new MCP validator instance
func NewMCPValidator(providerType string, providerConfig map[string]interface{}) (*MCPValidator, error) {
	// Create provider
	provider, err := providers.GetProvider(providerType, providerConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to create provider: %w", err)
	}

	// Create validators
	keywordDetector := validators.NewKeywordDetector()
	requestValidator := validators.NewRequestValidator(provider)
	responseValidator := validators.NewResponseValidator(provider)

	return &MCPValidator{
		providerType:      providerType,
		provider:          provider,
		keywordDetector:   keywordDetector,
		requestValidator:  requestValidator,
		responseValidator: responseValidator,
	}, nil
}

// NewMCPValidatorWithConfig creates a new MCP validator with full configuration
func NewMCPValidatorWithConfig(config *types.AppConfig) (*MCPValidator, error) {
	providerConfig := make(map[string]interface{})

	// Set basic config
	providerConfig["timeout"] = config.LLM.Timeout
	providerConfig["temperature"] = config.LLM.Temperature

	// Set provider-specific config
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

	return NewMCPValidator(config.LLM.ProviderType, providerConfig)
}

// Validate is a generic validation method that handles request/response automatically
func (mv *MCPValidator) Validate(ctx context.Context, payload interface{}, toolDescription *string) *types.ValidationResponse {
	// Serialize payload to string
	var payloadStr string
	switch v := payload.(type) {
	case string:
		payloadStr = v
	default:
		if jsonData, err := json.Marshal(v); err == nil {
			payloadStr = string(jsonData)
		} else {
			response := types.NewValidationResponse()
			response.SetError(fmt.Sprintf("failed to serialize payload: %v", err))
			return response
		}
	}

	request := &types.ValidationRequest{
		MCPPayload:      payloadStr,
		ToolDescription: toolDescription,
	}

	keywordResponse := mv.keywordDetector.Validate(ctx, request)
	if keywordResponse.Verdict != nil && keywordResponse.Verdict.IsMaliciousRequest {
		log.Printf("INFO: Threats detected by keyword detector, blocking")
		return keywordResponse
	}

	return mv.detectValidationType(payload).Validate(ctx, request)
}

// detectValidationType returns the appropriate LLM validator (request/response)
func (mv *MCPValidator) detectValidationType(payload interface{}) validators.Validator {
	// Convert payload to map for analysis
	var payloadMap map[string]interface{}

	switch v := payload.(type) {
	case map[string]interface{}:
		payloadMap = v
	case string:
		// Try to parse JSON string
		if err := json.Unmarshal([]byte(v), &payloadMap); err != nil {
			// If not JSON, treat as request
			return mv.requestValidator
		}
	default:
		// For other types, try to marshal to JSON first
		if jsonData, err := json.Marshal(v); err == nil {
			if err := json.Unmarshal(jsonData, &payloadMap); err != nil {
				return mv.requestValidator
			}
		} else {
			return mv.requestValidator
		}
	}

	// Check for response indicators
	if payloadMap != nil {
		// Common response fields that indicate this is a response
		responseIndicators := []string{"result", "error", "data", "content", "output", "response"}
		for _, indicator := range responseIndicators {
			if _, exists := payloadMap[indicator]; exists {
				return mv.responseValidator
			}
		}

		// Check for request-specific fields
		requestIndicators := []string{"method", "params", "arguments", "name", "id"}
		for _, indicator := range requestIndicators {
			if _, exists := payloadMap[indicator]; exists {
				return mv.requestValidator
			}
		}
	}

	// Default to request validator
	return mv.requestValidator
}

// Close cleans up resources
func (mv *MCPValidator) Close() error {
	// For now, no cleanup is needed
	// In the future, this could close connections, cancel goroutines, etc.
	return nil
}
