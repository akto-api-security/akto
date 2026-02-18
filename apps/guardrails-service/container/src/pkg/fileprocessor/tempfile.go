package fileprocessor

import (
	"fmt"
	"io"
	"os"
)

// withTempFile buffers an io.Reader to a temporary file with the given extension,
// invokes fn with the file path, and guarantees cleanup regardless of outcome.
// The extension is required so that format-detecting libraries (e.g. tabula)
// can identify the file type from the path.
// Recovers from panics in fn to prevent corrupt files from crashing the process.
func withTempFile(r io.Reader, ext string, fn func(path string) (string, error)) (result string, err error) {
	tmp, tmpErr := os.CreateTemp("", "guardrails-*"+ext)
	if tmpErr != nil {
		return "", fmt.Errorf("create temp file: %w", tmpErr)
	}
	defer os.Remove(tmp.Name())

	if _, copyErr := io.Copy(tmp, r); copyErr != nil {
		_ = tmp.Close()
		return "", fmt.Errorf("buffer to temp file: %w", copyErr)
	}
	_ = tmp.Close()

	defer func() {
		if p := recover(); p != nil {
			err = fmt.Errorf("file processing panicked: %v", p)
		}
	}()

	return fn(tmp.Name())
}
