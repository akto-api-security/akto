package fileprocessor

import (
	"context"
	"io"

	"github.com/akto-api-security/guardrails-service/pkg/mediaprovider"
)

type VideoProcessor struct {
	transcriber mediaprovider.TranscriptionProvider
	maxBytes    int
}

func NewVideoProcessor(transcriber mediaprovider.TranscriptionProvider, maxBytes int) *VideoProcessor {
	return &VideoProcessor{transcriber: transcriber, maxBytes: maxBytes}
}

func (p *VideoProcessor) SupportedExtensions() []string {
	return []string{".mp4", ".mov", ".webm", ".avi", ".mkv"}
}

func (p *VideoProcessor) ExtractContent(ctx context.Context, r io.Reader, ext string) (string, error) {
	return transcribeMedia(ctx, r, ext, p.maxBytes, p.transcriber, "video")
}
