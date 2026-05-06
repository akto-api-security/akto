package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"
)

// maxLLMResponseSize caps how much of a provider response we read into memory.
const maxLLMResponseSize = 1 * 1024 * 1024 // 1MB

// Provider selector values for SCANNER_LLM_PROVIDER.
const (
	LLMProviderOpenAI    = "openai"
	LLMProviderAnthropic = "anthropic"
)

// Default models if OPENAI_MODEL / ANTHROPIC_MODEL are not set.
const (
	DefaultOpenAIModel    = "gpt-4o-mini"
	DefaultAnthropicModel = "claude-haiku-4-5-20251001"
)

// LLMProvider abstracts the underlying LLM API (OpenAI or Anthropic).
// Implementations should be safe for concurrent use.
type LLMProvider interface {
	Name() string
	Complete(ctx context.Context, prompt string) (string, error)
}

// ── OpenAI provider ──────────────────────────────────────────────────────────

type openAIProvider struct {
	apiKey     string
	model      string
	httpClient *http.Client
}

type openAIChatRequest struct {
	Model       string          `json:"model"`
	Messages    []openAIMessage `json:"messages"`
	Temperature float64         `json:"temperature"`
	MaxTokens   int             `json:"max_tokens"`
}

type openAIMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type openAIChatResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
}

// NewOpenAIProvider constructs an OpenAI-backed LLMProvider. Returns an error
// if apiKey is empty. If model is empty, DefaultOpenAIModel is used.
func NewOpenAIProvider(apiKey, model string, httpClient *http.Client) (LLMProvider, error) {
	if apiKey == "" {
		return nil, fmt.Errorf("OPENAI_API_KEY is required when SCANNER_LLM_PROVIDER=openai")
	}
	if model == "" {
		model = DefaultOpenAIModel
	}
	if httpClient == nil {
		httpClient = &http.Client{Timeout: defaultTimeout}
	}
	log.Printf("[LLMScanner/OpenAI] Initialized provider model=%s", model)
	return &openAIProvider{apiKey: apiKey, model: model, httpClient: httpClient}, nil
}

func (p *openAIProvider) Name() string { return LLMProviderOpenAI }

func (p *openAIProvider) Complete(ctx context.Context, prompt string) (string, error) {
	reqBody := openAIChatRequest{
		Model:       p.model,
		Temperature: 0.1,
		MaxTokens:   256,
		Messages:    []openAIMessage{{Role: "user", Content: prompt}},
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("marshal OpenAI request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", "https://api.openai.com/v1/chat/completions", bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("create OpenAI request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+p.apiKey)

	start := time.Now()
	resp, err := p.httpClient.Do(req)
	elapsed := time.Since(start)
	if err != nil {
		log.Printf("[LLMScanner/OpenAI] request failed after %s: %v", elapsed, err)
		return "", fmt.Errorf("OpenAI request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		var buf bytes.Buffer
		_, _ = buf.ReadFrom(io.LimitReader(resp.Body, 1024))
		log.Printf("[LLMScanner/OpenAI] non-OK status=%d body=%s", resp.StatusCode, buf.String())
		return "", fmt.Errorf("OpenAI returned status %d", resp.StatusCode)
	}

	var buf bytes.Buffer
	if _, err := buf.ReadFrom(io.LimitReader(resp.Body, maxLLMResponseSize)); err != nil {
		return "", fmt.Errorf("read OpenAI response: %w", err)
	}

	var chatResp openAIChatResponse
	if err := json.Unmarshal(buf.Bytes(), &chatResp); err != nil {
		return "", fmt.Errorf("parse OpenAI response: %w", err)
	}
	if len(chatResp.Choices) == 0 {
		return "", fmt.Errorf("no choices in OpenAI response")
	}
	log.Printf("[LLMScanner/OpenAI] completed in %s", elapsed)
	return chatResp.Choices[0].Message.Content, nil
}

// ── Anthropic provider ───────────────────────────────────────────────────────

type anthropicProvider struct {
	apiKey     string
	model      string
	httpClient *http.Client
}

type anthropicRequest struct {
	Model     string             `json:"model"`
	MaxTokens int                `json:"max_tokens"`
	Messages  []anthropicMessage `json:"messages"`
}

type anthropicMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type anthropicResponse struct {
	Content []struct {
		Text string `json:"text"`
	} `json:"content"`
}

// NewAnthropicProvider constructs an Anthropic-backed LLMProvider. Returns an
// error if apiKey is empty. If model is empty, DefaultAnthropicModel is used.
func NewAnthropicProvider(apiKey, model string, httpClient *http.Client) (LLMProvider, error) {
	if apiKey == "" {
		return nil, fmt.Errorf("ANTHROPIC_API_KEY is required when SCANNER_LLM_PROVIDER=anthropic")
	}
	if model == "" {
		model = DefaultAnthropicModel
	}
	if httpClient == nil {
		httpClient = &http.Client{Timeout: defaultTimeout}
	}
	log.Printf("[LLMScanner/Anthropic] Initialized provider model=%s", model)
	return &anthropicProvider{apiKey: apiKey, model: model, httpClient: httpClient}, nil
}

func (p *anthropicProvider) Name() string { return LLMProviderAnthropic }

func (p *anthropicProvider) Complete(ctx context.Context, prompt string) (string, error) {
	reqBody := anthropicRequest{
		Model:     p.model,
		MaxTokens: 256,
		Messages:  []anthropicMessage{{Role: "user", Content: prompt}},
	}

	jsonData, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("marshal Anthropic request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", "https://api.anthropic.com/v1/messages", bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("create Anthropic request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-api-key", p.apiKey)
	req.Header.Set("anthropic-version", "2023-06-01")

	start := time.Now()
	resp, err := p.httpClient.Do(req)
	elapsed := time.Since(start)
	if err != nil {
		log.Printf("[LLMScanner/Anthropic] request failed after %s: %v", elapsed, err)
		return "", fmt.Errorf("Anthropic request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		var buf bytes.Buffer
		_, _ = buf.ReadFrom(io.LimitReader(resp.Body, 1024))
		log.Printf("[LLMScanner/Anthropic] non-OK status=%d body=%s", resp.StatusCode, buf.String())
		return "", fmt.Errorf("Anthropic returned status %d", resp.StatusCode)
	}

	var buf bytes.Buffer
	if _, err := buf.ReadFrom(io.LimitReader(resp.Body, maxLLMResponseSize)); err != nil {
		return "", fmt.Errorf("read Anthropic response: %w", err)
	}

	var anthResp anthropicResponse
	if err := json.Unmarshal(buf.Bytes(), &anthResp); err != nil {
		return "", fmt.Errorf("parse Anthropic response: %w", err)
	}
	if len(anthResp.Content) == 0 {
		return "", fmt.Errorf("no content in Anthropic response")
	}
	log.Printf("[LLMScanner/Anthropic] completed in %s", elapsed)
	return anthResp.Content[0].Text, nil
}
