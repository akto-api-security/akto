package guardrails

import (
	"sync"
	"time"
)

// RateLimiter implements a simple token bucket rate limiter
type RateLimiter struct {
	config     RateLimitConfig
	tokens     int
	lastRefill time.Time
	mutex      sync.Mutex
}

// NewRateLimiter creates a new rate limiter with the given configuration
func NewRateLimiter(config RateLimitConfig) *RateLimiter {
	return &RateLimiter{
		config:     config,
		tokens:     config.BurstSize,
		lastRefill: time.Now(),
	}
}

// Allow checks if a request is allowed based on rate limiting
func (r *RateLimiter) Allow() bool {
	r.mutex.Lock()
	defer r.mutex.Unlock()

	now := time.Now()

	// Refill tokens based on time elapsed
	timeElapsed := now.Sub(r.lastRefill)
	tokensToAdd := int(timeElapsed.Minutes()) * r.config.RequestsPerMinute / 60

	if tokensToAdd > 0 {
		r.tokens = min(r.config.BurstSize, r.tokens+tokensToAdd)
		r.lastRefill = now
	}

	// Check if we have tokens available
	if r.tokens > 0 {
		r.tokens--
		return true
	}

	return false
}

// min returns the minimum of two integers
func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
