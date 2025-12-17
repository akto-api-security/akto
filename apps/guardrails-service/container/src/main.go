package main

import (
	"context"
	"fmt"
	"os"

	"github.com/akto-api-security/guardrails-service/handlers"
	"github.com/akto-api-security/guardrails-service/pkg/config"
	"github.com/akto-api-security/guardrails-service/pkg/kafka"
	"github.com/akto-api-security/guardrails-service/pkg/validator"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

func main() {
	// Load configuration
	cfg := config.LoadConfig()

	// Initialize logger
	logger := initLogger(cfg.LogLevel)
	defer logger.Sync()

	logger.Info("Starting guardrails-service",
		zap.Int("port", cfg.ServerPort),
		zap.String("database_abstractor_url", cfg.DatabaseAbstractorURL),
		zap.String("agent_guard_engine_url", cfg.AgentGuardEngineURL),
		zap.Bool("kafka_enabled", cfg.KafkaEnabled))

	// Set environment variables for akto-gateway library
	if cfg.AgentGuardEngineURL != "" {
		os.Setenv("AGENT_GUARD_ENGINE_URL", cfg.AgentGuardEngineURL)
	}
	if cfg.DatabaseAbstractorToken != "" {
		os.Setenv("AKTO_API_TOKEN", cfg.DatabaseAbstractorToken)
	}

	// Initialize validator service
	validatorService, err := validator.NewService(cfg, logger)
	if err != nil {
		logger.Fatal("Failed to initialize validator service", zap.Error(err))
	}

	// Run in Kafka consumer mode or HTTP server mode based on configuration
	if cfg.KafkaEnabled {
		runKafkaConsumer(cfg, validatorService, logger)
	} else {
		runHTTPServer(cfg, validatorService, logger)
	}
}

// runHTTPServer starts the HTTP server mode
func runHTTPServer(cfg *config.Config, validatorService *validator.Service, logger *zap.Logger) {
	// Initialize handlers
	validationHandler := handlers.NewValidationHandler(validatorService, logger)

	// Setup Gin router
	router := setupRouter(validationHandler, logger)

	// Start server
	addr := fmt.Sprintf(":%d", cfg.ServerPort)
	logger.Info("Server starting in HTTP mode", zap.String("address", addr))

	if err := router.Run(addr); err != nil {
		logger.Fatal("Failed to start server", zap.Error(err))
	}
}

// runKafkaConsumer starts the Kafka consumer mode
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

func setupRouter(validationHandler *handlers.ValidationHandler, logger *zap.Logger) *gin.Engine {
	// Set Gin mode based on environment
	if os.Getenv("GIN_MODE") == "" {
		gin.SetMode(gin.ReleaseMode)
	}

	router := gin.New()

	// Middleware
	router.Use(gin.Recovery())
	router.Use(loggingMiddleware(logger))

	// Health check endpoint
	router.GET("/health", validationHandler.HealthCheck)

	// API routes
	api := router.Group("/api")
	{
		api.POST("/health", validationHandler.HealthCheck)

		// Batch ingestion endpoint (similar to mini-runtime-service)
		api.POST("/ingestData", validationHandler.IngestData)

		// Individual validation endpoints
		api.POST("/validate/request", validationHandler.ValidateRequest)
		api.POST("/validate/response", validationHandler.ValidateResponse)
	}

	return router
}

func initLogger(logLevel string) *zap.Logger {
	level := zapcore.InfoLevel
	switch logLevel {
	case "debug":
		level = zapcore.DebugLevel
	case "warn":
		level = zapcore.WarnLevel
	case "error":
		level = zapcore.ErrorLevel
	}

	config := zap.NewDevelopmentConfig()
	config.Level = zap.NewAtomicLevelAt(level)
	config.EncoderConfig.EncodeTime = zapcore.TimeEncoderOfLayout("2006-01-02 15:04:05")
	config.EncoderConfig.EncodeLevel = zapcore.CapitalColorLevelEncoder

	logger, err := config.Build()
	if err != nil {
		panic(fmt.Sprintf("Failed to initialize logger: %v", err))
	}

	return logger
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
