package fileprocessor

import (
	"context"
	"io"
	"strings"
)

// TxtProcessor extracts plain text from text-based files (UTF-8).
// Works entirely in memory -- no temp file needed.
type TxtProcessor struct{}

func (TxtProcessor) SupportedExtensions() []string {
	return []string{
		".txt", ".md", ".csv", ".tsv", ".json",
		".xml", ".yaml", ".yml", ".log", ".cfg", ".ini",
	}
}

// ExtractContent reads all bytes from r and returns the content as a trimmed UTF-8 string.
func (TxtProcessor) ExtractContent(_ context.Context, r io.Reader, _ string) (string, error) {
	data, err := io.ReadAll(r)
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(string(data)), nil
}
