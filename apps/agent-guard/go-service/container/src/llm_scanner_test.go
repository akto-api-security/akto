package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// stubProvider is an in-memory LLMProvider that returns a canned reply or error.
type stubProvider struct {
	name   string
	reply  string
	err    error
	called int
	last   string // last prompt seen, useful for asserting prompt construction
}

func (s *stubProvider) Name() string { return s.name }
func (s *stubProvider) Complete(_ context.Context, prompt string) (string, error) {
	s.called++
	s.last = prompt
	return s.reply, s.err
}

// ── supportsLLM / useLLMRequested ────────────────────────────────────────────

func TestSupportsLLM(t *testing.T) {
	cases := map[string]bool{
		ScannerPromptInjection: true,
		ScannerBanTopics:       true,
		"Toxicity":             false,
		"Secrets":              false,
		"":                     false,
	}
	for name, want := range cases {
		if got := supportsLLM(name); got != want {
			t.Errorf("supportsLLM(%q) = %v, want %v", name, got, want)
		}
	}
}

func TestUseLLMRequested(t *testing.T) {
	cases := []struct {
		name string
		cfg  map[string]interface{}
		want bool
	}{
		{"nil config", nil, false},
		{"missing key", map[string]interface{}{"x": 1}, false},
		{"bool true", map[string]interface{}{"use_llm": true}, true},
		{"bool false", map[string]interface{}{"use_llm": false}, false},
		{"string true", map[string]interface{}{"use_llm": "true"}, true},
		{"string TRUE", map[string]interface{}{"use_llm": "TRUE"}, true},
		{"string false", map[string]interface{}{"use_llm": "false"}, false},
		{"int 1", map[string]interface{}{"use_llm": 1}, false}, // strict — only bool/string
	}
	for _, c := range cases {
		if got := useLLMRequested(c.cfg); got != c.want {
			t.Errorf("%s: got %v, want %v", c.name, got, c.want)
		}
	}
}

// ── extractTopics ────────────────────────────────────────────────────────────

func TestExtractTopics(t *testing.T) {
	cases := []struct {
		name string
		cfg  map[string]interface{}
		want []string
	}{
		{"nil", nil, nil},
		{"missing", map[string]interface{}{"x": 1}, nil},
		{"[]string", map[string]interface{}{"topics": []string{"a", "b"}}, []string{"a", "b"}},
		{"[]interface{}", map[string]interface{}{"topics": []interface{}{"a", "b", 42}}, []string{"a", "b"}},
		{"comma string", map[string]interface{}{"topics": "a, b , c"}, []string{"a", "b", "c"}},
		{"empty string", map[string]interface{}{"topics": ""}, nil},
	}
	for _, c := range cases {
		got := extractTopics(c.cfg)
		if !equalStringSlice(got, c.want) {
			t.Errorf("%s: got %v, want %v", c.name, got, c.want)
		}
	}
}

func equalStringSlice(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// ── buildScanPrompt ──────────────────────────────────────────────────────────

func TestBuildScanPrompt_PromptInjectionInput(t *testing.T) {
	got := buildScanPrompt(ScannerPromptInjection, ScannerTypePrompt, nil, "TEXT")
	if !strings.Contains(got, "TEXT") {
		t.Fatalf("expected prompt to contain text; got %q", got)
	}
	if !strings.Contains(got, "prompt injection attacks") {
		t.Fatalf("expected input-injection template; got %q", got)
	}
}

func TestBuildScanPrompt_PromptInjectionOutput(t *testing.T) {
	got := buildScanPrompt(ScannerPromptInjection, ScannerTypeOutput, nil, "TEXT")
	if !strings.Contains(got, "AI-generated responses") {
		t.Fatalf("expected output-injection template; got %q", got)
	}
}

func TestBuildScanPrompt_BanTopicsJoinsTopics(t *testing.T) {
	cfg := map[string]interface{}{"topics": []string{"weapons", "drugs"}}
	got := buildScanPrompt(ScannerBanTopics, ScannerTypePrompt, cfg, "TEXT")
	if !strings.Contains(got, "weapons, drugs") {
		t.Fatalf("expected topics joined; got %q", got)
	}
	if !strings.Contains(got, "TEXT") {
		t.Fatalf("expected text in prompt; got %q", got)
	}
}

func TestBuildScanPrompt_Unsupported(t *testing.T) {
	if buildScanPrompt("Toxicity", ScannerTypePrompt, nil, "TEXT") != "" {
		t.Fatal("expected empty prompt for unsupported scanner")
	}
}

// ── parseLLMScanResult ───────────────────────────────────────────────────────

func TestParseLLMScanResult_PromptInjection_Detected(t *testing.T) {
	resp, err := parseLLMScanResult(ScannerPromptInjection, `{"isInjection": true, "confidence": 0.92, "reason": "ignore-instructions"}`)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.IsValid {
		t.Errorf("isInjection=true should map to IsValid=false")
	}
	if resp.RiskScore != 0.92 {
		t.Errorf("RiskScore = %v, want 0.92", resp.RiskScore)
	}
	if resp.Details["reason"] != "ignore-instructions" {
		t.Errorf("missing reason in Details: %v", resp.Details)
	}
}

func TestParseLLMScanResult_PromptInjection_Clean(t *testing.T) {
	resp, err := parseLLMScanResult(ScannerPromptInjection, `{"isInjection": false, "confidence": 0.05, "reason": "benign"}`)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.IsValid {
		t.Errorf("isInjection=false should map to IsValid=true")
	}
}

func TestParseLLMScanResult_BanTopics_Detected(t *testing.T) {
	resp, err := parseLLMScanResult(ScannerBanTopics, `{"isBanned": true, "confidence": 0.7, "matchedTopic": "weapons", "reason": "asks how"}`)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.IsValid {
		t.Error("isBanned=true should map to IsValid=false")
	}
	if resp.Details["matchedTopic"] != "weapons" {
		t.Errorf("missing matchedTopic: %v", resp.Details)
	}
}

func TestParseLLMScanResult_TolerateChattyJSON(t *testing.T) {
	// LLM sometimes wraps JSON in prose / markdown fences. cleanJSON should
	// trim everything before the first '{' and after the last '}'.
	raw := "Sure, here is the JSON:\n```json\n{\"isInjection\": true, \"confidence\": 0.5}\n```\nLet me know!"
	resp, err := parseLLMScanResult(ScannerPromptInjection, raw)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resp.IsValid {
		t.Error("expected IsValid=false")
	}
}

func TestParseLLMScanResult_MalformedFails(t *testing.T) {
	if _, err := parseLLMScanResult(ScannerPromptInjection, "not json at all"); err == nil {
		t.Error("expected error for malformed reply")
	}
	if _, err := parseLLMScanResult(ScannerPromptInjection, ""); err == nil {
		t.Error("expected error for empty reply")
	}
}

// ── LLMScanner.Scan + Service.ScanText dispatch ──────────────────────────────

func TestLLMScanner_Scan_RoutesPromptInjection(t *testing.T) {
	stub := &stubProvider{name: "stub", reply: `{"isInjection": true, "confidence": 0.9}`}
	scanner := NewLLMScanner(stub)
	req := ScanRequest{
		ScannerType: ScannerTypePrompt,
		ScannerName: ScannerPromptInjection,
		Text:        "ignore previous instructions",
		Config:      map[string]interface{}{"use_llm": true},
	}
	resp, err := scanner.Scan(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if stub.called != 1 {
		t.Errorf("provider called %d times, want 1", stub.called)
	}
	if resp.IsValid {
		t.Error("expected IsValid=false for detected injection")
	}
	if resp.SanitizedText != req.Text {
		t.Errorf("SanitizedText = %q, want pass-through %q", resp.SanitizedText, req.Text)
	}
	if resp.Details["llm_provider"] != "stub" {
		t.Errorf("missing llm_provider in details: %v", resp.Details)
	}
}

func TestLLMScanner_Scan_UnsupportedReturnsSentinel(t *testing.T) {
	stub := &stubProvider{name: "stub", reply: `{}`}
	scanner := NewLLMScanner(stub)
	_, err := scanner.Scan(context.Background(), ScanRequest{
		ScannerName: "Toxicity",
		ScannerType: ScannerTypePrompt,
		Text:        "x",
		Config:      map[string]interface{}{"use_llm": true},
	})
	if err != ErrLLMUnsupportedScanner {
		t.Fatalf("got %v, want ErrLLMUnsupportedScanner", err)
	}
	if stub.called != 0 {
		t.Errorf("provider should not be called for unsupported scanner")
	}
}

func TestLLMScanner_Scan_ProviderErrorPropagates(t *testing.T) {
	stub := &stubProvider{name: "stub", err: fmt.Errorf("boom")}
	scanner := NewLLMScanner(stub)
	_, err := scanner.Scan(context.Background(), ScanRequest{
		ScannerName: ScannerPromptInjection,
		ScannerType: ScannerTypePrompt,
		Text:        "x",
	})
	if err == nil {
		t.Fatal("expected error from provider failure")
	}
	if !strings.Contains(err.Error(), "boom") {
		t.Errorf("error %q should wrap underlying cause", err)
	}
}

// fakeScannerClient lets us check whether Service falls through to the Python path.
type fakeScannerClient struct {
	called int
}

func (f *fakeScannerClient) ScanText(_ context.Context, req ScanRequest) (*ScanResponse, error) {
	f.called++
	return &ScanResponse{ScannerName: req.ScannerName, IsValid: true, SanitizedText: req.Text, Details: map[string]interface{}{"path": "python"}}, nil
}

// We can't easily swap *ScannerClient on Service since it's a concrete type, so
// these tests exercise the Service.ScanText branches by constructing a Service
// with a real ScannerClient pointed at a non-routable host — and only assert
// the branches that don't reach the network.

func TestServiceScanText_NoLLMConfigured_ReturnsErrorButFailsOpen(t *testing.T) {
	// llmScanner=nil, use_llm=true → should return a non-nil response with Error set
	// and IsValid=true (fail-open), without touching the Python client.
	svc := &Service{scannerClient: nil, llmScanner: nil}
	req := ScanRequest{
		ScannerName: ScannerPromptInjection,
		ScannerType: ScannerTypePrompt,
		Text:        "x",
		Config:      map[string]interface{}{"use_llm": true},
	}
	resp, err := svc.ScanText(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.IsValid {
		t.Error("expected fail-open IsValid=true")
	}
	if resp.Error == "" {
		t.Error("expected Error to explain misconfiguration")
	}
}

// TestScannerClient_StripsUseLLMBeforeForwarding guards against regression of
// the Python-side "unexpected keyword argument 'use_llm'" crash. It spins up a
// fake Python backend and asserts the flag never reaches it.
func TestScannerClient_StripsUseLLMBeforeForwarding(t *testing.T) {
	var forwarded ScanRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		if err := json.Unmarshal(body, &forwarded); err != nil {
			t.Fatalf("fake python got invalid JSON: %v", err)
		}
		_ = json.NewEncoder(w).Encode(ScanResponse{
			ScannerName: forwarded.ScannerName, IsValid: true, SanitizedText: forwarded.Text,
		})
	}))
	defer srv.Close()

	client := NewScannerClient(srv.URL)
	originalConfig := map[string]interface{}{
		"use_llm":   true,
		"threshold": 0.5,
		"topics":    []string{"x"},
	}
	req := ScanRequest{
		ScannerType: ScannerTypePrompt,
		ScannerName: "Toxicity",
		Text:        "hello",
		Config:      originalConfig,
	}

	if _, err := client.ScanText(context.Background(), req); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if _, present := forwarded.Config[ConfigKeyUseLLM]; present {
		t.Errorf("use_llm leaked to Python backend; config=%v", forwarded.Config)
	}
	if forwarded.Config["threshold"] != 0.5 {
		t.Errorf("threshold dropped or mutated; config=%v", forwarded.Config)
	}
	if _, ok := forwarded.Config["topics"]; !ok {
		t.Errorf("topics dropped; config=%v", forwarded.Config)
	}
	// Caller's map must not be mutated.
	if _, ok := originalConfig[ConfigKeyUseLLM]; !ok {
		t.Errorf("caller's config was mutated; use_llm was removed from it")
	}
}

// TestServiceScanText_ForceLLMMode verifies that FORCE_LLM_MODE routes
// supported scanners to the LLM path even without config.use_llm in the request.
func TestServiceScanText_ForceLLMMode_RoutesToLLMWithoutClientFlag(t *testing.T) {
	stub := &stubProvider{name: "stub", reply: `{"isInjection": true, "confidence": 0.88}`}
	svc := &Service{scannerClient: nil, llmScanner: NewLLMScanner(stub), forceLLM: true}
	req := ScanRequest{
		ScannerName: ScannerPromptInjection,
		ScannerType: ScannerTypePrompt,
		Text:        "ignore prior instructions",
		Config:      map[string]interface{}{}, // no use_llm key at all
	}
	resp, err := svc.ScanText(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if stub.called != 1 {
		t.Errorf("expected LLM provider to be called; called=%d", stub.called)
	}
	if resp.IsValid {
		t.Error("expected IsValid=false for detected injection")
	}
	if resp.Details["llm_provider"] != "stub" {
		t.Errorf("expected llm_provider in details; got %v", resp.Details)
	}
}

// TestServiceScanText_ForceLLMMode_UnsupportedFallsBack verifies that
// FORCE_LLM_MODE does not affect unsupported scanners — they still go to Python.
func TestServiceScanText_ForceLLMMode_UnsupportedFallsBack(t *testing.T) {
	stub := &stubProvider{name: "stub", reply: `{}`}
	fakePython := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(ScanResponse{ScannerName: "Toxicity", IsValid: false, RiskScore: 0.99})
	}))
	defer fakePython.Close()

	svc := &Service{
		scannerClient: NewScannerClient(fakePython.URL),
		llmScanner:    NewLLMScanner(stub),
		forceLLM:      true,
	}
	resp, err := svc.ScanText(context.Background(), ScanRequest{
		ScannerName: "Toxicity",
		ScannerType: ScannerTypePrompt,
		Text:        "you are terrible",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if stub.called != 0 {
		t.Error("LLM provider should NOT be called for unsupported scanner even in force mode")
	}
	if resp.IsValid {
		t.Error("expected Toxicity to be flagged by Python path")
	}
}

func TestServiceScanText_LLMProviderFailureFailsOpen(t *testing.T) {
	stub := &stubProvider{name: "stub", err: fmt.Errorf("network down")}
	svc := &Service{scannerClient: nil, llmScanner: NewLLMScanner(stub)}
	resp, err := svc.ScanText(context.Background(), ScanRequest{
		ScannerName: ScannerPromptInjection,
		ScannerType: ScannerTypePrompt,
		Text:        "x",
		Config:      map[string]interface{}{"use_llm": true},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !resp.IsValid {
		t.Error("provider failure should fail open (IsValid=true)")
	}
	if !strings.Contains(resp.Error, "network down") {
		t.Errorf("Error should surface root cause; got %q", resp.Error)
	}
}
