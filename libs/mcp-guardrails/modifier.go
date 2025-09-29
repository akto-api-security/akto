package guardrails

import (
	"encoding/json"
	"fmt"
	"regexp"
	"sync"
)

// Modifier handles request and response modifications based on guardrail templates
type Modifier struct {
	client *GuardrailClient
	mu     sync.RWMutex
}

// NewModifier creates a new modifier instance
func NewModifier(client *GuardrailClient) *Modifier {
	return &Modifier{
		client: client,
	}
}

// ModifyRequest modifies a request based on active guardrail templates
func (m *Modifier) ModifyRequest(requestData string) ModificationResult {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := ModificationResult{
		Modified: false,
		Blocked:  false,
		Data:     requestData,
		Warnings: []string{},
	}

	// Get all templates
	templates := m.client.GetTemplates()

	for templateID, template := range templates {
		// Check if template has request_payload filters
		for _, condition := range template.Filter.Or {
			if condition.RequestPayload != nil {
				modified, blocked, reason, warnings := m.applyPayloadFilters(
					requestData,
					condition.RequestPayload.Regex,
					"request",
					templateID,
				)

				if blocked {
					result.Blocked = true
					result.Reason = reason
					result.Warnings = append(result.Warnings, warnings...)
					return result
				}

				if modified {
					result.Modified = true
					result.Data = requestData
					result.Warnings = append(result.Warnings, warnings...)
				}
			}
		}
	}

	return result
}

// ModifyResponse modifies a response based on active guardrail templates
func (m *Modifier) ModifyResponse(responseData string) ModificationResult {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := ModificationResult{
		Modified: false,
		Blocked:  false,
		Data:     responseData,
		Warnings: []string{},
	}

	// Get all templates
	templates := m.client.GetTemplates()

	for templateID, template := range templates {
		// Check if template has response_payload filters
		for _, condition := range template.Filter.Or {
			if condition.ResponsePayload != nil {
				modified, blocked, reason, warnings := m.applyPayloadFilters(
					responseData,
					condition.ResponsePayload.Regex,
					"response",
					templateID,
				)

				if blocked {
					result.Blocked = true
					result.Reason = reason
					result.Warnings = append(result.Warnings, warnings...)
					return result
				}

				if modified {
					result.Modified = true
					result.Data = responseData
					result.Warnings = append(result.Warnings, warnings...)
				}
			}
		}
	}

	return result
}

// applyPayloadFilters applies regex filters to payload data
func (m *Modifier) applyPayloadFilters(data string, patterns []string, payloadType, templateID string) (bool, bool, string, []string) {
	var warnings []string
	modified := false
	blocked := false
	reason := ""

	for _, pattern := range patterns {
		// Compile regex pattern
		re, err := regexp.Compile(pattern)
		if err != nil {
			warnings = append(warnings, fmt.Sprintf("Invalid regex pattern in template %s: %v", templateID, err))
			continue
		}

		// Check if pattern matches
		if re.MatchString(data) {
			// For now, we'll block on any match
			// In a real implementation, you might want to sanitize instead
			blocked = true
			reason = fmt.Sprintf("Blocked by guardrail template %s: pattern matched in %s payload", templateID, payloadType)
			warnings = append(warnings, fmt.Sprintf("Pattern matched in %s: %s", payloadType, pattern))
			break
		}
	}

	return modified, blocked, reason, warnings
}

// ModifyRequestJSON modifies a JSON request based on guardrail templates
func (m *Modifier) ModifyRequestJSON(requestData []byte) (ModificationResult, error) {
	var request interface{}
	if err := json.Unmarshal(requestData, &request); err != nil {
		return ModificationResult{
			Blocked:  true,
			Reason:   "Invalid JSON format",
			Warnings: []string{fmt.Sprintf("Failed to parse JSON: %v", err)},
		}, nil
	}

	// Convert back to string for processing
	requestStr := string(requestData)
	result := m.ModifyRequest(requestStr)

	// If modified, convert back to JSON
	if result.Modified {
		// For now, we'll just return the original data
		// In a real implementation, you'd apply the modifications
		result.Data = requestStr
	}

	return result, nil
}

// ModifyResponseJSON modifies a JSON response based on guardrail templates
func (m *Modifier) ModifyResponseJSON(responseData []byte) (ModificationResult, error) {
	var response interface{}
	if err := json.Unmarshal(responseData, &response); err != nil {
		return ModificationResult{
			Blocked:  true,
			Reason:   "Invalid JSON format",
			Warnings: []string{fmt.Sprintf("Failed to parse JSON: %v", err)},
		}, nil
	}

	// Convert back to string for processing
	responseStr := string(responseData)
	result := m.ModifyResponse(responseStr)

	// If modified, convert back to JSON
	if result.Modified {
		// For now, we'll just return the original data
		// In a real implementation, you'd apply the modifications
		result.Data = responseStr
	}

	return result, nil
}

// SanitizeData sanitizes sensitive data in the payload
func (m *Modifier) SanitizeData(data string, patterns []string) string {
	sanitized := data

	for _, pattern := range patterns {
		re, err := regexp.Compile(pattern)
		if err != nil {
			continue
		}

		// Replace matches with sanitized text
		sanitized = re.ReplaceAllString(sanitized, "***REDACTED***")
	}

	return sanitized
}

// CheckForSensitiveData checks if data contains sensitive information
func (m *Modifier) CheckForSensitiveData(data string, patterns []string) (bool, []string) {
	var matchedPatterns []string

	for _, pattern := range patterns {
		re, err := regexp.Compile(pattern)
		if err != nil {
			continue
		}

		if re.MatchString(data) {
			matchedPatterns = append(matchedPatterns, pattern)
		}
	}

	return len(matchedPatterns) > 0, matchedPatterns
}

// GetTemplateStats returns statistics about loaded templates
func (m *Modifier) GetTemplateStats() map[string]interface{} {
	templates := m.client.GetTemplates()

	stats := map[string]interface{}{
		"total_templates": len(templates),
		"templates":       make([]map[string]interface{}, 0),
	}

	templateList := make([]map[string]interface{}, 0)
	for id, template := range templates {
		templateInfo := map[string]interface{}{
			"id":       id,
			"name":     template.Info.Name,
			"severity": template.Info.Severity,
			"category": template.Info.Category.Name,
		}
		templateList = append(templateList, templateInfo)
	}

	stats["templates"] = templateList
	return stats
}
