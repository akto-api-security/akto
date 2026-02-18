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

	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"github.com/akto-api-security/mcp-endpoint-shield/mcp"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
)

const chunkRetryDelay = 200 * time.Millisecond

// urlFetchClient is a shared HTTP client for fetching files from URLs.
// Redirects are disabled to prevent SSRF via open-redirect chains.
var urlFetchClient = &http.Client{
	CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

type fileInput struct {
	Reader   io.ReadCloser
	Filename string
}

type chunkResult struct {
	Result *mcp.ValidationResult
	Err    error
}

// fileResult holds the validation outcome for a single file.
type fileResult struct {
	Filename         string
	Allowed          bool
	Reason           string
	TotalChunks      int
	FailedChunkIndex int
	ChunkResults     []*chunkResult
	FailedResult     *mcp.ValidationResult
}

// ValidateFile handles POST /api/validate/file.
// Accepts multiple multipart "file" uploads OR multiple "url" form fields (mutually exclusive).
// Files are processed sequentially with fail-fast: stops on the first blocked file.
func (h *ValidationHandler) ValidateFile(c *gin.Context) {
	if h.cfg == nil {
		h.logger.Error("File validation config is nil")
		c.JSON(http.StatusInternalServerError, gin.H{"error": "server configuration error"})
		return
	}

	maxSize := int64(h.cfg.FileValidateMaxSizeBytes)
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSize)

	contextSource := strings.TrimSpace(c.PostForm("contextSource"))

	inputs, statusCode, err := h.resolveInputs(c)
	if form := c.Request.MultipartForm; form != nil {
		defer form.RemoveAll()
	}
	if err != nil {
		h.logger.Warn("Failed to resolve file inputs", zap.Error(err))
		c.JSON(statusCode, gin.H{"error": err.Error()})
		return
	}

	ctx := c.Request.Context()
	var fileResults []*fileResult

	for _, input := range inputs {
		fr := h.validateSingleFile(ctx, input, contextSource)
		fileResults = append(fileResults, fr)
		input.Reader.Close()

		if !fr.Allowed {
			break
		}
	}
	// Close remaining unprocessed inputs (skipped due to fail-fast).
	for i := len(fileResults); i < len(inputs); i++ {
		inputs[i].Reader.Close()
	}

	h.writeMultiFileResponse(c, fileResults, len(inputs))
}

// validateSingleFile runs the full pipeline (extract, sanitize, chunk, validate)
// for one file and returns the result.
func (h *ValidationHandler) validateSingleFile(ctx context.Context, input *fileInput, contextSource string) *fileResult {
	fr := &fileResult{Filename: input.Filename, Allowed: true}

	ext := fileprocessor.ExtensionFromFilename(input.Filename)
	processor := h.fileRegistry.Get(ext)
	if processor == nil {
		fr.Allowed = false
		fr.Reason = "unsupported file type; allowed: " + strings.Join(h.fileRegistry.SupportedExtensions(), ", ")
		return fr
	}

	rawText, err := processor.ExtractContent(ctx, input.Reader, ext)
	if err != nil {
		h.logger.Warn("Content extraction failed", zap.Error(err), zap.String("file", input.Filename))
		fr.Allowed = false
		fr.Reason = "failed to extract content: " + err.Error()
		return fr
	}

	text := fileprocessor.SanitizeText(rawText)
	rawText = "" // allow GC to reclaim the unsanitized copy
	if strings.TrimSpace(text) == "" {
		fr.Allowed = false
		fr.Reason = "no text could be extracted from the file"
		return fr
	}

	chunks := fileprocessor.ChunkWordBoundary(text, h.cfg.FileValidateChunkSize, h.cfg.FileValidateChunkOverlap)
	if len(chunks) == 0 {
		fr.Allowed = false
		fr.Reason = "no content to validate"
		return fr
	}
	if len(chunks) > h.cfg.FileValidateMaxChunks {
		fr.Allowed = false
		fr.Reason = fmt.Sprintf("file content too large: produced %d chunks (max %d)", len(chunks), h.cfg.FileValidateMaxChunks)
		return fr
	}

	h.logger.Info("Validating file",
		zap.String("filename", input.Filename),
		zap.Int("extractedChars", len(text)),
		zap.Int("totalChunks", len(chunks)),
		zap.Int("concurrency", h.cfg.FileValidateMaxConcurrent))

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
			zap.Int("chunkSize", h.cfg.FileValidateChunkSize),
			zap.Int("chunkOverlap", h.cfg.FileValidateChunkOverlap))
	}

	results := h.validateChunks(ctx, chunks, contextSource)
	fr.TotalChunks = len(chunks)
	fr.ChunkResults = results

	for i, r := range results {
		if r.Err != nil {
			fr.Allowed = false
			fr.Reason = "validation error after retries: " + r.Err.Error()
			fr.FailedChunkIndex = i + 1
			return fr
		}
		if !r.Result.Allowed {
			fr.Allowed = false
			fr.Reason = r.Result.Reason
			if fr.Reason == "" {
				fr.Reason = "content blocked by guardrail policy"
			}
			fr.FailedChunkIndex = i + 1
			fr.FailedResult = r.Result
			return fr
		}
	}
	return fr
}

// resolveInputs collects all file uploads or URL fields from the request.
// Files and URLs are mutually exclusive; the total count is capped by config.
func (h *ValidationHandler) resolveInputs(c *gin.Context) ([]*fileInput, int, error) {
	form, formErr := c.MultipartForm()

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

	hasFiles := len(fileHeaders) > 0
	hasURLs := len(rawURLs) > 0

	if hasFiles && hasURLs {
		return nil, http.StatusBadRequest, fmt.Errorf("provide either 'file' uploads or 'url' fields, not both")
	}

	if !hasFiles && !hasURLs {
		if formErr != nil && isBodyTooLarge(formErr) {
			return nil, http.StatusRequestEntityTooLarge, fmt.Errorf("request body exceeds maximum size of %s", formatBytes(h.cfg.FileValidateMaxSizeBytes))
		}
		return nil, http.StatusBadRequest, fmt.Errorf("provide at least one 'file' upload or 'url' field")
	}

	maxFiles := h.cfg.FileValidateMaxFiles
	if maxFiles <= 0 {
		maxFiles = 1
	}

	if hasFiles {
		if len(fileHeaders) > maxFiles {
			return nil, http.StatusBadRequest, fmt.Errorf("too many files: %d provided (max %d)", len(fileHeaders), maxFiles)
		}
		return h.openUploads(fileHeaders)
	}

	if len(rawURLs) > maxFiles {
		return nil, http.StatusBadRequest, fmt.Errorf("too many URLs: %d provided (max %d)", len(rawURLs), maxFiles)
	}
	return h.fetchFromURLs(c.Request.Context(), rawURLs)
}

func (h *ValidationHandler) openUploads(headers []*multipart.FileHeader) ([]*fileInput, int, error) {
	inputs := make([]*fileInput, 0, len(headers))
	for _, fh := range headers {
		fi, statusCode, err := openUpload(fh)
		if err != nil {
			closeInputs(inputs)
			return nil, statusCode, err
		}
		inputs = append(inputs, fi)
	}
	return inputs, 0, nil
}

func (h *ValidationHandler) fetchFromURLs(ctx context.Context, rawURLs []string) ([]*fileInput, int, error) {
	inputs := make([]*fileInput, 0, len(rawURLs))
	for _, rawURL := range rawURLs {
		fi, statusCode, err := h.fetchFromURL(ctx, rawURL)
		if err != nil {
			closeInputs(inputs)
			return nil, statusCode, err
		}
		inputs = append(inputs, fi)
	}
	return inputs, 0, nil
}

func closeInputs(inputs []*fileInput) {
	for _, fi := range inputs {
		fi.Reader.Close()
	}
}

func openUpload(fh *multipart.FileHeader) (*fileInput, int, error) {
	src, err := fh.Open()
	if err != nil {
		return nil, http.StatusBadRequest, fmt.Errorf("unable to read uploaded file: %w", err)
	}
	return &fileInput{Reader: src, Filename: fh.Filename}, 0, nil
}

func (h *ValidationHandler) fetchFromURL(ctx context.Context, rawURL string) (*fileInput, int, error) {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return nil, http.StatusBadRequest, fmt.Errorf("invalid URL: %w", err)
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return nil, http.StatusBadRequest, fmt.Errorf("only http and https URLs are supported")
	}

	ext, err := fileprocessor.ExtensionFromURL(rawURL)
	if err != nil {
		return nil, http.StatusBadRequest, err
	}
	if h.fileRegistry.Get(ext) == nil {
		return nil, http.StatusBadRequest, fmt.Errorf("unsupported file type from URL; allowed: %s", strings.Join(h.fileRegistry.SupportedExtensions(), ", "))
	}

	timeout := time.Duration(h.cfg.FileValidateURLTimeoutSec) * time.Second
	fetchCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	req, err := http.NewRequestWithContext(fetchCtx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, http.StatusBadRequest, fmt.Errorf("invalid URL: %w", err)
	}

	resp, err := urlFetchClient.Do(req)
	if err != nil {
		return nil, http.StatusBadGateway, fmt.Errorf("failed to fetch URL: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		return nil, http.StatusBadGateway, fmt.Errorf("URL returned HTTP %d", resp.StatusCode)
	}

	maxSize := int64(h.cfg.FileValidateMaxSizeBytes)
	body := &sizeLimitedReader{
		Reader: io.LimitReader(resp.Body, maxSize+1),
		Closer: resp.Body,
		limit:  maxSize,
	}

	filename := filenameFromPath(parsed.Path, ext)
	return &fileInput{Reader: body, Filename: filename}, 0, nil
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

// sizeLimitedReader wraps an io.LimitReader to produce a clear error when
// the response body exceeds the configured maximum.
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
		return n, fmt.Errorf("file from URL exceeds maximum size of %s", formatBytes(int(r.limit)))
	}
	return n, err
}

// validateChunks validates all chunks in parallel with bounded concurrency and per-chunk retry.
func (h *ValidationHandler) validateChunks(ctx context.Context, chunks []string, contextSource string) []*chunkResult {
	results := make([]*chunkResult, len(chunks))
	g, gCtx := errgroup.WithContext(ctx)
	g.SetLimit(h.cfg.FileValidateMaxConcurrent)

	for i, chunk := range chunks {
		g.Go(func() error {
			payload := marshalPromptPayload(chunk)
			results[i] = h.validateWithRetry(gCtx, payload, contextSource)
			return nil
		})
	}
	_ = g.Wait()
	return results
}

func (h *ValidationHandler) validateWithRetry(ctx context.Context, payload, contextSource string) *chunkResult {
	maxRetries := h.cfg.FileValidateMaxRetries
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
		result, err := h.validatorService.ValidateRequest(ctx, payload, contextSource, "", "")
		if err != nil {
			lastErr = err
			continue
		}
		return &chunkResult{Result: result}
	}
	return &chunkResult{Err: lastErr}
}

// writeMultiFileResponse builds the JSON response with per-file results.
func (h *ValidationHandler) writeMultiFileResponse(c *gin.Context, results []*fileResult, totalFiles int) {
	overallAllowed := true
	failedFileIndex := 0

	fileEntries := make([]gin.H, len(results))
	for i, fr := range results {
		entry := gin.H{
			"filename":         fr.Filename,
			"allowed":          fr.Allowed,
			"totalChunks":      fr.TotalChunks,
			"failedChunkIndex": fr.FailedChunkIndex,
		}
		if !fr.Allowed {
			entry["reason"] = fr.Reason
			if overallAllowed {
				overallAllowed = false
				failedFileIndex = i + 1
			}
		}
		if fr.FailedResult != nil {
			entry["modified"] = fr.FailedResult.Modified
			entry["modifiedPayload"] = fr.FailedResult.ModifiedPayload
		}
		if fr.TotalChunks > 1 && fr.ChunkResults != nil {
			entry["chunkResults"] = buildChunkDetails(fr.ChunkResults)
		}
		fileEntries[i] = entry
	}

	c.JSON(http.StatusOK, gin.H{
		"allowed":         overallAllowed,
		"totalFiles":      totalFiles,
		"failedFileIndex": failedFileIndex,
		"fileResults":     fileEntries,
	})
}

func buildChunkDetails(results []*chunkResult) []gin.H {
	details := make([]gin.H, len(results))
	for i, r := range results {
		entry := gin.H{"chunkIndex": i + 1, "allowed": true}
		if r.Err != nil {
			entry["allowed"] = false
			entry["error"] = r.Err.Error()
		} else {
			entry["allowed"] = r.Result.Allowed
			if r.Result.Reason != "" {
				entry["reason"] = r.Result.Reason
			}
		}
		details[i] = entry
	}
	return details
}

func marshalPromptPayload(content string) string {
	b, err := json.Marshal(map[string]string{"prompt": content})
	if err != nil {
		return content
	}
	return string(b)
}

// isBodyTooLarge checks whether the error from multipart parsing indicates the
// request body exceeded the MaxBytesReader limit.
func isBodyTooLarge(err error) bool {
	if err == nil {
		return false
	}
	msg := err.Error()
	return strings.Contains(msg, "http: request body too large") ||
		strings.Contains(msg, "max bytes")
}

func formatBytes(n int) string {
	const (
		kb = 1024
		mb = 1024 * kb
	)
	switch {
	case n >= mb:
		return fmt.Sprintf("%d MB", n/mb)
	case n >= kb:
		return fmt.Sprintf("%d KB", n/kb)
	default:
		return fmt.Sprintf("%d bytes", n)
	}
}
