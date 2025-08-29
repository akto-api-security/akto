package providers

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"mcp-threat-detection/mcp-threat/constants"
	"mcp-threat-detection/mcp-threat/types"
)

// LLMProvider defines the interface for different LLM providers
type LLMProvider interface {
	Generate(ctx context.Context, messages []types.ChatMessage, config map[string]interface{}) (string, error)
}

// OpenAIProvider implements LLM provider for OpenAI using direct API calls
type OpenAIProvider struct {
	apiKey  string
	model   string
	baseURL string
	timeout int
}

// NewOpenAIProvider creates a new OpenAI provider
func NewOpenAIProvider(config map[string]interface{}) (*OpenAIProvider, error) {
	apiKey, ok := config["api_key"].(string)
	if !ok || apiKey == "" {
		return nil, fmt.Errorf("API key is required for OpenAI provider")
	}

	model, ok := config["model"].(string)
	if !ok || model == "" {
		model = constants.DefaultModel
	}

	baseURL := "https://api.openai.com/v1"
	if customURL, ok := config["base_url"].(string); ok && customURL != "" {
		baseURL = customURL
	}

	timeout := constants.DefaultTimeout
	if t, ok := config["timeout"].(int); ok {
		timeout = t
	}

	return &OpenAIProvider{
		apiKey:  apiKey,
		model:   model,
		baseURL: baseURL,
		timeout: timeout,
	}, nil
}

// Generate generates a response using OpenAI API directly
func (op *OpenAIProvider) Generate(ctx context.Context, messages []types.ChatMessage, config map[string]interface{}) (string, error) {
	// Convert messages to OpenAI format
	openaiMessages := make([]map[string]interface{}, len(messages))
	for i, msg := range messages {
		openaiMessages[i] = map[string]interface{}{
			"role":    msg.Role,
			"content": msg.Content,
		}
	}

	// Get configuration values
	temperature := constants.DefaultTemperature
	if temp, ok := config["temperature"].(float64); ok {
		temperature = temp
	}

	// Create request payload
	payload := map[string]interface{}{
		"model":                 op.model,
		"messages":              openaiMessages,
		"temperature":           temperature,
		"max_completion_tokens": 10000,
	}

	jsonData, err := json.Marshal(payload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request: %w", err)
	}

	// Create HTTP request
	req, err := http.NewRequestWithContext(ctx, "POST", op.baseURL+"/chat/completions", bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+op.apiKey)

	// Execute request
	client := &http.Client{Timeout: time.Duration(op.timeout) * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("OpenAI API request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("OpenAI API error: %d - %s", resp.StatusCode, string(body))
	}

	// Parse response
	var response types.LLMResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return "", fmt.Errorf("failed to decode response: %w", err)
	}

	if response.Error != nil {
		return "", fmt.Errorf("OpenAI API error: %s", response.Error.Message)
	}

	if len(response.Choices) == 0 {
		return "", fmt.Errorf("no choices in OpenAI response")
	}

	return response.Choices[0].Message.Content, nil
}

// SelfHostedProvider implements LLM provider for self-hosted LLMs
type SelfHostedProvider struct {
	endpoint string
	apiKey   *string
	model    string
	timeout  int
}

// NewSelfHostedProvider creates a new self-hosted provider
func NewSelfHostedProvider(config map[string]interface{}) (*SelfHostedProvider, error) {
	endpoint, ok := config["endpoint"].(string)
	if !ok || endpoint == "" {
		return nil, fmt.Errorf("endpoint is required for self-hosted provider")
	}

	model, ok := config["model"].(string)
	if !ok || model == "" {
		model = "default"
	}

	timeout := constants.DefaultTimeout
	if t, ok := config["timeout"].(int); ok {
		timeout = t
	}

	provider := &SelfHostedProvider{
		endpoint: endpoint,
		model:    model,
		timeout:  timeout,
	}

	// API key is optional for self-hosted
	if apiKey, ok := config["api_key"].(string); ok && apiKey != "" {
		provider.apiKey = &apiKey
	}

	return provider, nil
}

// Generate generates a response using self-hosted LLM
func (shp *SelfHostedProvider) Generate(ctx context.Context, messages []types.ChatMessage, config map[string]interface{}) (string, error) {
	// Create request payload
	payload := types.LLMRequest{
		Model:       shp.model,
		Messages:    messages,
		Temperature: constants.DefaultTemperature,
		MaxTokens:   4000,
	}

	// Override with config values
	if temp, ok := config["temperature"].(float64); ok {
		payload.Temperature = temp
	}

	jsonData, err := json.Marshal(payload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request: %w", err)
	}

	// Create HTTP request
	req, err := http.NewRequestWithContext(ctx, "POST", shp.endpoint, bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if shp.apiKey != nil {
		req.Header.Set("Authorization", "Bearer "+*shp.apiKey)
	}

	// Execute request
	client := &http.Client{Timeout: time.Duration(shp.timeout) * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("self-hosted LLM request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("self-hosted LLM error: %d - %s", resp.StatusCode, string(body))
	}

	// Parse response
	var response types.LLMResponse
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return "", fmt.Errorf("failed to decode response: %w", err)
	}

	if response.Error != nil {
		return "", fmt.Errorf("self-hosted LLM error: %s", response.Error.Message)
	}

	if len(response.Choices) == 0 {
		return "", fmt.Errorf("no choices in self-hosted LLM response")
	}

	return response.Choices[0].Message.Content, nil
}

// GetProvider creates a provider instance based on type
func GetProvider(providerType string, config map[string]interface{}) (LLMProvider, error) {
	switch providerType {
	case constants.ProviderOpenAI:
		return NewOpenAIProvider(config)
	case constants.ProviderSelfHosted:
		return NewSelfHostedProvider(config)
	default:
		return nil, fmt.Errorf("unsupported provider type: %s", providerType)
	}
}
