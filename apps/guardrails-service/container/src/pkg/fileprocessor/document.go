package fileprocessor

import (
	"context"
	"fmt"
	"io"
	"strings"
	"sync"

	"github.com/akto-api-security/guardrails-service/pkg/mediaprovider"
	"github.com/tsawler/tabula"
)

// ocrConcurrency bounds the OCR calls one document makes at a time; each is a vision-model call.
const ocrConcurrency = 4

// DocumentProcessor extracts a document's text layer and, given an OCR provider, the text in the
// images embedded in it: a scanned or "printed to PDF" page, a screenshot pasted into a deck or a
// sheet. Without a provider, or past maxImages, the images are not read and ExtractContent says so
// with ErrIncomplete.
type DocumentProcessor struct {
	ocr           mediaprovider.OCRProvider
	maxImages     int
	maxImageBytes int
}

func NewDocumentProcessor(ocr mediaprovider.OCRProvider, maxImages, maxImageBytes int) *DocumentProcessor {
	return &DocumentProcessor{ocr: ocr, maxImages: maxImages, maxImageBytes: maxImageBytes}
}

func (*DocumentProcessor) SupportedExtensions() []string {
	return []string{".pdf", ".docx", ".xlsx", ".pptx", ".odt", ".html", ".htm", ".epub"}
}

// ExtractContent returns the text layer followed by the text OCR read from the embedded images.
// When some images were not read, the text it has is returned together with ErrIncomplete.
func (p *DocumentProcessor) ExtractContent(ctx context.Context, r io.Reader, ext string) (string, error) {
	// Without OCR one image is enough to know the document can't be fully read.
	limit := p.maxImages
	if p.ocr == nil {
		limit = 0
	}
	var images embeddedImages
	text, err := withTempFile(r, ext, func(path string) (string, error) {
		text, _, err := tabula.Open(path).Text()
		if err != nil {
			return "", err
		}
		images = collectImages(path, ext, limit, p.maxImageBytes)
		return strings.TrimSpace(text), nil
	})
	if err != nil {
		return "", err
	}

	read, failed := p.readImages(ctx, images.data)
	if len(read) > 0 {
		text = strings.TrimSpace(strings.Join(append([]string{text}, read...), "\n\n"))
	}
	if images.unreadable > 0 || failed > 0 || images.truncated {
		return text, fmt.Errorf("%w: embedded images: %d read, %d failed OCR, %d unreadable, more than %d: %t",
			ErrIncomplete, len(images.data)-failed, failed, images.unreadable, limit, images.truncated)
	}
	return text, nil
}

// readImages OCRs images in parallel and returns the non-empty texts in document order, with the
// number of images that could not be read.
func (p *DocumentProcessor) readImages(ctx context.Context, images [][]byte) ([]string, int) {
	texts := make([]string, len(images))
	errs := make([]error, len(images))
	sem := make(chan struct{}, ocrConcurrency)
	var wg sync.WaitGroup
	for i, img := range images {
		wg.Add(1)
		go func(i int, img []byte) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			texts[i], errs[i] = ocrImage(ctx, p.ocr, img)
		}(i, img)
	}
	wg.Wait()

	var read []string
	failed := 0
	for i, err := range errs {
		if err != nil {
			failed++
			continue
		}
		if t := strings.TrimSpace(texts[i]); t != "" {
			read = append(read, t)
		}
	}
	return read, failed
}
