package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/segmentio/kafka-go"
	"go.uber.org/zap"
)

// ThreatProducer buffers malicious events onto Kafka so they survive a threat
// backend outage. It is installed as the endpoint-shield ThreatSink, which
// hands it the exact JSON body that would otherwise have been POSTed — the
// threat client re-POSTs those bytes unchanged.
type ThreatProducer struct {
	writer *kafka.Writer
	logger *zap.Logger
}

// NewThreatProducer creates a producer against cfg.ThreatKafka. It does not
// contact the broker; kafka-go dials lazily on first write.
func NewThreatProducer(cfg *config.Config, logger *zap.Logger) (*ThreatProducer, error) {
	tk := cfg.ThreatKafka
	if tk.BrokerURL == "" {
		return nil, fmt.Errorf("threat kafka enabled but no broker configured " +
			"(set GUARDRAILS_THREAT_KAFKA_BROKER_URL or KAFKA_BROKER_URL)")
	}

	dialer := newDialer(tk.UseTLS, tk.Username, tk.Password, logger)

	writer := kafka.NewWriter(kafka.WriterConfig{
		Brokers: []string{tk.BrokerURL},
		Topic:   tk.Topic,
		Dialer:  dialer,
		// Balancer is only consulted when a message carries no key. Keyed
		// messages hash to a partition so one session stays ordered.
		Balancer:     &kafka.Hash{},
		BatchTimeout: 200 * time.Millisecond,
		WriteTimeout: 10 * time.Second,
		// Leader-acked. Every broker we target runs RF=1, so this is one disk
		// either way, but it makes the write acknowledged rather than blind.
		RequiredAcks: int(kafka.RequireAll),
		// Synchronous on purpose. Async would return nil before the broker
		// answered, so a broker outage would look like success and the
		// direct-POST fallback in Sink could never fire. Every ReportThreat
		// call site already runs in its own goroutine, so blocking here costs
		// nothing on the request path.
		Async: false,
	})

	p := &ThreatProducer{writer: writer, logger: logger}

	logger.Info("Threat event Kafka producer created",
		zap.String("broker", tk.BrokerURL),
		zap.String("topic", tk.Topic),
		zap.Bool("tls", tk.UseTLS))

	return p, nil
}

// Sink adapts the producer to the endpoint-shield ThreatSink signature.
//
// If the buffer write fails — broker down, topic missing, message too large —
// it falls back to POSTing the event directly, which is what the service did
// before the buffer existed. Enabling the buffer can therefore never be worse
// than not having it.
func (p *ThreatProducer) Sink() mcp.ThreatSink {
	return func(ctx context.Context, body []byte) error {
		err := p.write(ctx, body)
		if err == nil {
			return nil
		}

		p.logger.Warn("Buffering threat event failed, falling back to direct POST",
			zap.Error(err))

		if postErr := mcp.PostThreatReportBody(ctx, body); postErr != nil {
			return fmt.Errorf("kafka write failed (%v) and direct post failed: %w", err, postErr)
		}
		return nil
	}
}

func (p *ThreatProducer) write(ctx context.Context, body []byte) error {
	msg := kafka.Message{
		Key:   partitionKey(body),
		Value: body,
	}
	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		return fmt.Errorf("writing threat event to kafka: %w", err)
	}
	return nil
}

func (p *ThreatProducer) Close() error {
	return p.writer.Close()
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
