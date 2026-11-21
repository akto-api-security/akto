package main

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/utils"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"go.uber.org/zap"
)

// Image OCR must reach the same agent-guard as the text scans: SCANNER_API_URL.
func TestImageOCRGoesToTheScannerAgentGuard(t *testing.T) {
	var paths []string
	agentGuard := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		paths = append(paths, r.URL.Path)
		_, _ = w.Write([]byte(`{"text": "Quarterly sales summary", "provider": "arbiter:bedrock"}`))
	}))
	defer agentGuard.Close()
	t.Setenv(utils.ScannerAPIURLEnv, agentGuard.URL)

	registry := fileprocessor.NewRegistry()
	cfg := &config.Config{File: config.FileConfig{Media: config.MediaConfig{Provider: "agent_guard", MaxImageBytes: 1 << 20}}}
	registerMediaProcessors(registry, cfg, zap.NewNop())

	processor := registry.Get(".png")
	if processor == nil {
		t.Fatal("no image processor registered for the agent_guard provider")
	}
	text, err := processor.ExtractContent(context.Background(), bytes.NewReader([]byte("\x89PNG\r\n\x1a\n")), ".png")
	if err != nil || text != "Quarterly sales summary" {
		t.Fatalf("ExtractContent = (%q, %v)", text, err)
	}
	if len(paths) != 1 || paths[0] != "/ocr" {
		t.Fatalf("agent-guard calls = %v, want one POST /ocr on the SCANNER_API_URL host", paths)
	}
}

func TestScannerAPIURLDefaultsLikeTheScannerClient(t *testing.T) {
	t.Setenv(utils.ScannerAPIURLEnv, "")
	if got := scannerAPIURL(); got != utils.DefaultScannerAPIURL {
		t.Errorf("unset SCANNER_API_URL: got %q, want the scanner client's default %q", got, utils.DefaultScannerAPIURL)
	}
	t.Setenv(utils.ScannerAPIURLEnv, "http://agent-guard:8090")
	if got := scannerAPIURL(); got != "http://agent-guard:8090" {
		t.Errorf("got %q", got)
	}
}
