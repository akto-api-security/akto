package dbabstractor

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/auth"
	"go.uber.org/zap"
)

// Client represents a client for database-abstractor service
type Client struct {
	baseURL    string
	httpClient *http.Client
	logger     *zap.Logger
}

// NewClient creates a new database-abstractor client
func NewClient(logger *zap.Logger) *Client {
	baseURL := buildDatabaseAbstractorURL()

	return &Client{
		baseURL: baseURL,
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		logger: logger,
	}
}

// buildDatabaseAbstractorURL builds the database abstractor URL
// Similar to buildDbAbstractorUrl() in ClientActor.java
func buildDatabaseAbstractorURL() string {
	dbAbsHost := os.Getenv("DATABASE_ABSTRACTOR_SERVICE_URL")
	if dbAbsHost == "" {
		dbAbsHost = "https://cyborg.akto.io"
	}

	if strings.HasSuffix(dbAbsHost, "/") {
		dbAbsHost = strings.TrimSuffix(dbAbsHost, "/")
	}

	return dbAbsHost + "/api"
}

// FetchGuardrailPolicies fetches guardrail policies from database-abstractor service
func (c *Client) FetchGuardrailPolicies() ([]byte, error) {
	url := c.baseURL + "/fetchGuardrailPolicies"

	// Create POST request with empty body
	req, err := http.NewRequest("POST", url, bytes.NewBuffer([]byte("{}")))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Add JWT token to Authorization header
	token := auth.GetDatabaseAbstractorServiceToken()
	if token == "" {
		return nil, fmt.Errorf("DATABASE_ABSTRACTOR_SERVICE_TOKEN not set")
	}
	req.Header.Set("Authorization", token)
	req.Header.Set("Content-Type", "application/json")

	c.logger.Info("Fetching guardrail policies", zap.String("url", url))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch guardrail policies: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to fetch guardrail policies, status: %d, body: %s", resp.StatusCode, string(body))
	}

	c.logger.Info("Successfully fetched guardrail policies",
		zap.Int("responseSize", len(body)),
		zap.String("responsePreview", string(body[:min(len(body), 500)])))

	return body, nil
}

// FetchMcpAuditInfo fetches MCP audit policies from database-abstractor service
func (c *Client) FetchMcpAuditInfo() ([]byte, error) {
	url := c.baseURL + "/fetchMcpAuditInfo"

	// Create request body with remarksList
	requestBody := map[string]any{
		"remarksList": []string{"Conditionally Approved", "Rejected"},
	}
	jsonBody, err := json.Marshal(requestBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request body: %w", err)
	}

	// Create POST request
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Add JWT token to Authorization header
	token := auth.GetDatabaseAbstractorServiceToken()
	if token == "" {
		return nil, fmt.Errorf("DATABASE_ABSTRACTOR_SERVICE_TOKEN not set")
	}
	req.Header.Set("Authorization", token)
	req.Header.Set("Content-Type", "application/json")

	c.logger.Info("Fetching MCP audit info", zap.String("url", url))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch MCP audit info: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to fetch MCP audit info, status: %d, body: %s", resp.StatusCode, string(body))
	}

	c.logger.Info("Successfully fetched MCP audit info",
		zap.Int("responseSize", len(body)),
		zap.String("responsePreview", string(body[:min(len(body), 500)])))

	return body, nil
}

// SendRequest sends a generic request to database-abstractor service
func (c *Client) SendRequest(method, endpoint string, body interface{}) ([]byte, error) {
	url := c.baseURL + endpoint

	var reqBody io.Reader
	if body != nil {
		jsonData, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		reqBody = bytes.NewBuffer(jsonData)
	}

	req, err := http.NewRequest(method, url, reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	// Add JWT token to Authorization header
	token := auth.GetDatabaseAbstractorServiceToken()
	if token == "" {
		return nil, fmt.Errorf("DATABASE_ABSTRACTOR_SERVICE_TOKEN not set")
	}
	req.Header.Set("Authorization", token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("request failed with status %d: %s", resp.StatusCode, string(respBody))
	}

	return respBody, nil
}
