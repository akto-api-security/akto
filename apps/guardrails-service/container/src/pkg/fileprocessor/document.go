package fileprocessor

import (
	"context"
	"io"
	"strings"

	"github.com/tsawler/tabula"
)

type DocumentProcessor struct{}

func (DocumentProcessor) SupportedExtensions() []string {
	return []string{".pdf", ".docx", ".xlsx", ".pptx", ".odt", ".html", ".htm", ".epub"}
}

func (DocumentProcessor) ExtractContent(_ context.Context, r io.Reader, ext string) (string, error) {
	return withTempFile(r, ext, func(path string) (string, error) {
		text, _, err := tabula.Open(path).Text()
		if err != nil {
			return "", err
		}
		return strings.TrimSpace(text), nil
	})
}
