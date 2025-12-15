package kafka

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/segmentio/kafka-go"
	"github.com/segmentio/kafka-go/sasl/plain"
	"go.uber.org/zap"
)

// Consumer represents a Kafka consumer for processing API traffic
type Consumer struct {
	reader           *kafka.Reader
	validatorService *validator.Service
	logger           *zap.Logger
	config           *config.Config
	batchSize        int
}

// NewConsumer creates a new Kafka consumer
func NewConsumer(cfg *config.Config, validatorService *validator.Service, logger *zap.Logger) (*Consumer, error) {
	reader := createKafkaReader(cfg, logger)

	return &Consumer{
		reader:           reader,
		validatorService: validatorService,
		logger:           logger,
		config:           cfg,
		batchSize:        cfg.KafkaBatchSize,
	}, nil
}

// createKafkaReader creates and configures a Kafka reader
func createKafkaReader(cfg *config.Config, logger *zap.Logger) *kafka.Reader {
	dialer := &kafka.Dialer{
		Timeout:   10 * time.Second,
		DualStack: true,
	}

	// Configure TLS if enabled
	if cfg.KafkaUseTLS {
		tlsConfig, err := newTLSConfig()
		if err != nil {
			logger.Warn("Failed to create TLS config, continuing without TLS", zap.Error(err))
		} else {
			dialer.TLS = tlsConfig
		}
	}

	// Configure SASL authentication if credentials provided
	if cfg.KafkaUsername != "" && cfg.KafkaPassword != "" {
		dialer.SASLMechanism = plain.Mechanism{
			Username: cfg.KafkaUsername,
			Password: cfg.KafkaPassword,
		}
		logger.Info("Kafka SASL authentication configured", zap.String("username", cfg.KafkaUsername))
	}

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        []string{cfg.KafkaBrokerURL},
		Topic:          cfg.KafkaTopic,
		GroupID:        cfg.KafkaGroupID,
		Dialer:         dialer,
		MinBytes:       1,
		MaxBytes:       10e6, // 10MB
		MaxWait:        time.Duration(cfg.KafkaMaxWaitSec) * time.Second,
		CommitInterval: 1 * time.Second,
		StartOffset:    kafka.LastOffset,
	})

	logger.Info("Kafka reader created",
		zap.String("broker", cfg.KafkaBrokerURL),
		zap.String("topic", cfg.KafkaTopic),
		zap.String("groupID", cfg.KafkaGroupID),
		zap.Int("maxWaitSec", cfg.KafkaMaxWaitSec))

	return reader
}

// newTLSConfig creates a TLS configuration for Kafka
func newTLSConfig() (*tls.Config, error) {
	tlsCACertPath := os.Getenv("KAFKA_TLS_CA_CERT_PATH")
	if tlsCACertPath == "" {
		tlsCACertPath = "./ca.crt"
	}

	// Check if CA cert file exists
	if _, err := os.Stat(tlsCACertPath); os.IsNotExist(err) {
		// Return basic TLS config without custom CA
		return &tls.Config{
			InsecureSkipVerify: os.Getenv("KAFKA_INSECURE_SKIP_VERIFY") == "true",
			MinVersion:         tls.VersionTLS12,
		}, nil
	}

	caCert, err := os.ReadFile(tlsCACertPath)
	if err != nil {
		return nil, err
	}

	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	return &tls.Config{
		RootCAs:            caCertPool,
		InsecureSkipVerify: os.Getenv("KAFKA_INSECURE_SKIP_VERIFY") == "true",
		MinVersion:         tls.VersionTLS12,
	}, nil
}

// Start starts consuming messages from Kafka
func (c *Consumer) Start(ctx context.Context) error {
	c.logger.Info("Starting Kafka consumer",
		zap.String("topic", c.config.KafkaTopic),
		zap.Int("batchSize", c.batchSize),
		zap.Int("batchLingerSec", c.config.KafkaBatchLingerSec))

	// Setup signal handling for graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	// Create a cancellable context
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Handle shutdown signal
	go func() {
		sig := <-sigChan
		c.logger.Info("Received shutdown signal", zap.String("signal", sig.String()))
		cancel()
	}()

	batch := make([]models.IngestDataBatch, 0, c.batchSize)
	ticker := time.NewTicker(time.Duration(c.config.KafkaBatchLingerSec) * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			c.logger.Info("Context cancelled, processing remaining batch")
			if len(batch) > 0 {
				c.processBatch(ctx, batch)
			}
			return ctx.Err()

		case <-ticker.C:
			// Process batch on timeout if we have messages
			if len(batch) > 0 {
				c.logger.Debug("Processing batch on timeout", zap.Int("size", len(batch)))
				c.processBatch(ctx, batch)
				batch = batch[:0]
			}

		default:
			// Read message with timeout
			readCtx, readCancel := context.WithTimeout(ctx, 1*time.Second)
			msg, err := c.reader.ReadMessage(readCtx)
			readCancel()

			if err != nil {
				if errors.Is(err, context.DeadlineExceeded) {
					// Timeout is expected when no messages available, continue silently
					continue
				}
				if errors.Is(err, context.Canceled) {
					return nil
				}
				c.logger.Error("Failed to read message from Kafka", zap.Error(err))
				continue
			}

			// Parse the message
			data, err := c.parseMessage(msg.Value)
			if err != nil {
				c.logger.Error("Failed to parse Kafka message",
					zap.Error(err),
					zap.String("value", string(msg.Value)))
				continue
			}

			batch = append(batch, *data)

			// Process batch if full
			if len(batch) >= c.batchSize {
				c.logger.Debug("Processing full batch", zap.Int("size", len(batch)))
				c.processBatch(ctx, batch)
				batch = batch[:0]
			}
		}
	}
}

// parseMessage parses a Kafka message into IngestDataBatch
func (c *Consumer) parseMessage(value []byte) (*models.IngestDataBatch, error) {
	var data models.IngestDataBatch
	if err := json.Unmarshal(value, &data); err != nil {
		return nil, err
	}
	return &data, nil
}

// processBatch processes a batch of messages through the validator
func (c *Consumer) processBatch(ctx context.Context, batch []models.IngestDataBatch) {
	if len(batch) == 0 {
		return
	}

	c.logger.Info("Processing batch from Kafka", zap.Int("size", len(batch)))

	results, err := c.validatorService.ValidateBatch(ctx, batch)
	if err != nil {
		c.logger.Error("Failed to validate batch", zap.Error(err))
		return
	}

	// Log results summary
	blockedRequests := 0
	blockedResponses := 0
	for _, result := range results {
		if !result.RequestAllowed {
			blockedRequests++
		}
		if !result.ResponseAllowed {
			blockedResponses++
		}
	}

	c.logger.Info("Batch processing completed",
		zap.Int("total", len(batch)),
		zap.Int("blockedRequests", blockedRequests),
		zap.Int("blockedResponses", blockedResponses))
}

// Close closes the Kafka consumer
func (c *Consumer) Close() error {
	c.logger.Info("Closing Kafka consumer")
	return c.reader.Close()
}
