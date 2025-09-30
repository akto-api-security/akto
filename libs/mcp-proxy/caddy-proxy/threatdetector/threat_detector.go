package threatdetector

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/caddyserver/caddy/v2"
	"github.com/caddyserver/caddy/v2/caddyconfig/caddyfile"
	"github.com/caddyserver/caddy/v2/caddyconfig/httpcaddyfile"
	"github.com/caddyserver/caddy/v2/modules/caddyhttp"

	clientpkg "github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/client"
	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
	"go.uber.org/zap"
)

func init() {
	caddy.RegisterModule(ThreatDetector{})
	httpcaddyfile.RegisterHandlerDirective("threat_detector", parseCaddyfile)
}

// ThreatDetector is a Caddy HTTP handler module that validates requests
// using the mcp-threat library via direct function calls
type ThreatDetector struct {
	// LLM Configuration
	LLMProvider    string  `json:"llm_provider,omitempty"`
	LLMModel       string  `json:"llm_model,omitempty"`
	LLMAPIKey      string  `json:"llm_api_key,omitempty"`
	LLMTimeout     int     `json:"llm_timeout,omitempty"`
	LLMTemperature float64 `json:"llm_temperature,omitempty"`
	Debug          bool    `json:"debug,omitempty"`

	validator *clientpkg.MCPValidator
	logger    *zap.Logger
}

// SSEResponseWriter wraps http.ResponseWriter to rewrite SSE URLs
type SSEResponseWriter struct {
	http.ResponseWriter
	proxyPrefix string
	urlPattern  *regexp.Regexp
}

func NewSSEResponseWriter(w http.ResponseWriter, proxyPrefix string) *SSEResponseWriter {
	// Pattern to match SSE data lines with URLs like "data: /message?..."
	pattern := regexp.MustCompile(`(data:\s*)(\/[^\s\r\n]*)`)
	return &SSEResponseWriter{
		ResponseWriter: w,
		proxyPrefix:    proxyPrefix,
		urlPattern:     pattern,
	}
}

func (w *SSEResponseWriter) Write(data []byte) (int, error) {
	// Only rewrite if the data contains URLs that need rewriting
	if w.urlPattern.Match(data) {
		// Rewrite URLs in SSE data
		rewritten := w.urlPattern.ReplaceAllFunc(data, func(match []byte) []byte {
			parts := w.urlPattern.FindSubmatch(match)
			if len(parts) == 3 {
				prefix := parts[1]      // "data: "
				url := string(parts[2]) // "/message?..."
				// Rewrite to include proxy prefix: "/proxy/kite/message?..."
				newURL := w.proxyPrefix + url
				return append(prefix, []byte(newURL)...)
			}
			return match
		})
		return w.ResponseWriter.Write(rewritten)
	}

	// Pass through unchanged data directly
	return w.ResponseWriter.Write(data)
}

// CaddyModule returns the Caddy module information
func (ThreatDetector) CaddyModule() caddy.ModuleInfo {
	return caddy.ModuleInfo{
		ID:  "http.handlers.threat_detector",
		New: func() caddy.Module { return new(ThreatDetector) },
	}
}

// Provision sets up the threat detector
func (td *ThreatDetector) Provision(ctx caddy.Context) error {
	td.logger = ctx.Logger(td)

	// Read from environment variables if not set in config
	if td.LLMProvider == "" {
		td.LLMProvider = getEnvOrDefault("MCP_LLM_PROVIDER", "openai")
	}
	if td.LLMModel == "" {
		td.LLMModel = getEnvOrDefault("MCP_LLM_MODEL", "gpt-4")
	}
	if td.LLMAPIKey == "" {
		td.LLMAPIKey = os.Getenv("OPENAI_API_KEY")
	}
	if td.LLMTimeout == 0 {
		if timeoutStr := os.Getenv("MCP_LLM_TIMEOUT"); timeoutStr != "" {
			if timeout, err := strconv.Atoi(timeoutStr); err == nil {
				td.LLMTimeout = timeout
			} else {
				td.LLMTimeout = 60
			}
		} else {
			td.LLMTimeout = 60
		}
	}
	if td.LLMTemperature == 0 {
		if tempStr := os.Getenv("MCP_LLM_TEMPERATURE"); tempStr != "" {
			if temp, err := strconv.ParseFloat(tempStr, 64); err == nil {
				td.LLMTemperature = temp
			}
		}
	}
	if debugStr := os.Getenv("MCP_DEBUG"); debugStr == "true" {
		td.Debug = true
	}

	// Create MCP validator configuration
	config := &types.AppConfig{
		LLM: types.LLMConfig{
			ProviderType: td.LLMProvider,
			Model:        td.LLMModel,
			Timeout:      td.LLMTimeout,
			Temperature:  td.LLMTemperature,
		},
		Debug: td.Debug,
	}

	// Set API key if provided
	if td.LLMAPIKey != "" {
		config.LLM.APIKey = &td.LLMAPIKey
	}

	// Create validator - THIS IS A DIRECT IN-PROCESS INSTANTIATION
	var err error
	td.validator, err = clientpkg.NewMCPValidatorWithConfig(config)
	if err != nil {
		return fmt.Errorf("failed to create MCP validator: %v", err)
	}

	td.logger.Info("threat detector initialized",
		zap.String("provider", td.LLMProvider),
		zap.String("model", td.LLMModel),
		zap.Bool("debug", td.Debug))

	return nil
}

// ServeHTTP handles the HTTP request - this is called for every request
func (td *ThreatDetector) ServeHTTP(w http.ResponseWriter, r *http.Request, next caddyhttp.Handler) error {
	// Check if this is an SSE request - skip validation for SSE streams
	// SSE endpoints typically have /sse in the path or Accept: text/event-stream header
	isSSE := strings.Contains(r.URL.Path, "/sse") ||
		r.Header.Get("Accept") == "text/event-stream" ||
		strings.Contains(r.Header.Get("Accept"), "text/event-stream")

	// Check if this is a MCP message endpoint (like /message?sessionId=...)
	isMCPMessage := strings.Contains(r.URL.Path, "/message") && strings.Contains(r.URL.RawQuery, "sessionId")

	// Prepare to use SSE writer if this is an SSE request
	var responseWriter http.ResponseWriter = w
	if isSSE {
		td.logger.Debug("SSE request detected, will use SSE writer for response",
			zap.String("path", r.URL.Path))

		proxyPrefix := "/proxy/kite" // Default to kite
		if strings.Contains(r.URL.Path, "/proxy/github") {
			proxyPrefix = "/proxy/github"
		}

		responseWriter = NewSSEResponseWriter(w, proxyPrefix)
	}

	if isMCPMessage {
		// For MCP message endpoints, we need to validate the actual MCP request body
		// This is where the real threat detection happens
		td.logger.Debug("MCP message request detected, performing full validation",
			zap.String("path", r.URL.Path))
	}

	// Regular (non-SSE) request handling
	var bodyContent string
	if r.Body != nil {
		body, err := io.ReadAll(r.Body)
		if err == nil {
			bodyContent = string(body)
			// Restore body for downstream handlers
			r.Body = io.NopCloser(bytes.NewReader(body))
		}
	}

	// Prepare headers for validation
	headers := make(map[string]string)
	for key, values := range r.Header {
		if len(values) > 0 {
			headers[key] = values[0]
		}
	}

	// Create MCP payload
	mcpPayload := map[string]interface{}{
		"method":  r.Method,
		"uri":     r.URL.Path,
		"headers": headers,
		"body":    bodyContent,
	}

	// DIRECT FUNCTION CALL - No HTTP, no network, just a function call
	ctx, cancel := context.WithTimeout(r.Context(), time.Duration(td.LLMTimeout)*time.Second)
	defer cancel()

	validationResponse := td.validator.Validate(ctx, mcpPayload, nil)

	// Check validation result
	if !validationResponse.Success {
		errorMsg := "Validation failed"
		if validationResponse.Error != nil {
			errorMsg = fmt.Sprintf("Validation error: %s", *validationResponse.Error)
		}
		td.logger.Debug("validation failed", zap.String("error", errorMsg))
		http.Error(w, errorMsg, http.StatusForbidden)
		return nil
	}

	if validationResponse.Verdict != nil && validationResponse.Verdict.IsMaliciousRequest {
		reason := fmt.Sprintf("Malicious request detected: %s", validationResponse.Verdict.Reasoning)
		td.logger.Warn("blocking malicious request",
			zap.String("method", r.Method),
			zap.String("path", r.URL.Path),
			zap.String("reason", reason))
		http.Error(w, reason, http.StatusForbidden)
		return nil
	}

	// Request is safe, pass to next handler with appropriate writer
	return next.ServeHTTP(responseWriter, r)
}

// Cleanup cleans up the validator when Caddy shuts down
func (td *ThreatDetector) Cleanup() error {
	if td.validator != nil {
		return td.validator.Close()
	}
	return nil
}

// parseCaddyfile unmarshals tokens from the Caddyfile
func parseCaddyfile(h httpcaddyfile.Helper) (caddyhttp.MiddlewareHandler, error) {
	var td ThreatDetector
	err := td.UnmarshalCaddyfile(h.Dispenser)
	return &td, err
}

// UnmarshalCaddyfile implements caddyfile.Unmarshaler
func (td *ThreatDetector) UnmarshalCaddyfile(d *caddyfile.Dispenser) error {
	for d.Next() {
		for d.NextBlock(0) {
			switch d.Val() {
			case "llm_provider":
				if !d.Args(&td.LLMProvider) {
					return d.ArgErr()
				}
			case "llm_model":
				if !d.Args(&td.LLMModel) {
					return d.ArgErr()
				}
			case "llm_api_key":
				if !d.Args(&td.LLMAPIKey) {
					return d.ArgErr()
				}
			case "llm_timeout":
				var timeout string
				if !d.Args(&timeout) {
					return d.ArgErr()
				}
				fmt.Sscanf(timeout, "%d", &td.LLMTimeout)
			case "llm_temperature":
				var temp string
				if !d.Args(&temp) {
					return d.ArgErr()
				}
				fmt.Sscanf(temp, "%f", &td.LLMTemperature)
			case "debug":
				td.Debug = true
			default:
				return d.Errf("unrecognized subdirective %q", d.Val())
			}
		}
	}
	return nil
}

// getEnvOrDefault returns environment variable value or default
func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
