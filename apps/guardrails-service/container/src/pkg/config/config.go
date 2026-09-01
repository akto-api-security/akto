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

	AuthEnabled  bool
	RSAPublicKey string

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

	// ThreatKafka buffers malicious events onto Kafka instead of POSTing them
	// straight to the threat backend, so events survive a backend outage. The
	// Java threat client (apps/threat-detection, GUARDRAILS_THREAT_CLIENT_ENABLED)
	// drains the topic and forwards to the backend.
	ThreatKafka ThreatKafkaConfig

	PolicyRefreshIntervalMin int

	McpAllowedListRefreshIntervalMin int

	CollectionRefreshIntervalMin int

	// Supports comma-separated or "regex:" prefixed patterns.
	FilterHost string
	FilterPath string

	SkipPaths string

	SessionSyncIntervalMin int
	SessionEnabled         bool

	NhiEnabled         bool
	NhiScanIntervalMin int

	// ValidationTimeoutMs bounds the whole synchronous validate path (all parallel
	// policy goroutines, their async scanner waits, and each /scan call) with one
	// deadline. Keep it below the caller's client timeout (data-ingestion's
	// GUARDRAILS_CLIENT_TIMEOUT_MS, default 3000) so a slow/saturated scanner
	// fails open here *before* the caller gives up. <=0 disables the bound.
	ValidationTimeoutMs int

	File FileConfig
}

// ThreatKafkaConfig is the producer half of the malicious-event buffer, read by
// guardrails-service running in HTTP mode. When Enabled is false the service
// keeps POSTing threat reports directly to the threat backend.
type ThreatKafkaConfig struct {
	Enabled   bool
	BrokerURL string
	Topic     string
	UseTLS    bool
	Username  string
	Password  string
}

type FileConfig struct {
	Enabled          bool
	MaxFiles         int
	MaxTextFileBytes int
	ChunkSize        int
	ChunkOverlap     int
	MaxChunks        int
	MaxRetries       int
	MaxConcurrent    int
	URLTimeoutSec    int

	// BlockOnRedaction fails an upload whose content a policy masked, instead of
	// letting it through. /api/validate/file answers with a verdict only — it has no
	// field to hand the masked text back in — so a "mask" verdict that does not block
	// means the original, unmasked file is what actually gets used. Defaults to true;
	// set FILE_VALIDATE_BLOCK_ON_REDACTION=false to go back to allowing them.
	BlockOnRedaction bool

	Media MediaConfig
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

// defaultChunkSize keeps one chunk small enough that its LLM adjudication finishes well
// inside the upstream ceiling. The LLM proxy (cyborg's LLMAction) has a hardcoded 60s read
// timeout and answers 422 past it, and a measured 9.3KB payload already cost 8-10s — so a
// 32000-char chunk could not reliably complete. Cost is per value enumerated, not just per
// byte, so a dense PII table is the worst case and this is sized for it.
const defaultChunkSize = 6000

// defaultMaxChunks derives the chunk ceiling from the byte cap so shrinking ChunkSize can
// never start rejecting files that used to pass. FILE_VALIDATE_MAX_CHUNKS still wins.
//
// Chunks advance by chunkSize-overlap, not chunkSize, so the overlap has to be in the
// divisor: at 5MB/6000 the naive figure is 874 but the real count is 904.
func defaultMaxChunks(maxTextFileBytes, chunkSize, overlap int) int {
	if chunkSize <= 0 {
		chunkSize = defaultChunkSize
	}
	advance := chunkSize - overlap
	if advance < 1 {
		advance = chunkSize
	}
	// +2 for the first chunk and the trailing partial one.
	return (maxTextFileBytes / advance) + 2
}

func LoadConfig() *Config {
	dbAbstractorToken := getEnv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "")
	maxTextFileBytes := getEnvAsInt("FILE_VALIDATE_MAX_TEXT_FILE_BYTES", 5*1024*1024)
	chunkSize := getEnvAsInt("FILE_VALIDATE_CHUNK_SIZE", defaultChunkSize)
	chunkOverlap := getEnvAsInt("FILE_VALIDATE_CHUNK_OVERLAP", 200)
	return &Config{
		ServerPort:                       getEnvAsInt("SERVER_PORT", 8080),
		DatabaseAbstractorURL:            getEnv("DATABASE_ABSTRACTOR_SERVICE_URL", "https://ultron.akto.io"),
		DatabaseAbstractorToken:          dbAbstractorToken,
		AgentGuardEngineURL:              getEnv("AGENT_GUARD_ENGINE_URL", "https://akto-agent-guard-engine.billing-53a.workers.dev"),
		ThreatBackendURL:                 getEnv("THREAT_BACKEND_URL", "https://tbs.akto.io"),
		ThreatBackendToken:               getEnv("THREAT_BACKEND_TOKEN", dbAbstractorToken),
		LogLevel:                         getEnv("LOG_LEVEL", "info"),
		AuthEnabled:                      getEnvAsBool("AKTO_GR_AUTHENTICATE", false),
		RSAPublicKey:                     getEnv("RSA_PUBLIC_KEY", ""),
		KafkaEnabled:                     getEnvAsBool("KAFKA_ENABLED", false),
		KafkaBrokerURL:                   getEnv("KAFKA_BROKER_URL", "localhost:29092"),
		KafkaTopic:                       getEnv("KAFKA_TOPIC", "akto.api.logs"),
		KafkaGroupID:                     getEnv("KAFKA_GROUP_ID", "guardrails-service"),
		KafkaUseTLS:                      getEnvAsBool("KAFKA_USE_TLS", false),
		KafkaUsername:                    getEnv("KAFKA_USERNAME", ""),
		KafkaPassword:                    getEnv("KAFKA_PASSWORD", ""),
		KafkaBatchSize:                   getEnvAsInt("KAFKA_BATCH_SIZE", 100),
		KafkaBatchLingerSec:              getEnvAsInt("KAFKA_BATCH_LINGER_SEC", 5),
		KafkaMaxWaitSec:                  getEnvAsInt("KAFKA_MAX_WAIT_SEC", 1),
		PolicyRefreshIntervalMin:         getEnvAsInt("POLICY_REFRESH_INTERVAL_MIN", 1),
		FilterHost:                       getEnv("FILTER_HOST", ""),
		FilterPath:                       getEnv("FILTER_PATH", ""),
		SkipPaths:                        getEnv("GUARDRAILS_SKIP_PATHS", ""),
		SessionSyncIntervalMin:           getEnvAsInt("SESSION_SYNC_INTERVAL_MIN", 5),
		SessionEnabled:                   getEnvAsBool("SESSION_ENABLED", true),
		McpAllowedListRefreshIntervalMin: getEnvAsInt("MCP_ALLOWLIST_REFRESH_INTERVAL_MIN", 1),
		CollectionRefreshIntervalMin:     getEnvAsInt("COLLECTION_REFRESH_INTERVAL_MIN", 5),
		NhiEnabled:                       getEnvAsBool("NHI_ENABLED", true),
		NhiScanIntervalMin:               getEnvAsInt("NHI_SCAN_INTERVAL_MIN", 30),
		ValidationTimeoutMs:              getEnvAsInt("GUARDRAILS_VALIDATION_TIMEOUT_MS", 2500),
		ThreatKafka:                      loadThreatKafkaConfig(),
		File: FileConfig{
			Enabled:          getEnvAsBool("FILE_VALIDATION_ENABLED", false),
			MaxFiles:         getEnvAsInt("FILE_VALIDATE_MAX_FILES", 5),
			MaxTextFileBytes: maxTextFileBytes,
			ChunkSize:        chunkSize,
			ChunkOverlap:     chunkOverlap,
			MaxChunks:        getEnvAsInt("FILE_VALIDATE_MAX_CHUNKS", defaultMaxChunks(maxTextFileBytes, chunkSize, chunkOverlap)),
			MaxRetries:       getEnvAsInt("FILE_VALIDATE_MAX_RETRIES", 2),
			MaxConcurrent:    getEnvAsInt("FILE_VALIDATE_MAX_CONCURRENT", 5),
			URLTimeoutSec:    getEnvAsInt("FILE_VALIDATE_URL_TIMEOUT_SEC", 30),
			BlockOnRedaction: getEnvAsBool("FILE_VALIDATE_BLOCK_ON_REDACTION", true),
			Media: MediaConfig{
				Provider:      getEnv("MEDIA_PROVIDER", ""),
				VisionAPIKey:  getEnv("MEDIA_VISION_API_KEY", ""),
				VisionBaseURL: getEnv("MEDIA_VISION_BASE_URL", ""),
				SpeechAPIKey:  getEnv("MEDIA_SPEECH_API_KEY", ""),
				SpeechBaseURL: getEnv("MEDIA_SPEECH_BASE_URL", ""),
				MaxImageBytes: getEnvAsInt("MEDIA_MAX_IMAGE_BYTES", 2*1024*1024),
				MaxAudioBytes: getEnvAsInt("MEDIA_MAX_AUDIO_BYTES", 10*1024*1024),
				MaxVideoBytes: getEnvAsInt("MEDIA_MAX_VIDEO_BYTES", 25*1024*1024),
			},
		},
	}
}

// DefaultThreatTopic is the buffer topic. Producer and threat client must agree
// on it; it is deliberately separate from the traffic-detector's
// akto.threat_detection.alerts so a guardrails backlog cannot starve alerts and
// retention can be sized independently.
const DefaultThreatTopic = "akto.threat_detection.guardrail_events"

func loadThreatKafkaConfig() ThreatKafkaConfig {
	return ThreatKafkaConfig{
		Enabled: getEnvAsBool("GUARDRAILS_THREAT_KAFKA_ENABLED", false),
		// Falls back to the traffic consumer's broker: most installs run one.
		BrokerURL: getEnv("GUARDRAILS_THREAT_KAFKA_BROKER_URL", getEnv("KAFKA_BROKER_URL", "")),
		Topic:     getEnv("GUARDRAILS_THREAT_KAFKA_TOPIC", DefaultThreatTopic),
		UseTLS:    getEnvAsBool("GUARDRAILS_THREAT_KAFKA_USE_TLS", false),
		// AKTO_KAFKA_* are the names the Helm charts and the Java modules
		// already use. Deliberately not chained to KAFKA_USERNAME/PASSWORD,
		// which belong to the traffic consumer and may target another cluster.
		Username: getEnv("GUARDRAILS_THREAT_KAFKA_USERNAME", getEnv("AKTO_KAFKA_USERNAME", "")),
		Password: getEnv("GUARDRAILS_THREAT_KAFKA_PASSWORD", getEnv("AKTO_KAFKA_PASSWORD", "")),
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
