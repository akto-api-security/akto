package handlers

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

// ChatCompletionResponse is the OpenAI chat-completion response shape
// expected by Databricks external-model serving (llm/v1/chat task):
// https://docs.databricks.com/aws/en/machine-learning/foundation-models/external-models
type ChatCompletionResponse struct {
	ID      string   `json:"id"`
	Object  string   `json:"object"`
	Created int64    `json:"created"`
	Model   string   `json:"model"`
	Choices []Choice `json:"choices"`
	Usage   Usage    `json:"usage"`
}

type Choice struct {
	Index        int     `json:"index"`
	Message      Message `json:"message"`
	FinishReason string  `json:"finish_reason"`
}

type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type Usage struct {
	PromptTokens int `json:"prompt_tokens"`
	TotalTokens  int `json:"total_tokens"`
}

const defaultChatCompletionModel = "guardrails-stub-model"

// ChatCompletions is a stub OpenAI-compatible /v1/chat/completions endpoint
// with no relation to guardrails' own validation logic. It exists solely so
// guardrails-service can be registered as a Databricks custom external
// model provider for testing. It accepts any request body, logs the
// headers and body, and always returns a fixed canned response.
func ChatCompletions(logger *zap.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		body, err := c.GetRawData()
		if err != nil {
			logger.Warn("chat/completions: failed to read request body", zap.Error(err))
		}

		logger.Info("chat/completions: request received",
			zap.Any("headers", c.Request.Header),
			zap.ByteString("body", body))

		c.JSON(http.StatusOK, ChatCompletionResponse{
			ID:      "chatcmpl-stub-1",
			Object:  "chat.completion",
			Created: time.Now().Unix(),
			Model:   modelFromBody(body),
			Choices: []Choice{
				{
					Index: 0,
					Message: Message{
						Role:    "assistant",
						Content: "This is a stub response from guardrails-service.",
					},
					FinishReason: "stop",
				},
			},
			Usage: Usage{
				PromptTokens: 8,
				TotalTokens:  8,
			},
		})
	}
}

// modelFromBody best-effort echoes the caller-supplied "model" field back
// in the response; falls back to a fixed default if absent or unparseable.
func modelFromBody(body []byte) string {
	var parsed struct {
		Model string `json:"model"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil || parsed.Model == "" {
		return defaultChatCompletionModel
	}
	return parsed.Model
}
