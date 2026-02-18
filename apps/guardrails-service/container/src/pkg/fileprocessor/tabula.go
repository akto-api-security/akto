package fileprocessor

import (
	"context"
	"io"
	"strings"

	"github.com/tsawler/tabula"
)

// TabulaProcessor extracts plain text from PDF, Office, eBook, and HTML documents
// using tsawler/tabula. Tabula auto-detects the format from the file extension.
// Library: github.com/tsawler/tabula, MIT, pure Go.
type TabulaProcessor struct{}

func (TabulaProcessor) SupportedExtensions() []string {
	return []string{".pdf", ".docx", ".xlsx", ".pptx", ".odt", ".html", ".htm", ".epub"}
}

func (TabulaProcessor) ExtractContent(_ context.Context, r io.Reader, ext string) (string, error) {
	return withTempFile(r, ext, func(path string) (string, error) {
		text, _, err := tabula.Open(path).Text()
		if err != nil {
			return "", err
		}
		return strings.TrimSpace(text), nil
	})
}
