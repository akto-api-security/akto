package handlers

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"github.com/akto-api-security/guardrails-service/pkg/session"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
)

const chunkRetryDelay = 200 * time.Millisecond

// Redirects disabled to prevent SSRF via open-redirect chains.
var urlFetchClient = &http.Client{
	CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

type fileInput struct {
	Reader   io.ReadCloser
	Filename string
	Err      error
}

type chunkResult struct {
	Result *mcp.ValidationResult
	Err    error
}

type fileResult struct {
	Filename         string
	Allowed          bool
	Reason           string
	TotalChunks      int
	FailedChunkIndex int
	ChunkResults     []*chunkResult
	FailedResult     *mcp.ValidationResult
}

// ValidateFile handles POST /api/validate/file (multipart "file" uploads and/or "url" fields).
func (h *ValidationHandler) ValidateFile(c *gin.Context) {
	defer func() {
		if p := recover(); p != nil {
			h.logger.Error("File validation panicked; allowing file", zap.Any("panic", p))
			if !c.Writer.Written() {
				allowFile(c)
			}
		}
	}()
	if h.cfg == nil {
		h.logger.Error("File validation config is nil; allowing file")
		allowFile(c)
		return
	}

	if !h.cfg.File.Enabled {
		allowFile(c)
		return
	}

	maxBody := int64(h.fileRegistry.MaxPerFileBytes()) * int64(h.cfg.File.MaxFiles)
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxBody)

	form, formErr := c.MultipartForm()
	if form != nil {
		defer form.RemoveAll()
	}
	if formErr != nil {
		h.logger.Warn("Failed to parse file inputs; allowing request",
			zap.String("skipReason", "multipart-parse-failed"), zap.Error(formErr))
		allowFile(c)
		return
	}

	contextSource := strings.TrimSpace(c.PostForm("contextSource"))

	requestHeaders := h.fileRequestHeaders(c)

	sessionID, requestID := session.ExtractSessionIDsFromRequest(c.Request, requestHeaders)

	// Pre-flight policy gate: with no policy applicable to this caller there is nothing
	// to enforce, so skip fetching, extracting and inspecting the content entirely.
	if h.policyGate != nil {
		applicable, err := h.policyGate(contextSource, requestHeaders)
		if err != nil {
			// Could not tell — inspect rather than assume there is nothing to enforce.
			h.logger.Warn("Policy gate failed; inspecting content",
				zap.String("sessionID", sessionID), zap.Error(err))
		} else if !applicable {
			h.logger.Info("ValidateFile - no applicable policies; allowing without inspection",
				zap.String("contextSource", contextSource),
				zap.String("sessionID", sessionID))
			allowFile(c)
			return
		}
	}

	inputs := h.resolveInputs(c.Request.Context(), form)
	defer closeInputs(inputs)
	if len(inputs) == 0 {
		h.logger.Warn("No file inputs to inspect; allowing request",
			zap.String("skipReason", "no-inputs"), zap.String("sessionID", sessionID))
		allowFile(c)
		return
	}

	h.logger.Info("ValidateFile - received request",
		zap.Int("fileCount", len(inputs)),
		zap.String("contextSource", contextSource),
		zap.String("path", strings.TrimSpace(c.PostForm("path"))),
		zap.String("method", strings.TrimSpace(c.PostForm("method"))),
		zap.String("sessionID", sessionID))

	meta := &models.ValidateRequestParams{
		ContextSource:  contextSource,
		Path:           strings.TrimSpace(c.PostForm("path")),
		Method:         strings.TrimSpace(c.PostForm("method")),
		AktoAccountID:  strings.TrimSpace(c.PostForm("akto_account_id")),
		AktoVxlanID:    strings.TrimSpace(c.PostForm("akto_vxlan_id")),
		IP:             strings.TrimSpace(c.PostForm("ip")),
		RequestHeaders: requestHeaders,
		Time:           strings.TrimSpace(c.PostForm("time")),
		StatusCode:     strings.TrimSpace(c.PostForm("statusCode")),
		Status:         strings.TrimSpace(c.PostForm("status")),
		Tag:            strings.TrimSpace(c.PostForm("tag")),
		Metadata:       strings.TrimSpace(c.PostForm("metadata")),
		Source:         "file",
	}

	ctx := c.Request.Context()
	var fileResults []*fileResult

	for _, input := range inputs {
		fr := h.validateSingleFile(ctx, input, meta, sessionID, requestID)
		fileResults = append(fileResults, fr)

		if !fr.Allowed {
			break
		}
	}

	allowedCount := 0
	for _, fr := range fileResults {
		if fr.Allowed {
			allowedCount++
		}
	}
	h.logger.Info("ValidateFile - completed",
		zap.Int("fileCount", len(inputs)),
		zap.Int("allowedCount", allowedCount),
		zap.String("sessionID", sessionID))
	h.writeMultiFileResponse(c, fileResults)
}

func (h *ValidationHandler) validateSingleFile(ctx context.Context, input *fileInput, meta *models.ValidateRequestParams, sessionID, requestID string) (fr *fileResult) {
	fr = &fileResult{Filename: input.Filename, Allowed: true}
	defer func() {
		if p := recover(); p != nil {
			h.logger.Error("File inspection panicked; allowing uninspected content", zap.Any("panic", p), zap.String("file", input.Filename))
		}
	}()
	if input.Err != nil {
		h.logger.Warn("File unavailable for inspection; allowing file", zap.Error(input.Err), zap.String("file", input.Filename))
		return fr
	}

	ext := fileprocessor.ExtensionFromFilename(input.Filename)
	processor := h.fileRegistry.Get(ext)
	if processor == nil {
		h.logger.Warn("Unsupported file type; allowing uninspected file",
			zap.String("skipReason", "unsupported-file-type"),
			zap.String("file", input.Filename), zap.String("ext", ext),
			zap.Strings("supported", h.fileRegistry.SupportedExtensions()))
		return fr
	}

	rawText, err := processor.ExtractContent(ctx, input.Reader, ext)
	if err != nil {
		// Fail open when parsing fails; there is no extracted content to validate.
		h.logger.Warn("Content extraction failed; allowing file", zap.Error(err), zap.String("file", input.Filename))
		return fr
	}

	text := fileprocessor.SanitizeText(rawText)
	rawText = "" // allow GC to reclaim the unsanitized copy
	if strings.TrimSpace(text) == "" {
		h.logger.Warn("No text extracted; allowing file", zap.String("file", input.Filename))
		return fr
	}

	chunks := fileprocessor.ChunkWordBoundary(text, h.cfg.File.ChunkSize, h.cfg.File.ChunkOverlap)
	if len(chunks) == 0 {
		h.logger.Warn("No content to validate; allowing file", zap.String("file", input.Filename))
		return fr
	}
	if h.cfg.File.MaxChunks > 0 {
		chunks = capInputs(h.logger, chunks, h.cfg.File.MaxChunks, "chunk")
	}

	h.logger.Info("Validating file",
		zap.String("filename", input.Filename),
		zap.Int("extractedChars", len(text)),
		zap.Int("totalChunks", len(chunks)),
		zap.Int("concurrency", h.cfg.File.MaxConcurrent))

	if h.logger.Core().Enabled(zap.DebugLevel) {
		chunkSizes := make([]int, len(chunks))
		totalChars := 0
		for i, ch := range chunks {
			chunkSizes[i] = len(ch)
			totalChars += len(ch)
		}
		h.logger.Debug("Chunk details",
			zap.String("filename", input.Filename),
			zap.Int("totalCharsWithOverlap", totalChars),
			zap.Ints("chunkSizes", chunkSizes),
			zap.Int("chunkSize", h.cfg.File.ChunkSize),
			zap.Int("chunkOverlap", h.cfg.File.ChunkOverlap))
	}

	results := h.validateChunks(ctx, chunks, meta, sessionID, requestID, h.validatorService.ValidateRequest)
	return h.applyFileChunkResults(fr, results)
}

func (h *ValidationHandler) applyFileChunkResults(fr *fileResult, results []*chunkResult) *fileResult {
	fr.TotalChunks = len(results)
	fr.ChunkResults = results

	for i, r := range results {
		if r == nil {
			continue
		}
		if r.Err != nil {
			h.logger.Warn("Chunk validation failed after retries; allowing chunk", zap.Error(r.Err), zap.String("file", fr.Filename), zap.Int("chunk", i+1))
			continue
		}
		if h.chunkStopsFile(r.Result) {
			fr.Allowed = false
			fr.Reason = chunkBlockReason(r.Result)
			fr.FailedChunkIndex = i + 1
			fr.FailedResult = r.Result
			return fr
		}
	}
	return fr
}

// chunkStopsFile reports whether a chunk's verdict fails the whole upload.
//
// A masked chunk counts. This endpoint answers with a verdict and nothing else — it
// discards ModifiedPayload — so allowing a "mask" verdict hands the caller a green light
// on the original file with the sensitive spans still in it. Blocking is the only
// enforcement the response shape can express; see FileConfig.BlockOnRedaction to opt out.
func (h *ValidationHandler) chunkStopsFile(r *mcp.ValidationResult) bool {
	if r == nil {
		return false
	}
	return !r.Allowed || (h.cfg.File.BlockOnRedaction && r.Modified)
}

// chunkBlockReason describes why a chunk failed the upload. A masked chunk carries no
// Reason of its own (Reason is lifted off the blocked response, which a mask never
// builds), so name the behaviour that masked it instead.
func chunkBlockReason(r *mcp.ValidationResult) string {
	if r.Reason != "" {
		return r.Reason
	}
	if r.Allowed && r.Modified {
		if r.Behaviour != "" {
			return "file contains sensitive content redacted by guardrail policy (" + r.Behaviour + ")"
		}
		return "file contains sensitive content redacted by guardrail policy"
	}
	return "content blocked by guardrail policy"
}

func (h *ValidationHandler) fileRequestHeaders(c *gin.Context) string {
	if raw := strings.TrimSpace(c.PostForm("requestHeaders")); raw != "" {
		return raw
	}

	headers := make(map[string]string, 2)
	if hostname := strings.TrimSpace(c.PostForm("hostname")); hostname != "" {
		headers["Host"] = hostname
	}
	session.CopyIdentityHeaders(headers, c.Request.Header)
	if len(headers) == 0 {
		return ""
	}
	b, err := json.Marshal(headers)
	if err != nil {
		h.logger.Warn("Failed to build request headers for file validation", zap.Error(err))
		return ""
	}
	return string(b)
}

func (h *ValidationHandler) resolveInputs(ctx context.Context, form *multipart.Form) []*fileInput {
	var fileHeaders []*multipart.FileHeader
	if form != nil && form.File != nil {
		fileHeaders = form.File["file"]
	}

	var rawURLs []string
	if form != nil && form.Value != nil {
		for _, u := range form.Value["url"] {
			if trimmed := strings.TrimSpace(u); trimmed != "" {
				rawURLs = append(rawURLs, trimmed)
			}
		}
	}

	maxFiles := h.cfg.File.MaxFiles
	if maxFiles <= 0 {
		maxFiles = 1
	}

	// Uploads and URLs are no longer mutually exclusive: inspecting both is strictly safer
	// than rejecting the request, and uploads go first so a URL can never crowd one out.
	inputs := h.openUploads(capInputs(h.logger, fileHeaders, maxFiles, "file"))
	if remaining := maxFiles - len(inputs); remaining > 0 {
		inputs = append(inputs, h.fetchFromURLs(ctx, capInputs(h.logger, rawURLs, remaining, "url"))...)
	} else if len(rawURLs) > 0 {
		h.logger.Warn("URL inputs dropped uninspected; upload count already at the limit",
			zap.String("skipReason", "max-files-exceeded"),
			zap.Int("droppedURLs", len(rawURLs)), zap.Int("maxFiles", maxFiles))
	}
	return inputs
}

// capInputs truncates items to limit, logging whatever it drops uninspected.
func capInputs[T any](logger *zap.Logger, items []T, limit int, kind string) []T {
	if len(items) <= limit {
		return items
	}
	logger.Warn("Inputs dropped uninspected; count over limit",
		zap.String("skipReason", "max-files-exceeded"),
		zap.String("kind", kind),
		zap.Int("provided", len(items)), zap.Int("limit", limit))
	return items[:limit]
}

func (h *ValidationHandler) openUploads(headers []*multipart.FileHeader) []*fileInput {
	inputs := make([]*fileInput, 0, len(headers))
	for _, fh := range headers {
		ext := fileprocessor.ExtensionFromFilename(fh.Filename)
		limit := h.fileRegistry.MaxBytesForExt(ext)
		if limit > 0 && fh.Size > int64(limit) {
			inputs = append(inputs, &fileInput{Filename: fh.Filename, Err: fmt.Errorf(
				"file is too large: %s (max %s)",
				fileprocessor.FormatBytes(int(fh.Size)), fileprocessor.FormatBytes(limit))})
			continue
		}
		inputs = append(inputs, openUpload(fh))
	}
	return inputs
}

func (h *ValidationHandler) fetchFromURLs(ctx context.Context, rawURLs []string) []*fileInput {
	inputs := make([]*fileInput, 0, len(rawURLs))
	for _, rawURL := range rawURLs {
		inputs = append(inputs, h.fetchFromURL(ctx, rawURL))
	}
	return inputs
}

func closeInputs(inputs []*fileInput) {
	for _, fi := range inputs {
		if fi.Reader != nil {
			fi.Reader.Close()
		}
	}
}

func openUpload(fh *multipart.FileHeader) *fileInput {
	src, err := fh.Open()
	if err != nil {
		return &fileInput{Filename: fh.Filename, Err: fmt.Errorf("unable to read uploaded file: %w", err)}
	}
	return &fileInput{Reader: src, Filename: fh.Filename}
}

func (h *ValidationHandler) fetchFromURL(ctx context.Context, rawURL string) *fileInput {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return &fileInput{Err: fmt.Errorf("invalid URL: %w", err)}
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return &fileInput{Err: fmt.Errorf("only http and https URLs are supported, got %q", parsed.Scheme)}
	}

	ext, err := fileprocessor.ExtensionFromURL(rawURL)
	if err != nil {
		return &fileInput{Err: err}
	}
	if h.fileRegistry.Get(ext) == nil {
		return &fileInput{Filename: filenameFromPath(parsed.Path, ext),
			Err: fmt.Errorf("unsupported file type from URL: %q", ext)}
	}

	timeout := time.Duration(h.cfg.File.URLTimeoutSec) * time.Second
	fetchCtx, cancel := context.WithTimeout(ctx, timeout)
	keepOpen := false
	defer func() {
		if !keepOpen {
			cancel()
		}
	}()

	filename := filenameFromPath(parsed.Path, ext)

	req, err := http.NewRequestWithContext(fetchCtx, http.MethodGet, rawURL, nil)
	if err != nil {
		return &fileInput{Filename: filename, Err: fmt.Errorf("invalid URL: %w", err)}
	}

	resp, err := urlFetchClient.Do(req)
	if err != nil {
		return &fileInput{Filename: filename, Err: fmt.Errorf("failed to fetch URL: %w", err)}
	}
	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		return &fileInput{Filename: filename, Err: fmt.Errorf("URL returned HTTP %d", resp.StatusCode)}
	}

	maxSize := int64(h.fileRegistry.MaxBytesForExt(ext))
	body := &sizeLimitedReader{
		Reader: io.LimitReader(resp.Body, maxSize+1),
		Closer: &cancelOnClose{ReadCloser: resp.Body, cancel: cancel},
		limit:  maxSize,
	}
	keepOpen = true // The body must remain readable until extraction finishes.
	return &fileInput{Reader: body, Filename: filename}
}

type cancelOnClose struct {
	io.ReadCloser
	cancel context.CancelFunc
}

func (r *cancelOnClose) Close() error {
	defer r.cancel()
	return r.ReadCloser.Close()
}

func filenameFromPath(urlPath, fallbackExt string) string {
	if idx := strings.LastIndex(urlPath, "/"); idx >= 0 {
		urlPath = urlPath[idx+1:]
	}
	if urlPath == "" {
		return "download" + fallbackExt
	}
	return urlPath
}

// sizeLimitedReader wraps an io.LimitReader; returns a clear error on overflow.
type sizeLimitedReader struct {
	io.Reader
	io.Closer
	limit     int64
	bytesRead int64
}

func (r *sizeLimitedReader) Read(p []byte) (int, error) {
	n, err := r.Reader.Read(p)
	r.bytesRead += int64(n)
	if r.bytesRead > r.limit {
		return n, fmt.Errorf("file from URL exceeds maximum size of %s", fileprocessor.FormatBytes(int(r.limit)))
	}
	return n, err
}

var errChunkBlocked = fmt.Errorf("chunk blocked")

type fileRequestValidator func(context.Context, *models.ValidateRequestParams, string, string) (*mcp.ValidationResult, string, error)

func (h *ValidationHandler) validateChunks(ctx context.Context, chunks []string, meta *models.ValidateRequestParams, sessionID, requestID string, validate fileRequestValidator) []*chunkResult {
	results := make([]*chunkResult, len(chunks))
	g, gCtx := errgroup.WithContext(ctx)
	concurrency := h.cfg.File.MaxConcurrent
	if concurrency <= 0 {
		concurrency = 1
	}
	g.SetLimit(concurrency)

	for i, chunk := range chunks {
		g.Go(func() error {
			if gCtx.Err() != nil {
				return nil
			}
			payload := marshalPromptPayload(chunk)
			results[i] = h.validateWithRetry(gCtx, payload, meta, sessionID, requestID, validate)
			if h.chunkStopsFile(results[i].Result) {
				return errChunkBlocked
			}
			return nil
		})
	}
	_ = g.Wait()
	return results
}

func (h *ValidationHandler) validateWithRetry(ctx context.Context, payload string, meta *models.ValidateRequestParams, sessionID, requestID string, validate fileRequestValidator) (result *chunkResult) {
	defer func() {
		if p := recover(); p != nil {
			result = &chunkResult{Err: fmt.Errorf("chunk validation panicked: %v", p)}
		}
	}()
	params := *meta
	params.RequestPayload = payload
	maxRetries := h.cfg.File.MaxRetries
	var lastErr error
	for attempt := 0; attempt <= maxRetries; attempt++ {
		if attempt > 0 {
			timer := time.NewTimer(chunkRetryDelay)
			select {
			case <-timer.C:
			case <-ctx.Done():
				timer.Stop()
				return &chunkResult{Err: ctx.Err()}
			}
		}
		result, _, err := validate(ctx, &params, sessionID, requestID)
		if err != nil {
			lastErr = err
			continue
		}
		return &chunkResult{Result: result}
	}
	return &chunkResult{Err: lastErr}
}

// fileValidationVerdict is the lowercase-keyed JSON form of the file endpoint's verdict.
// The mcp.ValidationResult library struct marshals with capitalized Go field names
// (Allowed, Reason, ...); this DTO keeps the same fields but the lowercase keys clients
// expect. Only the file endpoint uses it; /validate/request and /validate/response are
// unchanged.
type fileValidationVerdict struct {
	Allowed         bool                 `json:"allowed"`
	Modified        bool                 `json:"modified"`
	ModifiedPayload string               `json:"modifiedPayload"`
	Reason          string               `json:"reason"`
	Metadata        types.ThreatMetadata `json:"metadata"`
	Behaviour       string               `json:"behaviour,omitempty"`
}

func newFileValidationVerdict(r *mcp.ValidationResult) fileValidationVerdict {
	if r == nil {
		return fileValidationVerdict{Allowed: true}
	}
	return fileValidationVerdict{
		Allowed:         r.Allowed,
		Modified:        r.Modified,
		ModifiedPayload: r.ModifiedPayload,
		Reason:          r.Reason,
		Metadata:        r.Metadata,
		Behaviour:       r.Behaviour,
	}
}

func (h *ValidationHandler) writeMultiFileResponse(c *gin.Context, results []*fileResult) {
	c.JSON(http.StatusOK, newFileValidationVerdict(fileVerdict(results)))
}

// fileVerdict collapses the per-file results into a single mcp.ValidationResult so the
// file endpoint answers in the same shape as /validate/request and /validate/response:
// `Allowed` plus, on a block, the `Reason` and `behaviour` that stopped the upload. The
// first blocked file wins (evaluation already stops at it). ModifiedPayload is always
// left empty — this endpoint enforces by blocking and never returns rewritten file
// content (see chunkStopsFile).
func fileVerdict(results []*fileResult) *mcp.ValidationResult {
	for _, fr := range results {
		if fr.Allowed {
			continue
		}
		verdict := &mcp.ValidationResult{Allowed: false, Reason: fr.Reason}
		if fr.FailedResult != nil {
			verdict.Behaviour = fr.FailedResult.Behaviour
			verdict.Modified = fr.FailedResult.Modified
			verdict.Metadata = fr.FailedResult.Metadata
		}
		return verdict
	}
	return &mcp.ValidationResult{Allowed: true}
}

// allowFile writes the fail-open verdict shared by every path that cannot (or need not)
// inspect content, in the same mcp.ValidationResult shape as a validated allow.
func allowFile(c *gin.Context) {
	c.JSON(http.StatusOK, newFileValidationVerdict(nil))
}

func marshalPromptPayload(content string) string {
	b, err := json.Marshal(map[string]string{"prompt": content})
	if err != nil {
		return content
	}
	return string(b)
}
