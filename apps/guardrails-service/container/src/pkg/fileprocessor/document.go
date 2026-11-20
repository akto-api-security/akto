package fileprocessor

import (
	"context"
	"fmt"
	"io"
	"sort"
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
// Each image's OCR starts as soon as the image is found, while the rest of the document is parsed.
func (p *DocumentProcessor) ExtractContent(ctx context.Context, r io.Reader, ext string) (string, error) {
	// Without OCR one image is enough to know the document can't be fully read.
	limit := p.maxImages
	if p.ocr == nil {
		limit = 0
	}
	run := &ocrRun{ctx: ctx, ocr: p.ocr, sem: make(chan struct{}, ocrConcurrency), texts: map[int]string{}}
	images := embeddedImages{limit: limit, onAdd: run.start}
	text, err := withTempFile(r, ext, func(path string) (string, error) {
		collectImages(path, ext, p.maxImageBytes, &images)
		text, _, err := tabula.Open(path).Text()
		if err != nil {
			return "", err
		}
		return strings.TrimSpace(text), nil
	})
	read, failed := run.wait()
	if err != nil {
		return "", err
	}

	if len(read) > 0 {
		text = strings.TrimSpace(strings.Join(append([]string{text}, read...), "\n\n"))
	}
	if images.unreadable > 0 || failed > 0 || images.truncated {
		return text, fmt.Errorf("%w: embedded images: %d read, %d failed OCR, %d unreadable, more than %d: %t",
			ErrIncomplete, images.count-failed, failed, images.unreadable, limit, images.truncated)
	}
	return text, nil
}

// ocrRun OCRs images in the background, ocrConcurrency at a time.
type ocrRun struct {
	ctx    context.Context
	ocr    mediaprovider.OCRProvider
	sem    chan struct{}
	wg     sync.WaitGroup
	mu     sync.Mutex
	texts  map[int]string
	failed int
}

// start reads image i of the document.
func (r *ocrRun) start(i int, img []byte) {
	r.wg.Add(1)
	go func() {
		defer r.wg.Done()
		r.sem <- struct{}{}
		defer func() { <-r.sem }()
		text, err := ocrImage(r.ctx, r.ocr, img)
		r.mu.Lock()
		defer r.mu.Unlock()
		if err != nil {
			r.failed++
			return
		}
		r.texts[i] = strings.TrimSpace(text)
	}()
}

// wait returns the non-empty texts in document order and how many images could not be read.
func (r *ocrRun) wait() ([]string, int) {
	r.wg.Wait()
	indexes := make([]int, 0, len(r.texts))
	for i := range r.texts {
		indexes = append(indexes, i)
	}
	sort.Ints(indexes)
	var read []string
	for _, i := range indexes {
		if r.texts[i] != "" {
			read = append(read, r.texts[i])
		}
	}
	return read, r.failed
}
