package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/adapter"
	"github.com/akto-api-security/otel-ingestion-service/pkg/auth"
	"github.com/akto-api-security/otel-ingestion-service/pkg/config"
	"github.com/akto-api-security/otel-ingestion-service/pkg/keystore"
	"github.com/akto-api-security/otel-ingestion-service/pkg/otlp"
	"github.com/akto-api-security/otel-ingestion-service/pkg/pipeline"
	"github.com/akto-api-security/otel-ingestion-service/pkg/sink"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

func main() {
	cfg := config.Load()
	logger := newLogger(cfg.LogLevel)
	defer logger.Sync()

	keyProvider, err := keystore.New(keystore.Options{
		AuthEnabled:       cfg.AuthEnabled,
		RSAPublicKey:      cfg.RSAPublicKey,
		MongoConn:         cfg.MongoConn,
		MongoDB:           cfg.MongoDB,
		KeyRefreshMinutes: cfg.KeyRefreshMinutes,
		Logger:            logger,
	})
	if err != nil {
		logger.Fatal("keystore init failed", zap.Error(err))
	}
	defer func() {
		if keyProvider != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			_ = keyProvider.Close(ctx)
		}
	}()

	verifier := auth.NewVerifier(cfg.AuthEnabled, keyProvider, cfg.RevokedTokens)

	queue := pipeline.NewQueue(cfg.QueueSize)
	registry := adapter.NewRegistry()
	eventSink := sink.NewLoggingSink(logger, cfg.LogSensitive)
	workers := pipeline.NewWorkerPool(queue, registry, eventSink, logger, cfg.WorkerCount)

	handler := otlp.NewHandler(verifier, queue, cfg.MaxBatchBytes, logger)
	mux := http.NewServeMux()
	handler.Register(mux)

	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", cfg.ServerPort),
		Handler:      mux,
		ReadTimeout:  time.Duration(cfg.ReadTimeoutSec) * time.Second,
		WriteTimeout: time.Duration(cfg.WriteTimeoutSec) * time.Second,
		IdleTimeout:  time.Duration(cfg.IdleTimeoutSec) * time.Second,
	}

	go func() {
		keySource := "disabled"
		switch {
		case cfg.AuthEnabled && cfg.RSAPublicKey != "":
			keySource = "env:RSA_PUBLIC_KEY"
		case cfg.AuthEnabled && cfg.MongoConn != "":
			keySource = "mongo:common.configs/HYBRID_SAAS"
		}
		logger.Info("otel-ingestion-service starting",
			zap.Int("port", cfg.ServerPort),
			zap.Bool("auth_enabled", cfg.AuthEnabled),
			zap.String("key_source", keySource),
			zap.Int("queue_size", cfg.QueueSize),
			zap.Int("workers", cfg.WorkerCount))
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("server failed", zap.Error(err))
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(cfg.ShutdownTimeoutSec)*time.Second)
	defer cancel()
	_ = server.Shutdown(ctx)
	queue.Close()
	workers.Wait()
	logger.Info("otel-ingestion-service stopped")
}

func newLogger(level string) *zap.Logger {
	var zapLevel zapcore.Level
	if err := zapLevel.UnmarshalText([]byte(level)); err != nil {
		zapLevel = zapcore.InfoLevel
	}
	cfg := zap.NewProductionConfig()
	cfg.Level = zap.NewAtomicLevelAt(zapLevel)
	logger, err := cfg.Build()
	if err != nil {
		panic(err)
	}
	return logger
}
