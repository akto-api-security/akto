package validators

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"mcp-threat-detection/mcp-threat/constants"
	"mcp-threat-detection/mcp-threat/providers"
	"mcp-threat-detection/mcp-threat/types"
)

// Validator defines the interface that all validators must implement
type Validator interface {
	Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse
}

// BaseLLMValidator provides common validation functionality for LLM-based validators
type BaseLLMValidator struct {
	provider providers.LLMProvider
	prompt   string
}

// NewBaseLLMValidator creates a new base validator
func NewBaseLLMValidator(provider providers.LLMProvider, prompt string) *BaseLLMValidator {
	return &BaseLLMValidator{
		provider: provider,
		prompt:   prompt,
	}
}

// buildContext builds the context string for validation
func (bv *BaseLLMValidator) buildContext(mcpPayload interface{}, toolDesc *string) string {
	var contextParts []string

	// Tool description
	if toolDesc != nil && *toolDesc != "" {
		contextParts = append(contextParts, fmt.Sprintf("Tool/Resource Description: %s", *toolDesc))
	} else {
		contextParts = append(contextParts, "Tool/Resource Description: <blank>")
	}

	// MCP payload
	var payloadStr string
	switch v := mcpPayload.(type) {
	case string:
		payloadStr = v
	default:
		if jsonData, err := json.MarshalIndent(v, "", "  "); err == nil {
			payloadStr = string(jsonData)
		} else {
			payloadStr = fmt.Sprintf("%v", v)
		}
	}

	contextParts = append(contextParts, fmt.Sprintf("MCP %s:", bv.getPayloadType()))
	contextParts = append(contextParts, payloadStr)

	return strings.Join(contextParts, "\n")
}

// getPayloadType returns the type of payload being validated
func (bv *BaseLLMValidator) getPayloadType() string {
	return "Request (raw)"
}

// parseResponse parses the LLM response into structured data
func (bv *BaseLLMValidator) parseResponse(rawResponse string) map[string]interface{} {
	// Try to extract JSON from the response
	startIdx := strings.Index(rawResponse, "{")
	endIdx := strings.LastIndex(rawResponse, "}")

	if startIdx != -1 && endIdx > startIdx {
		jsonStr := rawResponse[startIdx : endIdx+1]
		var parsed map[string]interface{}
		if err := json.Unmarshal([]byte(jsonStr), &parsed); err == nil {
			return parsed
		}
	}

	// Fallback: try to parse the entire response
	var parsed map[string]interface{}
	if err := json.Unmarshal([]byte(rawResponse), &parsed); err == nil {
		return parsed
	}

	// Return error response if JSON parsing fails
	return map[string]interface{}{
		"error":        "invalid_json_response",
		"raw_response": rawResponse,
		"parse_error":  "failed to parse JSON response",
	}
}

// createVerdict creates a Verdict object from parsed response
func (bv *BaseLLMValidator) createVerdict(parsedResponse map[string]interface{}) *types.Verdict {
	verdict := types.NewVerdict()

	// Handle different response formats
	var isMalicious bool
	if malicious, ok := parsedResponse["is_malicious_request"].(bool); ok {
		isMalicious = malicious
	} else if unsafe, ok := parsedResponse["is_unsafe_response"].(bool); ok {
		isMalicious = unsafe
	}
	verdict.IsMaliciousRequest = isMalicious

	// Set confidence
	if confidence, ok := parsedResponse["confidence"].(float64); ok {
		verdict.Confidence = confidence
	}

	// Set categories
	if categories, ok := parsedResponse["categories"].([]interface{}); ok {
		for _, cat := range categories {
			if catStr, ok := cat.(string); ok {
				if category := types.ThreatCategory(catStr); category.IsValid() {
					verdict.AddCategory(category)
				}
			}
		}
	}

	// Set evidence
	if evidence, ok := parsedResponse["evidence"].([]interface{}); ok {
		for _, ev := range evidence {
			if evStr, ok := ev.(string); ok {
				verdict.AddEvidence(evStr)
			}
		}
	}

	// Set policy action
	if policyAction, ok := parsedResponse["policy_action"].(string); ok {
		if action := types.PolicyAction(policyAction); action.IsValid() {
			verdict.PolicyAction = action
		}
	}

	// Set reasoning
	if reasoning, ok := parsedResponse["reasoning"].(string); ok {
		verdict.Reasoning = reasoning
	}

	// Set raw response
	if rawJSON, err := json.Marshal(parsedResponse); err == nil {
		verdict.Raw = rawJSON
	}

	return verdict
}

// RequestValidator validates MCP requests
type RequestValidator struct {
	BaseLLMValidator
}

// NewRequestValidator creates a new request validator
func NewRequestValidator(provider providers.LLMProvider) *RequestValidator {
	return &RequestValidator{
		BaseLLMValidator: *NewBaseLLMValidator(provider, constants.RequestValidatorPrompt),
	}
}

// getPayloadType returns the type of payload being validated
func (rv *RequestValidator) getPayloadType() string {
	return "Request (raw)"
}

// Validate validates an MCP request
func (rv *RequestValidator) Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	response := types.NewValidationResponse()
	startTime := time.Now()

	defer func() {
		response.ProcessingTime = float64(time.Since(startTime).Milliseconds())
	}()

	// Build context
	context := rv.buildContext(
		request.MCPPayload,
		request.ToolDescription,
	)

	// Create messages
	messages := []types.ChatMessage{
		{
			Role:    "system",
			Content: rv.prompt,
		},
		{
			Role:    "user",
			Content: context,
		},
	}

	// Generate LLM response
	rawResponse, err := rv.provider.Generate(ctx, messages, nil)
	if err != nil {
		response.SetError(fmt.Sprintf("LLM generation failed: %v", err))
		return response
	}

	// Parse response
	parsedResponse := rv.parseResponse(rawResponse)
	verdict := rv.createVerdict(parsedResponse)

	// Set success response
	response.SetSuccess(verdict, response.ProcessingTime)
	return response
}

// ResponseValidator validates MCP responses
type ResponseValidator struct {
	BaseLLMValidator
}

// NewResponseValidator creates a new response validator
func NewResponseValidator(provider providers.LLMProvider) *ResponseValidator {
	return &ResponseValidator{
		BaseLLMValidator: *NewBaseLLMValidator(provider, constants.ResponseValidatorPrompt),
	}
}

// getPayloadType returns the type of payload being validated
func (rv *ResponseValidator) getPayloadType() string {
	return "Response (raw)"
}

// Validate validates an MCP response
func (rv *ResponseValidator) Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	response := types.NewValidationResponse()
	startTime := time.Now()

	defer func() {
		response.ProcessingTime = float64(time.Since(startTime).Milliseconds())
	}()

	// Build context
	context := rv.buildContext(
		request.MCPPayload,
		request.ToolDescription,
	)

	// Create messages
	messages := []types.ChatMessage{
		{
			Role:    "system",
			Content: rv.prompt,
		},
		{
			Role:    "user",
			Content: context,
		},
	}

	// Generate LLM response
	rawResponse, err := rv.provider.Generate(ctx, messages, nil)
	if err != nil {
		response.SetError(fmt.Sprintf("LLM generation failed: %v", err))
		return response
	}

	// Parse response
	parsedResponse := rv.parseResponse(rawResponse)
	verdict := rv.createVerdict(parsedResponse)

	// Set success response
	response.SetSuccess(verdict, response.ProcessingTime)
	return response
}
