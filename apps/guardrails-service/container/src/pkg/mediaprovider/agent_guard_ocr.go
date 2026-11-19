package mediaprovider

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// AgentGuardOCR implements OCRProvider via agent-guard's POST /ocr, which runs the image
// through agent-guard's configured OCR providers (Gemma by default, see OCR_MODEL_CONFIG_JSON
// in agent-guard). It needs no OCR credentials here: agent-guard holds them.
type AgentGuardOCR struct {
	baseURL string
	client  *http.Client
}

func NewAgentGuardOCR(baseURL string) *AgentGuardOCR {
	return &AgentGuardOCR{
		baseURL: strings.TrimRight(baseURL, "/"),
		client:  &http.Client{Timeout: 60 * time.Second},
	}
}

func (a *AgentGuardOCR) ExtractText(ctx context.Context, r io.Reader) (string, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return "", fmt.Errorf("read image: %w", err)
	}
	body, err := json.Marshal(map[string]string{"image_b64": base64.StdEncoding.EncodeToString(data)})
	if err != nil {
		return "", fmt.Errorf("encode request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, a.baseURL+"/ocr", bytes.NewReader(body))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := a.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("agent-guard ocr request: %w", err)
	}
	defer resp.Body.Close()

	const maxResponseBytes = 5 * 1024 * 1024
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("agent-guard ocr returned HTTP %d: %s", resp.StatusCode, truncate(string(respBody), 200))
	}

	var result struct {
		Text *string `json:"text"`
	}
	if err := json.Unmarshal(respBody, &result); err != nil {
		return "", fmt.Errorf("parse agent-guard ocr response: %w", err)
	}
	if result.Text == nil {
		return "", fmt.Errorf("agent-guard ocr response has no text: %s", truncate(string(respBody), 200))
	}
	return *result.Text, nil
}
