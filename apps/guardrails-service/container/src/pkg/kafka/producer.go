package kafka

import (
	"context"
	"encoding/json"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/segmentio/kafka-go"
	"github.com/segmentio/kafka-go/sasl/plain"
	"go.uber.org/zap"
)

// Producer writes IngestDataBatch messages to Kafka for async policy evaluation.
// It reuses the same broker/topic/TLS/SASL config as the consumer so no
// additional environment variables are needed beyond KAFKA_BROKER_URL and KAFKA_TOPIC.
type Producer struct {
	writer *kafka.Writer
	logger *zap.Logger
}

// NewProducer creates a Kafka writer. Uses the same broker, topic, TLS, and SASL
// settings as the consumer (config.KafkaBrokerURL / KafkaTopic / etc.).
func NewProducer(cfg *config.Config, logger *zap.Logger) (*Producer, error) {
	transport := &kafka.Transport{}

	if cfg.KafkaUseTLS {
		tlsConfig, err := newTLSConfig()
		if err != nil {
			logger.Warn("Kafka producer: failed to build TLS config, continuing without TLS", zap.Error(err))
		} else {
			transport.TLS = tlsConfig
		}
	}

	if cfg.KafkaUsername != "" && cfg.KafkaPassword != "" {
		transport.SASL = plain.Mechanism{
			Username: cfg.KafkaUsername,
			Password: cfg.KafkaPassword,
		}
	}

	writer := &kafka.Writer{
		Addr:         kafka.TCP(cfg.KafkaBrokerURL),
		Topic:        cfg.KafkaTopic,
		Transport:    transport,
		Async:        true, // non-blocking; errors are logged via ErrorLogger
		RequiredAcks: kafka.RequireOne,
	}

	logger.Info("Kafka async producer created",
		zap.String("broker", cfg.KafkaBrokerURL),
		zap.String("topic", cfg.KafkaTopic))

	return &Producer{writer: writer, logger: logger}, nil
}

// Produce serialises data as JSON and writes it to the Kafka topic.
func (p *Producer) Produce(ctx context.Context, data models.IngestDataBatch) error {
	payload, err := json.Marshal(data)
	if err != nil {
		return err
	}
	return p.writer.WriteMessages(ctx, kafka.Message{Value: payload})
}

// Close flushes pending messages and closes the underlying writer.
func (p *Producer) Close() error {
	return p.writer.Close()
}
