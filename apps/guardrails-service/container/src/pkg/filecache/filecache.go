// Package filecache remembers files that /api/validate/file inspected and allowed, so the copies an
// agent re-sends with its history on every turn are not extracted, OCR'd and scanned again.
//
// It lives server-side, in the Redis that guardrails-service already uses (REDIS_URL), so every
// replica shares it and the clients (egress proxy, browser extension, hooks) stay stateless. The
// caller keys an entry on the file's bytes and on the policies that apply to the caller, so a
// policy change reaches a remembered file at once; the TTL only bounds how long an entry lives.
// Only inspected allows are stored: a blocked file, or one let through without its content being
// checked, is checked again. Every Redis failure reads as a miss, so the cache never lets a file
// through on its own.
package filecache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"time"

	redis "github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

const (
	keyPrefix = "guardrails:file-allowed:v1:"
	// A slow Redis must not hold up a verdict: past this, the file is inspected as on a miss.
	opTimeout = 200 * time.Millisecond
)

// Store remembers allowed files by key.
type Store interface {
	Allowed(ctx context.Context, key string) bool
	Remember(ctx context.Context, key string)
}

// Key is the cache key for a file's bytes under a scope: whatever else the verdict depends on
// (account, context source, tag, the applicable policies, the validation settings).
func Key(scope string, data []byte) string {
	file := sha256.Sum256(data)
	sum := sha256.Sum256([]byte(scope + "|" + hex.EncodeToString(file[:])))
	return keyPrefix + hex.EncodeToString(sum[:])
}

type redisStore struct {
	client *redis.Client
	ttl    time.Duration
	logger *zap.Logger
}

// NewRedis returns a Store on client, or nil (no caching) when there is no client or ttl is not
// positive.
func NewRedis(client *redis.Client, ttl time.Duration, logger *zap.Logger) Store {
	if client == nil || ttl <= 0 {
		return nil
	}
	return &redisStore{client: client, ttl: ttl, logger: logger}
}

func (s *redisStore) Allowed(ctx context.Context, key string) bool {
	ctx, cancel := context.WithTimeout(ctx, opTimeout)
	defer cancel()
	n, err := s.client.Exists(ctx, key).Result()
	if err != nil {
		s.logger.Warn("File cache lookup failed; inspecting the file", zap.Error(err))
		return false
	}
	return n == 1
}

func (s *redisStore) Remember(ctx context.Context, key string) {
	ctx, cancel := context.WithTimeout(context.WithoutCancel(ctx), opTimeout)
	defer cancel()
	if err := s.client.Set(ctx, key, "1", s.ttl).Err(); err != nil {
		s.logger.Warn("File cache write failed; the file will be inspected again", zap.Error(err))
	}
}
