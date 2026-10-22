package validator

import (
	"context"
	"sync/atomic"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/models"
	"go.uber.org/zap"
	"golang.org/x/sync/errgroup"
)

// redactCounters tallies one RedactPayloads call for its summary log line.
type redactCounters struct {
	hits, misses, redacted, basic, unchanged, unavailable atomic.Int64
}

// RedactPayloads masks the payloads of a batch of endpoint calls for display, for example as stored
// sample data or an agent trace. It does not use guardrail policies: every payload gets the same
// complete redaction that threat events carry (mcp.RedactForDisplay), so the result depends on
// the payload alone and can be cached by it.
//
// Every side that was sent gets an explicit status; nothing that failed reads as clean:
//   - REDACTED: masked in full, payload changed
//   - UNCHANGED: masked in full, nothing needed masking
//   - REDACTED_BASIC: masked by the regex and secret scrub only because the Anonymize scanner did
//     not answer; safer than raw, weaker than REDACTED
//   - UNAVAILABLE: over the size cap, or the call's deadline passed before it was processed
//
// REDACTED and UNCHANGED are served from and stored in the shared cache. REDACTED_BASIC and
// UNAVAILABLE never are, so an outage cannot pin weaker redaction for the TTL.
func (s *Service) RedactPayloads(ctx context.Context, req *models.RedactPayloadsRequest) []models.RedactPayloadResult {
	results := make([]models.RedactPayloadResult, len(req.Items))
	counters := &redactCounters{}
	start := time.Now()

	if s.config.RedactTimeoutMs > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, time.Duration(s.config.RedactTimeoutMs)*time.Millisecond)
		defer cancel()
	}

	var group errgroup.Group
	group.SetLimit(max(s.config.RedactConcurrency, 1))
	for i := range req.Items {
		i := i
		group.Go(func() error {
			results[i] = s.redactItem(ctx, req.Items[i], counters)
			return nil
		})
	}
	_ = group.Wait() // redactItem reports its own failures per item and never returns an error

	s.logger.Info("RedactPayloads - completed",
		zap.Int("items", len(req.Items)),
		zap.Int64("cacheHits", counters.hits.Load()),
		zap.Int64("cacheMisses", counters.misses.Load()),
		zap.Int64("redacted", counters.redacted.Load()),
		zap.Int64("redactedBasic", counters.basic.Load()),
		zap.Int64("unchanged", counters.unchanged.Load()),
		zap.Int64("unavailable", counters.unavailable.Load()),
		zap.Int64("latencyMs", time.Since(start).Milliseconds()))
	return results
}

func (s *Service) redactItem(ctx context.Context, item models.RedactPayloadItem, counters *redactCounters) models.RedactPayloadResult {
	return models.RedactPayloadResult{
		ID:       item.ID,
		Request:  s.redactSide(ctx, item, item.RequestPayload, counters),
		Response: s.redactSide(ctx, item, item.ResponsePayload, counters),
	}
}

// redactSide returns nil when the side was not sent.
func (s *Service) redactSide(ctx context.Context, item models.RedactPayloadItem, payload string, counters *redactCounters) *models.RedactedPayloadSide {
	if payload == "" {
		return nil
	}
	unavailable := func() *models.RedactedPayloadSide {
		counters.unavailable.Add(1)
		return &models.RedactedPayloadSide{Status: models.RedactionUnavailable}
	}
	if limit := s.config.RedactMaxPayloadBytes; limit > 0 && len(payload) > limit {
		return unavailable()
	}
	// Items still queued behind the concurrency limit when the deadline passes are not started:
	// masking them under an expired context would only produce the weaker scrub.
	if ctx.Err() != nil {
		return unavailable()
	}

	key := mcp.RedactionCacheKey(item.Method, item.Host, item.Path, payload)
	if redacted, unchanged, ok := s.redactionCache.Get(key); ok {
		counters.hits.Add(1)
		if unchanged {
			counters.unchanged.Add(1)
			return &models.RedactedPayloadSide{Status: models.RedactionUnchanged}
		}
		counters.redacted.Add(1)
		return &models.RedactedPayloadSide{Status: models.RedactionRedacted, Payload: redacted}
	}
	counters.misses.Add(1)

	redactor := s.displayRedactor
	if redactor == nil {
		redactor = mcp.RedactForDisplay
	}
	result := redactor(ctx, payload)

	switch {
	case result.Basic:
		counters.basic.Add(1)
		return &models.RedactedPayloadSide{Status: models.RedactionRedactedBasic, Payload: result.Text}
	case result.Changed:
		s.redactionCache.Put(key, result.Text, false)
		counters.redacted.Add(1)
		return &models.RedactedPayloadSide{Status: models.RedactionRedacted, Payload: result.Text}
	default:
		s.redactionCache.Put(key, "", true)
		counters.unchanged.Add(1)
		return &models.RedactedPayloadSide{Status: models.RedactionUnchanged}
	}
}
