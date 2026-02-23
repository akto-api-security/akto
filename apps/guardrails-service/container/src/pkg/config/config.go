package config

import (
	"os"
	"strconv"
)

type Config struct {
	ServerPort int

	DatabaseAbstractorURL   string
	DatabaseAbstractorToken string
	AgentGuardEngineURL     string
	ThreatBackendURL        string
	ThreatBackendToken      string
	LogLevel                string

	KafkaEnabled        bool
	KafkaBrokerURL      string
	KafkaTopic          string
	KafkaGroupID        string
	KafkaUseTLS         bool
	KafkaUsername       string
	KafkaPassword       string
	KafkaBatchSize      int
	KafkaBatchLingerSec int
	KafkaMaxWaitSec     int

	PolicyRefreshIntervalMin int

	// Supports comma-separated or "regex:" prefixed patterns.
	FilterHost string
	FilterPath string

	SessionSyncIntervalMin int
	SessionEnabled         bool

	File FileConfig
}

type FileConfig struct {
	MaxFiles      int
	MaxSizeBytes  int
	ChunkSize     int
	ChunkOverlap  int
	MaxChunks     int
	MaxRetries    int
	MaxConcurrent int
	URLTimeoutSec int
	Media         MediaConfig
}

// MediaConfig holds configuration for external media processing APIs.
// Vision and Speech have independent key/endpoint pairs for separate Azure resources.
type MediaConfig struct {
	Provider      string // "azure" or "" (disabled)
	VisionAPIKey  string
	VisionBaseURL string
	SpeechAPIKey  string
	SpeechBaseURL string
	MaxImageBytes int
	MaxAudioBytes int
	MaxVideoBytes int
}

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
		File: FileConfig{
			MaxFiles:      getEnvAsInt("FILE_VALIDATE_MAX_FILES", 5),
			MaxSizeBytes:  getEnvAsInt("FILE_VALIDATE_MAX_SIZE_BYTES", 2*1024*1024),
			ChunkSize:     getEnvAsInt("FILE_VALIDATE_CHUNK_SIZE", 32000),
			ChunkOverlap:  getEnvAsInt("FILE_VALIDATE_CHUNK_OVERLAP", 200),
			MaxChunks:     getEnvAsInt("FILE_VALIDATE_MAX_CHUNKS", 500),
			MaxRetries:    getEnvAsInt("FILE_VALIDATE_MAX_RETRIES", 2),
			MaxConcurrent: getEnvAsInt("FILE_VALIDATE_MAX_CONCURRENT", 5),
			URLTimeoutSec: getEnvAsInt("FILE_VALIDATE_URL_TIMEOUT_SEC", 30),
			Media: MediaConfig{
				Provider:      getEnv("MEDIA_PROVIDER", ""),
				VisionAPIKey:  getEnv("MEDIA_VISION_API_KEY", ""),
				VisionBaseURL: getEnv("MEDIA_VISION_BASE_URL", ""),
				SpeechAPIKey:  getEnv("MEDIA_SPEECH_API_KEY", ""),
				SpeechBaseURL: getEnv("MEDIA_SPEECH_BASE_URL", ""),
				MaxImageBytes: getEnvAsInt("MEDIA_MAX_IMAGE_BYTES", 4*1024*1024),
				MaxAudioBytes: getEnvAsInt("MEDIA_MAX_AUDIO_BYTES", 10*1024*1024),
				MaxVideoBytes: getEnvAsInt("MEDIA_MAX_VIDEO_BYTES", 25*1024*1024),
			},
		},
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

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
