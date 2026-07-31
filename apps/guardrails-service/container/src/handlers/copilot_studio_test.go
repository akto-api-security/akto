package handlers

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/akto-api-security/akto-endpoint-shield/mcp"
	"github.com/akto-api-security/akto-endpoint-shield/mcp/types"
	"github.com/akto-api-security/guardrails-service/models"
)

// sampleEvaluationRequest mirrors the example from Microsoft's spec:
// https://learn.microsoft.com/en-us/microsoft-copilot-studio/external-security-webhooks-interface-developers
func sampleEvaluationRequest() *models.EvaluationRequest {
	return &models.EvaluationRequest{
		PlannerContext: models.PlannerContext{
			UserMessage: "Send an email to the customer",
			Thought:     "User wants to notify customer",
			ChatHistory: []models.ChatMessage{
				{ID: "m1", Role: "user", Content: "Send an email to the customer"},
				{ID: "m2", Role: "assistant", Content: "Which customer should I email?"},
			},
		},
		ToolDefinition: models.ToolDefinition{
			ID:          "tool-123",
			Type:        "PrebuiltToolDefinition",
			Name:        "Send email",
			Description: "Sends an email to specified recipients.",
		},
		InputValues: map[string]interface{}{
			"to":  "customer@foobar.com",
			"bcc": "hacker@evil.com",
		},
		ConversationMetadata: models.ConversationMetadata{
			Agent:          models.AgentContext{ID: "agent-guid", TenantID: "tenant-guid", EnvironmentID: "env-guid", IsPublished: true},
			ConversationID: "conv-id",
		},
	}
}

func TestToolExecutionContentIncludesFreeTextFields(t *testing.T) {
	content := toolExecutionContent(sampleEvaluationRequest())

	for _, want := range []string{
		"Send an email to the customer",
		"User wants to notify customer",
		"user: Send an email to the customer",
		"assistant: Which customer should I email?",
		"Send email: Sends an email to specified recipients.",
		"bcc: hacker@evil.com",
	} {
		if !strings.Contains(content, want) {
			t.Errorf("toolExecutionContent() missing %q, got: %s", want, content)
		}
	}
}

func TestBuildValidateParamsFromEvaluationRequest(t *testing.T) {
	req := sampleEvaluationRequest()
	params := buildValidateParamsFromEvaluationRequest(req)

	if params.ContextSource != string(types.ContextSourceEndpoint) {
		t.Errorf("ContextSource = %q, want %q", params.ContextSource, types.ContextSourceEndpoint)
	}
	if want := "/copilot/conversation/messages/conv-id"; params.Path != want {
		t.Errorf("Path = %q, want %q", params.Path, want)
	}
	if params.Method != "POST" {
		t.Errorf("Method = %q, want POST", params.Method)
	}

	var payload map[string]string
	if err := json.Unmarshal([]byte(params.RequestPayload), &payload); err != nil {
		t.Fatalf("RequestPayload is not valid JSON: %v", err)
	}
	if _, ok := payload["prompt"]; !ok {
		t.Errorf("RequestPayload missing %q key: %s", "prompt", params.RequestPayload)
	}

	var headers map[string]string
	if err := json.Unmarshal([]byte(params.RequestHeaders), &headers); err != nil {
		t.Fatalf("RequestHeaders is not valid JSON: %v", err)
	}
	if want := "agent-guid.copilot-studio-env-guid.microsoft.com"; headers["Host"] != want {
		t.Errorf("Host = %q, want %q", headers["Host"], want)
	}
}

func TestSanitizeBotName(t *testing.T) {
	cases := map[string]string{
		"agent-guid":       "agent-guid",
		"My Cool Bot!!":    "My-Cool-Bot",
		"  leading/trail ": "leading-trail",
		"agent_123":        "agent-123",
		"봇이름":              "봇이름",
		"":                 "",
	}
	for input, want := range cases {
		if got := sanitizeBotName(input); got != want {
			t.Errorf("sanitizeBotName(%q) = %q, want %q", input, got, want)
		}
	}
}

func TestCopilotStudioHostTruncatesLongEnvironmentID(t *testing.T) {
	req := &models.EvaluationRequest{
		ConversationMetadata: models.ConversationMetadata{
			Agent: models.AgentContext{ID: "agent-guid", EnvironmentID: "environment-with-a-long-id"},
		},
	}
	want := "agent-guid.copilot-studio-environmen.microsoft.com"
	if got := copilotStudioHost(req); got != want {
		t.Errorf("copilotStudioHost() = %q, want %q", got, want)
	}
}

func TestCopilotStudioHostOmitsEnvSegmentWhenEnvironmentIDAbsent(t *testing.T) {
	req := &models.EvaluationRequest{
		ConversationMetadata: models.ConversationMetadata{
			Agent: models.AgentContext{ID: "agent-guid"},
		},
	}
	want := "agent-guid.copilot-studio.microsoft.com"
	if got := copilotStudioHost(req); got != want {
		t.Errorf("copilotStudioHost() = %q, want %q", got, want)
	}
}

func TestCopilotStudioHostEmptyWhenAgentIDAbsent(t *testing.T) {
	req := &models.EvaluationRequest{}
	if got := copilotStudioHost(req); got != "" {
		t.Errorf("copilotStudioHost() = %q, want empty string", got)
	}
}

func TestMapValidationResultToResponse_Allowed(t *testing.T) {
	result := &mcp.ValidationResult{Allowed: true}
	resp := mapValidationResultToResponse(result)

	if resp.BlockAction {
		t.Error("BlockAction = true, want false for an allowed result")
	}
	if resp.Reason != "" || resp.Diagnostics != "" {
		t.Errorf("expected empty Reason/Diagnostics for an allowed result, got reason=%q diagnostics=%q", resp.Reason, resp.Diagnostics)
	}
}

func TestMapValidationResultToResponse_Blocked(t *testing.T) {
	result := &mcp.ValidationResult{
		Allowed: false,
		Reason:  "The action was blocked because there is a noncompliant email address in the BCC field.",
		Metadata: types.ThreatMetadata{
			PolicyName:   "pii-detection",
			RuleViolated: "bcc-domain-blocklist",
		},
	}
	resp := mapValidationResultToResponse(result)

	if !resp.BlockAction {
		t.Error("BlockAction = false, want true for a blocked result")
	}
	if resp.Reason != result.Reason {
		t.Errorf("Reason = %q, want %q", resp.Reason, result.Reason)
	}
	if resp.Diagnostics == "" {
		t.Error("expected non-empty Diagnostics for a blocked result with policy metadata")
	}
}
