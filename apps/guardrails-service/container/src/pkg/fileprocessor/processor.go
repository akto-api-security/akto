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

// FileProcessor extracts text content from a file for guardrail validation.
type FileProcessor interface {
	SupportedExtensions() []string
	ExtractContent(ctx context.Context, r io.Reader, ext string) (string, error)
}

// Registry maps file extensions to processors. Concurrent-safe.
type Registry struct {
	mu    sync.RWMutex
	byExt map[string]FileProcessor
}

func NewRegistry() *Registry {
	return &Registry{byExt: make(map[string]FileProcessor)}
}

func (r *Registry) Register(p FileProcessor) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, ext := range p.SupportedExtensions() {
		r.byExt[normalizeExt(ext)] = p
	}
}

func (r *Registry) Get(ext string) FileProcessor {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.byExt[normalizeExt(ext)]
}

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

func ExtensionFromFilename(name string) string {
	return normalizeExt(filepath.Ext(name))
}

// ExtensionFromURL extracts the file extension from a URL's path (ignoring query params).
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

func DefaultRegistry() *Registry {
	r := NewRegistry()
	r.Register(TxtProcessor{})
	r.Register(DocumentProcessor{})
	return r
}
