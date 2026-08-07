package sink

import (
	"sync"
	"time"
)

// pendingTurnTTL bounds how long an incomplete prompt turn (e.g. a Cowork user_prompt
// with no model/response yet) is held waiting for a later OTLP export to complete it.
// Cowork's OTel exporter runs inside the user's session and may flush user_prompt and
// api_request in separate HTTP exports for long-running (async) turns.
const pendingTurnTTL = 5 * time.Minute

type pendingEntry struct {
	turn   *promptTurn
	expiry time.Time
}

// pendingTurnCache holds in-flight promptTurns across OTLP exports, keyed by prompt.id
// (see mergePromptTurnEvents). Without it, an export containing only user_prompt and a
// later export containing only api_request would become two ingest rows instead of one,
// and the earlier row would be missing the model.
type pendingTurnCache struct {
	mu      sync.Mutex
	entries map[string]*pendingEntry
}

var pendingTurns = &pendingTurnCache{entries: make(map[string]*pendingEntry)}

// get returns and removes a still-live pending turn for key, or nil if absent or expired.
func (c *pendingTurnCache) get(key string) *promptTurn {
	c.mu.Lock()
	defer c.mu.Unlock()
	e, ok := c.entries[key]
	if !ok {
		return nil
	}
	delete(c.entries, key)
	if time.Now().After(e.expiry) {
		return nil
	}
	return e.turn
}

// put stashes an incomplete turn to be resumed by a later export.
func (c *pendingTurnCache) put(key string, turn *promptTurn) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.entries[key] = &pendingEntry{turn: turn, expiry: time.Now().Add(pendingTurnTTL)}
}

// takeExpired removes and returns turns whose TTL has passed, so callers can flush them
// (e.g. as prompt-only rows) instead of holding them in memory forever.
func (c *pendingTurnCache) takeExpired() map[string]*promptTurn {
	c.mu.Lock()
	defer c.mu.Unlock()
	expired := make(map[string]*promptTurn)
	now := time.Now()
	for key, e := range c.entries {
		if now.After(e.expiry) {
			expired[key] = e.turn
			delete(c.entries, key)
		}
	}
	return expired
}
