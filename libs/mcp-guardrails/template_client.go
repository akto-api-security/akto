package guardrails

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// NewTemplateClient creates a new template client
func NewTemplateClient(baseURL string) *TemplateClient {
	return &TemplateClient{
		BaseURL: baseURL,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

// FetchGuardrailTemplates fetches all active guardrail templates from the API
func (c *TemplateClient) FetchGuardrailTemplates(activeOnly bool) ([]YamlTemplate, error) {
	endpoint := "/api/mcp/fetchGuardrailTemplates"
	params := url.Values{}
	params.Add("activeOnly", fmt.Sprintf("%t", activeOnly))
	params.Add("includeYamlContent", "true")

	resp, err := c.makeRequest("POST", endpoint, params)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch guardrail templates: %w", err)
	}

	if len(resp.ActionErrors) > 0 {
		return nil, fmt.Errorf("API returned errors: %v", resp.ActionErrors)
	}

	return resp.McpGuardrailTemplates, nil
}

// FetchGuardrailTemplatesByType fetches guardrail templates by type from the API
func (c *TemplateClient) FetchGuardrailTemplatesByType(guardrailType string) ([]YamlTemplate, error) {
	endpoint := "/api/mcp/fetchGuardrailTemplatesByType"
	params := url.Values{}
	params.Add("guardrailType", guardrailType)
	params.Add("includeYamlContent", "true")

	resp, err := c.makeRequest("POST", endpoint, params)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch guardrail templates by type: %w", err)
	}

	if len(resp.ActionErrors) > 0 {
		return nil, fmt.Errorf("API returned errors: %v", resp.ActionErrors)
	}

	return resp.McpGuardrailTemplates, nil
}

// FetchGuardrailConfigs fetches parsed guardrail configurations from the API
func (c *TemplateClient) FetchGuardrailConfigs(includeYamlContent bool) (map[string]MCPGuardrailConfig, error) {
	endpoint := "/api/mcp/fetchGuardrailConfigs"
	params := url.Values{}
	params.Add("includeYamlContent", fmt.Sprintf("%t", includeYamlContent))

	resp, err := c.makeRequest("POST", endpoint, params)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch guardrail configs: %w", err)
	}

	if len(resp.ActionErrors) > 0 {
		return nil, fmt.Errorf("API returned errors: %v", resp.ActionErrors)
	}

	return resp.McpGuardrailConfigs, nil
}

// FetchGuardrailTemplate fetches a specific guardrail template by ID
func (c *TemplateClient) FetchGuardrailTemplate(templateID string) (*YamlTemplate, error) {
	endpoint := "/api/mcp/fetchGuardrailTemplate"
	params := url.Values{}
	params.Add("templateId", templateID)

	resp, err := c.makeRequest("POST", endpoint, params)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch guardrail template: %w", err)
	}

	if len(resp.ActionErrors) > 0 {
		return nil, fmt.Errorf("API returned errors: %v", resp.ActionErrors)
	}

	return resp.McpGuardrailTemplate, nil
}

// HealthCheck checks if the MCP Guardrails service is healthy
func (c *TemplateClient) HealthCheck() error {
	endpoint := "/api/mcp/health"

	resp, err := c.makeRequest("GET", endpoint, nil)
	if err != nil {
		return fmt.Errorf("health check failed: %w", err)
	}

	if len(resp.ActionErrors) > 0 {
		return fmt.Errorf("health check returned errors: %v", resp.ActionErrors)
	}

	return nil
}

// makeRequest makes an HTTP request to the API
func (c *TemplateClient) makeRequest(method, endpoint string, params url.Values) (*APIResponse, error) {
	reqURL := c.BaseURL + endpoint

	var req *http.Request
	var err error

	if method == "GET" && params != nil {
		reqURL += "?" + params.Encode()
		req, err = http.NewRequest(method, reqURL, nil)
	} else if method == "POST" && params != nil {
		req, err = http.NewRequest(method, reqURL, nil)
		if err == nil {
			req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
			// For POST requests, we'll send parameters in the body
			req.Body = io.NopCloser(strings.NewReader(params.Encode()))
		}
	} else {
		req, err = http.NewRequest(method, reqURL, nil)
	}

	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Accept", "application/json")

	httpResp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to make request: %w", err)
	}
	defer httpResp.Body.Close()

	if httpResp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(httpResp.Body)
		return nil, fmt.Errorf("API request failed with status %d: %s", httpResp.StatusCode, string(body))
	}

	body, err := io.ReadAll(httpResp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	var apiResp APIResponse
	if err := json.Unmarshal(body, &apiResp); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return &apiResp, nil
}
