package mediaprovider

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"strings"
	"time"
)

// AzureSpeech implements TranscriptionProvider via Azure AI Speech Fast Transcription.
type AzureSpeech struct {
	apiKey  string
	baseURL string
	client  *http.Client
}

func NewAzureSpeech(apiKey, baseURL string) *AzureSpeech {
	return &AzureSpeech{
		apiKey:  apiKey,
		baseURL: strings.TrimRight(baseURL, "/"),
		client:  &http.Client{Timeout: 120 * time.Second},
	}
}

func (a *AzureSpeech) Transcribe(ctx context.Context, r io.Reader, format string) (string, error) {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	definition := `{"locales":["en-US"]}`
	if err := writer.WriteField("definition", definition); err != nil {
		return "", fmt.Errorf("write definition field: %w", err)
	}

	filename := "audio." + format
	part, err := writer.CreateFormFile("audio", filename)
	if err != nil {
		return "", fmt.Errorf("create form file: %w", err)
	}
	if _, err := io.Copy(part, r); err != nil {
		return "", fmt.Errorf("copy audio data: %w", err)
	}
	if err := writer.Close(); err != nil {
		return "", fmt.Errorf("close multipart writer: %w", err)
	}

	url := a.baseURL + "/speechtotext/transcriptions:transcribe?api-version=2024-11-15"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, &buf)
	if err != nil {
		return "", fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Ocp-Apim-Subscription-Key", a.apiKey)

	resp, err := a.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("azure speech request: %w", err)
	}
	defer resp.Body.Close()

	const maxResponseBytes = 5 * 1024 * 1024
	respBody, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("azure speech returned HTTP %d: %s", resp.StatusCode, truncate(string(respBody), 200))
	}

	return parseAzureSpeechResponse(respBody)
}

func parseAzureSpeechResponse(body []byte) (string, error) {
	var result struct {
		CombinedPhrases []struct {
			Text string `json:"text"`
		} `json:"combinedPhrases"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
		return "", fmt.Errorf("parse azure speech response: %w", err)
	}

	var texts []string
	for _, p := range result.CombinedPhrases {
		if p.Text != "" {
			texts = append(texts, p.Text)
		}
	}
	return strings.Join(texts, " "), nil
}
