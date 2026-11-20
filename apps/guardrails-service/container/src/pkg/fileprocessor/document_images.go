package fileprocessor

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"image"
	"io"
	"path"
	"strings"

	"github.com/tsawler/tabula/reader"
)

// Images smaller than this on either side are icons, bullets and rules, not text worth an OCR call.
const minImageDimension = 32

// Raster formats Go decodes. Other images in an Office file (EMF/WMF) can't be read; SVG is
// skipped because Office stores a PNG fallback next to it.
var zipRasterExts = map[string]bool{
	".png": true, ".jpg": true, ".jpeg": true, ".gif": true, ".bmp": true,
	".tif": true, ".tiff": true, ".webp": true,
}
var zipVectorExts = map[string]bool{".emf": true, ".wmf": true}

// Office and ODF keep a rendered preview of the document here; its text is the document's own.
var zipThumbnailPrefixes = []string{"docprops/thumbnail", "thumbnails/"}

// embeddedImages is what a document holds besides its text layer: the distinct images worth
// reading, in document order, and how many were left out.
type embeddedImages struct {
	data       [][]byte
	seen       map[[32]byte]bool
	limit      int
	unreadable int  // found but undecodable, oversized or in a format OCR can't take
	truncated  bool // more images than limit
}

func (e *embeddedImages) add(img []byte) {
	if e.seen == nil {
		e.seen = map[[32]byte]bool{}
	}
	sum := sha256.Sum256(img)
	if e.seen[sum] {
		return // the same logo on every page is read once
	}
	if len(e.data) >= e.limit {
		e.truncated = true
		return
	}
	e.seen[sum] = true
	e.data = append(e.data, img)
}

// full reports that collecting further can't change the outcome.
func (e *embeddedImages) full() bool { return e.truncated }

// collectImages finds the images in the document at path, keeping at most limit of them.
// maxBytes caps one image stored in a zip-based document.
func collectImages(path, ext string, limit, maxBytes int) embeddedImages {
	images := embeddedImages{limit: limit}
	if ext == ".pdf" {
		collectPDFImages(path, &images)
	} else {
		collectZipImages(path, maxBytes, &images)
	}
	return images
}

// collectPDFImages reads the image XObjects drawn on each page. Images inside form XObjects and
// inline images are not covered.
func collectPDFImages(file string, images *embeddedImages) {
	r, err := reader.Open(file)
	if err != nil {
		return
	}
	defer r.Close()
	n, err := r.PageCount()
	if err != nil {
		return
	}
	for i := 0; i < n && !images.full(); i++ {
		page, err := r.GetPage(i)
		if err != nil {
			continue
		}
		pageImages, err := r.ExtractPageImages(page)
		if err != nil {
			images.unreadable++
			continue
		}
		for j := range pageImages {
			if images.full() {
				return
			}
			img := &pageImages[j]
			if img.Width < minImageDimension || img.Height < minImageDimension {
				continue
			}
			if img.Width*img.Height > maxPixelCount {
				images.unreadable++
				continue
			}
			normalizeICCColorSpace(img)
			png, err := img.ToPNG()
			if err != nil {
				images.unreadable++
				continue
			}
			images.add(png)
		}
	}
}

// normalizeICCColorSpace names an ICC-based image's colour space by its component count, which
// the pixel data gives away; tabula would otherwise decode an RGB image as greyscale.
func normalizeICCColorSpace(img *reader.PageImage) {
	if img.ColorSpace != "ICCBased" || img.BitsPerComponent != 8 {
		return
	}
	switch pixels := img.Width * img.Height; {
	case len(img.Data) >= pixels*4:
		img.ColorSpace = "DeviceCMYK"
	case len(img.Data) >= pixels*3:
		img.ColorSpace = "DeviceRGB"
	}
}

// collectZipImages reads the raster images stored in a zip-based document (docx, xlsx, pptx, odt,
// epub). A document that is not a zip (html) has none.
func collectZipImages(file string, maxBytes int, images *embeddedImages) {
	zr, err := zip.OpenReader(file)
	if err != nil {
		return
	}
	defer zr.Close()
	for _, f := range zr.File {
		if images.full() {
			return
		}
		name := strings.ToLower(f.Name)
		ext := path.Ext(name)
		if zipVectorExts[ext] {
			images.unreadable++
			continue
		}
		if !zipRasterExts[ext] || hasAnyPrefix(name, zipThumbnailPrefixes) {
			continue
		}
		if f.UncompressedSize64 > uint64(maxBytes) {
			images.unreadable++
			continue
		}
		data, err := readZipEntry(f, maxBytes)
		if err != nil {
			images.unreadable++
			continue
		}
		cfg, _, err := image.DecodeConfig(bytes.NewReader(data))
		if err != nil {
			images.unreadable++
			continue
		}
		if cfg.Width < minImageDimension || cfg.Height < minImageDimension {
			continue
		}
		images.add(data)
	}
}

// readZipEntry reads one entry, refusing more than maxBytes whatever its header claims.
func readZipEntry(f *zip.File, maxBytes int) ([]byte, error) {
	rc, err := f.Open()
	if err != nil {
		return nil, err
	}
	defer rc.Close()
	data, err := io.ReadAll(io.LimitReader(rc, int64(maxBytes)+1))
	if err != nil {
		return nil, err
	}
	if len(data) > maxBytes {
		return nil, errImageTooLarge
	}
	return data, nil
}

func hasAnyPrefix(s string, prefixes []string) bool {
	for _, p := range prefixes {
		if strings.HasPrefix(s, p) {
			return true
		}
	}
	return false
}
