package config

import "testing"

// Shrinking ChunkSize must not start rejecting files the old config accepted: the chunk
// ceiling has to track the byte cap, not sit at a fixed number that silently contradicts it.
func TestDefaultMaxChunksCoversTheByteCap(t *testing.T) {
	const maxBytes = 5 * 1024 * 1024

	tests := []struct {
		name      string
		chunkSize int
	}{
		{"default", defaultChunkSize},
		{"operator shrinks further", 2000},
		{"operator enlarges", 32000},
		{"non-positive falls back", 0},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			const overlap = 200
			got := defaultMaxChunks(maxBytes, tt.chunkSize, overlap)
			size := tt.chunkSize
			if size <= 0 {
				size = defaultChunkSize
			}
			// Mirrors ChunkWordBoundary: after the first chunk, each one advances by
			// size-overlap, so the ceiling must be computed against the advance.
			need := 1 + (maxBytes-size+(size-overlap)-1)/(size-overlap)
			if got < need {
				t.Errorf("maxChunks = %d, need at least %d for a %d-byte extract at chunkSize %d overlap %d",
					got, need, maxBytes, size, overlap)
			}
		})
	}
}

func TestDefaultChunkSizeFitsUpstreamCeiling(t *testing.T) {
	// A measured 9306-char payload cost ~10s against a 60s upstream ceiling. Keep the
	// default comfortably under that payload so one chunk cannot approach the timeout.
	if defaultChunkSize > 9306 {
		t.Errorf("defaultChunkSize = %d, want <= 9306 (the measured 10s payload)", defaultChunkSize)
	}
}
