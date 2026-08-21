package kafka

import (
	"crypto/tls"
	"crypto/x509"
	"os"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/segmentio/kafka-go/sasl/plain"
	"go.uber.org/zap"
)

// newDialer builds a Kafka dialer with optional TLS and SASL/PLAIN. It is
// shared by the traffic consumer, the threat producer, and the threat client so
// all three authenticate the same way against whichever broker they target.
//
// A TLS config that fails to build is logged and skipped rather than fatal,
// matching the traffic consumer's long-standing behaviour.
func newDialer(useTLS bool, username, password string, logger *zap.Logger) *kafka.Dialer {
	dialer := &kafka.Dialer{
		Timeout:   10 * time.Second,
		DualStack: true,
	}

	if useTLS {
		tlsConfig, err := newTLSConfig()
		if err != nil {
			logger.Warn("Failed to create TLS config, continuing without TLS", zap.Error(err))
		} else {
			dialer.TLS = tlsConfig
		}
	}

	if username != "" && password != "" {
		dialer.SASLMechanism = plain.Mechanism{
			Username: username,
			Password: password,
		}
		logger.Info("Kafka SASL authentication configured", zap.String("username", username))
	}

	return dialer
}

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
