package main

import (
	"context"
	"fmt"
	"log"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/client"
)

func main() {
	// Create a validator with policy support
	validator, err := client.NewMCPValidator("openai", map[string]interface{}{
		"api_key": "your-api-key-here",
		"model":   "gpt-4",
	})
	if err != nil {
		log.Fatalf("Failed to create validator: %v", err)
	}
	defer validator.Close()

	// Test payloads
	testPayloads := []struct {
		name    string
		payload string
		desc    string
	}{
		{
			name:    "Sensitive Data",
			payload: `{"method": "login", "password": "secret123", "username": "admin"}`,
			desc:    "Contains password field",
		},
		{
			name:    "Admin Access",
			payload: `{"method": "admin_action", "user": "admin", "action": "delete_user"}`,
			desc:    "Contains admin keyword",
		},
		{
			name:    "Phishing Attempt",
			payload: `{"message": "Please verify your account to continue using our service"}`,
			desc:    "Contains phishing keywords",
		},
		{
			name:    "Clean Request",
			payload: `{"method": "get_data", "id": "123", "type": "user"}`,
			desc:    "Clean payload with no violations",
		},
	}

	ctx := context.Background()

	for _, test := range testPayloads {
		fmt.Printf("\n=== Testing: %s ===\n", test.name)
		fmt.Printf("Description: %s\n", test.desc)
		fmt.Printf("Payload: %s\n", test.payload)

		response := validator.Validate(ctx, test.payload, nil)

		if response.Error != nil {
			fmt.Printf("❌ Error: %s\n", *response.Error)
			continue
		}

		if response.Verdict != nil {
			if response.Verdict.IsMaliciousRequest {
				fmt.Printf("🚨 BLOCKED - Policy violation detected\n")
				fmt.Printf("   Action: %s\n", response.Verdict.PolicyAction)
				fmt.Printf("   Confidence: %.2f\n", response.Verdict.Confidence)
				fmt.Printf("   Reasoning: %s\n", response.Verdict.Reasoning)
				if len(response.Verdict.Evidence) > 0 {
					fmt.Printf("   Evidence: %s\n", response.Verdict.Evidence[0])
				}
			} else {
				fmt.Printf("✅ ALLOWED - No policy violations\n")
				fmt.Printf("   Action: %s\n", response.Verdict.PolicyAction)
				fmt.Printf("   Confidence: %.2f\n", response.Verdict.Confidence)
			}
		}

		fmt.Printf("   Processing time: %.2fms\n", response.ProcessingTime)
	}

	fmt.Printf("\n=== Policy Validation System Demo Complete ===\n")
}
