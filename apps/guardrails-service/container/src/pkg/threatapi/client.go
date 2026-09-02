// Package threatapi talks directly to threat-detection-backend's internal (non-dashboard)
// API — same host/auth as the external mcp package's ReportThreat/UpdateThreatRemediation.
// Only covers what has no upstream equivalent: polling an activity's decision by refId.
package threatapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	defaultThreatDetectionAPIURL = "https://tbs.akto.io/api/threat_detection/record_malicious_event"
	threatDetectionAPIURLEnv     = "THREAT_DETECTION_API_URL"
)

// Client is a minimal HTTP client for threat-detection-backend. Not pkg/dbabstractor.Client —
// that talks to the dashboard; this talks to threat-detection-backend directly.
type Client struct {
	baseURL    string // e.g. https://tbs.akto.io/api/threat_detection
	httpClient *http.Client
}

func NewClient() *Client {
	return &Client{
		baseURL:    resolveBaseURL(),
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}
}

func resolveBaseURL() string {
	url := defaultThreatDetectionAPIURL
	if envURL := strings.TrimSpace(os.Getenv(threatDetectionAPIURLEnv)); envURL != "" {
		url = envURL
	}
	return strings.TrimSuffix(url, "/record_malicious_event")
}

// apiToken is set at startup from cfg.DatabaseAbstractorToken (main.go).
func apiToken() string {
	return os.Getenv("AKTO_API_TOKEN")
}

// ApprovalStatus is the current human-approval decision for one activity/refId.
type ApprovalStatus struct {
	Found         bool   `json:"found"`
	Status        string `json:"status"`
	HumanResponse string `json:"humanResponse"`
}

// CheckHumanApprovalStatus looks up refId's current decision. Found=false is a normal
// outcome (write not landed yet), not an error.
func (c *Client) CheckHumanApprovalStatus(ctx context.Context, refId string) (*ApprovalStatus, error) {
	body, err := json.Marshal(map[string]string{"refId": refId})
	if err != nil {
		return nil, fmt.Errorf("marshal poll request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/get_approval_status", bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("build poll request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+apiToken())

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("poll approval status: %w", err)
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read poll response: %w", err)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("threat-detection-backend returned %d: %s", resp.StatusCode, string(raw))
	}

	var out ApprovalStatus
	if err := json.Unmarshal(raw, &out); err != nil {
		return nil, fmt.Errorf("unmarshal poll response: %w", err)
	}
	return &out, nil
}
