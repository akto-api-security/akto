package auth

import (
	"crypto/sha256"
	"encoding/hex"
	"sync"
	"time"
)

type cacheEntry struct {
	accountID int
	expiresAt time.Time
}

type tokenCache struct {
	mu       sync.RWMutex
	entries  map[string]cacheEntry
	maxTTL   time.Duration
}

func newTokenCache(maxTTL time.Duration) *tokenCache {
	return &tokenCache{
		entries: make(map[string]cacheEntry),
		maxTTL:  maxTTL,
	}
}

func (c *tokenCache) get(token string) (int, bool) {
	key := hashToken(token)
	now := time.Now()

	c.mu.RLock()
	entry, ok := c.entries[key]
	c.mu.RUnlock()
	if !ok {
		return 0, false
	}
	if now.After(entry.expiresAt) {
		c.mu.Lock()
		delete(c.entries, key)
		c.mu.Unlock()
		return 0, false
	}
	return entry.accountID, true
}

func (c *tokenCache) set(token string, accountID int, jwtExp time.Time) {
	key := hashToken(token)
	ttl := c.maxTTL
	if !jwtExp.IsZero() {
		if remaining := time.Until(jwtExp); remaining > 0 && remaining < ttl {
			ttl = remaining
		}
	}
	c.mu.Lock()
	c.entries[key] = cacheEntry{
		accountID: accountID,
		expiresAt: time.Now().Add(ttl),
	}
	c.mu.Unlock()
}

func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
