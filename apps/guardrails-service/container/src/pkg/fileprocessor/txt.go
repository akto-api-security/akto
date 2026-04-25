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
		// Plain text & config
		".txt", ".md", ".csv", ".tsv", ".log", ".cfg", ".ini", ".env", ".toml", ".properties",
		// Data formats
		".json", ".xml", ".yaml", ".yml", ".jsonl", ".ndjson",
		// Source code
		".py", ".js", ".ts", ".jsx", ".tsx", ".java", ".go", ".rb", ".php",
		".c", ".cpp", ".h", ".hpp", ".cs", ".rs", ".swift", ".kt", ".scala",
		".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
		// Web
		".css", ".scss", ".less", ".graphql", ".gql", ".sql",
		// Misc
		".r", ".m", ".tex", ".rst", ".diff", ".patch",
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
