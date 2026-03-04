package config

import (
	"os"
	"strconv"
)

// Config holds the application configuration
type Config struct {
	// Server configuration
	ServerPort int

	// Database abstractor service
	DatabaseAbstractorURL   string
	DatabaseAbstractorToken string

	// Agent Guard Engine URL for NLP model computations
	AgentGuardEngineURL string

	// Threat backend service for publishing results to dashboard
	ThreatBackendURL   string
	ThreatBackendToken string

	// Logging
	LogLevel string

	// Kafka configuration
	KafkaEnabled        bool
	KafkaBrokerURL      string
	KafkaTopic          string
	KafkaGroupID        string
	KafkaUseTLS         bool
	KafkaUsername       string
	KafkaPassword       string
	KafkaBatchSize      int
	KafkaBatchLingerSec int // How long to wait before processing a partial batch (seconds)
	KafkaMaxWaitSec     int // Kafka fetch.max.wait - max time broker waits before returning data

	// Policy cache configuration
	PolicyRefreshIntervalMin int // How often to refresh policies from database (minutes)

	// Traffic filter configuration (mutually exclusive)
	// Supports comma-separated values for multiple matches, e.g., "api.example.com,app.example.com"
	// Also supports regex patterns by prefixing with "regex:", e.g., "regex:.*\\.example\\.com"
	FilterHost string // Filter traffic by host header (comma-separated or regex)
	FilterPath string // Filter traffic by path prefix (comma-separated or regex)

	// Session management configuration
	SessionSyncIntervalMin int  // Minutes between cyborg API syncs (default: 5)
	SessionEnabled         bool // Enable session-based guardrailing (default: true)
}

// LoadConfig loads configuration from environment variables
func LoadConfig() *Config {
	dbAbstractorToken := getEnv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "")
	return &Config{
		ServerPort:               getEnvAsInt("SERVER_PORT", 8080),
		DatabaseAbstractorURL:    getEnv("DATABASE_ABSTRACTOR_SERVICE_URL", "https://cyborg.akto.io"),
		DatabaseAbstractorToken:  dbAbstractorToken,
		AgentGuardEngineURL:      getEnv("AGENT_GUARD_ENGINE_URL", "https://akto-agent-guard-engine.billing-53a.workers.dev"),
		ThreatBackendURL:         getEnv("THREAT_BACKEND_URL", "https://tbs.akto.io"),
		ThreatBackendToken:       getEnv("THREAT_BACKEND_TOKEN", dbAbstractorToken),
		LogLevel:                 getEnv("LOG_LEVEL", "info"),
		KafkaEnabled:             getEnvAsBool("KAFKA_ENABLED", false),
		KafkaBrokerURL:           getEnv("KAFKA_BROKER_URL", "localhost:29092"),
		KafkaTopic:               getEnv("KAFKA_TOPIC", "akto.api.logs"),
		KafkaGroupID:             getEnv("KAFKA_GROUP_ID", "guardrails-service"),
		KafkaUseTLS:              getEnvAsBool("KAFKA_USE_TLS", false),
		KafkaUsername:            getEnv("KAFKA_USERNAME", ""),
		KafkaPassword:            getEnv("KAFKA_PASSWORD", ""),
		KafkaBatchSize:           getEnvAsInt("KAFKA_BATCH_SIZE", 100),
		KafkaBatchLingerSec:      getEnvAsInt("KAFKA_BATCH_LINGER_SEC", 5),
		KafkaMaxWaitSec:          getEnvAsInt("KAFKA_MAX_WAIT_SEC", 1),
		PolicyRefreshIntervalMin: getEnvAsInt("POLICY_REFRESH_INTERVAL_MIN", 15),
		FilterHost:               getEnv("FILTER_HOST", ""),
		FilterPath:               getEnv("FILTER_PATH", ""),
		SessionSyncIntervalMin:   getEnvAsInt("SESSION_SYNC_INTERVAL_MIN", 5),
		SessionEnabled:           getEnvAsBool("SESSION_ENABLED", true),
	}
}

// getEnv gets an environment variable or returns a default value
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// getEnvAsInt gets an environment variable as an integer or returns a default value
func getEnvAsInt(key string, defaultValue int) int {
	valueStr := os.Getenv(key)
	if valueStr == "" {
		return defaultValue
	}
	value, err := strconv.Atoi(valueStr)
	if err != nil {
		return defaultValue
	}
	return value
}

// getEnvAsBool gets an environment variable as a boolean or returns a default value
func getEnvAsBool(key string, defaultValue bool) bool {
	valueStr := os.Getenv(key)
	if valueStr == "" {
		return defaultValue
	}
	value, err := strconv.ParseBool(valueStr)
	if err != nil {
		return defaultValue
	}
	return value
}
