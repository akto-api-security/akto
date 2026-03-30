package mediaprovider

import (
	"context"
	"io"
)

// OCRProvider extracts text from an image via an external OCR API.
type OCRProvider interface {
	ExtractText(ctx context.Context, r io.Reader) (string, error)
}
