package kafka

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"os"
	"os/signal"
	"regexp"
	"strings"
	"syscall"
	"time"

	"github.com/akto-api-security/guardrails-service/models"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/segmentio/kafka-go"
	"github.com/segmentio/kafka-go/sasl/plain"
	"go.uber.org/zap"
)

const regexPrefix = "regex:"

// Consumer represents a Kafka consumer for processing API traffic
type Consumer struct {
	reader           *kafka.Reader
	validatorService *validator.Service
	logger           *zap.Logger
	config           *config.Config
	batchSize        int
	// Pre-parsed filter values for performance
	filterHosts     []string
	filterHostRegex *regexp.Regexp
	filterPaths     []string
	filterPathRegex *regexp.Regexp
}

// NewConsumer creates a new Kafka consumer
func NewConsumer(cfg *config.Config, validatorService *validator.Service, logger *zap.Logger) (*Consumer, error) {
	reader := createKafkaReader(cfg, logger)

	consumer := &Consumer{
		reader:           reader,
		validatorService: validatorService,
		logger:           logger,
		config:           cfg,
		batchSize:        cfg.KafkaBatchSize,
	}

	// Parse filter configurations
	consumer.parseFilterConfig(logger)

	return consumer, nil
}

// parseFilterConfig parses the filter configuration from config
func (c *Consumer) parseFilterConfig(logger *zap.Logger) {
	// Parse host filter
	if c.config.FilterHost != "" {
		if strings.HasPrefix(c.config.FilterHost, regexPrefix) {
			pattern := strings.TrimPrefix(c.config.FilterHost, regexPrefix)
			compiled, err := regexp.Compile(pattern)
			if err != nil {
				logger.Error("Failed to compile host filter regex, filter disabled",
					zap.String("pattern", pattern),
					zap.Error(err))
			} else {
				c.filterHostRegex = compiled
				logger.Info("Host filter configured with regex",
					zap.String("pattern", pattern))
			}
		} else {
			// Comma-separated values
			hosts := strings.Split(c.config.FilterHost, ",")
			for _, host := range hosts {
				trimmed := strings.TrimSpace(host)
				if trimmed != "" {
					c.filterHosts = append(c.filterHosts, trimmed)
				}
			}
			if len(c.filterHosts) > 0 {
				logger.Info("Host filter configured with values",
					zap.Strings("hosts", c.filterHosts))
			}
		}
	}

	// Parse path filter
	if c.config.FilterPath != "" {
		if strings.HasPrefix(c.config.FilterPath, regexPrefix) {
			pattern := strings.TrimPrefix(c.config.FilterPath, regexPrefix)
			compiled, err := regexp.Compile(pattern)
			if err != nil {
				logger.Error("Failed to compile path filter regex, filter disabled",
					zap.String("pattern", pattern),
					zap.Error(err))
			} else {
				c.filterPathRegex = compiled
				logger.Info("Path filter configured with regex",
					zap.String("pattern", pattern))
			}
		} else {
			// Comma-separated values
			paths := strings.Split(c.config.FilterPath, ",")
			for _, path := range paths {
				trimmed := strings.TrimSpace(path)
				if trimmed != "" {
					c.filterPaths = append(c.filterPaths, trimmed)
				}
			}
			if len(c.filterPaths) > 0 {
				logger.Info("Path filter configured with values",
					zap.Strings("paths", c.filterPaths))
			}
		}
	}
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

	// Log filter configuration
	if c.filterHostRegex != nil || len(c.filterHosts) > 0 {
		c.logger.Info("Traffic filter active: filtering by host")
	} else if c.filterPathRegex != nil || len(c.filterPaths) > 0 {
		c.logger.Info("Traffic filter active: filtering by path")
	} else {
		c.logger.Info("No traffic filters configured, all traffic will be processed")
	}

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

			// Apply traffic filter
			if !c.filterTraffic(data) {
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

// filterTraffic checks if the traffic should pass through based on host or path filters.
// Returns true if traffic should be processed, false if it should be filtered out.
// If no filters are configured, all traffic passes through.
// Host and path filters are mutually exclusive - only one is applied at a time.
// Supports comma-separated values (uses contains matching) and regex patterns (prefixed with "regex:").
func (c *Consumer) filterTraffic(data *models.IngestDataBatch) bool {
	// If no filters configured, allow all traffic
	hasHostFilter := c.filterHostRegex != nil || len(c.filterHosts) > 0
	hasPathFilter := c.filterPathRegex != nil || len(c.filterPaths) > 0

	if !hasHostFilter && !hasPathFilter {
		return true
	}

	// Host filter takes precedence (mutually exclusive)
	if hasHostFilter {
		host := c.extractHost(data)
		if host == "" {
			c.logger.Debug("No host found in traffic, filtering out",
				zap.String("path", data.Path))
			return false
		}
		matches := c.matchHost(host)
		if !matches {
			c.logger.Debug("Host does not match filter, filtering out",
				zap.String("host", host))
		}
		return matches
	}

	// Path filter
	if hasPathFilter {
		matches := c.matchPath(data.Path)
		if !matches {
			c.logger.Debug("Path does not match filter, filtering out",
				zap.String("path", data.Path))
		}
		return matches
	}

	return true
}

// matchHost checks if the host matches the configured filter (regex or comma-separated values)
// Uses case-insensitive contains check for comma-separated values
func (c *Consumer) matchHost(host string) bool {
	if c.filterHostRegex != nil {
		return c.filterHostRegex.MatchString(host)
	}
	hostLower := strings.ToLower(host)
	for _, filterHost := range c.filterHosts {
		if strings.Contains(hostLower, strings.ToLower(filterHost)) {
			return true
		}
	}
	return false
}

// matchPath checks if the path matches the configured filter (regex or comma-separated values)
// Uses contains check for comma-separated values
func (c *Consumer) matchPath(path string) bool {
	if c.filterPathRegex != nil {
		return c.filterPathRegex.MatchString(path)
	}
	for _, filterPath := range c.filterPaths {
		if strings.Contains(path, filterPath) {
			return true
		}
	}
	return false
}

// extractHost extracts the host from request headers or path
func (c *Consumer) extractHost(data *models.IngestDataBatch) string {
	// Try to get host from request headers
	if data.RequestHeaders != "" {
		var headers map[string]interface{}
		if err := json.Unmarshal([]byte(data.RequestHeaders), &headers); err == nil {
			// Check for Host header (case-insensitive)
			for key, value := range headers {
				if strings.EqualFold(key, "host") {
					switch v := value.(type) {
					case string:
						return v
					case []interface{}:
						if len(v) > 0 {
							if s, ok := v[0].(string); ok {
								return s
							}
						}
					}
				}
			}
		}
	}

	// Try to extract host from path if it's a full URL
	if strings.HasPrefix(data.Path, "http://") || strings.HasPrefix(data.Path, "https://") {
		// Parse URL to extract host
		path := data.Path
		// Remove protocol
		if strings.HasPrefix(path, "https://") {
			path = strings.TrimPrefix(path, "https://")
		} else {
			path = strings.TrimPrefix(path, "http://")
		}
		// Extract host (before first slash)
		if idx := strings.Index(path, "/"); idx > 0 {
			return path[:idx]
		}
		return path
	}

	return ""
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
