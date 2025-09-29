package guardrails

import (
	"io"
	"net/http"
	"testing"
	"time"
)

// Mock HTTP client for testing
type mockHTTPClient struct {
	response []byte
	err      error
}

func (m *mockHTTPClient) Do(req *http.Request) (*http.Response, error) {
	if m.err != nil {
		return nil, m.err
	}

	// Create a mock response
	resp := &http.Response{
		StatusCode: 200,
		Body:       &mockResponseBody{data: m.response},
	}
	return resp, nil
}

type mockResponseBody struct {
	data []byte
	pos  int
}

func (m *mockResponseBody) Read(p []byte) (n int, err error) {
	if m.pos >= len(m.data) {
		return 0, io.EOF
	}
	n = copy(p, m.data[m.pos:])
	m.pos += n
	return n, nil
}

func (m *mockResponseBody) Close() error {
	return nil
}

// Test data
var testTemplateContent = `id: PIIDataLeak 
filter:
  or:
    - response_payload:
        regex:
          - (?:\\b(?:4\\d{3}|5[1-5]\\d{2}|2\\d{3}|3[47]\\d{1,2})[\\s\\-]?\\d{4,6}[\\s\\-]?\\d{4,6}?(?:[\\s\\-]\\d{3,4})?(?:\\d{3})?|\\b(?!000|666|9\\d{2})([0-8]\\d{2}|7([0-6]\\d))([-]?|\\s{1})(?!00)\\d\\d\\3(?!0000)\\d{4})\\b
    - response_payload:
        regex:
          - \\b(\\d{4}[- ]?\\d{4}[- ]?\\d{4}|[A-Z]{5}[0-9]{4}[A-Z])\\b

info:
  name: "PIIDataLeak"
  description: "PII Data Leak refers to the accidental or unauthorized exposure of Personally Identifiable Information"
  details: "PII leaks commonly stem from insecure logging, improperly secured APIs"
  impact: "Exposed PII can lead to identity theft, financial fraud, regulatory violations"
  category:
    name: "PIIDataLeak"
    displayName: "PIIDataLeak"
  subCategory: "PIIDataLeak"
  severity: MEDIUM`

var testAPIResponse = APIResponse{
	MCPGuardrailTemplates: []MCPGuardrailTemplate{
		{
			ID:        "PIIDataLeak",
			Author:    "system",
			Content:   testTemplateContent,
			CreatedAt: 1759113676,
			UpdatedAt: 1759113676,
			Hash:      481648519,
			Inactive:  false,
			Source:    "CUSTOM",
		},
	},
}

func TestNewGuardrailEngine(t *testing.T) {
	config := ClientConfig{
		APIURL:        "http://localhost:8082",
		AuthToken:     "testing",
		FetchInterval: 10 * time.Minute,
	}

	engine := NewGuardrailEngine(config)

	if engine == nil {
		t.Fatal("Expected engine to be created")
	}

	if engine.client == nil {
		t.Fatal("Expected client to be initialized")
	}

	if engine.modifier == nil {
		t.Fatal("Expected modifier to be initialized")
	}
}

func TestGuardrailClient_FetchGuardrailTemplates(t *testing.T) {
	// This test would require mocking the HTTP client
	// For now, we'll test the parsing logic
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	// Test parsing a template
	template := MCPGuardrailTemplate{
		ID:       "test",
		Content:  testTemplateContent,
		Inactive: false,
	}

	parsed, err := client.parseTemplate(template)
	if err != nil {
		t.Fatalf("Failed to parse template: %v", err)
	}

	if parsed.ID != "test" {
		t.Errorf("Expected ID 'test', got '%s'", parsed.ID)
	}

	if len(parsed.Filter.Or) == 0 {
		t.Fatal("Expected filter conditions to be parsed")
	}

	// Check if response_payload filters are parsed
	foundResponsePayload := false
	for _, condition := range parsed.Filter.Or {
		if condition.ResponsePayload != nil && len(condition.ResponsePayload.Regex) > 0 {
			foundResponsePayload = true
			break
		}
	}

	if !foundResponsePayload {
		t.Fatal("Expected response_payload filters to be found")
	}
}

func TestModifier_ModifyRequest(t *testing.T) {
	// Create a mock client with test templates
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	// Add a test template
	client.Templates["test"] = ParsedTemplate{
		ID: "test",
		Filter: Filter{
			Or: []FilterCondition{
				{
					RequestPayload: &PayloadFilter{
						Regex: []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`}, // Credit card pattern
					},
				},
			},
		},
	}

	modifier := NewModifier(client)

	// Test with sensitive data
	requestData := `{"method": "test", "data": "1234-5678-9012-3456"`
	result := modifier.ModifyRequest(requestData)

	if !result.Blocked {
		t.Error("Expected request to be blocked due to credit card pattern")
	}

	if result.Reason == "" {
		t.Error("Expected block reason to be provided")
	}

	// Test with safe data
	safeRequestData := `{"method": "test", "data": "safe data"}`
	safeResult := modifier.ModifyRequest(safeRequestData)

	if safeResult.Blocked {
		t.Error("Expected safe request to not be blocked")
	}
}

func TestModifier_ModifyResponse(t *testing.T) {
	// Create a mock client with test templates
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	// Add a test template
	client.Templates["test"] = ParsedTemplate{
		ID: "test",
		Filter: Filter{
			Or: []FilterCondition{
				{
					ResponsePayload: &PayloadFilter{
						Regex: []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`}, // Credit card pattern
					},
				},
			},
		},
	}

	modifier := NewModifier(client)

	// Test with sensitive data
	responseData := `{"status": "success", "data": "1234-5678-9012-3456"}`
	result := modifier.ModifyResponse(responseData)

	if !result.Blocked {
		t.Error("Expected response to be blocked due to credit card pattern")
	}

	if result.Reason == "" {
		t.Error("Expected block reason to be provided")
	}

	// Test with safe data
	safeResponseData := `{"status": "success", "data": "safe data"}`
	safeResult := modifier.ModifyResponse(safeResponseData)

	if safeResult.Blocked {
		t.Error("Expected safe response to not be blocked")
	}
}

func TestModifier_ModifyRequestJSON(t *testing.T) {
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	client.Templates["test"] = ParsedTemplate{
		ID: "test",
		Filter: Filter{
			Or: []FilterCondition{
				{
					RequestPayload: &PayloadFilter{
						Regex: []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`},
					},
				},
			},
		},
	}

	modifier := NewModifier(client)

	// Test with valid JSON containing sensitive data
	requestData := []byte(`{"method": "test", "data": "1234-5678-9012-3456"`)
	result, err := modifier.ModifyRequestJSON(requestData)

	if err != nil {
		t.Fatalf("Unexpected error: %v", err)
	}

	if !result.Blocked {
		t.Error("Expected request to be blocked")
	}
}

func TestModifier_ModifyResponseJSON(t *testing.T) {
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	client.Templates["test"] = ParsedTemplate{
		ID: "test",
		Filter: Filter{
			Or: []FilterCondition{
				{
					ResponsePayload: &PayloadFilter{
						Regex: []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`},
					},
				},
			},
		},
	}

	modifier := NewModifier(client)

	// Test with valid JSON containing sensitive data
	responseData := []byte(`{"status": "success", "data": "1234-5678-9012-3456"}`)
	result, err := modifier.ModifyResponseJSON(responseData)

	if err != nil {
		t.Fatalf("Unexpected error: %v", err)
	}

	if !result.Blocked {
		t.Error("Expected response to be blocked")
	}
}

func TestModifier_SanitizeData(t *testing.T) {
	modifier := &Modifier{}

	data := "My credit card is 1234-5678-9012-3456"
	patterns := []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`}

	sanitized := modifier.SanitizeData(data, patterns)

	if sanitized == data {
		t.Error("Expected data to be sanitized")
	}

	if !contains(sanitized, "***REDACTED***") {
		t.Error("Expected sanitized data to contain redaction marker")
	}
}

func TestModifier_CheckForSensitiveData(t *testing.T) {
	modifier := &Modifier{}

	data := "My credit card is 1234-5678-9012-3456"
	patterns := []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`}

	hasSensitive, matchedPatterns := modifier.CheckForSensitiveData(data, patterns)

	if !hasSensitive {
		t.Error("Expected sensitive data to be detected")
	}

	if len(matchedPatterns) == 0 {
		t.Error("Expected matched patterns to be returned")
	}

	// Test with safe data
	safeData := "This is safe data"
	hasSensitive, _ = modifier.CheckForSensitiveData(safeData, patterns)

	if hasSensitive {
		t.Error("Expected safe data to not be flagged as sensitive")
	}
}

func TestGuardrailEngine_GetTemplateStats(t *testing.T) {
	config := ClientConfig{
		APIURL:        "http://localhost:8082",
		AuthToken:     "testing",
		FetchInterval: 10 * time.Minute,
	}

	engine := NewGuardrailEngine(config)

	// Add some test templates
	engine.client.Templates["test1"] = ParsedTemplate{
		ID: "test1",
		Info: TemplateInfo{
			Name:     "Test Template 1",
			Severity: "HIGH",
			Category: struct {
				Name        string `yaml:"name"`
				DisplayName string `yaml:"displayName"`
			}{
				Name:        "TestCategory",
				DisplayName: "Test Category",
			},
		},
	}

	stats := engine.GetTemplateStats()

	if stats["total_templates"] != 1 {
		t.Errorf("Expected 1 template, got %v", stats["total_templates"])
	}

	templates, ok := stats["templates"].([]map[string]interface{})
	if !ok {
		t.Fatal("Expected templates to be a slice")
	}

	if len(templates) != 1 {
		t.Errorf("Expected 1 template in stats, got %d", len(templates))
	}
}

func TestGuardrailEngine_IsHealthy(t *testing.T) {
	config := ClientConfig{
		APIURL:        "http://localhost:8082",
		AuthToken:     "testing",
		FetchInterval: 10 * time.Minute,
	}

	engine := NewGuardrailEngine(config)

	// Initially should not be healthy (no templates)
	if engine.IsHealthy() {
		t.Error("Expected engine to not be healthy initially")
	}

	// Add templates and set last fetch time
	engine.client.Templates["test"] = ParsedTemplate{ID: "test"}
	engine.client.LastFetch = time.Now()

	if !engine.IsHealthy() {
		t.Error("Expected engine to be healthy with templates and recent fetch")
	}
}

func TestGuardrailEngine_GetHealthStatus(t *testing.T) {
	config := ClientConfig{
		APIURL:        "http://localhost:8082",
		AuthToken:     "testing",
		FetchInterval: 10 * time.Minute,
	}

	engine := NewGuardrailEngine(config)

	status := engine.GetHealthStatus()

	if status["healthy"] == nil {
		t.Error("Expected health status to include 'healthy' field")
	}

	if status["template_count"] == nil {
		t.Error("Expected health status to include 'template_count' field")
	}

	if status["last_fetch"] == nil {
		t.Error("Expected health status to include 'last_fetch' field")
	}
}

// Helper function to check if string contains substring
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 ||
		(len(s) > len(substr) && (s[:len(substr)] == substr ||
			s[len(s)-len(substr):] == substr ||
			contains(s[1:], substr))))
}

// Benchmark tests
func BenchmarkModifier_ModifyRequest(b *testing.B) {
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	client.Templates["test"] = ParsedTemplate{
		ID: "test",
		Filter: Filter{
			Or: []FilterCondition{
				{
					RequestPayload: &PayloadFilter{
						Regex: []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`},
					},
				},
			},
		},
	}

	modifier := NewModifier(client)
	requestData := `{"method": "test", "data": "1234-5678-9012-3456"`

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		modifier.ModifyRequest(requestData)
	}
}

func BenchmarkModifier_ModifyResponse(b *testing.B) {
	client := &GuardrailClient{
		Templates: make(map[string]ParsedTemplate),
	}

	client.Templates["test"] = ParsedTemplate{
		ID: "test",
		Filter: Filter{
			Or: []FilterCondition{
				{
					ResponsePayload: &PayloadFilter{
						Regex: []string{`\b\d{4}[- ]?\d{4}[- ]?\d{4}\b`},
					},
				},
			},
		},
	}

	modifier := NewModifier(client)
	responseData := `{"status": "success", "data": "1234-5678-9012-3456"}`

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		modifier.ModifyResponse(responseData)
	}
}
