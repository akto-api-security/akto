package main

import (
	"context"
	"fmt"
	"net/http"
	_ "net/http/pprof"
	"os"
	"strconv"

	"time"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/depstats"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/guardcache"
	"github.com/akto-api-security/akto-endpoint-shield/utils"
	"github.com/akto-api-security/guardrails-service/handlers"
	"github.com/akto-api-security/guardrails-service/pkg/auth"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/dbabstractor"
	"github.com/akto-api-security/guardrails-service/pkg/filecache"
	"github.com/akto-api-security/guardrails-service/pkg/fileprocessor"
	"github.com/akto-api-security/guardrails-service/pkg/kafka"
	"github.com/akto-api-security/guardrails-service/pkg/logsink"
	"github.com/akto-api-security/guardrails-service/pkg/mediaprovider"
	"github.com/akto-api-security/guardrails-service/pkg/metrics"
	"github.com/akto-api-security/guardrails-service/pkg/nhi"
	"github.com/akto-api-security/guardrails-service/pkg/nhi/source/endpointshield"
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

	auth.InitModuleType(logger, "GUARDRAIL", cfg.DatabaseAbstractorToken, cfg.DatabaseAbstractorURL)

	logger.Info("Starting guardrails-service",
		zap.Int("port", cfg.ServerPort),
		zap.String("database_abstractor_url", cfg.DatabaseAbstractorURL),
		zap.String("scanner_api_url", scannerAPIURL()),
		zap.Bool("kafka_enabled", cfg.KafkaEnabled))

	if os.Getenv("GUARDRAILS_SKIP_PII_REDACTION") == "true" {
		logger.Warn("GUARDRAILS_SKIP_PII_REDACTION=true: async PII redaction disabled — load-test only, never use in production")
	}
	if os.Getenv("GUARDRAILS_SKIP_PII_SYNC") == "true" {
		logger.Warn("GUARDRAILS_SKIP_PII_SYNC=true: synchronous PII rules disabled — load-test only, never use in production")
	}

	if cfg.DatabaseAbstractorToken != "" {
		os.Setenv("AKTO_API_TOKEN", cfg.DatabaseAbstractorToken)
	}
	mcp.SetScannerAccountID(auth.AccountIDFromServiceToken())

	if cfg.ThreatKafka.Enabled {
		producer, err := kafka.NewThreatProducer(cfg, logger)
		if err != nil {
			logger.Fatal("Failed to create threat event producer", zap.Error(err))
		}
		defer producer.Close()

		mcp.SetThreatSink(producer.Sink())
		logger.Info("Threat events will be buffered on Kafka",
			zap.String("topic", cfg.ThreatKafka.Topic),
			zap.String("broker", cfg.ThreatKafka.BrokerURL))
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

// metricsFlushLoop periodically drains guardrail metrics and pushes them to the
// db-abstractor every 60s: per-account latency, instance-level CPU/memory/
// goroutine gauges, full-text semantic-cache hit/miss counts, and client-observed
// call health (errors, latency) for the two downstream dependencies — agent-guard
// (includes the Anonymize scanner) and the embedder (combined across the cache's
// single-text lookup and intentguard's batched calls). It blocks forever, so
// start it with `go`.
func metricsFlushLoop(dbClient *dbabstractor.Client, logger *zap.Logger, acc *metrics.Accumulator) {
	sysSampler, err := metrics.NewSystemSampler()
	if err != nil {
		// Non-fatal: the service still validates without instance metrics.
		logger.Warn("System metrics sampler unavailable; CPU/memory/cache/dependency gauges disabled", zap.Error(err))
	}

	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		for accountId, batch := range acc.DrainAll() {
			flushMetrics(dbClient, logger, accountId, batch)
		}
		// Instance-level CPU/memory/goroutine gauges, cache hit/miss counts,
		// and downstream-dependency call health, attributed to the service
		// token's account (see metrics.SystemSampler).
		if sysSampler != nil {
			sysBatch := sysSampler.Sample()
			cacheStats := guardcache.DrainStats()
			sysBatch = append(sysBatch, sysSampler.CacheStats(cacheStats.HitsExact, cacheStats.HitsFuzzy, cacheStats.Misses)...)
			agStats := depstats.AgentGuard.Drain()
			sysBatch = append(sysBatch, sysSampler.DependencyStats("GUARDRAIL_AGENT_GUARD", agStats.Calls, agStats.Errors, agStats.AvgLatencyMs)...)
			embStats := depstats.Embedder.Drain()
			sysBatch = append(sysBatch, sysSampler.DependencyStats("GUARDRAIL_EMBEDDER", embStats.Calls, embStats.Errors, embStats.AvgLatencyMs)...)
			if len(sysBatch) > 0 {
				flushMetrics(dbClient, logger, strconv.FormatInt(sysBatch[0].AccountId, 10), sysBatch)
			}
		}
	}
}

// flushMetrics POSTs one account's metric batch to the db-abstractor and logs
// each value sent so the emitted metrics are observable in the service logs.
func flushMetrics(dbClient *dbabstractor.Client, logger *zap.Logger, account string, batch []metrics.MetricData) {
	payload := make([]interface{}, len(batch))
	for i, m := range batch {
		payload[i] = m
	}
	if err := dbClient.IngestMetrics(payload); err != nil {
		logger.Error("Failed to flush guardrail metrics", zap.String("account", account), zap.Error(err))
		return
	}
	for _, m := range batch {
		logger.Info("Flushed guardrail metric",
			zap.String("account", account),
			zap.String("metricId", m.MetricId),
			zap.Float64("value", m.Value),
			zap.String("instance", m.InstanceId),
			zap.Int64("timestamp", m.Timestamp))
	}
}

func runHTTPServer(cfg *config.Config, validatorService *validator.Service, logger *zap.Logger) {
	startPprofIfEnabled(logger)

	fileRegistry := fileprocessor.DefaultRegistry(cfg.File.MaxTextFileBytes)
	registerMediaProcessors(fileRegistry, cfg, logger)

	acc := metrics.NewAccumulator()
	dbClient := dbabstractor.NewClient(logger)
	go metricsFlushLoop(dbClient, logger, acc)
	dbClient.StartHeartbeat(context.Background(), dbabstractor.HeartbeatInterval)

	if cfg.NhiEnabled {
		// One Publisher is shared by every Source. Adding new sources later
		// (browser extension, AI agent proxy) is just another go-routine here.
		publisher := nhi.NewPublisher(dbClient, logger)
		sources := []nhi.Source{
			endpointshield.NewSource(dbClient, publisher, logger,
				time.Duration(cfg.NhiScanIntervalMin)*time.Minute),
		}
		nhiCtx := context.Background()
		for _, src := range sources {
			go src.Run(nhiCtx)
		}
	} else {
		logger.Info("NHI sources disabled (NHI_ENABLED=false)")
	}

	fileCache := filecache.NewRedis(guardcache.GetRedisClient(), time.Duration(cfg.File.CacheTTLSec)*time.Second, logger)
	if fileCache == nil {
		logger.Info("File verdict cache off (needs REDIS_URL and FILE_VALIDATE_CACHE_TTL_SEC > 0)")
	} else {
		logger.Info("File verdict cache on", zap.Int("ttlSec", cfg.File.CacheTTLSec))
	}
	validationHandler := handlers.NewValidationHandler(validatorService, logger, cfg, fileRegistry, acc, fileCache)

	var authMiddleware gin.HandlerFunc
	if cfg.AuthEnabled {
		m, err := auth.NewMiddleware(cfg.RSAPublicKey, logger)
		if err != nil {
			logger.Fatal("Failed to initialize auth middleware", zap.Error(err))
		}
		authMiddleware = m
		logger.Info("Inbound JWT authentication enabled for /api endpoints")
	} else {
		logger.Warn("Inbound authentication disabled (set AKTO_GR_AUTHENTICATE=true to enable)")
	}

	router := setupRouter(validationHandler, authMiddleware, logger)

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

func setupRouter(validationHandler *handlers.ValidationHandler, authMiddleware gin.HandlerFunc, logger *zap.Logger) *gin.Engine {
	if os.Getenv("GIN_MODE") == "" {
		gin.SetMode(gin.ReleaseMode)
	}

	router := gin.New()

	router.Use(gin.Recovery())
	router.Use(corsMiddleware())
	router.Use(loggingMiddleware(logger))

	// Root health check stays unauthenticated for liveness probes (worker/orchestrator).
	router.GET("/health", validationHandler.HealthCheck)
	// Backpressure breaker snapshot — unauthenticated, for diagnostics/load tests.
	router.GET("/backpressure", validationHandler.BackpressureStatus)

	api := router.Group("/api")
	if authMiddleware != nil {
		api.Use(authMiddleware)
	}
	{
		api.POST("/health", validationHandler.HealthCheck)
		api.POST("/ingestData", validationHandler.IngestData)
		api.POST("/validate/request", validationHandler.ValidateRequest)
		api.POST("/validate/requestWithPolicy", validationHandler.ValidateRequestWithPolicy)
		api.POST("/validate/replayWithPolicy", validationHandler.ReplayWithPolicy)
		api.POST("/validate/response", validationHandler.ValidateResponse)
		api.POST("/validate/file", validationHandler.ValidateFile)
		api.POST("/redact/payloads", validationHandler.RedactPayloads)
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
	if mc.Provider == "none" {
		return
	}

	ocr, transcriber := newMediaProviders(mc, scannerAPIURL(), logger)

	if ocr != nil {
		registry.RegisterWithLimit(fileprocessor.NewImageProcessor(ocr, mc.MaxImageBytes), mc.MaxImageBytes)
		registry.RegisterWithLimit(fileprocessor.NewDocumentProcessor(ocr, mc.MaxDocumentImages, mc.MaxImageBytes), cfg.File.MaxTextFileBytes)
		logger.Info("Image processing enabled",
			zap.String("provider", mc.Provider),
			zap.Int("maxBytes", mc.MaxImageBytes),
			zap.Int("maxDocumentImages", mc.MaxDocumentImages))
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

// scannerAPIURL is agent-guard's base URL, resolved the way the scanner client resolves it
// (mcp.NewScannerClient): SCANNER_API_URL, else the library default. Image OCR (/ocr) goes to
// the same agent-guard as the text scans (/scan), so the two can't point at different ones.
func scannerAPIURL() string {
	if u := os.Getenv(utils.ScannerAPIURLEnv); u != "" {
		return u
	}
	return utils.DefaultScannerAPIURL
}

func newMediaProviders(mc config.MediaConfig, agentGuardURL string, logger *zap.Logger) (mediaprovider.OCRProvider, mediaprovider.TranscriptionProvider) {
	var ocr mediaprovider.OCRProvider
	var transcriber mediaprovider.TranscriptionProvider

	switch mc.Provider {
	case "azure":
		if mc.VisionAPIKey != "" && mc.VisionBaseURL != "" {
			ocr = mediaprovider.NewAzureVision(mc.VisionAPIKey, mc.VisionBaseURL)
		} else {
			logger.Warn("Image OCR disabled (missing MEDIA_VISION_API_KEY or MEDIA_VISION_BASE_URL)")
		}
		if mc.SpeechAPIKey != "" && mc.SpeechBaseURL != "" {
			transcriber = mediaprovider.NewAzureSpeech(mc.SpeechAPIKey, mc.SpeechBaseURL)
		} else {
			logger.Warn("Audio/video transcription disabled (missing MEDIA_SPEECH_API_KEY or MEDIA_SPEECH_BASE_URL)")
		}
	case "agent_guard":
		// Images go to agent-guard's /ocr (Gemma by default). agent-guard has no
		// transcription, so audio and video stay uninspected.
		if agentGuardURL != "" {
			ocr = mediaprovider.NewAgentGuardOCR(agentGuardURL)
		} else {
			logger.Warn("Image OCR disabled (no agent-guard URL: SCANNER_API_URL)")
		}
	default:
		logger.Warn("Media processing disabled (unknown MEDIA_PROVIDER)", zap.String("provider", mc.Provider))
	}
	return ocr, transcriber
}

// startPprofIfEnabled serves Go runtime profiles on PPROF_PORT (default 6060).
// Set PPROF_ENABLED=true only during profiling — not for production.
func startPprofIfEnabled(logger *zap.Logger) {
	if os.Getenv("PPROF_ENABLED") != "true" {
		return
	}
	port := 6060
	if raw := os.Getenv("PPROF_PORT"); raw != "" {
		if p, err := strconv.Atoi(raw); err == nil && p > 0 {
			port = p
		}
	}
	addr := fmt.Sprintf(":%d", port)
	go func() {
		logger.Info("pprof server listening", zap.String("addr", addr))
		if err := http.ListenAndServe(addr, nil); err != nil {
			logger.Error("pprof server stopped", zap.Error(err))
		}
	}()
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
