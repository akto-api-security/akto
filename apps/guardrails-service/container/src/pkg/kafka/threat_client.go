package kafka

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/segmentio/kafka-go"
	"go.uber.org/zap"
)

const (
	// initialBackoff is the first pause after a retryable failure; it doubles
	// up to ThreatClientConfig.MaxBackoffSec.
	initialBackoff = 1 * time.Second
	// forwardTimeout bounds a single POST to the threat backend. A hung
	// backend must not stall the drain forever.
	forwardTimeout = 30 * time.Second
)

// ThreatClient drains the malicious-event buffer and forwards each event to the
// threat backend.
//
// Delivery is at-least-once and deliberately so: an event is committed only
// after the backend has accepted it, and a 5xx is retried indefinitely without
// committing. A backend outage therefore costs latency and duplicates, never
// events. A 4xx is dropped and committed — the backend parses with a strict
// protobuf JSON parser, so a body it rejects once it will reject forever, and
// retrying would wedge the partition.
type ThreatClient struct {
	reader *kafka.Reader
	cfg    config.ThreatClientConfig
	logger *zap.Logger
}

// NewThreatClient creates the client against cfg.ThreatClient.
func NewThreatClient(cfg *config.Config, logger *zap.Logger) (*ThreatClient, error) {
	tc := cfg.ThreatClient
	if tc.BrokerURL == "" {
		return nil, fmt.Errorf("threat client enabled but no broker configured " +
			"(set GUARDRAILS_THREAT_CLIENT_KAFKA_BROKER_URL or KAFKA_BROKER_URL)")
	}

	dialer := newDialer(tc.UseTLS, tc.Username, tc.Password, logger)

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:  []string{tc.BrokerURL},
		Topic:    tc.Topic,
		GroupID:  tc.GroupID,
		Dialer:   dialer,
		MinBytes: 1,
		MaxBytes: 10e6,
		MaxWait:  1 * time.Second,
		// Prefetch depth. Commits are explicit (see run), so this only affects
		// how many messages are held in memory ahead of processing.
		QueueCapacity: tc.BatchSize,
		// CommitInterval 0 keeps commits synchronous: a commit must not race
		// ahead of a forward that has not succeeded yet.
		CommitInterval: 0,
		// Start from the beginning so a client deployed after the producer
		// still drains whatever is already buffered.
		StartOffset: kafka.FirstOffset,
	})

	return &ThreatClient{reader: reader, cfg: tc, logger: logger}, nil
}

// Start runs the drain loop until ctx is cancelled or a shutdown signal
// arrives. It returns the context error on shutdown.
func (t *ThreatClient) Start(ctx context.Context) error {
	t.logger.Info("Starting guardrails threat client",
		zap.String("topic", t.cfg.Topic),
		zap.String("groupID", t.cfg.GroupID),
		zap.Int("maxBackoffSec", t.cfg.MaxBackoffSec))

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	go func() {
		sig := <-sigChan
		t.logger.Info("Received shutdown signal", zap.String("signal", sig.String()))
		cancel()
	}()

	defer func() {
		if err := t.reader.Close(); err != nil {
			t.logger.Warn("Error closing threat client reader", zap.Error(err))
		}
	}()

	return t.run(ctx)
}

func (t *ThreatClient) run(ctx context.Context) error {
	for {
		// FetchMessage does not commit — that only happens once the backend
		// has taken the event.
		msg, err := t.reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				t.logger.Info("Threat client shutting down")
				return ctx.Err()
			}
			t.logger.Error("Error fetching threat event", zap.Error(err))
			continue
		}

		forwarded := t.forwardWithRetry(ctx, msg)
		if !forwarded {
			// Shutdown mid-retry: leave the offset uncommitted so the next
			// process redelivers this event.
			return ctx.Err()
		}

		if err := t.reader.CommitMessages(ctx, msg); err != nil {
			// The event reached the backend; a failed commit only means it
			// will be redelivered and duplicated.
			t.logger.Error("Failed to commit threat event offset",
				zap.Int64("offset", msg.Offset),
				zap.Error(err))
		}
	}
}

// forwardWithRetry POSTs msg to the threat backend, retrying retryable failures
// indefinitely. It returns true when the message may be committed — either the
// backend accepted it, or rejected it in a way retrying cannot fix. It returns
// false only when ctx is cancelled mid-retry.
func (t *ThreatClient) forwardWithRetry(ctx context.Context, msg kafka.Message) bool {
	maxBackoff := time.Duration(t.cfg.MaxBackoffSec) * time.Second
	backoff := initialBackoff
	attempt := 0

	for {
		attempt++

		attemptCtx, cancel := context.WithTimeout(ctx, forwardTimeout)
		err := mcp.PostThreatReportBody(attemptCtx, msg.Value)
		cancel()

		if err == nil {
			if attempt > 1 {
				t.logger.Info("Threat event forwarded after retries",
					zap.Int("attempts", attempt),
					zap.Int64("offset", msg.Offset))
			}
			return true
		}

		var apiErr *mcp.ThreatAPIError
		if errors.As(err, &apiErr) && !apiErr.Retryable() {
			t.logger.Error("Dropping threat event rejected by backend",
				zap.Int("status", apiErr.StatusCode),
				zap.String("responseBody", apiErr.Body),
				zap.Int64("offset", msg.Offset),
				zap.String("filterId", filterIDOf(msg.Value)),
				zap.Error(err))
			return true
		}

		if ctx.Err() != nil {
			return false
		}

		// Only the first failure and then every 10th are logged: an hour-long
		// outage should not produce an hour-long log flood.
		if attempt == 1 || attempt%10 == 0 {
			t.logger.Error("Failed to forward threat event, will retry",
				zap.Int("attempt", attempt),
				zap.Duration("backoff", backoff),
				zap.Int64("offset", msg.Offset),
				zap.Error(err))
		}

		select {
		case <-ctx.Done():
			return false
		case <-time.After(backoff):
		}

		if backoff < maxBackoff {
			backoff *= 2
			if backoff > maxBackoff {
				backoff = maxBackoff
			}
		}
	}
}

// threatEventEnvelope is the minimal view of the buffered body needed for
// partitioning and logging. The body is forwarded verbatim; this never
// re-serialises it.
type threatEventEnvelope struct {
	MaliciousEvent struct {
		Actor     string `json:"actor"`
		SessionID string `json:"sessionId"`
		FilterID  string `json:"filterId"`
	} `json:"maliciousEvent"`
}

func parseEnvelope(body []byte) threatEventEnvelope {
	var env threatEventEnvelope
	// A body we cannot parse is still forwarded; the backend is the authority
	// on what is valid, so this only degrades the partition key and log detail.
	_ = json.Unmarshal(body, &env)
	return env
}

// partitionKey keeps one session's events on one partition, so they stay
// ordered relative to each other. Falls back to the actor, then to nil (which
// lets the balancer spread the message).
func partitionKey(body []byte) []byte {
	env := parseEnvelope(body)
	if env.MaliciousEvent.SessionID != "" {
		return []byte(env.MaliciousEvent.SessionID)
	}
	if env.MaliciousEvent.Actor != "" {
		return []byte(env.MaliciousEvent.Actor)
	}
	return nil
}

func filterIDOf(body []byte) string {
	return parseEnvelope(body).MaliciousEvent.FilterID
}
