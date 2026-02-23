package fileprocessor

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"strings"

	"github.com/akto-api-security/guardrails-service/pkg/mediaprovider"
)

type AudioProcessor struct {
	transcriber mediaprovider.TranscriptionProvider
	maxBytes    int
}

func NewAudioProcessor(transcriber mediaprovider.TranscriptionProvider, maxBytes int) *AudioProcessor {
	return &AudioProcessor{transcriber: transcriber, maxBytes: maxBytes}
}

func (p *AudioProcessor) SupportedExtensions() []string {
	return []string{".mp3", ".wav", ".ogg", ".m4a", ".flac", ".aac"}
}

func (p *AudioProcessor) ExtractContent(ctx context.Context, r io.Reader, ext string) (string, error) {
	return transcribeMedia(ctx, r, ext, p.maxBytes, p.transcriber, "audio")
}

func transcribeMedia(ctx context.Context, r io.Reader, ext string, maxBytes int, t mediaprovider.TranscriptionProvider, mediaType string) (string, error) {
	limited := io.LimitReader(r, int64(maxBytes)+1)
	var buf bytes.Buffer
	n, err := io.Copy(&buf, limited)
	if err != nil {
		return "", fmt.Errorf("read %s: %w", mediaType, err)
	}
	if n > int64(maxBytes) {
		return "", fmt.Errorf("%s too large: exceeds %s limit", mediaType, FormatBytes(maxBytes))
	}

	format := strings.TrimPrefix(ext, ".")
	return t.Transcribe(ctx, &buf, format)
}
