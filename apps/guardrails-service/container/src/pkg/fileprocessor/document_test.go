package fileprocessor

import (
	"archive/zip"
	"bytes"
	"compress/zlib"
	"context"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"io"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
)

// fakeOCR "reads" an image as its size and the colour of its first pixel, so a test can tell
// which image reached it and that it was decoded right. An image as wide as failWidth fails.
type fakeOCR struct {
	calls     atomic.Int32
	failWidth int
}

func (o *fakeOCR) ExtractText(_ context.Context, r io.Reader) (string, error) {
	o.calls.Add(1)
	img, _, err := image.Decode(r)
	if err != nil {
		return "", err
	}
	b := img.Bounds()
	if b.Dx() == o.failWidth {
		return "", errors.New("ocr unavailable")
	}
	cr, cg, cb, _ := img.At(b.Min.X, b.Min.Y).RGBA()
	return fmt.Sprintf("image %dx%d rgb(%d,%d,%d)", b.Dx(), b.Dy(), cr>>8, cg>>8, cb>>8), nil
}

func solid(w, h int, c color.Color) image.Image {
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	for i := 0; i < w*h; i++ {
		img.Set(i%w, i/w, c)
	}
	return img
}

func jpegBytes(t *testing.T, img image.Image) []byte {
	t.Helper()
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, &jpeg.Options{Quality: 100}); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func pngBytes(t *testing.T, img image.Image) []byte {
	t.Helper()
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

// pdfImage is an image XObject as a PDF stores it.
type pdfImage struct {
	w, h       int
	filter     string // "DCTDecode" or "FlateDecode"
	colorSpace string // a name, or "ICC" for [/ICCBased] with an RGB profile
	data       []byte
}

func jpegXObject(t *testing.T, w, h int, c color.Color) pdfImage {
	return pdfImage{w: w, h: h, filter: "DCTDecode", colorSpace: "DeviceRGB", data: jpegBytes(t, solid(w, h, c))}
}

// buildPDF writes a PDF whose pages draw the given images, the first page also carrying text, as
// a scanner or "Microsoft: Print To PDF" lays them out.
func buildPDF(t *testing.T, text string, pages ...[]pdfImage) []byte {
	t.Helper()
	var objs []string // object n is objs[n-1]
	add := func(body string) int { objs = append(objs, body); return len(objs) }
	stream := func(dict string, data []byte) string {
		return fmt.Sprintf("<< %s /Length %d >>\nstream\n%s\nendstream", dict, len(data), data)
	}

	add("<< /Type /Catalog /Pages 2 0 R >>")
	add("") // the page tree, once the kids are known
	font := add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
	icc := add(stream("/N 3", nil))
	var kids []string
	for i, images := range pages {
		var xobjects, content strings.Builder
		if i == 0 && text != "" {
			fmt.Fprintf(&content, "BT /F1 12 Tf 72 720 Td (%s) Tj ET\n", text)
		}
		for j, img := range images {
			cs := "/" + img.colorSpace
			if img.colorSpace == "ICC" {
				cs = fmt.Sprintf("[/ICCBased %d 0 R]", icc)
			}
			n := add(stream(fmt.Sprintf("/Type /XObject /Subtype /Image /Width %d /Height %d /ColorSpace %s /BitsPerComponent 8 /Filter /%s",
				img.w, img.h, cs, img.filter), img.data))
			fmt.Fprintf(&xobjects, "/Im%d %d 0 R ", j, n)
			fmt.Fprintf(&content, "q %d 0 0 %d 0 0 cm /Im%d Do Q\n", img.w, img.h, j)
		}
		c := add(stream("", []byte(content.String())))
		p := add(fmt.Sprintf("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents %d 0 R /Resources << /Font << /F1 %d 0 R >> /XObject << %s>> >> >>",
			c, font, xobjects.String()))
		kids = append(kids, fmt.Sprintf("%d 0 R", p))
	}
	objs[1] = fmt.Sprintf("<< /Type /Pages /Kids [%s] /Count %d >>", strings.Join(kids, " "), len(kids))

	var out bytes.Buffer
	out.WriteString("%PDF-1.7\n")
	offsets := make([]int, len(objs))
	for i, body := range objs {
		offsets[i] = out.Len()
		fmt.Fprintf(&out, "%d 0 obj\n%s\nendobj\n", i+1, body)
	}
	xref := out.Len()
	fmt.Fprintf(&out, "xref\n0 %d\n0000000000 65535 f \n", len(objs)+1)
	for _, off := range offsets {
		fmt.Fprintf(&out, "%010d 00000 n \n", off)
	}
	fmt.Fprintf(&out, "trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n", len(objs)+1, xref)
	return out.Bytes()
}

// buildZip writes a zip-based document (docx, xlsx, ...) with the given entries.
func buildZip(t *testing.T, entries map[string][]byte) []byte {
	t.Helper()
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	for name, data := range entries {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		_, _ = w.Write(data)
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

// minimalDocx is a Word document whose body is one paragraph of text.
func minimalDocx(t *testing.T, text string, media map[string][]byte) []byte {
	entries := map[string][]byte{
		"[Content_Types].xml": []byte(`<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`),
		"_rels/.rels":         []byte(`<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`),
		"word/document.xml":   []byte(`<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>` + text + `</w:t></w:r></w:p></w:body></w:document>`),
	}
	for name, data := range media {
		entries[name] = data
	}
	return buildZip(t, entries)
}

func extract(t *testing.T, p *DocumentProcessor, doc []byte, ext string) (string, error) {
	t.Helper()
	return p.ExtractContent(context.Background(), bytes.NewReader(doc), ext)
}

var red = color.RGBA{255, 0, 0, 255}

// A page that is only a picture of text (a scan, "Microsoft: Print To PDF") has no text layer:
// what it says reaches validation only through OCR.
func TestDocumentReadsTheImageOfAnImageOnlyPDF(t *testing.T) {
	ocr := &fakeOCR{}
	text, err := extract(t, NewDocumentProcessor(ocr, 10, 1<<20), buildPDF(t, "", []pdfImage{jpegXObject(t, 564, 348, red)}), ".pdf")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(text, "image 564x348 rgb(254,0,0)") && !strings.HasPrefix(text, "image 564x348 rgb(255,0,0)") {
		t.Fatalf("text = %q, want the OCR of the page image", text)
	}
}

func TestDocumentKeepsTheTextLayerBeforeTheImages(t *testing.T) {
	text, err := extract(t, NewDocumentProcessor(&fakeOCR{}, 10, 1<<20), buildPDF(t, "Quarterly report", []pdfImage{jpegXObject(t, 100, 50, red)}), ".pdf")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(text, "Quarterly report") || !strings.Contains(text, "\n\nimage 100x50") {
		t.Fatalf("text = %q, want the text layer, then the image's OCR", text)
	}
}

func flateXObject(w, h int, colorSpace string, pixel []byte) pdfImage {
	var z bytes.Buffer
	zw := zlib.NewWriter(&z)
	_, _ = zw.Write(bytes.Repeat(pixel, w*h))
	_ = zw.Close()
	return pdfImage{w: w, h: h, filter: "FlateDecode", colorSpace: colorSpace, data: z.Bytes()}
}

// Raw pixels reach OCR in their colours: RGB under an ICC profile (tabula alone would read it as
// grey), plain RGB and grey.
func TestDocumentDecodesRawPDFImages(t *testing.T) {
	for name, tc := range map[string]struct {
		img  pdfImage
		want string
	}{
		"ICC-based RGB": {flateXObject(40, 40, "ICC", []byte{255, 0, 0}), "image 40x40 rgb(255,0,0)"},
		"device RGB":    {flateXObject(40, 40, "DeviceRGB", []byte{0, 0, 255}), "image 40x40 rgb(0,0,255)"},
		"device grey":   {flateXObject(40, 40, "DeviceGray", []byte{128}), "image 40x40 rgb(128,128,128)"},
	} {
		t.Run(name, func(t *testing.T) {
			text, err := extract(t, NewDocumentProcessor(&fakeOCR{}, 10, 1<<20), buildPDF(t, "", []pdfImage{tc.img}), ".pdf")
			if err != nil || text != tc.want {
				t.Fatalf("text = %q, err = %v; want %q", text, err, tc.want)
			}
		})
	}
}

// captureOCR keeps the bytes it was sent.
type captureOCR struct {
	mu   sync.Mutex
	sent [][]byte
}

func (o *captureOCR) ExtractText(_ context.Context, r io.Reader) (string, error) {
	b, err := io.ReadAll(r)
	o.mu.Lock()
	defer o.mu.Unlock()
	o.sent = append(o.sent, b)
	return "text", err
}

// A scanned page is a JPEG in the PDF: it goes to OCR as stored, not decoded and re-encoded.
func TestDocumentSendsAStoredJPEGAsIs(t *testing.T) {
	page := jpegXObject(t, 300, 200, red)
	ocr := &captureOCR{}
	if _, err := extract(t, NewDocumentProcessor(ocr, 10, 1<<20), buildPDF(t, "", []pdfImage{page}), ".pdf"); err != nil {
		t.Fatal(err)
	}
	if len(ocr.sent) != 1 || !bytes.Equal(ocr.sent[0], page.data) {
		t.Fatalf("OCR got %d images; want the page's JPEG byte for byte", len(ocr.sent))
	}
}

func TestDocumentImageLimits(t *testing.T) {
	logo := jpegXObject(t, 120, 60, red)
	for name, tc := range map[string]struct {
		ocr        *fakeOCR
		noOCR      bool
		maxImages  int
		pages      [][]pdfImage
		calls      int32
		incomplete bool
	}{
		"icons are not worth an OCR call": {
			ocr: &fakeOCR{}, maxImages: 10, pages: [][]pdfImage{{jpegXObject(t, 16, 16, red)}},
		},
		"the same image on every page is read once": {
			ocr: &fakeOCR{}, maxImages: 10, pages: [][]pdfImage{{logo}, {logo}, {logo}}, calls: 1,
		},
		"past the cap the rest is not read": {
			ocr: &fakeOCR{}, maxImages: 1, pages: [][]pdfImage{{logo}, {jpegXObject(t, 80, 80, red)}}, calls: 1, incomplete: true,
		},
		"a failed OCR leaves the document incomplete": {
			ocr: &fakeOCR{failWidth: 80}, maxImages: 10, pages: [][]pdfImage{{logo, jpegXObject(t, 80, 80, red)}}, calls: 2, incomplete: true,
		},
		"without OCR an image can't be read": {
			ocr: &fakeOCR{}, noOCR: true, maxImages: 10, pages: [][]pdfImage{{logo}}, incomplete: true,
		},
		"without OCR a document without images is complete": {
			ocr: &fakeOCR{}, noOCR: true, maxImages: 10, pages: [][]pdfImage{{}},
		},
		"an undecodable image leaves the document incomplete": {
			ocr: &fakeOCR{}, maxImages: 10, pages: [][]pdfImage{{{w: 100, h: 100, filter: "DCTDecode", colorSpace: "DeviceRGB", data: []byte("not a jpeg")}}}, incomplete: true,
		},
	} {
		t.Run(name, func(t *testing.T) {
			p := NewDocumentProcessor(tc.ocr, tc.maxImages, 1<<20)
			if tc.noOCR {
				p = NewDocumentProcessor(nil, tc.maxImages, 1<<20)
			}
			_, err := extract(t, p, buildPDF(t, "Report", tc.pages...), ".pdf")
			if got := errors.Is(err, ErrIncomplete); got != tc.incomplete || (err != nil && !got) {
				t.Fatalf("err = %v, want incomplete = %v", err, tc.incomplete)
			}
			if got := tc.ocr.calls.Load(); got != tc.calls {
				t.Fatalf("OCR calls = %d, want %d", got, tc.calls)
			}
		})
	}
}

// Office documents keep their pictures as files in the zip: those are read, the rendered
// thumbnail of the document is not, and a vector picture Go can't decode leaves it incomplete.
func TestDocumentReadsTheImagesOfAnOfficeDocument(t *testing.T) {
	ocr := &fakeOCR{}
	doc := minimalDocx(t, "Meeting notes", map[string][]byte{
		"word/media/image1.png":   pngBytes(t, solid(200, 100, red)),
		"docProps/thumbnail.jpeg": jpegBytes(t, solid(256, 256, red)),
		"word/media/image2.png":   pngBytes(t, solid(10, 10, red)), // an icon
		"word/media/notapng.png":  []byte("corrupt"),
		"word/media/drawing.svg":  []byte("<svg/>"), // Office stores a PNG fallback next to it
		"word/media/screenshot.gif": func() []byte {
			var buf bytes.Buffer
			_ = png.Encode(&buf, solid(64, 64, red)) // misnamed: decoded by content
			return buf.Bytes()
		}(),
	})
	text, err := extract(t, NewDocumentProcessor(ocr, 10, 1<<20), doc, ".docx")
	if !strings.HasPrefix(text, "Meeting notes") || !strings.Contains(text, "image 200x100") || !strings.Contains(text, "image 64x64") {
		t.Fatalf("text = %q, want the body, then the two pictures' OCR", text)
	}
	if !errors.Is(err, ErrIncomplete) {
		t.Fatalf("err = %v, want ErrIncomplete for the corrupt picture", err)
	}
	if got := ocr.calls.Load(); got != 2 {
		t.Fatalf("OCR calls = %d, want 2 (not the thumbnail, the icon or the SVG)", got)
	}

	emf := minimalDocx(t, "Chart", map[string][]byte{"word/media/image1.emf": []byte("EMF")})
	if _, err := extract(t, NewDocumentProcessor(ocr, 10, 1<<20), emf, ".docx"); !errors.Is(err, ErrIncomplete) {
		t.Fatalf("err = %v, want ErrIncomplete for an EMF picture", err)
	}
}

func TestDocumentSkipsOversizedOfficeImages(t *testing.T) {
	ocr := &fakeOCR{}
	doc := minimalDocx(t, "Deck", map[string][]byte{"word/media/image1.png": pngBytes(t, solid(200, 100, red))})
	if _, err := extract(t, NewDocumentProcessor(ocr, 10, 64), doc, ".docx"); !errors.Is(err, ErrIncomplete) || ocr.calls.Load() != 0 {
		t.Fatalf("err = %v, OCR calls = %d; want an incomplete document and no OCR", err, ocr.calls.Load())
	}
}
