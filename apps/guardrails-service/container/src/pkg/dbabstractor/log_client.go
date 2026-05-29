package dbabstractor

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/akto-api-security/guardrails-service/pkg/auth"
)

// LogClient posts guardrails-service logs to database-abstractor.
// It is isolated from Client so log traffic (timeouts, pooling) never affects data fetches.
type LogClient struct {
	baseURL    string
	httpClient *http.Client
}

// NewLogClient creates a client used only by the async log sink.
func NewLogClient() *LogClient {
	transport := &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 10,
		IdleConnTimeout:     90 * time.Second,
	}

	return &LogClient{
		baseURL: buildDatabaseAbstractorURL(),
		httpClient: &http.Client{
			Timeout:   10 * time.Second,
			Transport: transport,
		},
	}
}

// HasServiceToken reports whether database-abstractor auth is configured.
func HasServiceToken() bool {
	return auth.GetDatabaseAbstractorServiceToken() != ""
}

// InsertGuardrailsServiceLog persists a log entry via database-abstractor.
func (c *LogClient) InsertGuardrailsServiceLog(key, message string, timestamp int) error {
	url := c.baseURL + "/insertGuardrailsServiceLog"

	body := map[string]any{
		"log": map[string]any{
			"key":       key,
			"log":       message,
			"timestamp": timestamp,
		},
	}
	jsonBody, err := json.Marshal(body)
	if err != nil {
		return fmt.Errorf("failed to marshal log body: %w", err)
	}

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonBody))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	token := auth.GetDatabaseAbstractorServiceToken()
	if token == "" {
		return fmt.Errorf("DATABASE_ABSTRACTOR_SERVICE_TOKEN not set")
	}
	req.Header.Set("Authorization", token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to insert guardrails service log: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("insert guardrails service log failed, status: %d, body: %s", resp.StatusCode, string(respBody))
	}
	return nil
}
