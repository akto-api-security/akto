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

type registryEntry struct {
	processor FileProcessor
	maxBytes  int
}

// Registry maps file extensions to processors. Concurrent-safe.
type Registry struct {
	mu    sync.RWMutex
	byExt map[string]registryEntry
}

func NewRegistry() *Registry {
	return &Registry{byExt: make(map[string]registryEntry)}
}

func (r *Registry) RegisterWithLimit(p FileProcessor, maxBytes int) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, ext := range p.SupportedExtensions() {
		r.byExt[normalizeExt(ext)] = registryEntry{processor: p, maxBytes: maxBytes}
	}
}

func (r *Registry) Get(ext string) FileProcessor {
	r.mu.RLock()
	defer r.mu.RUnlock()
	e, ok := r.byExt[normalizeExt(ext)]
	if !ok {
		return nil
	}
	return e.processor
}

func (r *Registry) MaxBytesForExt(ext string) int {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.byExt[normalizeExt(ext)].maxBytes
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

func (r *Registry) MaxPerFileBytes() int {
	r.mu.RLock()
	defer r.mu.RUnlock()
	max := 0
	for _, e := range r.byExt {
		if e.maxBytes > max {
			max = e.maxBytes
		}
	}
	return max
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

func DefaultRegistry(maxTextFileBytes int) *Registry {
	r := NewRegistry()
	r.RegisterWithLimit(TxtProcessor{}, maxTextFileBytes)
	r.RegisterWithLimit(DocumentProcessor{}, maxTextFileBytes)
	return r
}
