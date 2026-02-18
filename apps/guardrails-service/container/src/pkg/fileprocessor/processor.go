package fileprocessor

import (
	"context"
	"fmt"
	"io"
	"net/url"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"sync"
)

// FileProcessor extracts text content from an uploaded file for guardrail validation.
// Each implementation encapsulates its own I/O strategy (in-memory, temp file, etc.).
// The handler passes an io.Reader and the normalized file extension.
type FileProcessor interface {
	SupportedExtensions() []string
	ExtractContent(ctx context.Context, r io.Reader, ext string) (string, error)
}

// Registry maps file extensions to processors. Safe for concurrent use.
type Registry struct {
	mu    sync.RWMutex
	byExt map[string]FileProcessor
}

// NewRegistry returns an empty registry.
func NewRegistry() *Registry {
	return &Registry{byExt: make(map[string]FileProcessor)}
}

// Register adds a processor for each of its supported extensions.
func (r *Registry) Register(p FileProcessor) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, ext := range p.SupportedExtensions() {
		r.byExt[normalizeExt(ext)] = p
	}
}

// Get returns the processor for the given file extension, or nil if unsupported.
// ext may be with or without leading dot (e.g. "txt" or ".txt").
func (r *Registry) Get(ext string) FileProcessor {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.byExt[normalizeExt(ext)]
}

// SupportedExtensions returns a sorted list of allowed extensions (e.g. for error messages).
func (r *Registry) SupportedExtensions() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]string, 0, len(r.byExt))
	for ext := range r.byExt {
		out = append(out, ext)
	}
	sort.Strings(out)
	return out
}

func normalizeExt(ext string) string {
	ext = strings.ToLower(strings.TrimSpace(ext))
	if ext != "" && ext[0] != '.' {
		return "." + ext
	}
	return ext
}

// ExtensionFromFilename returns the extension from a filename (e.g. "doc.pdf" -> ".pdf").
func ExtensionFromFilename(name string) string {
	return normalizeExt(filepath.Ext(name))
}

// ExtensionFromURL parses a URL and returns the file extension from its path component.
// E.g. "https://bucket.s3.amazonaws.com/reports/q4.pdf?X-Amz-Signature=abc" -> ".pdf".
// Returns an error if the URL is invalid or has no recognizable file extension.
func ExtensionFromURL(rawURL string) (string, error) {
	parsed, err := url.Parse(rawURL)
	if err != nil {
		return "", fmt.Errorf("invalid URL: %w", err)
	}
	base := path.Base(parsed.Path)
	if base == "" || base == "." || base == "/" {
		return "", fmt.Errorf("URL path has no filename: %s", rawURL)
	}
	ext := normalizeExt(filepath.Ext(base))
	if ext == "" || ext == "." {
		return "", fmt.Errorf("URL path has no file extension: %s", rawURL)
	}
	return ext, nil
}

// DefaultRegistry returns a registry with all built-in processors registered.
func DefaultRegistry() *Registry {
	r := NewRegistry()
	r.Register(TxtProcessor{})
	r.Register(TabulaProcessor{})
	return r
}
