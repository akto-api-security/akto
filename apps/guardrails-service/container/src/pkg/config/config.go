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
}

// LoadConfig loads configuration from environment variables
func LoadConfig() *Config {
	return &Config{
		ServerPort:              getEnvAsInt("SERVER_PORT", 8080),
		DatabaseAbstractorURL:   getEnv("DATABASE_ABSTRACTOR_SERVICE_URL", "https://cyborg.akto.io"),
		DatabaseAbstractorToken: getEnv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", ""),
		AgentGuardEngineURL:     getEnv("AGENT_GUARD_ENGINE_URL", "https://akto-agent-guard-engine.billing-53a.workers.dev"),
		ThreatBackendURL:        getEnv("THREAT_BACKEND_URL", "https://tbs.akto.io"),
		ThreatBackendToken:      getEnv("THREAT_BACKEND_TOKEN", ""),
		LogLevel:                getEnv("LOG_LEVEL", "info"),
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
