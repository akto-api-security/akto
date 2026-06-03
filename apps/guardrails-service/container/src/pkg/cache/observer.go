package cache

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/pkg/slack"
	"go.uber.org/zap"
)

const maxPayloadPreview = 300

// CheckResult holds the outcome of a cache lookup.
type CheckResult struct {
	Hit       bool
	Allowed   bool
	Reason    string
	Behaviour string
	Distance  float64
	Err       error
	latencyMs float64
}

// Observer runs cache checks and stores results in the background.
// All public methods are non-blocking: they spawn goroutines and return immediately.
// The main validation path is never delayed.
type Observer struct {
	embedder    *Embedder
	vectorStore *VectorStore
	enabled     bool
	ttl         int
	logger      *zap.Logger
}

// NewObserver creates an Observer backed by the Go-native hugot embedder and
// go-redis vector store. When enabled=false all methods are no-ops.
func NewObserver(embedder *Embedder, vs *VectorStore, enabled bool, ttl int, logger *zap.Logger) *Observer {
	logger.Info("[CacheObserver] initialised",
		zap.Bool("enabled", enabled),
		zap.Int("defaultTTLSeconds", ttl),
	)
	return &Observer{
		embedder:    embedder,
		vectorStore: vs,
		enabled:     enabled,
		ttl:         ttl,
		logger:      logger,
	}
}

// CheckAsync spawns a goroutine that embeds the payload and searches the
// vector store. It immediately returns a buffered channel (size 1) — the
// goroutine writes exactly one *CheckResult and exits.
// The caller can read from the channel later without ever blocking.
func (o *Observer) CheckAsync(payload, ruleHash string) <-chan *CheckResult {
	ch := make(chan *CheckResult, 1)
	if !o.enabled {
		o.logger.Debug("[CacheObserver] CheckAsync skipped — observer disabled")
		ch <- &CheckResult{}
		return ch
	}

	preview := payloadPreview(payload)
	o.logger.Info("[CacheObserver] CheckAsync started",
		zap.String("ruleHash", ruleHash),
		zap.String("payloadPreview", preview),
		zap.Int("payloadBytes", len(payload)),
	)

	go func() {
		start := time.Now()
		result, err := o.check(payload, ruleHash)
		elapsed := time.Since(start)

		if err != nil {
			o.logger.Error("[CacheObserver] check goroutine failed",
				zap.String("ruleHash", ruleHash),
				zap.Duration("elapsed", elapsed),
				zap.Error(err),
			)
			ch <- &CheckResult{Err: err, latencyMs: float64(elapsed.Milliseconds())}
		} else {
			result.latencyMs = float64(elapsed.Milliseconds())
			o.logger.Info("[CacheObserver] check goroutine completed",
				zap.String("ruleHash", ruleHash),
				zap.Bool("hit", result.Hit),
				zap.Float64("distance", result.Distance),
				zap.Bool("cacheAllowed", result.Allowed),
				zap.Duration("elapsed", elapsed),
			)
			ch <- result
		}
	}()
	return ch
}

// CompareAndAlert must be called as a goroutine ("go observer.CompareAndAlert(...)").
// It waits for the cache check goroutine to complete, then:
//  1. Stores the real result in the cache (always — warms the cache).
//  2. Sends a Slack alert for every outcome: miss, hit+match, hit+mismatch.
func (o *Observer) CompareAndAlert(
	cacheCh <-chan *CheckResult,
	realResult *mcp.ValidationResult,
	payload, ruleHash, direction, sessionID, path string,
) {
	if !o.enabled {
		return
	}

	o.logger.Info("[CacheObserver] CompareAndAlert waiting for check result",
		zap.String("path", path),
		zap.String("direction", direction),
		zap.String("sessionID", sessionID),
		zap.String("ruleHash", ruleHash),
		zap.Bool("realAllowed", realResult.Allowed),
		zap.String("realReason", realResult.Reason),
	)

	waitStart := time.Now()
	cacheResult := <-cacheCh
	waitElapsed := time.Since(waitStart)

	o.logger.Info("[CacheObserver] cache check result received",
		zap.String("ruleHash", ruleHash),
		zap.Duration("channelWait", waitElapsed),
		zap.Bool("hit", cacheResult != nil && cacheResult.Hit),
		zap.Bool("checkErr", cacheResult != nil && cacheResult.Err != nil),
		zap.Float64("checkLatencyMs", func() float64 {
			if cacheResult != nil {
				return cacheResult.latencyMs
			}
			return 0
		}()),
	)

	// Always store real result to warm the cache for future requests.
	storeStart := time.Now()
	o.store(payload, ruleHash, realResult)
	o.logger.Info("[CacheObserver] store completed",
		zap.String("ruleHash", ruleHash),
		zap.Bool("allowed", realResult.Allowed),
		zap.Duration("storeElapsed", time.Since(storeStart)),
	)

	outcome := classifyOutcome(cacheResult, realResult)
	o.logger.Info("[CacheObserver] comparison outcome",
		zap.String("outcome", outcome),
		zap.String("path", path),
		zap.String("direction", direction),
		zap.String("ruleHash", ruleHash),
		zap.Bool("realAllowed", realResult.Allowed),
		zap.Float64("distance", func() float64 {
			if cacheResult != nil {
				return cacheResult.Distance
			}
			return 0
		}()),
	)

	msg := o.buildSlackMessage(cacheResult, realResult, payload, ruleHash, direction, sessionID, path)
	go slack.SendAlert(o.logger, msg)
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

func (o *Observer) check(payload, ruleHash string) (*CheckResult, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Step 1: embed payload
	embedStart := time.Now()
	vec, err := o.embedder.Embed(ctx, payload)
	if err != nil {
		return nil, fmt.Errorf("embed: %w", err)
	}
	o.logger.Debug("[CacheObserver] embedded payload for check",
		zap.String("ruleHash", ruleHash),
		zap.Int("dims", len(vec)),
		zap.Duration("embedLatency", time.Since(embedStart)),
	)

	// Step 2: vector similarity search
	searchStart := time.Now()
	entry, err := o.vectorStore.Check(ctx, vec, ruleHash)
	if err != nil {
		return nil, fmt.Errorf("vector search: %w", err)
	}
	o.logger.Debug("[CacheObserver] vector search done",
		zap.String("ruleHash", ruleHash),
		zap.Bool("hit", entry != nil),
		zap.Duration("searchLatency", time.Since(searchStart)),
	)

	if entry == nil {
		return &CheckResult{Hit: false}, nil
	}

	// Step 3: parse stored result
	var stored struct {
		Allowed   bool   `json:"allowed"`
		Reason    string `json:"reason"`
		Behaviour string `json:"behaviour"`
	}
	if err := json.Unmarshal([]byte(entry.Response), &stored); err != nil {
		o.logger.Warn("[CacheObserver] failed to parse cached response",
			zap.String("ruleHash", ruleHash),
			zap.String("raw", entry.Response),
			zap.Error(err),
		)
		return &CheckResult{Hit: false}, nil
	}

	return &CheckResult{
		Hit:       true,
		Allowed:   stored.Allowed,
		Reason:    stored.Reason,
		Behaviour: stored.Behaviour,
		Distance:  entry.Distance,
	}, nil
}

func (o *Observer) store(payload, ruleHash string, realResult *mcp.ValidationResult) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Step 1: embed payload
	embedStart := time.Now()
	vec, err := o.embedder.Embed(ctx, payload)
	if err != nil {
		o.logger.Error("[CacheObserver] embed failed during store",
			zap.String("ruleHash", ruleHash),
			zap.Duration("elapsed", time.Since(embedStart)),
			zap.Error(err),
		)
		return
	}
	o.logger.Debug("[CacheObserver] embedded payload for store",
		zap.String("ruleHash", ruleHash),
		zap.Duration("embedLatency", time.Since(embedStart)),
	)

	// Step 2: marshal result
	resultBytes, err := json.Marshal(map[string]any{
		"allowed":          realResult.Allowed,
		"modified":         realResult.Modified,
		"modified_payload": realResult.ModifiedPayload,
		"reason":           realResult.Reason,
		"behaviour":        realResult.Behaviour,
	})
	if err != nil {
		o.logger.Error("[CacheObserver] failed to marshal result",
			zap.String("ruleHash", ruleHash),
			zap.Error(err),
		)
		return
	}

	// Blocked results get shorter TTL — policies may change and unblock them.
	ttl := o.ttl
	if !realResult.Allowed {
		ttl = ttl / 2
		o.logger.Debug("[CacheObserver] TTL halved for blocked result",
			zap.Int("ttlSeconds", ttl),
		)
	}

	o.logger.Info("[CacheObserver] storing result",
		zap.String("ruleHash", ruleHash),
		zap.Bool("allowed", realResult.Allowed),
		zap.Int("ttlSeconds", ttl),
		zap.Int("payloadBytes", len(payload)),
		zap.Int("resultBytes", len(resultBytes)),
	)

	// Step 3: write to Redis
	if err := o.vectorStore.Store(ctx, vec, ruleHash, string(resultBytes), ttl); err != nil {
		o.logger.Error("[CacheObserver] vector store failed",
			zap.String("ruleHash", ruleHash),
			zap.Error(err),
		)
	}
}

func (o *Observer) buildSlackMessage(
	cr *CheckResult,
	real *mcp.ValidationResult,
	payload, ruleHash, direction, sessionID, path string,
) string {
	realStr := verdictStr(real.Allowed)
	reason := real.Reason
	if reason == "" {
		reason = "—"
	}
	behaviour := real.Behaviour
	if behaviour == "" {
		behaviour = "—"
	}
	preview := payloadPreview(payload)

	if cr == nil || cr.Err != nil || !cr.Hit {
		errLine := ""
		if cr != nil && cr.Err != nil {
			errLine = fmt.Sprintf("Error: %v\n", cr.Err)
		}
		return fmt.Sprintf(
			"%sMISS — guardrails-cache (%s)\n"+
				"real: %s   behaviour: %s   hash: %s\n"+
				"path: %s   session: %s\n\n"+
				"Input: %s\n\n"+
				"Reason: %s",
			errLine, direction,
			realStr, behaviour, ruleHash,
			path, sessionID,
			preview, reason,
		)
	}

	cacheStr := verdictStr(cr.Allowed)
	if cr.Allowed == real.Allowed {
		return fmt.Sprintf(
			"HIT ✅ MATCH — guardrails-cache (%s)\n"+
				"cache: %s   real: %s   distance: %.4f   latency: %.0fms   hash: %s\n"+
				"path: %s   session: %s\n\n"+
				"Input: %s\n\n"+
				"Reason: %s",
			direction,
			cacheStr, realStr, cr.Distance, cr.latencyMs, ruleHash,
			path, sessionID,
			preview, reason,
		)
	}

	return fmt.Sprintf(
		"HIT ⚠️ MISMATCH — guardrails-cache (%s)\n"+
			"cache: %s   real: %s   distance: %.4f   latency: %.0fms   hash: %s\n"+
			"path: %s   session: %s\n\n"+
			"Input: %s\n\n"+
			"Real: %s   behaviour: %s\n"+
			"Reason: %s",
		direction,
		cacheStr, realStr, cr.Distance, cr.latencyMs, ruleHash,
		path, sessionID,
		preview,
		realStr, behaviour, reason,
	)
}

func classifyOutcome(cr *CheckResult, real *mcp.ValidationResult) string {
	if cr == nil || cr.Err != nil {
		return "sidecar_error"
	}
	if !cr.Hit {
		return "miss"
	}
	if cr.Allowed == real.Allowed {
		return "hit_match"
	}
	return "hit_mismatch"
}

func verdictStr(allowed bool) string {
	if allowed {
		return "ALLOWED"
	}
	return "BLOCKED"
}

func payloadPreview(s string) string {
	if len(s) <= maxPayloadPreview {
		return s
	}
	return s[:maxPayloadPreview] + "..."
}
