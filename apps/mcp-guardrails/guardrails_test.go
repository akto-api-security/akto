package guardrails

import (
	"encoding/json"
	"testing"
	"time"
)

func TestNewGuardrailEngine(t *testing.T) {
	config := &GuardrailConfig{
		EnableDataSanitization: true,
		EnableContentFiltering: true,
		EnableRateLimiting:     true,
		EnableLogging:          true,
	}

	engine := NewGuardrailEngine(config)
	if engine == nil {
		t.Fatal("Expected engine to be created")
	}

	if engine.config != config {
		t.Error("Expected config to be set")
	}

	if len(engine.patterns) == 0 {
		t.Error("Expected default patterns to be loaded")
	}

	if len(engine.filters) == 0 {
		t.Error("Expected default filters to be loaded")
	}

	if engine.rateLimiter == nil {
		t.Error("Expected rate limiter to be created when enabled")
	}
}

func TestDataSanitization(t *testing.T) {
	config := &GuardrailConfig{
		EnableDataSanitization: true,
		SensitiveFields:        []string{"password", "secret"},
	}

	engine := NewGuardrailEngine(config)

	// Test response with sensitive data
	responseData := map[string]interface{}{
		"user": map[string]interface{}{
			"name":     "John Doe",
			"email":    "john@example.com",
			"password": "secret123",
			"ssn":      "123-45-6789",
		},
		"credentials": map[string]interface{}{
			"password": "secret123",
		},
	}

	responseBytes, _ := json.Marshal(responseData)
	response := &MCPResponse{
		ID:     "test",
		Result: responseBytes,
	}

	result := engine.ProcessResponse(response)

	if result.Blocked {
		t.Error("Expected response to not be blocked")
	}

	if result.SanitizedResponse == nil {
		t.Fatal("Expected sanitized response")
	}

	// Check if sensitive data was redacted
	sanitizedBytes, _ := json.Marshal(result.SanitizedResponse)
	sanitizedStr := string(sanitizedBytes)

	if !contains(sanitizedStr, "***REDACTED***") {
		t.Error("Expected password to be redacted")
	}

	if !contains(sanitizedStr, "***REDACTED_SSN***") {
		t.Error("Expected SSN to be redacted")
	}

	if contains(sanitizedStr, "secret123") {
		t.Error("Expected original password to be removed")
	}
}

func TestContentFiltering(t *testing.T) {
	config := &GuardrailConfig{
		EnableContentFiltering: true,
	}

	engine := NewGuardrailEngine(config)

	// Add a blocking filter
	blockingFilter := ContentFilter{
		Type:        "keyword",
		Pattern:     "malicious",
		Action:      "block",
		Description: "Malicious content detected",
	}
	engine.AddContentFilter(blockingFilter)

	// Test response with blocked content
	responseData := map[string]interface{}{
		"message": "This is malicious content",
	}

	responseBytes, _ := json.Marshal(responseData)
	response := &MCPResponse{
		ID:     "test",
		Result: responseBytes,
	}

	result := engine.ProcessResponse(response)

	if !result.Blocked {
		t.Error("Expected response to be blocked")
	}

	if result.BlockReason == "" {
		t.Error("Expected block reason to be set")
	}

	// Test response with warning content
	warningFilter := ContentFilter{
		Type:        "keyword",
		Pattern:     "warning",
		Action:      "warn",
		Description: "Warning content detected",
	}
	engine.AddContentFilter(warningFilter)

	responseData = map[string]interface{}{
		"message": "This is a warning message",
	}

	responseBytes, _ = json.Marshal(responseData)
	response = &MCPResponse{
		ID:     "test",
		Result: responseBytes,
	}

	result = engine.ProcessResponse(response)

	if result.Blocked {
		t.Error("Expected response to not be blocked")
	}

	if len(result.Warnings) == 0 {
		t.Error("Expected warnings to be generated")
	}
}

func TestRateLimiting(t *testing.T) {
	config := &GuardrailConfig{
		EnableRateLimiting: true,
		RateLimitConfig: RateLimitConfig{
			RequestsPerMinute: 2,
			BurstSize:         1,
			WindowSize:        time.Minute,
		},
	}

	engine := NewGuardrailEngine(config)

	request := &MCPRequest{
		ID:     "test",
		Method: "test/method",
	}

	// First request should be allowed
	result1 := engine.ProcessRequest(request)
	if result1.Blocked {
		t.Error("Expected first request to be allowed")
	}

	// Second request should be blocked (burst limit)
	result2 := engine.ProcessRequest(request)
	if !result2.Blocked {
		t.Error("Expected second request to be blocked")
	}

	if result2.BlockReason != "Rate limit exceeded" {
		t.Errorf("Expected rate limit reason, got: %s", result2.BlockReason)
	}
}

func TestInputValidation(t *testing.T) {
	config := &GuardrailConfig{
		EnableInputValidation: true,
		ValidationRules: map[string]string{
			"method": "required",
		},
	}

	engine := NewGuardrailEngine(config)

	// Test valid request
	validRequest := &MCPRequest{
		ID:     "test",
		Method: "tools/list",
	}

	result := engine.ProcessRequest(validRequest)
	if result.Blocked {
		t.Error("Expected valid request to be allowed")
	}

	// Test invalid request (empty method)
	invalidRequest := &MCPRequest{
		ID:     "test",
		Method: "",
	}

	result = engine.ProcessRequest(invalidRequest)
	if !result.Blocked {
		t.Error("Expected invalid request to be blocked")
	}

	if result.BlockReason == "" {
		t.Error("Expected block reason to be set")
	}
}

func TestCustomPatterns(t *testing.T) {
	config := &GuardrailConfig{
		EnableDataSanitization: true,
	}

	engine := NewGuardrailEngine(config)

	// Add custom pattern
	customPattern := SensitiveDataPattern{
		Name:        "custom_id",
		Pattern:     `\b[A-Z]{2}\d{6}\b`,
		Replacement: "***REDACTED_ID***",
		Description: "Custom ID format",
	}
	engine.AddSensitivePattern(customPattern)

	// Test response with custom pattern
	responseData := map[string]interface{}{
		"user_id": "AB123456",
		"name":    "John Doe",
	}

	responseBytes, _ := json.Marshal(responseData)
	response := &MCPResponse{
		ID:     "test",
		Result: responseBytes,
	}

	result := engine.ProcessResponse(response)

	if result.SanitizedResponse == nil {
		t.Fatal("Expected sanitized response")
	}

	sanitizedBytes, _ := json.Marshal(result.SanitizedResponse.Result)
	sanitizedStr := string(sanitizedBytes)

	if !contains(sanitizedStr, "***REDACTED_ID***") {
		t.Error("Expected custom ID to be redacted")
	}

	if contains(sanitizedStr, "AB123456") {
		t.Error("Expected original ID to be removed")
	}
}

func TestLogging(t *testing.T) {
	config := &GuardrailConfig{
		EnableLogging: true,
		LogLevel:      "INFO",
	}

	engine := NewGuardrailEngine(config)

	response := &MCPResponse{
		ID:     "test",
		Result: json.RawMessage(`{"message": "test"}`),
	}

	result := engine.ProcessResponse(response)

	if len(result.Logs) == 0 {
		t.Error("Expected logs to be generated")
	}

	// Check if log entry has required fields
	logEntry := result.Logs[0]
	if logEntry.Timestamp.IsZero() {
		t.Error("Expected timestamp to be set")
	}

	if logEntry.Level == "" {
		t.Error("Expected log level to be set")
	}

	if logEntry.Message == "" {
		t.Error("Expected log message to be set")
	}
}

func TestUpdateConfig(t *testing.T) {
	config1 := &GuardrailConfig{
		EnableDataSanitization: true,
		EnableLogging:          true,
	}

	engine := NewGuardrailEngine(config1)

	// Update configuration
	config2 := &GuardrailConfig{
		EnableDataSanitization: false,
		EnableLogging:          false,
	}

	engine.UpdateConfig(config2)

	if engine.config != config2 {
		t.Error("Expected config to be updated")
	}
}

// Helper function to check if string contains substring
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 || 
		(len(s) > len(substr) && (s[:len(substr)] == substr || 
		s[len(s)-len(substr):] == substr || 
		func() bool {
			for i := 1; i <= len(s)-len(substr); i++ {
				if s[i:i+len(substr)] == substr {
					return true
				}
			}
			return false
		}())))
} 