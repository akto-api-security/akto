package main

import (
	"context"
	"fmt"
	"net/http"
	"os"

	"time"

	"github.com/akto-api-security/akto-endpoint-shield/utils"
	"github.com/akto-api-security/guardrails-service/handlers"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"github.com/akto-api-security/guardrails-service/pkg/kafka"
	"github.com/akto-api-security/guardrails-service/pkg/logsink"
	"github.com/akto-api-security/guardrails-service/pkg/mediaprovider"
	"github.com/akto-api-security/guardrails-service/pkg/metrics"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

func main() {
	cfg := config.LoadConfig()

	logSink := logsink.NewAsyncSink(dbabstractor.NewLogClient())
	defer logSink.Close()

	logger := initLogger(cfg.LogLevel, logSink)
	defer logger.Sync()

	utils.SetLogger(logger)

	logger.Info("Starting guardrails-service",
		zap.Int("port", cfg.ServerPort),
		zap.String("database_abstractor_url", cfg.DatabaseAbstractorURL),
		zap.String("agent_guard_engine_url", cfg.AgentGuardEngineURL),
		zap.Bool("kafka_enabled", cfg.KafkaEnabled))

	if cfg.AgentGuardEngineURL != "" {
		os.Setenv("AGENT_GUARD_ENGINE_URL", cfg.AgentGuardEngineURL)
	}
	if cfg.DatabaseAbstractorToken != "" {
		os.Setenv("AKTO_API_TOKEN", cfg.DatabaseAbstractorToken)
	}

	validatorService, err := validator.NewService(cfg, logger)
	if err != nil {
		logger.Fatal("Failed to initialize validator service", zap.Error(err))
	}

	if cfg.KafkaEnabled {
		runKafkaConsumer(cfg, validatorService, logger)
	} else {
		runHTTPServer(cfg, validatorService, logger)
	}
}

func runHTTPServer(cfg *config.Config, validatorService *validator.Service, logger *zap.Logger) {
	// Wire async Kafka producer when enabled — WARN/ALERT LLM policies are deferred
	// to the Kafka consumer (CPU-based, jarvis.akto.io/v1) instead of GPU inline.
	if cfg.KafkaProducerEnabled {
		producer, err := kafka.NewProducer(cfg, logger)
		if err != nil {
			logger.Warn("Failed to create async Kafka producer; WARN/ALERT LLM policies will run inline",
				zap.Error(err))
		} else {
			validatorService.SetAsyncProducer(producer)
			logger.Info("Async Kafka producer attached",
				zap.String("broker", cfg.KafkaBrokerURL),
				zap.String("topic", cfg.KafkaTopic))
		}
	}

	fileRegistry := fileprocessor.DefaultRegistry(cfg.File.MaxTextFileBytes)
	registerMediaProcessors(fileRegistry, cfg, logger)

	acc := metrics.NewAccumulator()
	dbClient := dbabstractor.NewClient(logger)
	go func() {
		ticker := time.NewTicker(60 * time.Second)
		defer ticker.Stop()
		for range ticker.C {
			for accountId, batch := range acc.DrainAll() {
				payload := make([]interface{}, len(batch))
				for i, m := range batch {
					payload[i] = m
				}
				if err := dbClient.IngestMetrics(payload); err != nil {
					logger.Error("Failed to flush guardrail metrics", zap.String("account", accountId), zap.Error(err))
				}
			}
		}
	}()

	validationHandler := handlers.NewValidationHandler(validatorService, logger, cfg, fileRegistry, acc)

	router := setupRouter(validationHandler, logger)

	addr := fmt.Sprintf(":%d", cfg.ServerPort)
	logger.Info("Server starting in HTTP mode", zap.String("address", addr))

	if err := router.Run(addr); err != nil {
		logger.Fatal("Failed to start server", zap.Error(err))
	}
}

func runKafkaConsumer(cfg *config.Config, validatorService *validator.Service, logger *zap.Logger) {
	logger.Info("Starting in Kafka consumer mode",
		zap.String("broker", cfg.KafkaBrokerURL),
		zap.String("topic", cfg.KafkaTopic),
		zap.String("groupID", cfg.KafkaGroupID))

	consumer, err := kafka.NewConsumer(cfg, validatorService, logger)
	if err != nil {
		logger.Fatal("Failed to create Kafka consumer", zap.Error(err))
	}
	defer consumer.Close()

	ctx := context.Background()
	if err := consumer.Start(ctx); err != nil && err != context.Canceled {
		logger.Fatal("Kafka consumer stopped with error", zap.Error(err))
	}

	logger.Info("Kafka consumer stopped gracefully")
}

func corsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", "*")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}

func setupRouter(validationHandler *handlers.ValidationHandler, logger *zap.Logger) *gin.Engine {
	if os.Getenv("GIN_MODE") == "" {
		gin.SetMode(gin.ReleaseMode)
	}

	router := gin.New()

	router.Use(gin.Recovery())
	router.Use(corsMiddleware())
	router.Use(loggingMiddleware(logger))

	router.GET("/health", validationHandler.HealthCheck)

	api := router.Group("/api")
	{
		api.POST("/health", validationHandler.HealthCheck)
		api.POST("/ingestData", validationHandler.IngestData)
		api.POST("/validate/request", validationHandler.ValidateRequest)
		api.POST("/validate/requestWithPolicy", validationHandler.ValidateRequestWithPolicy)
		api.POST("/validate/response", validationHandler.ValidateResponse)
		api.POST("/validate/file", validationHandler.ValidateFile)
	}

	return router
}

func initLogger(logLevel string, logSink *logsink.AsyncSink) *zap.Logger {
	consoleLevel := parseLogLevel(logLevel)

	config := zap.NewDevelopmentConfig()
	config.Level = zap.NewAtomicLevelAt(consoleLevel)
	config.EncoderConfig.EncodeTime = zapcore.TimeEncoderOfLayout("2006-01-02 15:04:05.000")
	config.EncoderConfig.EncodeLevel = zapcore.CapitalColorLevelEncoder

	consoleLogger, err := config.Build()
	if err != nil {
		panic(fmt.Sprintf("Failed to initialize logger: %v", err))
	}

	if logSink == nil || !logSink.Enabled() {
		return consoleLogger
	}

	// Console respects LOG_LEVEL; DB receives all levels (debug and above).
	consoleCore := consoleLogger.Core()
	dbCore := logSink.NewCore(zapcore.DebugLevel)
	return zap.New(zapcore.NewTee(consoleCore, dbCore))
}

func parseLogLevel(logLevel string) zapcore.Level {
	switch logLevel {
	case "debug":
		return zapcore.DebugLevel
	case "warn":
		return zapcore.WarnLevel
	case "error":
		return zapcore.ErrorLevel
	default:
		return zapcore.InfoLevel
	}
}

func registerMediaProcessors(registry *fileprocessor.Registry, cfg *config.Config, logger *zap.Logger) {
	mc := cfg.File.Media
	if mc.Provider == "" {
		return
	}

	ocr, transcriber := newMediaProviders(mc, logger)

	if ocr != nil {
		registry.RegisterWithLimit(fileprocessor.NewImageProcessor(ocr, mc.MaxImageBytes), mc.MaxImageBytes)
		logger.Info("Image processing enabled",
			zap.String("provider", mc.Provider),
			zap.Int("maxBytes", mc.MaxImageBytes))
	}
	if transcriber != nil {
		registry.RegisterWithLimit(fileprocessor.NewAudioProcessor(transcriber, mc.MaxAudioBytes), mc.MaxAudioBytes)
		logger.Info("Audio processing enabled",
			zap.String("provider", mc.Provider),
			zap.Int("maxBytes", mc.MaxAudioBytes))

		registry.RegisterWithLimit(fileprocessor.NewVideoProcessor(transcriber, mc.MaxVideoBytes), mc.MaxVideoBytes)
		logger.Info("Video processing enabled",
			zap.String("provider", mc.Provider),
			zap.Int("maxBytes", mc.MaxVideoBytes))
	}
}

func newMediaProviders(mc config.MediaConfig, logger *zap.Logger) (mediaprovider.OCRProvider, mediaprovider.TranscriptionProvider) {
	var ocr mediaprovider.OCRProvider
	var transcriber mediaprovider.TranscriptionProvider

	visionReady := mc.VisionAPIKey != "" && mc.VisionBaseURL != ""
	speechReady := mc.SpeechAPIKey != "" && mc.SpeechBaseURL != ""

	if mc.VisionAPIKey == "" || mc.VisionBaseURL == "" {
		logger.Warn("Image OCR disabled (missing MEDIA_VISION_API_KEY or MEDIA_VISION_BASE_URL)")
	}
	if mc.SpeechAPIKey == "" || mc.SpeechBaseURL == "" {
		logger.Warn("Audio/video transcription disabled (missing MEDIA_SPEECH_API_KEY or MEDIA_SPEECH_BASE_URL)")
	}

	switch mc.Provider {
	case "azure":
		if visionReady {
			ocr = mediaprovider.NewAzureVision(mc.VisionAPIKey, mc.VisionBaseURL)
		}
		if speechReady {
			transcriber = mediaprovider.NewAzureSpeech(mc.SpeechAPIKey, mc.SpeechBaseURL)
		}
	}
	return ocr, transcriber
}

func loggingMiddleware(logger *zap.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		logger.Info("Request received",
			zap.String("method", c.Request.Method),
			zap.String("path", c.Request.URL.Path),
			zap.String("remote_addr", c.ClientIP()))

		c.Next()

		logger.Info("Request completed",
			zap.String("method", c.Request.Method),
			zap.String("path", c.Request.URL.Path),
			zap.Int("status", c.Writer.Status()))
	}
}
