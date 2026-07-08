package sink

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/akto-api-security/otel-ingestion-service/pkg/tenant"
	"go.uber.org/zap"
)

type HTTPSink struct {
	client *http.Client
	router *tenant.Router
	logger *zap.Logger
}

func NewHTTPSink(router *tenant.Router, timeout time.Duration, logger *zap.Logger) *HTTPSink {
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	return &HTTPSink{
		client: &http.Client{Timeout: timeout},
		router: router,
		logger: logger,
	}
}

func (s *HTTPSink) Emit(ctx context.Context, batch Batch) error {
	if len(batch.Events) == 0 {
		return nil
	}
	baseURL, err := s.router.DataIngestionURL(batch.AccountID)
	if err != nil {
		return err
	}

	body, err := toIngestDataRequest(batch)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, baseURL+"/api/ingestData", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if token := strings.TrimSpace(batch.AuthToken); token != "" {
		req.Header.Set("Authorization", token)
	}

	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("ingestData returned %d: %s", resp.StatusCode, strings.TrimSpace(string(respBody)))
	}
	_, _ = io.Copy(io.Discard, resp.Body)

	s.logger.Debug("ingestData forwarded",
		zap.Int("account_id", batch.AccountID),
		zap.Int("events", len(batch.Events)),
		zap.String("url", baseURL))
	return nil
}
