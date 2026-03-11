// Akto Guardrails callbacks for Google Vertex AI ADK.
//
// Environment Variables:
//
//	DATA_INGESTION_URL          : Base URL for Akto's data ingestion service (required).
//	SYNC_MODE                   : "true" (default) to block before the LLM call;
//	                              "false" to validate/log asynchronously after the response.
//	TIMEOUT                     : HTTP request timeout in seconds (default: 5).
//
// Auto-detected (Vertex AI Agent Engine injects these at runtime):
//
//	GOOGLE_CLOUD_PROJECT        : GCP project ID.
//	GOOGLE_CLOUD_LOCATION       : Region (e.g. "us-central1").
//	GOOGLE_CLOUD_AGENT_ENGINE_ID: Reasoning Engine resource ID.
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"google.golang.org/adk/agent"
	"google.golang.org/adk/model"
	"google.golang.org/genai"
)

// ---------------------------------------------------------------------------
// Internal types
// ---------------------------------------------------------------------------

// aktoSnapshot is the internal state stored between before and after callbacks.
type aktoSnapshot struct {
	Model    string                   `json:"model"`
	Messages []map[string]interface{} `json:"messages"`
	Blocked  bool                     `json:"blocked"`
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

var (
	dataIngestionURL = os.Getenv("DATA_INGESTION_URL")
	syncMode         = strings.ToLower(getEnvDefault("SYNC_MODE", "true")) == "true"
	timeout          = parseTimeoutSeconds(getEnvDefault("TIMEOUT", "5"))
)

const (
	aktoConnectorName = "vertex-ai-adk"
	httpProxyPath     = "/api/http-proxy"
	aktoSnapshotKey   = "__akto_request_snapshot"
)

// ModelName should be set by the caller before using the callbacks.
// ADK v0.2.0 does not populate LLMRequest.Model — the model name is stored
// internally in the gemini model object and never passed to callbacks.
var ModelName string

// Shared HTTP client.
var httpClient = &http.Client{
	Timeout: timeout,
	Transport: &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 20,
	},
}

func init() {
	log.Printf("[akto] callbacks initialized | data_ingestion_url=%q sync_mode=%v timeout=%v", dataIngestionURL, syncMode, timeout)
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

func getEnvDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func parseTimeoutSeconds(s string) time.Duration {
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		f = 5
	}
	return time.Duration(f * float64(time.Second))
}

func getVertexEndpointInfo() (host, path string) {
	project := os.Getenv("GOOGLE_CLOUD_PROJECT")
	location := os.Getenv("GOOGLE_CLOUD_LOCATION")
	engineID := os.Getenv("GOOGLE_CLOUD_AGENT_ENGINE_ID")

	if engineID != "" && project != "" && location != "" {
		// Agent Engine mode
		host = fmt.Sprintf("%s-aiplatform.googleapis.com", location)
		path = fmt.Sprintf("/v1/projects/%s/locations/%s/reasoningEngines/%s:query", project, location, engineID)
		return host, path
	}

	if kService := os.Getenv("K_SERVICE"); kService != "" {
		// Cloud Run mode
		serviceURL := os.Getenv("CLOUD_RUN_SERVICE_URL")
		serviceURL = strings.TrimPrefix(serviceURL, "https://")
		serviceURL = strings.TrimPrefix(serviceURL, "http://")
		serviceURL = strings.TrimRight(serviceURL, "/")
		if serviceURL == "" {
			serviceURL = "run.googleapis.com"
		}
		return serviceURL, "/"
	}

	// Local dev fallback
	return "generativelanguage.googleapis.com", "/v1/chat/completions"
}

func buildHTTPProxyParams(guardrails, ingestData bool) url.Values {
	params := url.Values{}
	params.Set("akto_connector", aktoConnectorName)
	if guardrails {
		params.Set("guardrails", "true")
	}
	if ingestData {
		params.Set("ingest_data", "true")
	}
	return params
}

func parseGuardrailsResult(result map[string]interface{}) (allowed bool, reason string) {
	if result == nil {
		return true, ""
	}
	data, ok := result["data"].(map[string]interface{})
	if !ok {
		return true, ""
	}
	gr, ok := data["guardrailsResult"].(map[string]interface{})
	if !ok || gr == nil {
		return true, ""
	}
	allowed = true
	if v, ok := gr["Allowed"].(bool); ok {
		allowed = v
	}
	if v, ok := gr["Reason"].(string); ok {
		reason = v
	}
	return allowed, reason
}

func contentsToMessages(contents []*genai.Content) []map[string]interface{} {
	log.Printf("[akto] contentsToMessages: converting %d content(s)", len(contents))
	var messages []map[string]interface{}
	for i, content := range contents {
		if content == nil {
			log.Printf("[akto]   content[%d]: nil, skipping", i)
			continue
		}
		role := string(content.Role)
		if role == "" {
			role = "user"
		}
		var textParts []string
		for _, p := range content.Parts {
			if p != nil && p.Text != "" {
				textParts = append(textParts, p.Text)
			}
		}
		text := strings.Join(textParts, " ")
		log.Printf("[akto]   content[%d]: role=%q text=%q", i, role, text)
		messages = append(messages, map[string]interface{}{
			"role":    role,
			"content": text,
		})
	}
	return messages
}

func llmResponseToDict(resp *model.LLMResponse) map[string]interface{} {
	if resp == nil || resp.Content == nil {
		return nil
	}
	role := string(resp.Content.Role)
	if role == "" {
		role = "model"
	}
	var textParts []string
	for _, p := range resp.Content.Parts {
		if p != nil && p.Text != "" {
			textParts = append(textParts, p.Text)
		}
	}
	return map[string]interface{}{
		"choices": []map[string]interface{}{
			{
				"message": map[string]interface{}{
					"role":    role,
					"content": strings.Join(textParts, " "),
				},
			},
		},
	}
}

func makeBlockedResponse(reason string) *model.LLMResponse {
	msg := "Blocked by Akto Guardrails"
	if reason != "" {
		msg = fmt.Sprintf("Blocked by Akto Guardrails: %s", reason)
	}
	return &model.LLMResponse{
		Content: &genai.Content{
			Role:  "model",
			Parts: []*genai.Part{{Text: msg}},
		},
	}
}

func mustJSON(v interface{}) string {
	b, err := json.Marshal(v)
	if err != nil {
		return "{}"
	}
	return string(b)
}

func buildPayload(
	ctx agent.CallbackContext,
	modelName string,
	messages []map[string]interface{},
	responseBody interface{},
	statusCode int,
) map[string]interface{} {
	agentName := ctx.AgentName()
	if agentName == "" {
		agentName = "unknown-agent"
	}
	invocationID := ctx.InvocationID()
	engineID := os.Getenv("GOOGLE_CLOUD_AGENT_ENGINE_ID")
	if engineID == "" {
		engineID = os.Getenv("K_SERVICE")
	}
	host, path := getVertexEndpointInfo()
	timestamp := strconv.FormatInt(time.Now().UnixMilli(), 10)
	statusStr := strconv.Itoa(statusCode)

	// Use a struct so JSON marshalling preserves field order (model before messages),
	// matching the Python payload exactly.
	type modelBody struct {
		Model    string                   `json:"model"`
		Messages []map[string]interface{} `json:"messages"`
	}
	requestPayload := mustJSON(map[string]interface{}{
		"body": mustJSON(modelBody{Model: modelName, Messages: messages}),
	})

	metadata := map[string]interface{}{
		"call_type":     "completion",
		"model":         modelName,
		"agent_name":    agentName,
		"invocation_id": invocationID,
	}

	// bot-name is nil when GOOGLE_CLOUD_AGENT_ENGINE_ID is not set, matching Python behaviour.
	var botName interface{}
	if engineID != "" {
		botName = engineID
	}
	tags := map[string]interface{}{
		"gen-ai":   "Gen AI",
		"ai-agent": aktoConnectorName,
		"bot-name": botName,
		"source":   "VERTEX_AI",
	}

	// Match Python: send "{}" string when there is no response body (before-model guardrails call).
	responsePayload := mustJSON(map[string]interface{}{})
	if responseBody != nil {
		responsePayload = mustJSON(map[string]interface{}{
			"body": mustJSON(responseBody),
		})
	}

	return map[string]interface{}{
		"path": path,
		"requestHeaders": mustJSON(map[string]interface{}{
			"host":         host,
			"content-type": "application/json",
		}),
		"responseHeaders": mustJSON(map[string]interface{}{
			"content-type": "application/json",
		}),
		"method":          "POST",
		"requestPayload":  requestPayload,
		"responsePayload": responsePayload,
		"ip":              "0.0.0.0",
		"destIp":          "127.0.0.1",
		"time":            timestamp,
		"statusCode":      statusStr,
		"type":            "HTTP/1.1",
		"status":          statusStr,
		"akto_account_id": "1000000",
		"akto_vxlan_id":   "0",
		"is_pending":      "false",
		"source":          "MIRRORING",
		"direction":       nil,
		"process_id":      nil,
		"socket_id":       nil,
		"daemonset_id":    nil,
		"enabled_graph":   nil,
		"tag":             mustJSON(tags),
		"metadata":        mustJSON(metadata),
		"contextSource":   "AGENTIC",
	}
}

func postHTTPProxy(guardrails, ingestData bool, payload map[string]interface{}) (*http.Response, error) {
	endpoint := dataIngestionURL + httpProxyPath
	params := buildHTTPProxyParams(guardrails, ingestData)

	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("marshal payload: %w", err)
	}

	u, err := url.Parse(endpoint)
	if err != nil {
		return nil, fmt.Errorf("parse endpoint: %w", err)
	}
	u.RawQuery = params.Encode()

	log.Printf("[akto] POST %s (guardrails=%v ingest=%v) payload_size=%d bytes", u.String(), guardrails, ingestData, len(body))
	log.Printf("[akto]   payload_body: %s", string(body))

	req, err := http.NewRequest(http.MethodPost, u.String(), bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	start := time.Now()
	resp, doErr := httpClient.Do(req)
	elapsed := time.Since(start)
	if doErr != nil {
		log.Printf("[akto]   POST failed after %v: %v", elapsed, doErr)
		return nil, doErr
	}
	log.Printf("[akto]   POST response: status=%d latency=%v", resp.StatusCode, elapsed)
	return resp, nil
}

func callGuardrailsValidation(
	ctx agent.CallbackContext,
	modelName string,
	messages []map[string]interface{},
) (allowed bool, reason string) {
	log.Printf("[akto] callGuardrailsValidation: model=%q messages=%d", modelName, len(messages))
	if dataIngestionURL == "" {
		log.Printf("[akto]   DATA_INGESTION_URL is empty, skipping guardrails (allow)")
		return true, ""
	}

	payload := buildPayload(ctx, modelName, messages, nil, 200)
	resp, err := postHTTPProxy(true, false, payload)
	if err != nil {
		log.Printf("[akto]   guardrails validation failed (fail-open): %v", err)
		return true, ""
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		log.Printf("[akto]   guardrails returned HTTP %d (fail-open)", resp.StatusCode)
		return true, ""
	}

	var result map[string]interface{}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		log.Printf("[akto]   guardrails response parse error (fail-open): %v", err)
		return true, ""
	}

	log.Printf("[akto]   guardrails raw response: %s", mustJSON(result))
	allowed, reason = parseGuardrailsResult(result)
	log.Printf("[akto]   guardrails result: allowed=%v reason=%q", allowed, reason)
	return allowed, reason
}

func ingestResponsePayload(
	ctx agent.CallbackContext,
	modelName string,
	messages []map[string]interface{},
	responseBody interface{},
	statusCode int,
	logHTTPError bool,
) {
	log.Printf("[akto] ingestResponsePayload: model=%q messages=%d statusCode=%d", modelName, len(messages), statusCode)
	if dataIngestionURL == "" {
		log.Printf("[akto]   DATA_INGESTION_URL is empty, skipping ingestion")
		return
	}

	payload := buildPayload(ctx, modelName, messages, responseBody, statusCode)
	resp, err := postHTTPProxy(false, true, payload)
	if err != nil {
		log.Printf("[akto]   ingestion POST failed: %v", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
		log.Printf("[akto]   ingestion succeeded")
	} else if logHTTPError {
		log.Printf("[akto]   ingestion failed: HTTP %d", resp.StatusCode)
	}
}

func asyncValidateAndIngest(
	ctx agent.CallbackContext,
	modelName string,
	messages []map[string]interface{},
	responseDict map[string]interface{},
) {
	log.Printf("[akto] asyncValidateAndIngest: model=%q messages=%d", modelName, len(messages))
	if dataIngestionURL == "" {
		log.Printf("[akto]   DATA_INGESTION_URL is empty, skipping async validate+ingest")
		return
	}

	payload := buildPayload(ctx, modelName, messages, responseDict, 200)
	resp, err := postHTTPProxy(true, true, payload)
	if err != nil {
		log.Printf("[akto]   async validate+ingest POST failed: %v", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == 200 {
		var result map[string]interface{}
		if err := json.NewDecoder(resp.Body).Decode(&result); err == nil {
			allowed, reason := parseGuardrailsResult(result)
			log.Printf("[akto]   async guardrails result: allowed=%v reason=%q", allowed, reason)
			if !allowed {
				log.Printf("[akto]   response flagged by guardrails (async mode, logged only): %s", reason)
			}
		} else {
			log.Printf("[akto]   async response parse error: %v", err)
		}
	} else {
		log.Printf("[akto]   async validate+ingest returned HTTP %d", resp.StatusCode)
	}
}

// ---------------------------------------------------------------------------
// Public callback functions
// ---------------------------------------------------------------------------

// AktoBeforeModelCallback is the ADK BeforeModelCallback that integrates Akto Guardrails
// pre-call validation.
//
// Signature matches llmagent.BeforeModelCallback:
//
//	func(agent.CallbackContext, *model.LLMRequest) (*model.LLMResponse, error)
//
// Register via:
//
//	llmagent.Config{
//	    BeforeModelCallbacks: []llmagent.BeforeModelCallback{callbacks.AktoBeforeModelCallback},
//	}
//
// Behaviour depends on SYNC_MODE:
//
//   - SYNC_MODE=true  (default):
//     Sends the request to Akto's guardrails endpoint before forwarding it to
//     the LLM. If the request is denied, a blocking LLMResponse is returned
//     and the LLM is never called. The blocked interaction is also ingested
//     for observability.
//
//   - SYNC_MODE=false:
//     Snapshots the request data in session state so that
//     AktoAfterModelCallback can perform a combined validate-and-ingest
//     call after the LLM responds. Returns nil so the LLM call proceeds
//     uninterrupted.
//
// Fail-open: any error during validation allows the request through.
func AktoBeforeModelCallback(
	ctx agent.CallbackContext,
	llmRequest *model.LLMRequest,
) (*model.LLMResponse, error) {
	log.Printf("[akto] ===== BeforeModelCallback START =====")
	log.Printf("[akto]   agent=%q sync_mode=%v", ctx.AgentName(), syncMode)

	modelName := ""
	var contents []*genai.Content
	if llmRequest != nil {
		modelName = llmRequest.Model
		contents = llmRequest.Contents
		log.Printf("[akto]   model=%q contents_count=%d", modelName, len(contents))
	} else {
		log.Printf("[akto]   llmRequest is nil")
	}
	if modelName == "" {
		modelName = ModelName
		log.Printf("[akto]   LLMRequest.Model was empty, using ModelName=%q", modelName)
	}
	messages := contentsToMessages(contents)
	log.Printf("[akto]   converted to %d message(s)", len(messages))

	// Extract only the latest user message for guardrails validation.
	var latestUserMsg []map[string]interface{}
	for _, m := range messages {
		if role, _ := m["role"].(string); role == "user" {
			latestUserMsg = []map[string]interface{}{m}
		}
	}
	if len(latestUserMsg) == 0 && len(messages) > 0 {
		latestUserMsg = messages[len(messages)-1:]
	}
	log.Printf("[akto]   latest user message for guardrails: %s", mustJSON(latestUserMsg))

	// Snapshot request data for AktoAfterModelCallback.
	_ = ctx.State().Set(aktoSnapshotKey, aktoSnapshot{
		Model:    modelName,
		Messages: latestUserMsg,
		Blocked:  false,
	})
	log.Printf("[akto]   snapshot saved (blocked=false)")

	if !syncMode {
		log.Printf("[akto]   async mode — skipping pre-LLM guardrails, returning nil")
		log.Printf("[akto] ===== BeforeModelCallback END (pass-through) =====")
		return nil, nil
	}

	allowed, reason := callGuardrailsValidation(ctx, modelName, latestUserMsg)
	if !allowed {
		log.Printf("[akto]   BLOCKED by guardrails — reason=%q, ingesting blocked interaction", reason)
		// Ingest the blocked interaction before surfacing the error.
		ingestResponsePayload(
			ctx, modelName, latestUserMsg,
			map[string]interface{}{"x-blocked-by": "Akto Proxy"},
			403, false,
		)
		// Mark snapshot so after_model_callback skips duplicate ingestion.
		_ = ctx.State().Set(aktoSnapshotKey, aktoSnapshot{
			Model:    modelName,
			Messages: latestUserMsg,
			Blocked:  true,
		})
		log.Printf("[akto] ===== BeforeModelCallback END (BLOCKED) =====")
		return makeBlockedResponse(reason), nil
	}

	log.Printf("[akto]   guardrails passed — forwarding to LLM")
	log.Printf("[akto] ===== BeforeModelCallback END (allowed) =====")
	return nil, nil
}

// AktoAfterModelCallback is the ADK AfterModelCallback that ingests or validates
// the completed interaction.
//
// Signature matches llmagent.AfterModelCallback:
//
//	func(agent.CallbackContext, *model.LLMResponse, error) (*model.LLMResponse, error)
//
// Register via:
//
//	llmagent.Config{
//	    AfterModelCallbacks: []llmagent.AfterModelCallback{callbacks.AktoAfterModelCallback},
//	}
//
// Behaviour depends on SYNC_MODE:
//
//   - SYNC_MODE=true  (default):
//     Ingests the request + LLM response pair for observability.
//
//   - SYNC_MODE=false:
//     Sends a combined validate-and-ingest request. If the response is
//     flagged by guardrails it is logged but NOT blocked (the LLM response
//     is already being returned to the caller).
//
// Always returns nil so the original LLMResponse is used unchanged.
func AktoAfterModelCallback(
	ctx agent.CallbackContext,
	llmResponse *model.LLMResponse,
	llmResponseError error,
) (*model.LLMResponse, error) {
	log.Printf("[akto] ===== AfterModelCallback START =====")
	log.Printf("[akto]   agent=%q sync_mode=%v", ctx.AgentName(), syncMode)

	if llmResponseError != nil {
		log.Printf("[akto]   LLM returned error: %v", llmResponseError)
	}

	snapshotRaw, err := ctx.State().Get(aktoSnapshotKey)
	if err != nil {
		log.Printf("[akto]   failed to get snapshot from state: %v — skipping", err)
		log.Printf("[akto] ===== AfterModelCallback END (no snapshot) =====")
		return nil, nil
	}
	snapshot, ok := snapshotRaw.(aktoSnapshot)
	if !ok {
		log.Printf("[akto]   snapshot type assertion failed (got %T) — skipping", snapshotRaw)
		log.Printf("[akto] ===== AfterModelCallback END (bad snapshot) =====")
		return nil, nil
	}
	log.Printf("[akto]   snapshot: model=%q messages=%d blocked=%v", snapshot.Model, len(snapshot.Messages), snapshot.Blocked)

	// Skip if this request was already blocked and ingested in before_model_callback.
	if snapshot.Blocked {
		log.Printf("[akto]   request was blocked in BeforeModelCallback — skipping duplicate ingestion")
		log.Printf("[akto] ===== AfterModelCallback END (already blocked) =====")
		return nil, nil
	}

	responseDict := llmResponseToDict(llmResponse)
	if responseDict != nil {
		log.Printf("[akto]   LLM response: %s", mustJSON(responseDict))
	} else {
		log.Printf("[akto]   LLM response is nil")
	}

	if syncMode {
		log.Printf("[akto]   sync mode — ingesting request+response pair")
		ingestResponsePayload(
			ctx, snapshot.Model, snapshot.Messages,
			responseDict, 200, true,
		)
	} else {
		log.Printf("[akto]   async mode — combined validate+ingest")
		asyncValidateAndIngest(
			ctx, snapshot.Model, snapshot.Messages, responseDict,
		)
	}

	log.Printf("[akto] ===== AfterModelCallback END =====")
	return nil, nil
}
