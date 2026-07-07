package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	ServerPort         int
	LogLevel           string
	AuthEnabled        bool
	RSAPublicKey       string
	MongoConn          string
	MongoDB            string
	KeyRefreshMinutes  int
	RevokedTokens      map[string]struct{}
	QueueSize          int
	WorkerCount        int
	MaxBatchBytes      int
	LogSensitive       bool
	ReadTimeoutSec     int
	WriteTimeoutSec    int
	IdleTimeoutSec     int
	ShutdownTimeoutSec int
}

func Load() *Config {
	revoked := make(map[string]struct{})
	if raw := os.Getenv("AKTO_OTLP_REVOKED_TOKENS"); raw != "" {
		for _, t := range strings.Split(raw, ",") {
			if t = strings.TrimSpace(t); t != "" {
				revoked[t] = struct{}{}
			}
		}
	}

	return &Config{
		ServerPort:         envInt("SERVER_PORT", 8080),
		LogLevel:           envStr("LOG_LEVEL", "info"),
		AuthEnabled:        envBool("AKTO_OTLP_AUTHENTICATE", true),
		RSAPublicKey:       os.Getenv("RSA_PUBLIC_KEY"),
		MongoConn:          os.Getenv("AKTO_MONGO_CONN"),
		MongoDB:            envStr("AKTO_MONGO_DB", "common"),
		KeyRefreshMinutes:  envInt("OTLP_KEY_REFRESH_MIN", 5),
		RevokedTokens:      revoked,
		QueueSize:          envInt("OTLP_QUEUE_SIZE", 50000),
		WorkerCount:        envInt("OTLP_WORKER_COUNT", 8),
		MaxBatchBytes:      envInt("OTLP_MAX_BATCH_BYTES", 4*1024*1024),
		LogSensitive:       envBool("OTLP_LOG_SENSITIVE", false),
		ReadTimeoutSec:     envInt("OTLP_READ_TIMEOUT_SEC", 10),
		WriteTimeoutSec:    envInt("OTLP_WRITE_TIMEOUT_SEC", 10),
		IdleTimeoutSec:     envInt("OTLP_IDLE_TIMEOUT_SEC", 120),
		ShutdownTimeoutSec: envInt("OTLP_SHUTDOWN_TIMEOUT_SEC", 30),
	}
}

func envStr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}

func envBool(key string, fallback bool) bool {
	if v := os.Getenv(key); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return fallback
}
