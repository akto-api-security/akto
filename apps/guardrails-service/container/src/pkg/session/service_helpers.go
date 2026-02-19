package session

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/akto-api-security/mcp-endpoint-shield/mcp"
	"github.com/akto-api-security/mcp-endpoint-shield/mcp/types"
	"go.uber.org/zap"
)

// CheckAndHandleMaliciousSession checks if session is malicious and returns blocked response
func CheckAndHandleMaliciousSession(sessionMgr *SessionManager, logger *zap.Logger, sessionID, requestID, payload string) (*mcp.ValidationResult, bool) {
	if sessionMgr == nil || sessionID == "" {
		return nil, false
	}

	if !sessionMgr.IsSessionMalicious(sessionID) {
		return nil, false
	}

	// Track request for audit trail
	if payload != "" {
		sessionMgr.TrackRequest(sessionID, requestID, payload)
	}

	logger.Warn("Blocking request from malicious session",
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID))

	blockedResponse := "Session blocked due to previous malicious activity"

	// Track blocked response
	sessionMgr.TrackResponse(sessionID, requestID, blockedResponse, true)

	return &mcp.ValidationResult{
		Allowed:         false,
		Modified:        false,
		ModifiedPayload: "",
		Reason:          blockedResponse,
		Metadata:        types.ThreatMetadata{},
	}, true
}

// TrackBlockedResponse tracks blocked response in session manager
func TrackBlockedResponse(sessionMgr *SessionManager, logger *zap.Logger, sessionID, requestID string, processResult *mcp.ProcessResult) {
	if sessionMgr == nil || sessionID == "" || !processResult.IsBlocked {
		return
	}

	blockedResponseMsg := "Request violated guardrail policy rules. The payload was identified as potentially malicious or harmful based on configured security policies."
	blockReason := "Request blocked by guardrail policy"

	if len(processResult.BlockedResponse) > 0 {
		if errorObj, exists := processResult.BlockedResponse["error"]; exists {
			if errorMap, ok := errorObj.(map[string]interface{}); ok {
				// Extract message from error.message
				if msg, exists := errorMap["message"]; exists {
					if msgStr, ok := msg.(string); ok {
						blockedResponseMsg = msgStr
					}
				}

				if dataObj, exists := errorMap["data"]; exists {
					if dataMap, ok := dataObj.(map[string]interface{}); ok {
						if reason, exists := dataMap["reason"]; exists {
							if reasonStr, ok := reason.(string); ok {
								blockReason = reasonStr
							}
						}
					}
				}
			}
		}
	}

	sessionMgr.TrackResponse(sessionID, requestID, blockedResponseMsg, true)
	if blockReason != "" {
		sessionMgr.UpdateBlockedReason(sessionID, blockReason)
	}

	logger.Info("Tracked blocked response for session",
		zap.String("sessionID", sessionID),
		zap.String("requestID", requestID),
		zap.String("blockReason", blockReason))
}

// TrackRequestAndGenerateSummary tracks request and generates summary asynchronously
func TrackRequestAndGenerateSummary(sessionMgr *SessionManager, logger *zap.Logger, sessionID, requestID, payload string) {
	if sessionMgr == nil || sessionID == "" || payload == "" {
		return
	}

	sessionMgr.TrackRequest(sessionID, requestID, payload)

	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()

		// Extract just the prompt for summarization
		prompt := ExtractPromptFromRequestPayload(payload)
		if err := sessionMgr.GenerateAndUpdateSummary(ctx, sessionID, prompt, true); err != nil {
			logger.Warn("Failed to generate session summary for request",
				zap.String("sessionID", sessionID),
				zap.Error(err))
		}
	}()
}

// TrackResponseAndGenerateSummary tracks response and generates summary asynchronously
func TrackResponseAndGenerateSummary(sessionMgr *SessionManager, logger *zap.Logger, sessionID, requestID, payload string, isMalicious bool) {
	if sessionMgr == nil || sessionID == "" || payload == "" {
		return
	}

	sessionMgr.TrackResponse(sessionID, requestID, payload, isMalicious)

	// Only generate summary for non-blocked responses
	if !isMalicious {
		go func() {
			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			defer cancel()

			// Extract just the response for summarization
			response := ExtractResponseFromResponsePayload(payload)
			if err := sessionMgr.GenerateAndUpdateSummary(ctx, sessionID, response, false); err != nil {
				logger.Warn("Failed to generate session summary for response",
					zap.String("sessionID", sessionID),
					zap.Error(err))
			}
		}()
	}
}

// GetModifiedPayloadWithSummary injects session summary into payload if available
func GetModifiedPayloadWithSummary(sessionMgr *SessionManager, logger *zap.Logger, payload, sessionID string) string {
	if sessionMgr == nil || sessionID == "" {
		return payload
	}

	sessionSummary, err := sessionMgr.GetSessionSummary(sessionID)
	if err != nil || sessionSummary == "" {
		return payload
	}

	modifiedPayload, err := InjectSessionSummary(payload, sessionSummary, logger)
	if err != nil {
		logger.Debug("Failed to inject session summary",
			zap.String("sessionID", sessionID),
			zap.Error(err))
		return payload
	}

	logger.Info("Injected session summary into prompt",
		zap.String("sessionID", sessionID),
		zap.Int("summaryLength", len(sessionSummary)))

	return modifiedPayload
}

// InjectSessionSummary injects session summary into request_body.prompt field
func InjectSessionSummary(payload, sessionSummary string, logger *zap.Logger) (string, error) {
	if sessionSummary == "" {
		return payload, nil
	}

	// Parse outer payload
	var payloadObj map[string]interface{}
	if err := json.Unmarshal([]byte(payload), &payloadObj); err != nil {
		return payload, fmt.Errorf("failed to parse outer payload: %w", err)
	}

	// Get request_body string
	requestBodyStr, ok := payloadObj["request_body"].(string)
	if !ok {
		return payload, fmt.Errorf("request_body not found or not a string")
	}

	// Parse request_body JSON
	var requestBodyObj map[string]interface{}
	if err := json.Unmarshal([]byte(requestBodyStr), &requestBodyObj); err != nil {
		return payload, fmt.Errorf("failed to parse request_body JSON: %w", err)
	}

	// Get prompt field
	originalPrompt, ok := requestBodyObj["prompt"].(string)
	if !ok {
		return payload, fmt.Errorf("prompt field not found or not a string")
	}

	// Inject session summary at the beginning of the prompt
	requestBodyObj["prompt"] = sessionSummary + "\n\n" + originalPrompt

	// Re-encode request_body
	modifiedRequestBodyBytes, err := json.Marshal(requestBodyObj)
	if err != nil {
		return payload, fmt.Errorf("failed to re-encode request_body: %w", err)
	}
	payloadObj["request_body"] = string(modifiedRequestBodyBytes)

	// Re-encode outer payload
	modifiedPayloadBytes, err := json.Marshal(payloadObj)
	if err != nil {
		return payload, fmt.Errorf("failed to re-encode outer payload: %w", err)
	}

	return string(modifiedPayloadBytes), nil
}
