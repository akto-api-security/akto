package guardrails

import (
	"time"
)

// GuardrailEngine is the main engine that coordinates template fetching and modification
type GuardrailEngine struct {
	client   *GuardrailClient
	modifier *Modifier
}

// NewGuardrailEngine creates a new guardrail engine
func NewGuardrailEngine(config ClientConfig) *GuardrailEngine {
	client := NewGuardrailClient(config)
	modifier := NewModifier(client)

	return &GuardrailEngine{
		client:   client,
		modifier: modifier,
	}
}

// Start starts the guardrail engine with periodic template fetching
func (e *GuardrailEngine) Start() {
	// Start periodic template fetching
	e.client.StartPeriodicFetching()
}

// Stop stops the guardrail engine (placeholder for future implementation)
func (e *GuardrailEngine) Stop() {
	// Future implementation for graceful shutdown
}

// TriggerTemplateFetching manually triggers template fetching
func (e *GuardrailEngine) TriggerTemplateFetching() error {
	return e.client.TriggerTemplateFetching()
}

// ModifyRequest modifies a request based on active guardrail templates
func (e *GuardrailEngine) ModifyRequest(requestData string) ModificationResult {
	return e.modifier.ModifyRequest(requestData)
}

// ModifyResponse modifies a response based on active guardrail templates
func (e *GuardrailEngine) ModifyResponse(responseData string) ModificationResult {
	return e.modifier.ModifyResponse(responseData)
}

// ModifyRequestJSON modifies a JSON request
func (e *GuardrailEngine) ModifyRequestJSON(requestData []byte) (ModificationResult, error) {
	return e.modifier.ModifyRequestJSON(requestData)
}

// ModifyResponseJSON modifies a JSON response
func (e *GuardrailEngine) ModifyResponseJSON(responseData []byte) (ModificationResult, error) {
	return e.modifier.ModifyResponseJSON(responseData)
}

// GetTemplates returns all loaded templates
func (e *GuardrailEngine) GetTemplates() map[string]ParsedTemplate {
	return e.client.GetTemplates()
}

// GetTemplate returns a specific template by ID
func (e *GuardrailEngine) GetTemplate(id string) (ParsedTemplate, bool) {
	return e.client.GetTemplate(id)
}

// GetTemplateStats returns statistics about loaded templates
func (e *GuardrailEngine) GetTemplateStats() map[string]interface{} {
	return e.modifier.GetTemplateStats()
}

// SanitizeData sanitizes sensitive data in the payload
func (e *GuardrailEngine) SanitizeData(data string, patterns []string) string {
	return e.modifier.SanitizeData(data, patterns)
}

// CheckForSensitiveData checks if data contains sensitive information
func (e *GuardrailEngine) CheckForSensitiveData(data string, patterns []string) (bool, []string) {
	return e.modifier.CheckForSensitiveData(data, patterns)
}

// GetLastFetchTime returns the time when templates were last fetched
func (e *GuardrailEngine) GetLastFetchTime() time.Time {
	return e.client.LastFetch
}

// GetFetchInterval returns the fetch interval
func (e *GuardrailEngine) GetFetchInterval() time.Duration {
	return e.client.FetchInterval
}

// IsHealthy checks if the guardrail engine is healthy
func (e *GuardrailEngine) IsHealthy() bool {
	// Check if we have templates loaded
	templates := e.GetTemplates()
	if len(templates) == 0 {
		return false
	}

	// Check if last fetch was recent (within 2x the fetch interval)
	lastFetch := e.GetLastFetchTime()
	if lastFetch.IsZero() {
		return false
	}

	interval := e.GetFetchInterval()
	if time.Since(lastFetch) > interval*2 {
		return false
	}

	return true
}

// GetHealthStatus returns detailed health status
func (e *GuardrailEngine) GetHealthStatus() map[string]interface{} {
	templates := e.GetTemplates()
	lastFetch := e.GetLastFetchTime()
	interval := e.GetFetchInterval()

	status := map[string]interface{}{
		"healthy":          e.IsHealthy(),
		"template_count":   len(templates),
		"last_fetch":       lastFetch,
		"fetch_interval":   interval,
		"next_fetch_in":    interval - time.Since(lastFetch),
		"templates_loaded": len(templates) > 0,
	}

	return status
}
