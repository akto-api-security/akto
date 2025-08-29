package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/client"
	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/config"
	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"

	"github.com/joho/godotenv"
)

func main() {
	// Parse command line flags
	var (
		payload  = flag.String("payload", "", "MCP payload (raw JSON string or @path/to/file.json) [REQUIRED]")
		toolDesc = flag.String("tool-desc", "", "Tool/Resource description (string or @file.txt) [OPTIONAL]")
		provider = flag.String("provider", "", "LLM provider to use (openai, self_hosted)")
		model    = flag.String("model", "", "Model name (overrides environment config)")
		endpoint = flag.String("endpoint", "", "Custom endpoint for self-hosted LLMs")
		apiKey   = flag.String("api-key", "", "API key (overrides environment config)")
		// verbose    = flag.Bool("verbose", false, "Verbose output")
		timeout = flag.Duration("timeout", 60*time.Second, "Timeout for validation")
	)
	flag.Parse()

	// Validate required flags
	if *payload == "" {
		log.Fatal("--payload is required")
	}

	// Load environment variables
	_ = godotenv.Load()

	// Load configuration
	appConfig, err := config.LoadConfigFromEnv()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Override config with command line arguments
	if *provider != "" {
		appConfig.LLM.ProviderType = *provider
	}
	if *model != "" {
		appConfig.LLM.Model = *model
	}
	if *apiKey != "" {
		appConfig.LLM.APIKey = apiKey
	}
	if *endpoint != "" {
		appConfig.LLM.BaseURL = endpoint
	}

	// Load inputs
	payloadData, err := loadInput(*payload)
	if err != nil {
		log.Fatalf("Failed to load payload: %v", err)
	}

	var toolDescData *string
	if *toolDesc != "" {
		data, err := loadInput(*toolDesc)
		if err != nil {
			log.Fatalf("Failed to load tool description: %v", err)
		}
		if str, ok := data.(string); ok {
			toolDescData = &str
		}
	}

	// Initialize validator
	validator, err := client.NewMCPValidatorWithConfig(appConfig)
	if err != nil {
		log.Fatalf("Failed to initialize validator: %v", err)
	}
	defer validator.Close()

	// Perform validation
	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()

	response := validator.Validate(ctx, payloadData, toolDescData)

	if !response.Success {
		if response.Error != nil {
			log.Fatalf("Validation failed: %s", *response.Error)
		}
		log.Fatalf("Validation failed: unknown error")
	}

	// Output result - only JSON format
	outputJSON(response)
}

// loadInput loads input from string or file
func loadInput(input string) (interface{}, error) {
	if input == "" {
		return nil, nil
	}

	// Check if it's a file path
	if input[0] == '@' {
		filePath := input[1:]
		data, err := os.ReadFile(filePath)
		if err != nil {
			return nil, fmt.Errorf("failed to read file %s: %w", filePath, err)
		}

		// Try to parse as JSON first
		var jsonData interface{}
		if err := json.Unmarshal(data, &jsonData); err == nil {
			return jsonData, nil
		}

		// If not JSON, return as string
		return string(data), nil
	}

	// Try to parse as JSON
	var jsonData interface{}
	if err := json.Unmarshal([]byte(input), &jsonData); err == nil {
		return jsonData, nil
	}

	// If not JSON, return as string
	return input, nil
}

// outputJSON outputs the result in JSON format
func outputJSON(response *types.ValidationResponse) {
	output := map[string]interface{}{
		"success": response.Success,
		"verdict": map[string]interface{}{
			"is_malicious_request": response.Verdict.IsMaliciousRequest,
			"confidence":           response.Verdict.Confidence,
			"categories":           response.Verdict.Categories,
			"evidence":             response.Verdict.Evidence,
			"policy_action":        response.Verdict.PolicyAction,
			"reasoning":            response.Verdict.Reasoning,
			"raw":                  nil,
		},
		"processing_time_ms": response.ProcessingTime,
	}

	jsonData, err := json.MarshalIndent(output, "", "  ")
	if err != nil {
		log.Fatalf("Failed to marshal output: %v", err)
	}

	fmt.Println(string(jsonData))
}
