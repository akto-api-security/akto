package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

type failingFileProcessor struct{}

func (failingFileProcessor) SupportedExtensions() []string { return []string{".pdf"} }

func (failingFileProcessor) ExtractContent(context.Context, io.Reader, string) (string, error) {
	return "", errors.New("malformed document")
}

func TestValidateFileAllowsExtractionFailure(t *testing.T) {
	for _, tc := range []struct {
		name       string
		filenames  []string
		allowed    bool
		reasonPart string
	}{
		{"parsing failure is allowed", []string{"broken.pdf"}, true, ""},
		{"later files are still checked", []string{"broken.pdf", "unsupported.bin"}, false, "unsupported file type"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			registry := fileprocessor.NewRegistry()
			registry.RegisterWithLimit(failingFileProcessor{}, 1024*1024)
			h := &ValidationHandler{
				cfg:    &config.Config{File: config.FileConfig{Enabled: true, MaxFiles: 2}},
				logger: zap.NewNop(), fileRegistry: registry,
			}
			var body bytes.Buffer
			writer := multipart.NewWriter(&body)
			for _, filename := range tc.filenames {
				part, err := writer.CreateFormFile("file", filename)
				if err != nil {
					t.Fatal(err)
				}
				if _, err := io.WriteString(part, "invalid document"); err != nil {
					t.Fatal(err)
				}
			}
			if err := writer.Close(); err != nil {
				t.Fatal(err)
			}
			recorder := httptest.NewRecorder()
			c, _ := gin.CreateTestContext(recorder)
			c.Request = httptest.NewRequest(http.MethodPost, "/api/validate/file", &body)
			c.Request.Header.Set("Content-Type", writer.FormDataContentType())
			h.ValidateFile(c)
			if recorder.Code != http.StatusOK {
				t.Fatalf("status = %d, body = %s", recorder.Code, recorder.Body.String())
			}
			var response struct {
				Allowed *bool  `json:"allowed"`
				Reason  string `json:"reason"`
			}
			if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
				t.Fatal(err)
			}
			if response.Allowed == nil || *response.Allowed != tc.allowed {
				t.Fatalf("unexpected verdict: %s", recorder.Body.String())
			}
			if (tc.allowed && response.Reason != "") || !strings.Contains(response.Reason, tc.reasonPart) {
				t.Fatalf("unexpected reason: %q", response.Reason)
			}
		})
	}
}
