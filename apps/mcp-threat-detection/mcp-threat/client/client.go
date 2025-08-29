package client

import (
	"context"
	"encoding/json"
	"fmt"

	"mcp-threat-detection/mcp-threat/constants"
	"mcp-threat-detection/mcp-threat/providers"
	"mcp-threat-detection/mcp-threat/types"
	"mcp-threat-detection/mcp-threat/validators"
)

// MCPValidator is the main client for MCP validation
type MCPValidator struct {
	providerType     string
	provider         providers.LLMProvider
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
	requestValidator := validators.NewRequestValidator(provider)
	responseValidator := validators.NewResponseValidator(provider)

	return &MCPValidator{
		providerType:     providerType,
		provider:         provider,
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

// ValidateRequest validates an MCP request
func (mv *MCPValidator) ValidateRequest(ctx context.Context, mcpPayload interface{}, toolDescription *string) *types.ValidationResponse {
	// Convert payload to string if it's not already
	var payloadStr string
	switch v := mcpPayload.(type) {
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

	// Create validation request
	request := &types.ValidationRequest{
		MCPPayload:      payloadStr,
		ToolDescription: toolDescription,
	}

	// Perform validation
	return mv.requestValidator.Validate(ctx, request)
}

// ValidateResponse validates an MCP response
func (mv *MCPValidator) ValidateResponse(ctx context.Context, mcpResponse interface{}, toolDescription *string) *types.ValidationResponse {
	// Convert response to string if it's not already
	var responseStr string
	switch v := mcpResponse.(type) {
	case string:
		responseStr = v
	default:
		if jsonData, err := json.Marshal(v); err == nil {
			responseStr = string(jsonData)
		} else {
			response := types.NewValidationResponse()
			response.SetError(fmt.Sprintf("failed to serialize response: %v", err))
			return response
		}
	}

	// Create validation request
	request := &types.ValidationRequest{
		MCPPayload:      responseStr,
		ToolDescription: toolDescription,
	}

	// Perform validation
	return mv.responseValidator.Validate(ctx, request)
}

// Validate is a generic validation method that automatically determines the type
func (mv *MCPValidator) Validate(ctx context.Context, payload interface{}, toolDescription *string) *types.ValidationResponse {
	// Auto-detect validation type based on payload content
	validationType := mv.detectValidationType(payload)
	
	if validationType == "response" {
		return mv.ValidateResponse(ctx, payload, toolDescription)
	}
	return mv.ValidateRequest(ctx, payload, toolDescription)
}

// detectValidationType automatically determines if payload is a request or response
func (mv *MCPValidator) detectValidationType(payload interface{}) string {
	// Convert payload to map for analysis
	var payloadMap map[string]interface{}
	
	switch v := payload.(type) {
	case map[string]interface{}:
		payloadMap = v
	case string:
		// Try to parse JSON string
		if err := json.Unmarshal([]byte(v), &payloadMap); err != nil {
			// If not JSON, treat as request
			return "request"
		}
	default:
		// For other types, try to marshal to JSON first
		if jsonData, err := json.Marshal(v); err == nil {
			if err := json.Unmarshal(jsonData, &payloadMap); err != nil {
				return "request"
			}
		} else {
			return "request"
		}
	}
	
	// Check for response indicators
	if payloadMap != nil {
		// Common response fields that indicate this is a response
		responseIndicators := []string{"result", "error", "data", "content", "output", "response"}
		for _, indicator := range responseIndicators {
			if _, exists := payloadMap[indicator]; exists {
				return "response"
			}
		}
		
		// Check for request-specific fields
		requestIndicators := []string{"method", "params", "arguments", "name", "id"}
		for _, indicator := range requestIndicators {
			if _, exists := payloadMap[indicator]; exists {
				return "request"
			}
		}
	}
	
	// Default to request validation
	return "request"
}

// Close cleans up resources
func (mv *MCPValidator) Close() error {
	// For now, no cleanup is needed
	// In the future, this could close connections, cancel goroutines, etc.
	return nil
} 