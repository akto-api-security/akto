package fileprocessor

import (
	"strings"
	"unicode"
	"unicode/utf8"
)

// SanitizeText removes null bytes and non-printable control characters
// (except common whitespace) from extracted file content. PDF and Office
// extractors frequently emit these artifacts.
func SanitizeText(text string) string {
	return strings.Map(func(r rune) rune {
		if r == '\t' || r == '\n' || r == '\r' {
			return r
		}
		if unicode.IsControl(r) {
			return -1 // drop
		}
		return r
	}, text)
}

// ChunkWordBoundary splits text into chunks of at most chunkSize characters,
// never splitting in the middle of a word. Break points (in order of preference):
// paragraph (\n\n), line (\n), word boundary (space/tab). If no boundary found,
// falls back to a UTF-8-safe hard split.
//
// overlap specifies how many characters from the end of each chunk are repeated
// at the start of the next chunk. This ensures sensitive patterns (emails, SSNs,
// credit cards, etc.) that span a chunk boundary are fully captured in at least
// one chunk. Set overlap to 0 to disable.
//
// Empty chunks (whitespace-only) are filtered out.
func ChunkWordBoundary(text string, chunkSize, overlap int) []string {
	if chunkSize <= 0 {
		chunkSize = 32000
	}
	if overlap < 0 || overlap >= chunkSize {
		overlap = 0
	}
	text = strings.TrimSpace(text)
	if text == "" {
		return nil
	}
	if len(text) <= chunkSize {
		return []string{text}
	}

	var chunks []string
	for len(text) > 0 {
		if len(text) <= chunkSize {
			if t := strings.TrimSpace(text); t != "" {
				chunks = append(chunks, t)
			}
			break
		}

		splitAt := findWordBoundary(text[:chunkSize])
		if chunk := strings.TrimSpace(text[:splitAt]); chunk != "" {
			chunks = append(chunks, chunk)
		}

		advance := splitAt - overlap
		if advance < 1 {
			advance = splitAt
		}
		text = text[advance:]
	}
	return chunks
}

// findWordBoundary returns the best byte-index at which to split, preferring
// paragraph (\n\n), then line (\n), then space/tab.
// Scans backward through the segment in a single pass, recording the
// first (nearest-to-end) occurrence of each boundary type, then picks
// the highest-priority one found.
// Falls back to a UTF-8-safe hard split if no whitespace boundary exists.
func findWordBoundary(segment string) int {
	paraSplit := -1
	lineSplit := -1
	spaceSplit := -1

	for i := len(segment) - 1; i >= 0; i-- {
		ch := segment[i]
		if ch == '\n' {
			if i > 0 && segment[i-1] == '\n' {
				if paraSplit == -1 {
					paraSplit = i + 1
				}
				i--
			} else if lineSplit == -1 {
				lineSplit = i + 1
			}
		} else if (ch == ' ' || ch == '\t') && spaceSplit == -1 {
			spaceSplit = i + 1
		}
		if paraSplit != -1 && lineSplit != -1 && spaceSplit != -1 {
			break
		}
	}

	if paraSplit != -1 {
		return paraSplit
	}
	if lineSplit != -1 {
		return lineSplit
	}
	if spaceSplit != -1 {
		return spaceSplit
	}
	return runeAlignedIndex(segment, len(segment))
}

// runeAlignedIndex adjusts idx backward so it doesn't land in the middle
// of a multi-byte UTF-8 rune. Returns idx unchanged if already aligned.
func runeAlignedIndex(s string, idx int) int {
	if idx >= len(s) {
		return len(s)
	}
	for idx > 0 && !utf8.RuneStart(s[idx]) {
		idx--
	}
	return idx
}
