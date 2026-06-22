package dbabstractor

import (
	"bytes"
	"encoding/json"
	"fmt"
)

// FileInspectionRule mirrors cyborg's com.akto.dto.endpoint_shield.FileInspectionRule
// (only fields we read on the wire — _id is the rule's MongoDB ObjectId hex).
type FileInspectionRule struct {
	ID            string `json:"_id"`
	IDHex         string `json:"idHex"`
	Path          string `json:"path"`
	ExistenceOnly bool   `json:"existenceOnly"`
	MaxDepth      int    `json:"maxDepth"`
	AccountID     int    `json:"accountId"`
	CreatedTs     int    `json:"createdTs"`
	UpdatedTs     int    `json:"updatedTs"`
}

// FileInspectionMatch mirrors cyborg's FileInspectionResult.Match.
type FileInspectionMatch struct {
	Path             string `json:"path"`
	Exists           bool   `json:"exists"`
	IsDir            *bool  `json:"isDir,omitempty"`
	Size             int64  `json:"size"`
	ModifiedAt       int    `json:"modifiedAt"`
	Sha256           string `json:"sha256"`
	ContentInline    string `json:"contentInline"`
	ContentBlobName  string `json:"contentBlobName"`
	ContentTruncated bool   `json:"contentTruncated"`
	ReadError        string `json:"readError"`
}

// FileInspectionResult mirrors cyborg's FileInspectionResult — one execution of one
// rule on one device.
type FileInspectionResult struct {
	ID           string                `json:"_id"`
	RuleID       string                `json:"ruleId"`
	AccountID    int                   `json:"accountId"`
	AgentID      string                `json:"agentId"`
	DeviceID     string                `json:"deviceId"`
	DeviceLabel  string                `json:"deviceLabel"`
	ExecutedAt   int                   `json:"executedAt"`
	Status       string                `json:"status"`
	ErrorMessage string                `json:"errorMessage"`
	Matches      []FileInspectionMatch `json:"matches"`
}

// AddFileInspectionRule creates (or updates, on path match) a file-inspection rule in
// cyborg. Returns the persisted rule with its assigned _id.
func (c *Client) AddFileInspectionRule(path string, existenceOnly bool, maxDepth int, addedBy string) (*FileInspectionRule, error) {
	body := map[string]any{
		"path":          path,
		"existenceOnly": existenceOnly,
		"maxDepth":      maxDepth,
		"addedBy":       addedBy,
	}
	raw, err := c.SendRequest("POST", "/addFileInspectionRule", body)
	if err != nil {
		return nil, fmt.Errorf("addFileInspectionRule: %w", err)
	}
	var wrap struct {
		Rule *FileInspectionRule `json:"rule"`
	}
	if err := json.Unmarshal(raw, &wrap); err != nil {
		return nil, fmt.Errorf("addFileInspectionRule: parse response: %w (body=%s)", err, truncate(raw, 200))
	}
	if wrap.Rule == nil {
		return nil, fmt.Errorf("addFileInspectionRule: no rule in response: %s", truncate(raw, 200))
	}
	return wrap.Rule, nil
}

// FetchFileInspectionResults returns up to `limit` results with executedAt > since,
// sorted ascending by executedAt. Pass since=0 on first call.
func (c *Client) FetchFileInspectionResults(since int, limit int) ([]FileInspectionResult, error) {
	body := map[string]any{
		"sinceExecutedAt": since,
		"limit":           limit,
	}
	raw, err := c.SendRequest("POST", "/fetchFileInspectionResults", body)
	if err != nil {
		return nil, fmt.Errorf("fetchFileInspectionResults: %w", err)
	}
	// Cyborg may serialize as either a bare array (legacy / empty case) or
	// the Struts-wrapped {"recentResults":[...]}. Dispatch on the first
	// non-whitespace byte so we accept either shape.
	trimmed := bytes.TrimLeft(raw, " \t\r\n")
	if len(trimmed) > 0 && trimmed[0] == '[' {
		var out []FileInspectionResult
		if err := json.Unmarshal(raw, &out); err != nil {
			return nil, fmt.Errorf("fetchFileInspectionResults: parse array: %w (body=%s)", err, truncate(raw, 200))
		}
		return out, nil
	}
	var wrap struct {
		RecentResults []FileInspectionResult `json:"recentResults"`
	}
	if err := json.Unmarshal(raw, &wrap); err != nil {
		return nil, fmt.Errorf("fetchFileInspectionResults: parse object: %w (body=%s)", err, truncate(raw, 200))
	}
	return wrap.RecentResults, nil
}

// GetFileContent downloads a previously-uploaded file's content from cyborg's blob
// storage and returns it as a UTF-8 string.
func (c *Client) GetFileContent(sha256 string) (string, error) {
	body := map[string]any{"sha256": sha256}
	raw, err := c.SendRequest("POST", "/getFileContent", body)
	if err != nil {
		return "", fmt.Errorf("getFileContent: %w", err)
	}
	var wrap struct {
		BlobContent string `json:"blobContent"`
	}
	if err := json.Unmarshal(raw, &wrap); err != nil {
		return "", fmt.Errorf("getFileContent: parse: %w (body=%s)", err, truncate(raw, 200))
	}
	return wrap.BlobContent, nil
}

// UpsertNhiIdentity sends one NHI record to cyborg. The server upserts on
// (hash, deviceId, source) — same secret on the same device in the same file
// updates in place; absence of a hash falls back to (contextSource,
// identityName). `identity` is any JSON-serialisable struct whose field
// names match com.akto.dto.nhi_governance.NhiIdentity (camelCase via lombok).
func (c *Client) UpsertNhiIdentity(identity any) error {
	body := map[string]any{"nhiIdentity": identity}
	_, err := c.SendRequest("POST", "/upsertNhiIdentity", body)
	if err != nil {
		return fmt.Errorf("upsertNhiIdentity: %w", err)
	}
	return nil
}

// UpsertNhiIdentities batches multiple records into a single call.
func (c *Client) UpsertNhiIdentities(identities []any) error {
	body := map[string]any{"nhiIdentities": identities}
	_, err := c.SendRequest("POST", "/upsertNhiIdentities", body)
	if err != nil {
		return fmt.Errorf("upsertNhiIdentities: %w", err)
	}
	return nil
}

func truncate(b []byte, n int) string {
	if len(b) <= n {
		return string(b)
	}
	return string(b[:n]) + "..."
}
