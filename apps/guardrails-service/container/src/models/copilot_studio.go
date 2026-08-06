package models

import (
	"bytes"
	"encoding/json"
)

// EvaluationRequest is the body of POST /analyze-tool-execution, sent by a
// Copilot Studio agent before invoking a tool.
// https://learn.microsoft.com/en-us/microsoft-copilot-studio/external-security-webhooks-interface-developers
type EvaluationRequest struct {
	PlannerContext       PlannerContext         `json:"plannerContext"`
	ToolDefinition       ToolDefinition         `json:"toolDefinition"`
	InputValues          map[string]interface{} `json:"inputValues"`
	ConversationMetadata ConversationMetadata   `json:"conversationMetadata"`
}

type PlannerContext struct {
	UserMessage          string                `json:"userMessage"`
	Thought              string                `json:"thought,omitempty"`
	ChatHistory          []ChatMessage         `json:"chatHistory,omitempty"`
	PreviousToolsOutputs []ToolExecutionOutput `json:"previousToolOutputs,omitempty"`
}

type ChatMessage struct {
	ID        string `json:"id"`
	Role      string `json:"role"`
	Content   string `json:"content"`
	Timestamp string `json:"timestamp,omitempty"`
}

type ToolExecutionOutput struct {
	ToolID    string           `json:"toolId"`
	ToolName  string           `json:"toolName"`
	Outputs   ExecutionOutputs `json:"outputs"`
	Timestamp string           `json:"timestamp,omitempty"`
}

type ExecutionOutput struct {
	Name        string      `json:"name"`
	Description string      `json:"description,omitempty"`
	Type        interface{} `json:"type,omitempty"`
	Value       interface{} `json:"value"`
}

// ExecutionOutputs tolerates both shapes Microsoft's own doc uses for this
// field: the reference table types it as ExecutionOutput[], but the worked
// example under POST /analyze-tool-execution sends a single ExecutionOutput
// object instead of an array.
type ExecutionOutputs []ExecutionOutput

func (e *ExecutionOutputs) UnmarshalJSON(data []byte) error {
	trimmed := bytes.TrimSpace(data)
	if len(trimmed) == 0 || string(trimmed) == "null" {
		*e = nil
		return nil
	}
	if trimmed[0] == '[' {
		var arr []ExecutionOutput
		if err := json.Unmarshal(data, &arr); err != nil {
			return err
		}
		*e = arr
		return nil
	}
	var single ExecutionOutput
	if err := json.Unmarshal(data, &single); err != nil {
		return err
	}
	*e = ExecutionOutputs{single}
	return nil
}

type ToolDefinition struct {
	ID               string       `json:"id"`
	Type             string       `json:"type"`
	Name             string       `json:"name"`
	Description      string       `json:"description"`
	InputParameters  []ToolInput  `json:"inputParameters,omitempty"`
	OutputParameters []ToolOutput `json:"outputParameters,omitempty"`
}

type ToolInput struct {
	Name        string      `json:"name"`
	Description string      `json:"description,omitempty"`
	Type        interface{} `json:"type,omitempty"`
}

type ToolOutput struct {
	Name        string      `json:"name"`
	Description string      `json:"description,omitempty"`
	Type        interface{} `json:"type,omitempty"`
}

type ConversationMetadata struct {
	Agent                  AgentContext    `json:"agent"`
	User                   *UserContext    `json:"user,omitempty"`
	Trigger                *TriggerContext `json:"trigger,omitempty"`
	ConversationID         string          `json:"conversationId"`
	PlanID                 string          `json:"planId,omitempty"`
	PlanStepID             string          `json:"planStepId,omitempty"`
	ParentAgentComponentID string          `json:"parentAgentComponentId,omitempty"`
}

type AgentContext struct {
	ID            string `json:"id"`
	TenantID      string `json:"tenantId"`
	EnvironmentID string `json:"environmentId"`
	// Name is the agent's display name (e.g. "금융 통찰력 세계") — not in
	// Microsoft's documented reference table, but present on real requests.
	Name        string `json:"name,omitempty"`
	Version     string `json:"version,omitempty"`
	IsPublished bool   `json:"isPublished"`
}

type UserContext struct {
	ID       string `json:"id,omitempty"`
	TenantID string `json:"tenantId,omitempty"`
}

type TriggerContext struct {
	ID         string `json:"id,omitempty"`
	SchemaName string `json:"schemaName,omitempty"`
}

// AnalyzeToolExecutionResponse is the 200 OK body of POST /analyze-tool-execution.
type AnalyzeToolExecutionResponse struct {
	BlockAction bool   `json:"blockAction"`
	ReasonCode  *int   `json:"reasonCode,omitempty"`
	Reason      string `json:"reason,omitempty"`
	Diagnostics string `json:"diagnostics,omitempty"`
}

// CopilotValidationResponse is the 200 OK body of POST /validate (Copilot Studio's
// setup/readiness check — distinct from models.ValidationResponse, used by /api/ingestData).
type CopilotValidationResponse struct {
	IsSuccessful bool   `json:"isSuccessful"`
	Status       string `json:"status"`
}

// WebhookErrorResponse is returned for non-2xx responses on either Copilot Studio
// webhook endpoint.
type WebhookErrorResponse struct {
	ErrorCode   int    `json:"errorCode"`
	Message     string `json:"message"`
	HTTPStatus  int    `json:"httpStatus"`
	Diagnostics string `json:"diagnostics,omitempty"`
}
