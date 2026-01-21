package main

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/akto/mcp-guardrails"
)

func main() {
	fmt.Println("MCP Guardrails Library Demo")
	fmt.Println("===========================")

	// Create a comprehensive guardrail configuration
	config := &guardrails.GuardrailConfig{
		EnableDataSanitization: true,
		SensitiveFields:        []string{"password", "api_key", "secret", "token"},
		RedactionPatterns:      []string{},
		
		EnableContentFiltering: true,
		BlockedKeywords:        []string{"malicious", "dangerous", "exploit"},
		AllowedDomains:         []string{"example.com", "api.example.com"},
		
		EnableRateLimiting: true,
		RateLimitConfig: guardrails.RateLimitConfig{
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
	engine := guardrails.NewGuardrailEngine(config)

	// Demo 1: Data Sanitization
	fmt.Println("\n1. Data Sanitization Demo")
	fmt.Println("-------------------------")
	
	responseWithSensitiveData := &guardrails.MCPResponse{
		ID: "demo_1",
		Result: json.RawMessage(`{
			"user": {
				"name": "John Doe",
				"email": "john.doe@example.com",
				"password": "super_secret_password_123",
				"credit_card": "1234-5678-9012-3456",
				"ssn": "123-45-6789",
				"api_key": "sk-1234567890abcdef1234567890abcdef"
			},
			"account": {
				"id": "ACC123456",
				"balance": 1000.50,
				"secret_token": "internal_secret_123"
			}
		}`),
	}

	result := engine.ProcessResponse(responseWithSensitiveData)
	
	if result.Blocked {
		fmt.Printf("❌ Response blocked: %s\n", result.BlockReason)
	} else {
		fmt.Println("✅ Response processed successfully")
		
		if len(result.Warnings) > 0 {
			fmt.Printf("⚠️  Warnings: %v\n", result.Warnings)
		}
		
		if result.SanitizedResponse != nil {
			fmt.Println("🔒 Data sanitization applied")
			// Show sanitized result
			sanitizedBytes, _ := json.MarshalIndent(result.SanitizedResponse.Result, "", "  ")
			fmt.Printf("Sanitized result:\n%s\n", string(sanitizedBytes))
		}
	}

	// Demo 2: Content Filtering
	fmt.Println("\n2. Content Filtering Demo")
	fmt.Println("-------------------------")
	
	// Add custom content filter
	customFilter := guardrails.ContentFilter{
		Type:        "keyword",
		Pattern:     "internal",
		Action:      "warn",
		Description: "Internal information detected",
	}
	engine.AddContentFilter(customFilter)

	responseWithInternalInfo := &guardrails.MCPResponse{
		ID: "demo_2",
		Result: json.RawMessage(`{
			"message": "This contains internal company information",
			"status": "success"
		}`),
	}

	result = engine.ProcessResponse(responseWithInternalInfo)
	
	if result.Blocked {
		fmt.Printf("❌ Response blocked: %s\n", result.BlockReason)
	} else {
		fmt.Println("✅ Response processed successfully")
		if len(result.Warnings) > 0 {
			fmt.Printf("⚠️  Warnings: %v\n", result.Warnings)
		}
	}

	// Demo 3: Rate Limiting
	fmt.Println("\n3. Rate Limiting Demo")
	fmt.Println("---------------------")
	
	request := &guardrails.MCPRequest{
		ID:     "demo_3",
		Method: "tools/list",
		Params: json.RawMessage(`{"include_hidden": true}`),
	}

	// Simulate multiple requests
	for i := 1; i <= 5; i++ {
		result := engine.ProcessRequest(request)
		if result.Blocked {
			fmt.Printf("❌ Request %d blocked: %s\n", i, result.BlockReason)
		} else {
			fmt.Printf("✅ Request %d allowed\n", i)
		}
	}

	// Demo 4: Input Validation
	fmt.Println("\n4. Input Validation Demo")
	fmt.Println("-------------------------")
	
	// Valid request
	validRequest := &guardrails.MCPRequest{
		ID:     "demo_4_valid",
		Method: "tools/call",
		Params: json.RawMessage(`{"tool": "file_read"}`),
	}

	result = engine.ProcessRequest(validRequest)
	if result.Blocked {
		fmt.Printf("❌ Valid request blocked: %s\n", result.BlockReason)
	} else {
		fmt.Println("✅ Valid request accepted")
	}

	// Invalid request (empty method)
	invalidRequest := &guardrails.MCPRequest{
		ID:     "demo_4_invalid",
		Method: "",
		Params: json.RawMessage(`{"tool": "file_read"}`),
	}

	result = engine.ProcessRequest(invalidRequest)
	if result.Blocked {
		fmt.Printf("❌ Invalid request correctly blocked: %s\n", result.BlockReason)
	} else {
		fmt.Println("⚠️  Invalid request should have been blocked")
	}

	// Demo 5: Custom Patterns
	fmt.Println("\n5. Custom Patterns Demo")
	fmt.Println("-----------------------")
	
	// Add custom sensitive pattern
	customPattern := guardrails.SensitiveDataPattern{
		Name:        "employee_id",
		Pattern:     `\bEMP\d{6}\b`,
		Replacement: "***REDACTED_EMPLOYEE_ID***",
		Description: "Employee ID format",
	}
	engine.AddSensitivePattern(customPattern)

	responseWithEmployeeData := &guardrails.MCPResponse{
		ID: "demo_5",
		Result: json.RawMessage(`{
			"employee": {
				"name": "Jane Smith",
				"id": "EMP123456",
				"department": "Engineering"
			}
		}`),
	}

	result = engine.ProcessResponse(responseWithEmployeeData)
	
	if result.Blocked {
		fmt.Printf("❌ Response blocked: %s\n", result.BlockReason)
	} else {
		fmt.Println("✅ Response processed successfully")
		
		if result.SanitizedResponse != nil {
			fmt.Println("🔒 Custom pattern applied")
			sanitizedBytes, _ := json.MarshalIndent(result.SanitizedResponse.Result, "", "  ")
			fmt.Printf("Sanitized result:\n%s\n", string(sanitizedBytes))
		}
	}

	// Demo 6: Logging
	fmt.Println("\n6. Logging Demo")
	fmt.Println("---------------")
	
	if len(result.Logs) > 0 {
		fmt.Println("📝 Log entries generated:")
		for i, logEntry := range result.Logs {
			fmt.Printf("  %d. [%s] %s - %s\n", 
				i+1, 
				logEntry.Level, 
				logEntry.Timestamp.Format("15:04:05"),
				logEntry.Message)
		}
	}

	fmt.Println("\n🎉 MCP Guardrails Demo Complete!")
	fmt.Println("The library is ready to be integrated into your MCP proxy server.")
} 