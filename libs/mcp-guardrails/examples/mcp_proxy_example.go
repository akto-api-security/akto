package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"

	guardrails "github.com/akto-api-security/akto/libs/mcp-guardrails"
)

// Example MCP Proxy with Guardrails Integration
// This demonstrates how to integrate the guardrails library into your MCP proxy

func main() {
	// Initialize guardrails integration
	guardrails, err := guardrails.NewMCPProxyIntegration()
	if err != nil {
		log.Fatalf("Failed to initialize guardrails: %v", err)
	}

	// Create HTTP server
	server := &http.Server{
		Addr:    ":8080",
		Handler: createHandler(guardrails),
	}

	log.Println("MCP Proxy with Guardrails starting on :8080")
	log.Fatal(server.ListenAndServe())
}

// createHandler creates the HTTP handler with guardrails middleware
func createHandler(guardrails *guardrails.MCPProxyIntegration) http.Handler {
	mux := http.NewServeMux()

	// MCP endpoints
	mux.HandleFunc("/mcp", func(w http.ResponseWriter, r *http.Request) {
		handleMCPRequest(w, r, guardrails)
	})

	// Health check endpoint
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		handleHealthCheck(w, r, guardrails)
	})

	// Status endpoint
	mux.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		handleStatus(w, r, guardrails)
	})

	return mux
}

// handleMCPRequest handles MCP requests with guardrails
func handleMCPRequest(w http.ResponseWriter, r *http.Request, guardrails *guardrails.MCPProxyIntegration) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Read request body
	requestBody, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Failed to read request body", http.StatusBadRequest)
		return
	}

	// Apply request guardrails
	blocked, reason, err := guardrails.RequestGuardrail(requestBody)
	if err != nil {
		log.Printf("Guardrail processing error: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}

	if blocked {
		log.Printf("Request blocked by guardrails: %s", reason)
		errorResponse := map[string]interface{}{
			"jsonrpc": "2.0",
			"error": map[string]interface{}{
				"code":    -32603,
				"message": reason,
			},
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusForbidden)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Process the request (forward to actual MCP server)
	// In a real implementation, you would forward this to your MCP server
	responseData, err := forwardToMCPServer(requestBody)
	if err != nil {
		log.Printf("MCP server error: %v", err)
		errorResponse := map[string]interface{}{
			"jsonrpc": "2.0",
			"error": map[string]interface{}{
				"code":    -32603,
				"message": "Internal server error",
			},
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Apply response guardrails
	processedResponse, modified, err := guardrails.ResponseGuardrail(responseData)
	if err != nil {
		log.Printf("Response guardrail processing error: %v", err)
		// Continue with original response if guardrail processing fails
		processedResponse = responseData
	}

	if modified {
		log.Println("Response was modified by guardrails")
	}

	// Send response
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write(processedResponse)
}

// handleHealthCheck handles health check requests
func handleHealthCheck(w http.ResponseWriter, r *http.Request, guardrails *guardrails.MCPProxyIntegration) {
	status := guardrails.GetStatus()

	response := map[string]interface{}{
		"status":     "healthy",
		"guardrails": status,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleStatus handles status requests
func handleStatus(w http.ResponseWriter, r *http.Request, guardrails *guardrails.MCPProxyIntegration) {
	if r.Method == http.MethodPost {
		// Manual template refresh
		err := guardrails.RefreshTemplates()
		if err != nil {
			http.Error(w, fmt.Sprintf("Failed to refresh templates: %v", err), http.StatusInternalServerError)
			return
		}
	}

	status := guardrails.GetStatus()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(status)
}

// forwardToMCPServer simulates forwarding to an MCP server
// In a real implementation, this would make an HTTP request to your MCP server
func forwardToMCPServer(requestData []byte) ([]byte, error) {
	// This is a placeholder implementation
	// In reality, you would:
	// 1. Parse the MCP request
	// 2. Forward it to your actual MCP server
	// 3. Return the response

	// For demo purposes, return a simple response
	response := map[string]interface{}{
		"jsonrpc": "2.0",
		"result": map[string]interface{}{
			"message": "Request processed successfully",
		},
	}

	return json.Marshal(response)
}

// Environment Variables Required:
// - GUARDRAIL_SERVICE_URL: URL of your database-abstractor service (e.g., "http://localhost:8080")
// - GUARDRAIL_SERVICE_TOKEN: Authentication token (optional)
// - GUARDRAIL_REFRESH_INTERVAL: Template refresh interval in minutes (default: 10)
// - GUARDRAIL_ENABLE_SANITIZATION: Enable data sanitization (default: true)
// - GUARDRAIL_ENABLE_CONTENT_FILTERING: Enable content filtering (default: true)
// - GUARDRAIL_ENABLE_RATE_LIMITING: Enable rate limiting (default: true)
// - GUARDRAIL_ENABLE_INPUT_VALIDATION: Enable input validation (default: true)
// - GUARDRAIL_ENABLE_OUTPUT_FILTERING: Enable output filtering (default: true)
// - GUARDRAIL_ENABLE_LOGGING: Enable logging (default: true)

// Example usage:
// export GUARDRAIL_SERVICE_URL="http://localhost:8080"
// export GUARDRAIL_REFRESH_INTERVAL="5"
// export GUARDRAIL_ENABLE_SANITIZATION="true"
// go run mcp_proxy_example.go
