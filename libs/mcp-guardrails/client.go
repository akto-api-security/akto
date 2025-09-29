package guardrails

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"gopkg.in/yaml.v3"
)

// NewGuardrailClient creates a new guardrail client
func NewGuardrailClient(config ClientConfig) *GuardrailClient {
	if config.FetchInterval == 0 {
		config.FetchInterval = 10 * time.Minute
	}

	return &GuardrailClient{
		APIURL:        config.APIURL,
		AuthToken:     config.AuthToken,
		Templates:     make(map[string]ParsedTemplate),
		FetchInterval: config.FetchInterval,
	}
}

// FetchGuardrailTemplates fetches guardrail templates from the API
func (c *GuardrailClient) FetchGuardrailTemplates(activeOnly bool) error {
	url := fmt.Sprintf("%s/api/mcp/fetchGuardrailTemplates", c.APIURL)

	requestBody := map[string]bool{
		"activeOnly": activeOnly,
	}

	jsonData, err := json.Marshal(requestBody)
	if err != nil {
		return fmt.Errorf("failed to marshal request: %w", err)
	}

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.AuthToken))
	req.Header.Set("Content-Type", "application/json")

	client := &http.Client{Timeout: 30 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("failed to make request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("API request failed with status: %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read response: %w", err)
	}

	var apiResponse APIResponse
	if err := json.Unmarshal(body, &apiResponse); err != nil {
		return fmt.Errorf("failed to unmarshal response: %w", err)
	}

	// Parse templates
	newTemplates := make(map[string]ParsedTemplate)
	for _, template := range apiResponse.MCPGuardrailTemplates {
		if template.Inactive {
			continue
		}

		parsed, err := c.parseTemplate(template)
		if err != nil {
			fmt.Printf("Warning: failed to parse template %s: %v\n", template.ID, err)
			continue
		}

		newTemplates[template.ID] = parsed
	}

	// Update templates atomically
	c.Templates = newTemplates
	c.LastFetch = time.Now()

	return nil
}

// parseTemplate parses a YAML template from the content
func (c *GuardrailClient) parseTemplate(template MCPGuardrailTemplate) (ParsedTemplate, error) {
	var parsed ParsedTemplate

	if err := yaml.Unmarshal([]byte(template.Content), &parsed); err != nil {
		return parsed, fmt.Errorf("failed to parse YAML content: %w", err)
	}

	// Set the ID from the template
	parsed.ID = template.ID

	return parsed, nil
}

// StartPeriodicFetching starts a goroutine that fetches templates at regular intervals
func (c *GuardrailClient) StartPeriodicFetching() {
	go func() {
		ticker := time.NewTicker(c.FetchInterval)
		defer ticker.Stop()

		// Initial fetch
		if err := c.FetchGuardrailTemplates(true); err != nil {
			fmt.Printf("Initial template fetch failed: %v\n", err)
		} else {
			fmt.Printf("Initial templates loaded successfully\n")
		}

		// Periodic fetching
		for range ticker.C {
			if err := c.FetchGuardrailTemplates(true); err != nil {
				fmt.Printf("Template refresh failed: %v\n", err)
			} else {
				fmt.Printf("Templates refreshed successfully at %v\n", time.Now())
			}
		}
	}()
}

// TriggerTemplateFetching manually triggers template fetching
func (c *GuardrailClient) TriggerTemplateFetching() error {
	return c.FetchGuardrailTemplates(true)
}

// GetTemplates returns a copy of all loaded templates
func (c *GuardrailClient) GetTemplates() map[string]ParsedTemplate {
	templates := make(map[string]ParsedTemplate)
	for k, v := range c.Templates {
		templates[k] = v
	}
	return templates
}

// GetTemplate returns a specific template by ID
func (c *GuardrailClient) GetTemplate(id string) (ParsedTemplate, bool) {
	template, exists := c.Templates[id]
	return template, exists
}
