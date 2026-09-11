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
	"sync/atomic"
	"testing"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

type failingFileProcessor struct{ calls atomic.Int32 }

func (*failingFileProcessor) SupportedExtensions() []string { return []string{".pdf"} }

func (p *failingFileProcessor) ExtractContent(context.Context, io.Reader, string) (string, error) {
	p.calls.Add(1)
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

// The blocked-file response must match the /validate/request shape: Allowed=false plus the
// Reason (and behaviour) that stopped the upload, so a caller sees why the file was rejected.
func TestWriteMultiFileResponseVerdict(t *testing.T) {
	h := &ValidationHandler{logger: zap.NewNop()}

	t.Run("allow carries no reason or behaviour", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		h.writeMultiFileResponse(c, []*fileResult{{Filename: "a.txt", Allowed: true}})

		var resp mcp.ValidationResult
		if err := json.Unmarshal(recorder.Body.Bytes(), &resp); err != nil {
			t.Fatal(err)
		}
		if !resp.Allowed || resp.Reason != "" || resp.Behaviour != "" {
			t.Fatalf("unexpected allow verdict: %s", recorder.Body.String())
		}
	})

	t.Run("block carries reason and behaviour from the failing chunk", func(t *testing.T) {
		blocked := &fileResult{
			Filename:     "b.txt",
			Allowed:      false,
			Reason:       "file contains sensitive content redacted by guardrail policy (mask)",
			FailedResult: &mcp.ValidationResult{Allowed: true, Modified: true, Behaviour: "mask", ModifiedPayload: "secret"},
		}
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		// The first blocked file wins even when an allowed file precedes it.
		h.writeMultiFileResponse(c, []*fileResult{{Allowed: true}, blocked})

		var resp mcp.ValidationResult
		if err := json.Unmarshal(recorder.Body.Bytes(), &resp); err != nil {
			t.Fatal(err)
		}
		if resp.Allowed {
			t.Fatalf("blocked file must not be allowed: %s", recorder.Body.String())
		}
		if resp.Reason != blocked.Reason {
			t.Fatalf("reason = %q, want %q", resp.Reason, blocked.Reason)
		}
		if resp.Behaviour != "mask" {
			t.Fatalf("behaviour = %q, want mask", resp.Behaviour)
		}
		// The endpoint enforces by blocking and must never leak the rewritten content.
		if resp.ModifiedPayload != "" {
			t.Fatalf("modifiedPayload must be empty, got %q", resp.ModifiedPayload)
		}
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

	t.Run("every URL comes back as its own input", func(t *testing.T) {
		// A failed fetch must not swallow the URLs after it: each one resolves to its own
		// input, marked uninspectable, so each gets its own (allow) verdict.
		inputs := h.fetchFromURLs(context.Background(), []string{
			server.URL + "/failed.txt", server.URL + "/unsupported.bin", "file:///etc/passwd", "::not-a-url",
		})
		if len(inputs) != 4 {
			t.Fatalf("expected 4 inputs, got %d", len(inputs))
		}
		for i, in := range inputs {
			if in.Err == nil || in.Reader != nil {
				t.Fatalf("input %d: expected an uninspectable input, got %+v", i, in)
			}
		}
	})

	t.Run("successful fetch remains readable", func(t *testing.T) {
		input := h.fetchFromURL(context.Background(), server.URL+"/valid.txt")
		if input.Err != nil {
			t.Fatal(input.Err)
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
		name        string
		filenames   []string
		maxFiles    int
		extractions int32
	}{
		{"parsing failure is allowed", []string{"broken.pdf"}, 2, 1},
		// An unsupported type is skipped before any processor sees it, and allows.
		{"unsupported type is allowed", []string{"broken.pdf", "unsupported.bin"}, 2, 1},
		// The invariant the unsupported-type case used to carry: a fail-open on the first
		// file does not stop the ones after it from being inspected.
		{"later files are still inspected", []string{"broken.pdf", "also-broken.pdf"}, 2, 2},
		// Inputs past MaxFiles are dropped uninspected instead of failing the request.
		{"inputs over the limit are dropped", []string{"broken.pdf", "also-broken.pdf"}, 1, 1},
	} {
		t.Run(tc.name, func(t *testing.T) {
			processor := &failingFileProcessor{}
			registry := fileprocessor.NewRegistry()
			registry.RegisterWithLimit(processor, 1024*1024)
			h := &ValidationHandler{
				cfg:    &config.Config{File: config.FileConfig{Enabled: true, MaxFiles: tc.maxFiles}},
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
			assertFileAllowed(t, recorder, true)
			if got := processor.calls.Load(); got != tc.extractions {
				t.Fatalf("extraction attempts = %d, want %d", got, tc.extractions)
			}
		})
	}
}

// newGateTestHandler returns a handler whose only supported type is ".pdf" (extraction
// always fails, so any file that reaches inspection is counted and allowed).
func newGateTestHandler(gate policyGate) (*ValidationHandler, *failingFileProcessor) {
	processor := &failingFileProcessor{}
	registry := fileprocessor.NewRegistry()
	registry.RegisterWithLimit(processor, 1024*1024)
	return &ValidationHandler{
		cfg:          &config.Config{File: config.FileConfig{Enabled: true, MaxFiles: 2, URLTimeoutSec: 5}},
		logger:       zap.NewNop(),
		fileRegistry: registry,
		policyGate:   gate,
	}, processor
}

func fileUploadRequest(t *testing.T, filename, content string, fields map[string]string) *http.Request {
	t.Helper()
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	if filename != "" {
		part, err := writer.CreateFormFile("file", filename)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := io.WriteString(part, content); err != nil {
			t.Fatal(err)
		}
	}
	for k, v := range fields {
		if err := writer.WriteField(k, v); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPost, "/api/validate/file", &body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	return req
}

func TestValidateFilePolicyGate(t *testing.T) {
	t.Run("no applicable policies skips inspection entirely", func(t *testing.T) {
		var gateCalls int
		h, processor := newGateTestHandler(func(contextSource, requestHeaders string) (bool, error) {
			gateCalls++
			if contextSource != "AGENTIC" {
				t.Errorf("contextSource = %q, want AGENTIC", contextSource)
			}
			return false, nil
		})
		// A URL input proves nothing is fetched either: this server must never be hit.
		server := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {
			t.Error("gated request must not fetch URL inputs")
		}))
		defer server.Close()

		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		c.Request = fileUploadRequest(t, "doc.pdf", "content", map[string]string{
			"contextSource": "AGENTIC", "url": server.URL + "/doc.pdf",
		})
		h.ValidateFile(c)

		assertFileAllowed(t, recorder, true)
		if gateCalls != 1 {
			t.Fatalf("gate calls = %d, want 1", gateCalls)
		}
		if got := processor.calls.Load(); got != 0 {
			t.Fatalf("gated request inspected %d files, want 0", got)
		}
	})

	t.Run("applicable policies inspect as usual", func(t *testing.T) {
		h, processor := newGateTestHandler(func(string, string) (bool, error) { return true, nil })
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		c.Request = fileUploadRequest(t, "doc.pdf", "content", nil)
		h.ValidateFile(c)

		assertFileAllowed(t, recorder, true)
		if got := processor.calls.Load(); got != 1 {
			t.Fatalf("extraction attempts = %d, want 1", got)
		}
	})

	t.Run("gate failure inspects rather than assuming no policies", func(t *testing.T) {
		h, processor := newGateTestHandler(func(string, string) (bool, error) {
			return false, errors.New("policy fetch failed")
		})
		recorder := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(recorder)
		c.Request = fileUploadRequest(t, "doc.pdf", "content", nil)
		h.ValidateFile(c)

		assertFileAllowed(t, recorder, true)
		if got := processor.calls.Load(); got != 1 {
			t.Fatalf("extraction attempts = %d, want 1", got)
		}
	})
}

// The gate resolves policies from the header map this endpoint synthesizes, so the identity
// headers a user-targeted policy matches on must survive into it.
func TestFileRequestHeaders(t *testing.T) {
	h := &ValidationHandler{logger: zap.NewNop()}

	t.Run("installer email header is carried over", func(t *testing.T) {
		c, _ := gin.CreateTestContext(httptest.NewRecorder())
		c.Request = fileUploadRequest(t, "", "", map[string]string{"hostname": "api.example.com"})
		c.Request.Header.Set("x-akto-installer-user_email", "someone@example.com")

		var headers map[string]string
		if err := json.Unmarshal([]byte(h.fileRequestHeaders(c)), &headers); err != nil {
			t.Fatal(err)
		}
		if headers["Host"] != "api.example.com" {
			t.Fatalf("Host = %q", headers["Host"])
		}
		if got := session.ExtractInstallerUserEmail(headers); got != "someone@example.com" {
			t.Fatalf("installer email = %q, want someone@example.com", got)
		}
	})

	t.Run("explicit requestHeaders field wins", func(t *testing.T) {
		c, _ := gin.CreateTestContext(httptest.NewRecorder())
		raw := `{"Host":"forwarded.example.com"}`
		c.Request = fileUploadRequest(t, "", "", map[string]string{
			"requestHeaders": raw, "hostname": "ignored.example.com",
		})
		if got := h.fileRequestHeaders(c); got != raw {
			t.Fatalf("headers = %q, want %q", got, raw)
		}
	})

	t.Run("no identity available yields empty headers", func(t *testing.T) {
		c, _ := gin.CreateTestContext(httptest.NewRecorder())
		c.Request = fileUploadRequest(t, "", "", nil)
		if got := h.fileRequestHeaders(c); got != "" {
			t.Fatalf("headers = %q, want empty", got)
		}
	})
}

// capInputs backs all three limits (files, URLs, chunks): over-limit inputs are truncated,
// never rejected.
func TestCapInputs(t *testing.T) {
	items := []string{"a", "b", "c"}
	for _, tc := range []struct {
		name  string
		limit int
		want  int
	}{
		{"under limit passes through", 5, 3},
		{"at limit passes through", 3, 3},
		{"over limit truncates", 2, 2},
		{"zero limit keeps nothing", 0, 0},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := capInputs(zap.NewNop(), items, tc.limit, "file"); len(got) != tc.want {
				t.Fatalf("len = %d, want %d", len(got), tc.want)
			}
		})
	}
}

// Uploads and URLs used to be mutually exclusive (a 400); now both are inspected, so that
// adding a stray url field cannot switch inspection off for the uploads beside it.
func TestValidateFileInspectsUploadsAndURLsTogether(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = io.WriteString(w, "remote content")
	}))
	defer server.Close()

	h, processor := newGateTestHandler(func(string, string) (bool, error) { return true, nil })
	recorder := httptest.NewRecorder()
	c, _ := gin.CreateTestContext(recorder)
	c.Request = fileUploadRequest(t, "local.pdf", "local content", map[string]string{
		"url": server.URL + "/remote.pdf",
	})
	h.ValidateFile(c)

	assertFileAllowed(t, recorder, true)
	if got := processor.calls.Load(); got != 2 {
		t.Fatalf("extraction attempts = %d, want 2 (upload + URL)", got)
	}
}
