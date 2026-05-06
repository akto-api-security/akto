package fileprocessor

import (
	"bytes"
	"context"
	"fmt"
	"image"
	_ "image/gif"
	"image/jpeg"
	_ "image/png"
	"io"

	"github.com/akto-api-security/guardrails-service/pkg/mediaprovider"
	"golang.org/x/image/draw"
	_ "golang.org/x/image/tiff"
	_ "golang.org/x/image/webp"
)

const maxImageDimension = 2048

// Reject images whose raw pixel count would exceed this (prevents decompression bombs).
const maxPixelCount = 4096 * 4096

type ImageProcessor struct {
	ocr      mediaprovider.OCRProvider
	maxBytes int
}

func NewImageProcessor(ocr mediaprovider.OCRProvider, maxBytes int) *ImageProcessor {
	return &ImageProcessor{ocr: ocr, maxBytes: maxBytes}
}

func (p *ImageProcessor) SupportedExtensions() []string {
	return []string{".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff"}
}

func (p *ImageProcessor) ExtractContent(ctx context.Context, r io.Reader, _ string) (string, error) {
	data, err := io.ReadAll(io.LimitReader(r, int64(p.maxBytes)+1))
	if err != nil {
		return "", fmt.Errorf("read image: %w", err)
	}
	if len(data) > p.maxBytes {
		return "", fmt.Errorf("image too large: exceeds %s limit", FormatBytes(p.maxBytes))
	}

	resized, err := downsampleIfNeeded(data)
	if err != nil {
		return "", fmt.Errorf("image resize: %w", err)
	}
	return p.ocr.ExtractText(ctx, bytes.NewReader(resized))
}

// downsampleIfNeeded scales images exceeding maxImageDimension, preserving aspect ratio.
// It validates pixel dimensions before full decode to prevent decompression bombs.
func downsampleIfNeeded(data []byte) ([]byte, error) {
	cfg, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return data, nil
	}

	if cfg.Width*cfg.Height > maxPixelCount {
		return nil, fmt.Errorf("image dimensions too large: %dx%d (%d pixels, max %d)", cfg.Width, cfg.Height, cfg.Width*cfg.Height, maxPixelCount)
	}

	if cfg.Width <= maxImageDimension && cfg.Height <= maxImageDimension {
		return data, nil
	}

	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode: %w", err)
	}

	newW, newH := fitDimensions(cfg.Width, cfg.Height, maxImageDimension)
	dst := image.NewRGBA(image.Rect(0, 0, newW, newH))
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, src.Bounds(), draw.Over, nil)

	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, dst, &jpeg.Options{Quality: 85}); err != nil {
		return nil, fmt.Errorf("encode resized image: %w", err)
	}
	return buf.Bytes(), nil
}

func fitDimensions(w, h, maxDim int) (int, int) {
	if w >= h {
		return maxDim, h * maxDim / w
	}
	return w * maxDim / h, maxDim
}
