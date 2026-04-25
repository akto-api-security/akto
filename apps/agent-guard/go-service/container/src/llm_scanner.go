package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"
)

// Scanner name / type constants used for LLM-path dispatch. Agent-guard accepts
// the broader set from the Python backend; the LLM path only handles these.
const (
	ScannerTypePrompt = "prompt"
	ScannerTypeOutput = "output"

	ScannerPromptInjection = "PromptInjection"
	ScannerBanTopics       = "BanTopics"
)

// Request-level config keys used by the LLM path.
const (
	ConfigKeyUseLLM = "use_llm" // bool — if true, route to LLMScanner
	ConfigKeyTopics = "topics"  // []string — used by BanTopics
)

// LLMScanner evaluates a single ScanRequest by calling the configured
// LLMProvider with the appropriate prompt template. Only PromptInjection
// (both scanner_type values) and BanTopics are supported; everything else
// returns ErrLLMUnsupportedScanner so the caller can fall back.
type LLMScanner struct {
	provider LLMProvider
}

// ErrLLMUnsupportedScanner is returned by Scan when the request targets a
// scanner the LLM path does not implement. Callers should fall through to the
// default (Python) backend on this error.
var ErrLLMUnsupportedScanner = fmt.Errorf("scanner not supported by LLM path")

// NewLLMScanner constructs a scanner around the given provider. provider must
// be non-nil.
func NewLLMScanner(provider LLMProvider) *LLMScanner {
	return &LLMScanner{provider: provider}
}

// supportsLLM reports whether the given scanner name is handled by the LLM path.
func supportsLLM(scannerName string) bool {
	switch scannerName {
	case ScannerPromptInjection, ScannerBanTopics:
		return true
	default:
		return false
	}
}

// useLLMRequested reports whether the request config opts into the LLM path.
// Accepts boolean true or the string "true" (case-insensitive) to tolerate
// loose JSON shapes from clients.
func useLLMRequested(config map[string]interface{}) bool {
	if config == nil {
		return false
	}
	switch v := config[ConfigKeyUseLLM].(type) {
	case bool:
		return v
	case string:
		return strings.EqualFold(strings.TrimSpace(v), "true")
	default:
		return false
	}
}

// Scan runs the request against the configured LLM provider and maps the
// provider's JSON answer into a ScanResponse.
func (s *LLMScanner) Scan(ctx context.Context, req ScanRequest) (*ScanResponse, error) {
	if !supportsLLM(req.ScannerName) {
		return nil, ErrLLMUnsupportedScanner
	}

	prompt := buildScanPrompt(req.ScannerName, req.ScannerType, req.Config, req.Text)
	if prompt == "" {
		return nil, ErrLLMUnsupportedScanner
	}

	start := time.Now()
	raw, err := s.provider.Complete(ctx, prompt)
	elapsed := time.Since(start)
	if err != nil {
		return nil, fmt.Errorf("LLM provider call failed: %w", err)
	}

	resp, parseErr := parseLLMScanResult(req.ScannerName, raw)
	if parseErr != nil {
		log.Printf("[LLMScanner] parse failed scanner=%s err=%v raw=%q", req.ScannerName, parseErr, truncate(raw, 500))
		return nil, fmt.Errorf("parse LLM response: %w", parseErr)
	}

	resp.SanitizedText = req.Text
	resp.ExecutionTime = float64(elapsed.Milliseconds())
	if resp.Details == nil {
		resp.Details = map[string]interface{}{}
	}
	resp.Details["llm_provider"] = s.provider.Name()
	resp.Details["scanner_type"] = req.ScannerType

	log.Printf("[LLMScanner] scan complete scanner=%s isValid=%v risk=%.2f elapsed_ms=%.0f",
		req.ScannerName, resp.IsValid, resp.RiskScore, resp.ExecutionTime)
	return resp, nil
}

// buildScanPrompt formats the right prompt template for the scanner. Returns
// "" for unsupported scanners (callers treat this as ErrLLMUnsupportedScanner).
func buildScanPrompt(scannerName, scannerType string, config map[string]interface{}, text string) string {
	switch scannerName {
	case ScannerPromptInjection:
		if scannerType == ScannerTypeOutput {
			return fmt.Sprintf(OutputPromptInjectionDetectionPrompt, text)
		}
		return fmt.Sprintf(PromptInjectionDetectionPrompt, text)
	case ScannerBanTopics:
		topics := extractTopics(config)
		return fmt.Sprintf(BanTopicsDetectionPrompt, strings.Join(topics, ", "), text)
	default:
		return ""
	}
}

// extractTopics pulls a string slice out of config["topics"], which may be
// []string, []interface{}, or a comma-separated string.
func extractTopics(config map[string]interface{}) []string {
	if config == nil {
		return nil
	}
	raw, ok := config[ConfigKeyTopics]
	if !ok {
		return nil
	}
	switch v := raw.(type) {
	case []string:
		return v
	case []interface{}:
		out := make([]string, 0, len(v))
		for _, item := range v {
			if s, ok := item.(string); ok && s != "" {
				out = append(out, s)
			}
		}
		return out
	case string:
		if v == "" {
			return nil
		}
		parts := strings.Split(v, ",")
		out := make([]string, 0, len(parts))
		for _, p := range parts {
			p = strings.TrimSpace(p)
			if p != "" {
				out = append(out, p)
			}
		}
		return out
	default:
		return nil
	}
}

// parseLLMScanResult converts the provider's JSON reply into a ScanResponse.
func parseLLMScanResult(scannerName, raw string) (*ScanResponse, error) {
	cleaned, err := cleanJSON(raw)
	if err != nil {
		return nil, err
	}

	var parsed map[string]interface{}
	if err := json.Unmarshal([]byte(cleaned), &parsed); err != nil {
		return nil, fmt.Errorf("unmarshal: %w", err)
	}

	resp := &ScanResponse{
		ScannerName: scannerName,
		Details:     map[string]interface{}{},
	}
	if reason, ok := parsed["reason"].(string); ok && reason != "" {
		resp.Details["reason"] = reason
	}

	switch scannerName {
	case ScannerPromptInjection:
		isInjection, _ := parsed["isInjection"].(bool)
		confidence, _ := parsed["confidence"].(float64)
		resp.IsValid = !isInjection
		resp.RiskScore = confidence

	case ScannerBanTopics:
		isBanned, _ := parsed["isBanned"].(bool)
		confidence, _ := parsed["confidence"].(float64)
		resp.IsValid = !isBanned
		resp.RiskScore = confidence
		if matched, ok := parsed["matchedTopic"].(string); ok && matched != "" {
			resp.Details["matchedTopic"] = matched
		}

	default:
		// Should not reach here — supportsLLM guards against this.
		resp.IsValid = true
	}

	return resp, nil
}

// cleanJSON extracts a JSON object from a possibly chatty LLM reply by
// trimming everything before the first '{' and after the last '}'. Ported
// verbatim from akto-gateway's utils.CleanJSON.
func cleanJSON(raw string) (string, error) {
	if raw == "" {
		return "", fmt.Errorf("empty response")
	}
	last := strings.LastIndex(raw, "}")
	if last != -1 {
		raw = raw[:last+1]
	}
	first := strings.Index(raw, "{")
	if first != -1 {
		raw = raw[first:]
	}
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", fmt.Errorf("no valid JSON found in response")
	}
	return raw, nil
}

// truncate returns at most n bytes of s, suffixed with "…" if truncation
// happened. Used for log redaction.
func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "…"
}
