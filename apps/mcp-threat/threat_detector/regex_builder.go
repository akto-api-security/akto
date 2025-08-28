package threat_detector

import (
	"fmt"
	"regexp"
	"strings"
)

func GetSuspiciousRegex() *regexp.Regexp {
	rex, err := buildUnionRegex(SuspiciousKeywords)
	if err != nil {
		return nil
	}
	return rex
}

func buildUnionRegex(uniqueKeywords []string) (*regexp.Regexp, error) {
	if len(uniqueKeywords) == 0 {
		return regexp.Compile("a^") // matches nothing
	}

	var parts []string
	for _, k := range uniqueKeywords {
		if strings.Contains(k, " ") {
			tokens := strings.Fields(k)
			for i := range tokens {
				tokens[i] = regexp.QuoteMeta(tokens[i])
			}
			parts = append(parts, strings.Join(tokens, `(?:[\s\S]*?)`))
		} else {
			// single word or tag like <instructions>
			parts = append(parts, regexp.QuoteMeta(k)+`(?:[\s\S]*?)`)
		}
	}

	// Go does NOT support lookbehind, so we use word boundaries only where applicable
	pattern := fmt.Sprintf(`(?i)(?:%s)`, strings.Join(parts, "|"))
	return regexp.Compile(pattern)
}

// func containsKeywords(text string, keywords []string) (bool, []Proof, error) {
// 	rex, err := buildUnionRegex(keywords)
// 	if err != nil {
// 		return false, nil, err
// 	}
// 	const context = 20
// 	var proofs []Proof

// 	idxs := rex.FindAllStringIndex(text, -1)
// 	for _, span := range idxs {
// 		start, end := span[0], span[1]

// 		// whole-word boundary check only if the match begins/ends with word chars
// 		if start < end {
// 			startsWithWord := isAsciiWord(text[start])
// 			endsWithWord := isAsciiWord(text[end-1])
// 			if startsWithWord || endsWithWord {
// 				if start > 0 {
// 					if isAsciiWord(text[start-1]) {
// 						continue
// 					}
// 				}
// 				if end < len(text) {
// 					if isAsciiWord(text[end]) {
// 						continue
// 					}
// 				}
// 			}
// 		}

// 		// snippet: up to 20 bytes each side (simple byte-window, like Python slicing)
// 		before := start - context
// 		if before < 0 {
// 			before = 0
// 		}
// 		after := end + context
// 		if after > len(text) {
// 			after = len(text)
// 		}
// 		snippet := text[before:after]

// 		// Ensure we don't cut inside a rune at boundaries
// 		for !utf8.ValidString(snippet) && before < end && after > start {
// 			// back off by one byte on both sides until valid
// 			if before < end {
// 				before++
// 			}
// 			if after > start {
// 				after--
// 			}
// 			if before < 0 {
// 				before = 0
// 			}
// 			if after > len(text) {
// 				after = len(text)
// 			}
// 			snippet = text[before:after]
// 		}

// 		proofs = append(proofs, Proof{
// 			Keyword: text[start:end],
// 			Snippet: snippet,
// 		})
// 	}

// 	return len(proofs) > 0, proofs, nil
// }
