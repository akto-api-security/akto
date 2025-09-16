package main

import (
	"context"
	"fmt"
	"log"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/client"
)

func mainRequestResponse() {
	// Create a validator with policy support
	validator, err := client.NewMCPValidator("openai", map[string]interface{}{
		"api_key": "your-api-key-here", // Replace with your actual API key
		"model":   "gpt-4",
	})
	if err != nil {
		log.Fatalf("Failed to create MCP validator: %v", err)
	}
	defer validator.Close()

	ctx := context.Background()

	// Example 1: Validate a request payload (policy validation only)
	fmt.Println("=== Testing ValidateRequest (Policy Validation Only) ===")
	requestPayload := `{"method": "read_file", "params": {"path": "/etc/passwd", "token": "secret123"}}`

	response := validator.ValidateRequest(ctx, requestPayload, nil)
	if response.Success && response.Verdict.IsMaliciousRequest {
		fmt.Printf("Malicious request detected by policy: %s\n", response.Verdict.Reasoning)
		fmt.Printf("Categories: %v\n", response.Verdict.Categories)
		fmt.Printf("Evidence: %v\n", response.Verdict.Evidence)
	} else if response.Error != nil {
		fmt.Printf("Validation error: %s\n", *response.Error)
	} else {
		fmt.Println("Request passed policy validation.")
	}

	// Example 2: Validate a response payload (policy validation only)
	fmt.Println("\n=== Testing ValidateResponse (Policy Validation Only) ===")
	responsePayload := `{"result": {"data": "password: admin123", "status": "success"}}`

	response = validator.ValidateResponse(ctx, responsePayload, nil)
	if response.Success && response.Verdict.IsMaliciousRequest {
		fmt.Printf("Malicious response detected by policy: %s\n", response.Verdict.Reasoning)
		fmt.Printf("Categories: %v\n", response.Verdict.Categories)
		fmt.Printf("Evidence: %v\n", response.Verdict.Evidence)
	} else if response.Error != nil {
		fmt.Printf("Validation error: %s\n", *response.Error)
	} else {
		fmt.Println("Response passed policy validation.")
	}

	// Example 3: Clean request
	fmt.Println("\n=== Testing Clean Request ===")
	cleanRequest := `{"method": "list_files", "params": {"directory": "/home/user"}}`

	response = validator.ValidateRequest(ctx, cleanRequest, nil)
	if response.Success && response.Verdict.IsMaliciousRequest {
		fmt.Printf("Malicious request detected by policy: %s\n", response.Verdict.Reasoning)
	} else if response.Error != nil {
		fmt.Printf("Validation error: %s\n", *response.Error)
	} else {
		fmt.Println("Clean request passed policy validation.")
	}

	// Example 4: Clean response
	fmt.Println("\n=== Testing Clean Response ===")
	cleanResponse := `{"result": {"files": ["doc1.txt", "doc2.txt"], "count": 2}}`

	response = validator.ValidateResponse(ctx, cleanResponse, nil)
	if response.Success && response.Verdict.IsMaliciousRequest {
		fmt.Printf("Malicious response detected by policy: %s\n", response.Verdict.Reasoning)
	} else if response.Error != nil {
		fmt.Printf("Validation error: %s\n", *response.Error)
	} else {
		fmt.Println("Clean response passed policy validation.")
	}
}
