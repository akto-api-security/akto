package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"github.com/joho/godotenv"
	"mcp-threat-detection/mcp-threat/client"
	"mcp-threat-detection/mcp-threat/config"
	"mcp-threat-detection/mcp-threat/types"
)

func main() {
	// Parse command line flags
	var (
		payload    = flag.String("payload", "", "MCP payload (raw JSON string or @path/to/file.json) [REQUIRED]")
		toolDesc   = flag.String("tool-desc", "", "Tool/Resource description (string or @file.txt) [OPTIONAL]")
		provider   = flag.String("provider", "", "LLM provider to use (openai, self_hosted)")
		model      = flag.String("model", "", "Model name (overrides environment config)")
		endpoint   = flag.String("endpoint", "", "Custom endpoint for self-hosted LLMs")
		apiKey     = flag.String("api-key", "", "API key (overrides environment config)")
		jsonOutput = flag.Bool("json", false, "Output result in JSON format")
		verbose    = flag.Bool("verbose", false, "Verbose output")
		timeout    = flag.Duration("timeout", 60*time.Second, "Timeout for validation")
	)
	flag.Parse()

	// Validate required flags
	if *payload == "" {
		log.Fatal("--payload is required")
	}

	// Load environment variables
	if err := godotenv.Load(); err != nil {
		if *verbose {
			log.Println("No .env file found, using environment variables")
		}
	}

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

	if *verbose {
		log.Printf("Initialized validator with provider: %s", appConfig.LLM.ProviderType)
		log.Printf("Model: %s", appConfig.LLM.Model)
	}

	// Perform validation
	if *verbose {
		log.Printf("Validating payload...")
		payloadStr := fmt.Sprintf("%v", payloadData)
		if len(payloadStr) > 200 {
			payloadStr = payloadStr[:200] + "..."
		}
		log.Printf("Payload: %s", payloadStr)
		if toolDescData != nil {
			log.Printf("Tool Description: %s", *toolDescData)
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()

	// Use generic validation that auto-detects type
	response := validator.Validate(ctx, payloadData, toolDescData)

	if !response.Success {
		if response.Error != nil {
			log.Fatalf("Validation failed: %s", *response.Error)
		} else {
			log.Fatalf("Validation failed: unknown error")
		}
	}

	// Output result
	if *jsonOutput {
		outputJSON(response)
	} else {
		outputFormatted(response)
	}
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
			"confidence":            response.Verdict.Confidence,
			"categories":            response.Verdict.Categories,
			"evidence":              response.Verdict.Evidence,
			"policy_action":         response.Verdict.PolicyAction,
			"reasoning":             response.Verdict.Reasoning,
			"raw":                   response.Verdict.Raw,
		},
		"processing_time_ms": response.ProcessingTime,
	}

	jsonData, err := json.MarshalIndent(output, "", "  ")
	if err != nil {
		log.Fatalf("Failed to marshal output: %v", err)
	}

	fmt.Println(string(jsonData))
}

// outputFormatted outputs the result in a formatted way
func outputFormatted(response *types.ValidationResponse) {
	fmt.Println("\n" + strings.Repeat("=", 60))
	fmt.Println("VALIDATION RESULT")
	fmt.Println(strings.Repeat("=", 60))

	if response.ProcessingTime > 0 {
		fmt.Printf("Processing Time: %.2fms\n", response.ProcessingTime)
	}

	verdict := response.Verdict
	fmt.Printf("Malicious: %s\n", boolToYesNo(verdict.IsMaliciousRequest))
	fmt.Printf("Confidence: %.2f\n", verdict.Confidence)
	fmt.Printf("Policy Action: %s\n", verdict.PolicyAction)

	// Format categories
	var categoryStrs []string
	for _, cat := range verdict.Categories {
		categoryStrs = append(categoryStrs, string(cat))
	}
	fmt.Printf("Categories: %s\n", strings.Join(categoryStrs, ", "))

	// Format evidence
	if len(verdict.Evidence) > 0 {
		fmt.Printf("Evidence: %s\n", strings.Join(verdict.Evidence, ", "))
	}

	fmt.Printf("Reasoning: %s\n", verdict.Reasoning)

	fmt.Println(strings.Repeat("=", 60))

	// Check if this should be blocked
	if verdict.IsMaliciousRequest {
		fmt.Println("\n🚨 RECOMMENDATION: This request should be BLOCKED!")
		if verdict.PolicyAction == types.PolicyActionBlock {
			fmt.Println("   Policy action: BLOCK")
		}
	} else {
		fmt.Println("\n✅ RECOMMENDATION: This request appears safe")
	}
}

// boolToYesNo converts a boolean to "YES" or "NO"
func boolToYesNo(b bool) string {
	if b {
		return "🚨 YES"
	}
	return "✅ NO"
}
