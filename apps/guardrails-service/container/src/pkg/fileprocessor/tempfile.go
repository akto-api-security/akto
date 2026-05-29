package fileprocessor

import (
	"fmt"
	"io"
	"os"
)

// withTempFile writes r to a temp file (with ext for format detection), calls fn, then cleans up.
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
