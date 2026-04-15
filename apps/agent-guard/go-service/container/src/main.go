package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

var (
	pythonServiceURL = getEnv("PYTHON_SERVICE_URL", "http://python-service:8092")
	defaultTimeout   = 120 * time.Second
)

// Environment variables for the LLM scanner path. All are optional; if
// SCANNER_LLM_PROVIDER is unset the service behaves exactly as before and
// requests with use_llm=true fall back to the Python path with a clear error.
const (
	EnvScannerLLMProvider = "SCANNER_LLM_PROVIDER" // "openai" | "anthropic"
	EnvOpenAIAPIKey       = "OPENAI_API_KEY"
	EnvOpenAIModel        = "OPENAI_MODEL"
	EnvAnthropicAPIKey    = "ANTHROPIC_API_KEY"
	EnvAnthropicModel     = "ANTHROPIC_MODEL"
)

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

type ScanRequest struct {
	ScannerType string                 `json:"scanner_type" binding:"required"`
	ScannerName string                 `json:"scanner_name" binding:"required"`
	Text        string                 `json:"text" binding:"required"`
	Config      map[string]interface{} `json:"config,omitempty"`
}

type ScanResponse struct {
	ScannerName   string                 `json:"scanner_name"`
	IsValid       bool                   `json:"is_valid"`
	RiskScore     float64                `json:"risk_score"`
	SanitizedText string                 `json:"sanitized_text"`
	Details       map[string]interface{} `json:"details,omitempty"`
	ExecutionTime float64                `json:"execution_time_ms"`
	Error         string                 `json:"error,omitempty"`
}

type ParallelScanRequest struct {
	Text     string        `json:"text" binding:"required"`
	Scanners []ScanRequest `json:"scanners" binding:"required,min=1"`
}

type ParallelScanResponse struct {
	RequestID    string         `json:"request_id"`
	TotalTime    float64        `json:"total_time_ms"`
	Results      []ScanResponse `json:"results"`
	FailureCount int            `json:"failure_count"`
	SuccessCount int            `json:"success_count"`
}

type StreamingScanRequest struct {
	Text     string        `json:"text" binding:"required"`
	Scanners []ScanRequest `json:"scanners" binding:"required,min=1"`
}

type ScannerClient struct {
	httpClient *http.Client
	baseURL    string
}

func NewScannerClient(baseURL string) *ScannerClient {
	return &ScannerClient{
		httpClient: &http.Client{Timeout: defaultTimeout},
		baseURL:    baseURL,
	}
}

func (c *ScannerClient) ScanText(ctx context.Context, req ScanRequest) (*ScanResponse, error) {
	startTime := time.Now()

	// use_llm is a transport-only flag consumed in Service.ScanText. Python's
	// scanner_service does scanner_class(**config), so any unknown key here
	// blows up scanner instantiation (e.g. Toxicity.__init__ got unexpected
	// keyword 'use_llm'). Strip it before forwarding. req is a value copy,
	// but Config is a map (reference) — so allocate a new map rather than
	// mutate the caller's.
	if _, leaked := req.Config[ConfigKeyUseLLM]; leaked {
		cleaned := make(map[string]interface{}, len(req.Config)-1)
		for k, v := range req.Config {
			if k == ConfigKeyUseLLM {
				continue
			}
			cleaned[k] = v
		}
		req.Config = cleaned
	}

	reqBody, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("marshal failed: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", c.baseURL+"/scan", bytes.NewBuffer(reqBody))
	if err != nil {
		return nil, fmt.Errorf("request creation failed: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read failed: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("scanner error: %s", string(body))
	}

	var scanResp ScanResponse
	if err := json.Unmarshal(body, &scanResp); err != nil {
		return nil, fmt.Errorf("parse failed: %w", err)
	}

	scanResp.ExecutionTime = float64(time.Since(startTime).Milliseconds())
	return &scanResp, nil
}

func (c *ScannerClient) HealthCheck(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, "GET", c.baseURL+"/health", nil)
	if err != nil {
		return err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("health check failed: %d", resp.StatusCode)
	}
	return nil
}

type Service struct {
	scannerClient *ScannerClient
	llmScanner    *LLMScanner // nil when no LLM provider is configured
}

func NewService(scannerClient *ScannerClient, llmScanner *LLMScanner) *Service {
	return &Service{scannerClient: scannerClient, llmScanner: llmScanner}
}

// ScanText is the single entry point used by all HTTP handlers. It routes the
// request to the LLM provider when the client opted in via config.use_llm and
// the scanner is supported, otherwise it falls through to the Python backend.
func (s *Service) ScanText(ctx context.Context, req ScanRequest) (*ScanResponse, error) {
	if useLLMRequested(req.Config) && supportsLLM(req.ScannerName) {
		if s.llmScanner == nil {
			// LLM mode asked for, but the server isn't configured. Fail open
			// (IsValid=true) and surface the condition so operators can fix
			// the deployment — matches gateway behavior for provider misconfig.
			return &ScanResponse{
				ScannerName:   req.ScannerName,
				IsValid:       true,
				RiskScore:     0,
				SanitizedText: req.Text,
				Error:         "LLM mode requested but SCANNER_LLM_PROVIDER is not configured on the server",
			}, nil
		}

		resp, err := s.llmScanner.Scan(ctx, req)
		if err == nil {
			return resp, nil
		}
		if err == ErrLLMUnsupportedScanner {
			// Quietly fall back to Python path for scanners the LLM doesn't handle.
			return s.scannerClient.ScanText(ctx, req)
		}
		// Hard error from the provider (network, auth, parse). Fail open with
		// an explanatory Error field; do not block traffic on LLM misbehavior.
		return &ScanResponse{
			ScannerName:   req.ScannerName,
			IsValid:       true,
			RiskScore:     0,
			SanitizedText: req.Text,
			Error:         fmt.Sprintf("LLM provider failed: %v", err),
		}, nil
	}
	return s.scannerClient.ScanText(ctx, req)
}

func (s *Service) RunParallelScans(ctx context.Context, req ParallelScanRequest) (*ParallelScanResponse, error) {
	startTime := time.Now()
	requestID := uuid.New().String()

	var wg sync.WaitGroup
	resultsChan := make(chan ScanResponse, len(req.Scanners))

	for _, scanner := range req.Scanners {
		wg.Add(1)
		go func(scanReq ScanRequest) {
			defer wg.Done()
			scanReq.Text = req.Text

			result, err := s.ScanText(ctx, scanReq)
			if err != nil {
				resultsChan <- ScanResponse{
					ScannerName:   scanReq.ScannerName,
					IsValid:       false,
					RiskScore:     1.0,
					SanitizedText: req.Text,
					Error:         err.Error(),
					ExecutionTime: 0,
				}
				return
			}
			resultsChan <- *result
		}(scanner)
	}

	go func() {
		wg.Wait()
		close(resultsChan)
	}()

	var results []ScanResponse
	successCount, failureCount := 0, 0

	for result := range resultsChan {
		results = append(results, result)
		if result.Error != "" {
			failureCount++
		} else {
			successCount++
		}
	}

	return &ParallelScanResponse{
		RequestID:    requestID,
		TotalTime:    float64(time.Since(startTime).Milliseconds()),
		Results:      results,
		FailureCount: failureCount,
		SuccessCount: successCount,
	}, nil
}

func (s *Service) RunStreamingScans(ctx context.Context, req StreamingScanRequest, resultsChan chan<- ScanResponse) {
	var wg sync.WaitGroup

	for _, scanner := range req.Scanners {
		wg.Add(1)
		go func(scanReq ScanRequest) {
			defer wg.Done()
			scanReq.Text = req.Text

			result, err := s.ScanText(ctx, scanReq)
			if err != nil {
				resultsChan <- ScanResponse{
					ScannerName:   scanReq.ScannerName,
					IsValid:       false,
					RiskScore:     1.0,
					SanitizedText: req.Text,
					Error:         err.Error(),
					ExecutionTime: 0,
				}
				return
			}
			resultsChan <- *result
		}(scanner)
	}

	go func() {
		wg.Wait()
		close(resultsChan)
	}()
}

func setupRouter(service *Service) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.Use(gin.Recovery())

	router.GET("/health", func(c *gin.Context) {
		ctx, cancel := context.WithTimeout(c.Request.Context(), 5*time.Second)
		defer cancel()

		if err := service.scannerClient.HealthCheck(ctx); err != nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"status": "unhealthy", "error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, gin.H{"status": "healthy", "service": "agent-guard-orchestrator"})
	})

	router.POST("/scan", func(c *gin.Context) {
		var req ScanRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		result, err := service.ScanText(c.Request.Context(), req)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, result)
	})

	router.POST("/scan/parallel", func(c *gin.Context) {
		var req ParallelScanRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		result, err := service.RunParallelScans(c.Request.Context(), req)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		c.JSON(http.StatusOK, result)
	})

	router.POST("/scan/stream", func(c *gin.Context) {
		var req StreamingScanRequest
		if err := c.ShouldBindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}

		c.Header("Content-Type", "text/event-stream")
		c.Header("Cache-Control", "no-cache")
		c.Header("Connection", "keep-alive")
		c.Header("X-Accel-Buffering", "no")

		resultsChan := make(chan ScanResponse)
		go service.RunStreamingScans(c.Request.Context(), req, resultsChan)

		c.Stream(func(w io.Writer) bool {
			if result, ok := <-resultsChan; ok {
				data, _ := json.Marshal(result)
				c.SSEvent("scan-result", string(data))
				return true
			}
			c.SSEvent("complete", "")
			return false
		})
	})

	return router
}

func main() {
	scannerClient := NewScannerClient(pythonServiceURL)
	// waitForPythonService(scannerClient);

	llmScanner := buildLLMScanner()
	service := NewService(scannerClient, llmScanner)
	router := setupRouter(service)

	printEnvVariables()
	port := getEnv("PORT", "8091")
	log.Printf("Starting go-service on :%s", port)
	if err := router.Run(":" + port); err != nil {
		log.Fatalf("Failed to start: %v", err)
	}
}

// buildLLMScanner constructs an LLMScanner from environment variables. Returns
// nil (with a log line) when no provider is configured or the configured one
// is missing credentials — in that case requests with use_llm=true surface a
// clear error from Service.ScanText instead of crashing the service.
func buildLLMScanner() *LLMScanner {
	providerName := strings.ToLower(strings.TrimSpace(os.Getenv(EnvScannerLLMProvider)))
	if providerName == "" {
		log.Printf("[LLMScanner] %s not set; LLM mode disabled", EnvScannerLLMProvider)
		return nil
	}

	httpClient := &http.Client{Timeout: defaultTimeout}

	var (
		provider LLMProvider
		err      error
	)
	switch providerName {
	case LLMProviderOpenAI:
		provider, err = NewOpenAIProvider(os.Getenv(EnvOpenAIAPIKey), os.Getenv(EnvOpenAIModel), httpClient)
	case LLMProviderAnthropic:
		provider, err = NewAnthropicProvider(os.Getenv(EnvAnthropicAPIKey), os.Getenv(EnvAnthropicModel), httpClient)
	default:
		log.Printf("[LLMScanner] unknown %s=%q (expected %q or %q); LLM mode disabled",
			EnvScannerLLMProvider, providerName, LLMProviderOpenAI, LLMProviderAnthropic)
		return nil
	}

	if err != nil {
		log.Printf("[LLMScanner] failed to initialize provider %q: %v; LLM mode disabled", providerName, err)
		return nil
	}
	return NewLLMScanner(provider)
}

func waitForPythonService(scannerClient *ScannerClient) {
	log.Println("Waiting for Python scanner service to be ready...")
	for i := 0; i < 30; i++ {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		err := scannerClient.HealthCheck(ctx)
		cancel()

		if err == nil {
			log.Println("Python scanner service is ready")
			break
		}

		if i == 29 {
			log.Fatal("Python scanner service did not become ready in time")
		}

		time.Sleep(2 * time.Second)
	}
}

func printEnvVariables() {
	log.Printf("PORT: %s", os.Getenv("PORT"))
	log.Printf("PYTHON_SERVICE_URL: %s", os.Getenv("PYTHON_SERVICE_URL"))
	log.Printf("GIN_MODE: %s", os.Getenv("GIN_MODE"))
	log.Printf("%s: %s", EnvScannerLLMProvider, os.Getenv(EnvScannerLLMProvider))
	log.Printf("%s: %s (default %s)", EnvOpenAIModel, os.Getenv(EnvOpenAIModel), DefaultOpenAIModel)
	log.Printf("%s: %s (default %s)", EnvAnthropicModel, os.Getenv(EnvAnthropicModel), DefaultAnthropicModel)
	log.Printf("%s set: %t", EnvOpenAIAPIKey, os.Getenv(EnvOpenAIAPIKey) != "")
	log.Printf("%s set: %t", EnvAnthropicAPIKey, os.Getenv(EnvAnthropicAPIKey) != "")
}
