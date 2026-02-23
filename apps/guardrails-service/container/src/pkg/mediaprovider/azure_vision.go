package mediaprovider

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// AzureVision implements OCRProvider via Azure Computer Vision OCR v3.2.
type AzureVision struct {
	apiKey  string
	baseURL string
	client  *http.Client
}

func NewAzureVision(apiKey, baseURL string) *AzureVision {
	return &AzureVision{
		apiKey:  apiKey,
		baseURL: strings.TrimRight(baseURL, "/"),
		client:  &http.Client{Timeout: 60 * time.Second},
	}
}

func (a *AzureVision) ExtractText(ctx context.Context, r io.Reader) (string, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return "", fmt.Errorf("read image: %w", err)
	}

	url := a.baseURL + "/vision/v3.2/ocr?detectOrientation=true&language=unk"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(data))
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("Ocp-Apim-Subscription-Key", a.apiKey)

	resp, err := a.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("azure vision request: %w", err)
	}
	defer resp.Body.Close()

	const maxResponseBytes = 5 * 1024 * 1024
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("azure vision returned HTTP %d: %s", resp.StatusCode, truncate(string(respBody), 200))
	}

	return parseAzureOCRResponse(respBody)
}

func parseAzureOCRResponse(body []byte) (string, error) {
	var result struct {
		Regions []struct {
			Lines []struct {
				Words []struct {
					Text string `json:"text"`
				} `json:"words"`
			} `json:"lines"`
		} `json:"regions"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
		return "", fmt.Errorf("parse azure ocr response: %w", err)
	}

	var lines []string
	for _, region := range result.Regions {
		for _, line := range region.Lines {
			var words []string
			for _, w := range line.Words {
				words = append(words, w.Text)
			}
			if len(words) > 0 {
				lines = append(lines, strings.Join(words, " "))
			}
		}
	}

	return strings.Join(lines, "\n"), nil
}
