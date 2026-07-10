package session

import (
	"context"
	"fmt"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp/guardcache"
	redis "github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

const (
	AnomalyTypeToolCallRate = "ToolCallRateExceeded"
	AnomalyTypeErrorRate = "ErrorRateExceeded"

	anomalyKeyPrefix = "anomaly:"
	redisTimeout     = 50 * time.Millisecond
	counterTTL       = 14400 // 4h — auto-cleanup for inactive sessions
)

// getRedisClient reuses the shared Redis connection pool from guardcache
// (akto-gateway). Returns nil when REDIS_URL is unset (fail-open).
func getRedisClient() *redis.Client {
	return guardcache.GetRedisClient()
}

type AnomalyEvent struct {
	SessionID   string
	AnomalyType string
	Details     string
	Timestamp   time.Time
}

// AnomalyDetector is a stateless Redis client for anomaly detection.
// Config (limits, policy name, behaviour) is passed per-call so multiple
// scoped policies can share one detector instance.
type AnomalyDetector struct {
	client *redis.Client
	logger *zap.Logger
}

// NewAnomalyDetector creates a Redis-backed anomaly detector. Returns nil if
// Redis is unavailable (fail-open: all detection calls become no-ops).
func NewAnomalyDetector(logger *zap.Logger) *AnomalyDetector {
	client := getRedisClient()
	if client == nil {
		logger.Warn("Anomaly detection disabled: no Redis URL configured")
		return nil
	}

	// Quick connectivity check (fail-open)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if err := client.Ping(ctx).Err(); err != nil {
		logger.Warn("Anomaly detection: Redis ping failed (will retry on use)", zap.Error(err))
	}

	logger.Info("Anomaly detector initialized")
	return &AnomalyDetector{client: client, logger: logger}
}

// RecordToolCall records a tool call for the session under a specific policy.
// Returns (event, breached). event is non-nil only on first breach (for reporting).
// breached is true whenever the limit has been reached or exceeded (for blocking).
func (d *AnomalyDetector) RecordToolCall(ctx context.Context, sessionID, policyName string, limit int) (*AnomalyEvent, bool) {
	if d == nil || sessionID == "" || limit <= 0 {
		return nil, false
	}

	counterKey := anomalyKeyPrefix + "tc:" + policyName + ":" + sessionID
	firedKey := anomalyKeyPrefix + "fired:" + policyName + ":" + sessionID + ":" + AnomalyTypeToolCallRate

	rctx, cancel := context.WithTimeout(ctx, redisTimeout)
	defer cancel()

	result, err := luaCounter.Run(rctx, d.client,
		[]string{counterKey, firedKey},
		limit,
		counterTTL,
	).Int()

	if err != nil {
		d.logger.Debug("Anomaly counter script failed (fail-open)", zap.Error(err))
		return nil, false
	}

	details := fmt.Sprintf("Tool call limit exceeded: %d calls per session (policy: %s)", limit, policyName)
	switch result {
	case 1:
		return &AnomalyEvent{
			SessionID:   sessionID,
			AnomalyType: AnomalyTypeToolCallRate,
			Details:     details,
			Timestamp:   time.Now(),
		}, true
	case 2:
		return nil, true
	}
	return nil, false
}

// RecordError records an error for the session under a specific policy.
// Returns (event, breached). event is non-nil only on first breach (for reporting).
// breached is true whenever the limit has been reached or exceeded (for blocking).
func (d *AnomalyDetector) RecordError(ctx context.Context, sessionID, policyName string, limit int) (*AnomalyEvent, bool) {
	if d == nil || sessionID == "" || limit <= 0 {
		return nil, false
	}

	counterKey := anomalyKeyPrefix + "err:" + policyName + ":" + sessionID
	firedKey := anomalyKeyPrefix + "fired:" + policyName + ":" + sessionID + ":" + AnomalyTypeErrorRate

	rctx, cancel := context.WithTimeout(ctx, redisTimeout)
	defer cancel()

	result, err := luaCounter.Run(rctx, d.client,
		[]string{counterKey, firedKey},
		limit,
		counterTTL,
	).Int()

	if err != nil {
		d.logger.Debug("Anomaly counter script failed (fail-open)", zap.Error(err))
		return nil, false
	}

	details := fmt.Sprintf("Error limit exceeded: %d errors per session (policy: %s)", limit, policyName)
	switch result {
	case 1:
		return &AnomalyEvent{
			SessionID:   sessionID,
			AnomalyType: AnomalyTypeErrorRate,
			Details:     details,
			Timestamp:   time.Now(),
		}, true
	case 2:
		return nil, true
	}
	return nil, false
}
