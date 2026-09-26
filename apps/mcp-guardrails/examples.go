// Package guardrails provides MCP guardrail functionality
package guardrails

import (
	"encoding/json"
	"fmt"
	"log"
	"time"
)

// Example usage of the MCP Guardrails library
func ExampleUsage() {
	// Create a guardrail configuration
	config := &GuardrailConfig{
		EnableDataSanitization: true,
		SensitiveFields:        []string{"password", "api_key", "secret"},
		RedactionPatterns:      []string{},
		
		EnableContentFiltering: true,
		BlockedKeywords:        []string{"malicious", "dangerous"},
		AllowedDomains:         []string{"example.com", "api.example.com"},
		
		EnableRateLimiting: true,
		RateLimitConfig: RateLimitConfig{
			RequestsPerMinute: 100,
			BurstSize:         10,
			WindowSize:        time.Minute,
		},
		
		EnableInputValidation: true,
		ValidationRules: map[string]string{
			"method": "required",
		},
		
		EnableOutputFiltering: true,
		OutputFilters:         []string{"block_sensitive"},
		
		EnableLogging: true,
		LogLevel:      "INFO",
	}

	// Create the guardrail engine
	engine := NewGuardrailEngine(config)

	// Example 1: Process a response with sensitive data
	exampleResponse := &MCPResponse{
		ID: "1",
		Result: json.RawMessage(`{
			"user": {
				"name": "John Doe",
				"email": "john.doe@example.com",
				"password": "secret123",
				"credit_card": "1234-5678-9012-3456"
			}
		}`),
	}

	result := engine.ProcessResponse(exampleResponse)
	fmt.Printf("Response processed: Blocked=%v, Warnings=%v\n", 
		result.Blocked, result.Warnings)

	// Example 2: Process a request with validation
	exampleRequest := &MCPRequest{
		ID:     "2",
		Method: "tools/list",
		Params: json.RawMessage(`{"include_hidden": true}`),
	}

	requestResult := engine.ProcessRequest(exampleRequest)
	fmt.Printf("Request processed: Blocked=%v, Warnings=%v\n", 
		requestResult.Blocked, requestResult.Warnings)

	// Example 3: Add custom sensitive pattern
	customPattern := SensitiveDataPattern{
		Name:        "custom_id",
		Pattern:     `\b[A-Z]{2}\d{6}\b`,
		Replacement: "***REDACTED_ID***",
		Description: "Custom ID format",
	}
	engine.AddSensitivePattern(customPattern)

	// Example 4: Add custom content filter
	customFilter := ContentFilter{
		Type:        "keyword",
		Pattern:     "internal",
		Action:      "warn",
		Description: "Internal information detected",
	}
	engine.AddContentFilter(customFilter)
}

// ExampleWithRealData demonstrates processing real MCP data
func ExampleWithRealData() {
	config := &GuardrailConfig{
		EnableDataSanitization: true,
		EnableContentFiltering: true,
		EnableRateLimiting:     true,
		EnableLogging:          true,
		LogLevel:               "DEBUG",
		RateLimitConfig: RateLimitConfig{
			RequestsPerMinute: 60,
			BurstSize:         5,
			WindowSize:        time.Minute,
		},
	}

	engine := NewGuardrailEngine(config)

	// Simulate MCP response with sensitive data
	responseData := map[string]interface{}{
		"tools": []map[string]interface{}{
			{
				"name":        "file_read",
				"description": "Read file contents",
				"parameters": map[string]interface{}{
					"path": "/etc/passwd",
				},
			},
		},
		"user_info": map[string]interface{}{
			"id":        "US123456",
			"email":     "admin@company.com",
			"password":  "super_secret_password",
			"api_key":   "sk-1234567890abcdef",
			"ssn":       "123-45-6789",
		},
	}

	responseBytes, _ := json.Marshal(responseData)
	response := &MCPResponse{
		ID:     "req_123",
		Result: responseBytes,
	}

	// Process the response
	result := engine.ProcessResponse(response)
	
	log.Printf("Processing complete:")
	log.Printf("  Blocked: %v", result.Blocked)
	log.Printf("  Block reason: %s", result.BlockReason)
	log.Printf("  Warnings: %v", result.Warnings)
	
	if result.SanitizedResponse != nil {
		log.Printf("  Response sanitized successfully")
	}
}

// ExampleRateLimiting demonstrates rate limiting functionality
func ExampleRateLimiting() {
	config := &GuardrailConfig{
		EnableRateLimiting: true,
		RateLimitConfig: RateLimitConfig{
			RequestsPerMinute: 10, // Very low for testing
			BurstSize:         2,
			WindowSize:        time.Minute,
		},
	}

	engine := NewGuardrailEngine(config)
	request := &MCPRequest{
		ID:     "test",
		Method: "test/method",
	}

	// Simulate multiple requests
	for i := 0; i < 5; i++ {
		result := engine.ProcessRequest(request)
		fmt.Printf("Request %d: Allowed=%v, Blocked=%v\n", 
			i+1, !result.Blocked, result.Blocked)
		
		if result.Blocked {
			fmt.Printf("  Reason: %s\n", result.BlockReason)
		}
	}
} 