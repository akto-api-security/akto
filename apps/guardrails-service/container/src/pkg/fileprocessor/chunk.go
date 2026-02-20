package fileprocessor

import (
	"strings"
	"unicode"
	"unicode/utf8"
)

// SanitizeText removes null bytes and non-printable control characters (except common whitespace).
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

// ChunkWordBoundary splits text into chunks of at most chunkSize characters on word boundaries.
// overlap repeats chars between adjacent chunks to catch boundary-spanning patterns.
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

// findWordBoundary scans backward for the best split point: \n\n > \n > space/tab > UTF-8-safe hard split.
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

// runeAlignedIndex adjusts idx backward so it doesn't split a multi-byte UTF-8 rune.
func runeAlignedIndex(s string, idx int) int {
	if idx >= len(s) {
		return len(s)
	}
	for idx > 0 && !utf8.RuneStart(s[idx]) {
		idx--
	}
	return idx
}
