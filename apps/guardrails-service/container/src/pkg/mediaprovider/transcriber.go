package mediaprovider

import (
	"context"
	"io"
)

// TranscriptionProvider converts audio into text via an external speech-to-text API.
type TranscriptionProvider interface {
	Transcribe(ctx context.Context, r io.Reader, format string) (string, error)
}
