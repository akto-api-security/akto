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
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
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

type panickingFileProcessor struct{}

func (panickingFileProcessor) SupportedExtensions() []string { return []string{".panic"} }

func (panickingFileProcessor) ExtractContent(context.Context, io.Reader, string) (string, error) {
	panic("parser crashed")
}

func assertFileAllowed(t *testing.T, recorder *httptest.ResponseRecorder, allowed bool) {
	t.Helper()
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
	if response.Allowed == nil || *response.Allowed != allowed || (allowed && response.Reason != "") {
		t.Fatalf("unexpected verdict: %s", recorder.Body.String())
	}
}

func TestValidateFileInspectionFailures(t *testing.T) {
	registry := fileprocessor.DefaultRegistry(1024)
	registry.RegisterWithLimit(panickingFileProcessor{}, 1024)
	h := &ValidationHandler{
		cfg:    &config.Config{File: config.FileConfig{Enabled: true, MaxFiles: 2, MaxChunks: 10}},
		logger: zap.NewNop(), fileRegistry: registry,
	}
	for _, tc := range []struct {
		name, filename, content string
	}{
		{"empty extraction", "empty.txt", ""},
		{"whitespace extraction", "empty.txt", " \t\n"},
		{"sanitized empty extraction", "empty.txt", "\x00\x01"},
		{"parser panic", "crash.panic", "data"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			fr := h.validateSingleFile(context.Background(), &fileInput{
				Filename: tc.filename, Reader: io.NopCloser(strings.NewReader(tc.content)),
			}, &models.ValidateRequestParams{}, "", "")
			recorder := httptest.NewRecorder()
			c, _ := gin.CreateTestContext(recorder)
			h.writeMultiFileResponse(c, []*fileResult{fr})
			assertFileAllowed(t, recorder, true)
		})
	}

	t.Run("unreadable uploaded file", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		c.Request = httptest.NewRequest(http.MethodPost, "/api/validate/file", nil)
		// No in-memory content or backing temp file: FileHeader.Open fails.
		c.Request.MultipartForm = &multipart.Form{File: map[string][]*multipart.FileHeader{
			"file": {{Filename: "missing.txt", Size: 1}},
		}}
		h.ValidateFile(c)
		assertFileAllowed(t, recorder, true)
	})

	t.Run("multipart parse failure", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		c.Request = httptest.NewRequest(http.MethodPost, "/api/validate/file", strings.NewReader("--broken"))
		c.Request.Header.Set("Content-Type", "multipart/form-data; boundary=broken")
		h.ValidateFile(c)
		assertFileAllowed(t, recorder, true)
	})

	t.Run("missing configuration", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		(&ValidationHandler{logger: zap.NewNop()}).ValidateFile(c)
		assertFileAllowed(t, recorder, true)
	})
}

func TestFileChunkFailuresDoNotHidePolicyBlocks(t *testing.T) {
	for _, tc := range []struct {
		name       string
		failure    string
		verdict    *mcp.ValidationResult
		allowed    bool
		concurrent int
	}{
		{"error then clean", "error", &mcp.ValidationResult{Allowed: true}, true, 1},
		{"error then blocked", "error", &mcp.ValidationResult{Allowed: false, Reason: "policy violation"}, false, 1},
		{"error then redacted", "error", &mcp.ValidationResult{Allowed: true, Modified: true}, false, 1},
		{"panic then blocked", "panic", &mcp.ValidationResult{Allowed: false}, false, 1},
		{"panic then clean", "panic", &mcp.ValidationResult{Allowed: true}, true, 1},
		{"concurrent error and block", "error", &mcp.ValidationResult{Allowed: false}, false, 2},
	} {
		t.Run(tc.name, func(t *testing.T) {
			h := &ValidationHandler{logger: zap.NewNop(), cfg: &config.Config{File: config.FileConfig{
				MaxConcurrent: tc.concurrent, BlockOnRedaction: true,
			}}}
			validate := func(_ context.Context, params *models.ValidateRequestParams, _, _ string) (*mcp.ValidationResult, string, error) {
				if params.RequestPayload == marshalPromptPayload("failure") {
					if tc.failure == "panic" {
						panic("validator panic")
					}
					return nil, "", errors.New("validator unavailable")
				}
				return tc.verdict, "", nil
			}
			results := h.validateChunks(context.Background(), []string{"failure", "policy check"}, &models.ValidateRequestParams{}, "", "", validate)
			if results[1] == nil || results[1].Result != tc.verdict {
				t.Fatal("inspection failure must not cancel the remaining policy checks")
			}
			fr := h.applyFileChunkResults(&fileResult{Filename: "sample.txt", Allowed: true}, results)
			if fr.Allowed != tc.allowed {
				t.Fatalf("allowed = %v, want %v; reason = %q", fr.Allowed, tc.allowed, fr.Reason)
			}
			if !tc.allowed && fr.FailedChunkIndex != 2 {
				t.Fatalf("failed chunk = %d, want policy-blocked chunk 2", fr.FailedChunkIndex)
			}
		})
	}
}

func TestFileChunkRetriesFailOpen(t *testing.T) {
	h := &ValidationHandler{logger: zap.NewNop(), cfg: &config.Config{File: config.FileConfig{MaxRetries: 1}}}
	calls := 0
	validate := func(context.Context, *models.ValidateRequestParams, string, string) (*mcp.ValidationResult, string, error) {
		calls++
		return nil, "", errors.New("validator unavailable")
	}
	result := h.validateWithRetry(context.Background(), "payload", &models.ValidateRequestParams{}, "", "", validate)
	if calls != 2 || result.Err == nil {
		t.Fatalf("expected error after 2 attempts, got %d attempts and %+v", calls, result)
	}
	if fr := h.applyFileChunkResults(&fileResult{Allowed: true}, []*chunkResult{result}); !fr.Allowed {
		t.Fatal("exhausted retries must allow the file")
	}

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	result = h.validateWithRetry(ctx, "payload", &models.ValidateRequestParams{}, "", "", validate)
	if !errors.Is(result.Err, context.Canceled) {
		t.Fatalf("expected cancellation error, got %v", result.Err)
	}
	if fr := h.applyFileChunkResults(&fileResult{Allowed: true}, []*chunkResult{result}); !fr.Allowed {
		t.Fatal("cancellation must allow the file")
	}
}

func TestFileURLFetchFailures(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/failed.txt":
			w.WriteHeader(http.StatusServiceUnavailable)
		case "/slow.txt":
			<-r.Context().Done()
		default:
			w.WriteHeader(http.StatusOK)
			w.(http.Flusher).Flush()
			// Delay the body until fetchFromURL has returned its reader.
			time.Sleep(10 * time.Millisecond)
			_, _ = io.WriteString(w, "text to inspect")
		}
	}))
	defer server.Close()
	h := &ValidationHandler{logger: zap.NewNop(), fileRegistry: fileprocessor.DefaultRegistry(1024),
		cfg: &config.Config{File: config.FileConfig{Enabled: true, MaxFiles: 2, URLTimeoutSec: 5}}}
	for _, path := range []string{"/failed.txt", "/slow.txt"} {
		t.Run(path, func(t *testing.T) {
			var body bytes.Buffer
			writer := multipart.NewWriter(&body)
			if err := writer.WriteField("url", server.URL+path); err != nil {
				t.Fatal(err)
			}
			if err := writer.Close(); err != nil {
				t.Fatal(err)
			}
			recorder := httptest.NewRecorder()
			c, _ := gin.CreateTestContext(recorder)
			ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
			defer cancel()
			c.Request = httptest.NewRequest(http.MethodPost, "/api/validate/file", &body).WithContext(ctx)
			c.Request.Header.Set("Content-Type", writer.FormDataContentType())
			h.ValidateFile(c)
			assertFileAllowed(t, recorder, true)
		})
	}

	t.Run("fetch failure does not skip later input restrictions", func(t *testing.T) {
		_, status, err := h.fetchFromURLs(context.Background(), []string{server.URL + "/failed.txt", server.URL + "/unsupported.bin"})
		if err == nil || status != http.StatusBadRequest {
			t.Fatalf("expected unsupported-type rejection, got %d, %v", status, err)
		}
	})

	t.Run("successful fetch remains readable", func(t *testing.T) {
		input, _, err := h.fetchFromURL(context.Background(), server.URL+"/valid.txt")
		if err != nil {
			t.Fatal(err)
		}
		defer input.Reader.Close()
		body, err := io.ReadAll(input.Reader)
		if err != nil || string(body) != "text to inspect" {
			t.Fatalf("body = %q, error = %v", body, err)
		}
	})
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
