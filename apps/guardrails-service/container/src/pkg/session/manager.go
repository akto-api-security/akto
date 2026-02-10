package session

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"
)

const (
	maxSyncBatchSize = 1000
)

// ConversationEntry represents a single conversation turn (request + response)
type ConversationEntry struct {
	RequestPayload  string `json:"requestPayload"`
	ResponsePayload string `json:"responsePayload"`
	RequestID       string `json:"requestId"`
	Timestamp       int64  `json:"timestamp"`
}

// SessionData holds in-memory session state
type SessionData struct {
	SessionID       string
	Conversations   []ConversationEntry
	LastSummary     string
	BlockedReason   string // Stores the most recent blocked request reason
	IsMalicious     bool
	LastUpdated     int64
	LastSyncedAt    int64 // Track when this session was last synced to cyborg
	CreatedAt       int64
	PendingRequests map[string]*ConversationEntry
	mu              sync.RWMutex
}

// SessionManager manages session state with in-memory cache and periodic sync to cyborg API
type SessionManager struct {
	sessions      map[string]*SessionData
	mu            sync.RWMutex
	httpClient    *http.Client
	cyborgURL     string
	authToken     string
	syncInterval  time.Duration
	inactiveAfter time.Duration // Remove sessions from memory after this duration of inactivity
	logger        *zap.Logger
	stopChan      chan struct{}
	wg            sync.WaitGroup
}

// SessionDocument represents the session context document structure for API calls
type SessionDocument struct {
	SessionIdentifier string              `json:"sessionIdentifier"`
	SessionSummary    string              `json:"sessionSummary"`
	ConversationInfo  []ConversationEntry `json:"conversationInfo"`
	BlockedReason     string              `json:"blockedReason,omitempty"` // Stores the most recent blocked request reason
	IsMalicious       bool                `json:"isMalicious"`
	UpdatedAt         int64               `json:"updatedAt"`
	CreatedAt         int64               `json:"createdAt"`
}

// NewSessionManager creates a new session manager with cyborg API client
func NewSessionManager(cyborgURL, authToken string, syncInterval time.Duration, logger *zap.Logger) (*SessionManager, error) {
	return &SessionManager{
		sessions: make(map[string]*SessionData),
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		cyborgURL:     strings.TrimSuffix(cyborgURL, "/"),
		authToken:     authToken,
		syncInterval:  syncInterval,
		inactiveAfter: 10 * time.Minute, // Remove sessions inactive for 10 minutes
		logger:        logger,
		stopChan:      make(chan struct{}),
	}, nil
}

// Start begins the periodic sync goroutine
func (sm *SessionManager) Start() {
	sm.wg.Add(1)
	go sm.syncLoop()
	sm.logger.Info("Session manager started", zap.Duration("syncInterval", sm.syncInterval))
}

// Stop gracefully shuts down the session manager
func (sm *SessionManager) Stop() {
	close(sm.stopChan)
	sm.wg.Wait()

	// Final sync to cyborg API
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	sm.syncToCyborg(ctx)

	sm.logger.Info("Session manager stopped")
}

// syncLoop runs the periodic sync process
func (sm *SessionManager) syncLoop() {
	defer sm.wg.Done()
	ticker := time.NewTicker(sm.syncInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
			sm.syncToCyborg(ctx)
			cancel()
		case <-sm.stopChan:
			return
		}
	}
}

// syncToCyborg persists modified sessions to cyborg API and cleans up inactive sessions
func (sm *SessionManager) syncToCyborg(ctx context.Context) {
	now := time.Now().Unix()
	inactiveThreshold := now - int64(sm.inactiveAfter.Seconds())

	// First pass: identify inactive sessions to remove
	sm.mu.Lock()
	toRemove := make([]string, 0)
	for sessionID, session := range sm.sessions {
		session.mu.RLock()
		if session.LastUpdated < inactiveThreshold {
			toRemove = append(toRemove, sessionID)
		}
		session.mu.RUnlock()
	}

	// Remove inactive sessions from memory
	for _, sessionID := range toRemove {
		delete(sm.sessions, sessionID)
	}

	// Copy remaining sessions for sync
	sessionsCopy := make(map[string]*SessionData, len(sm.sessions))
	for k, v := range sm.sessions {
		sessionsCopy[k] = v
	}
	sm.mu.Unlock()

	if len(toRemove) > 0 {
		sm.logger.Info("Removed inactive sessions from memory",
			zap.Int("count", len(toRemove)))
	}

	skippedCount := 0
	syncCount := 0
	errorCount := 0

	// Collect and sync sessions in batches
	docsToSync := make([]SessionDocument, 0, maxSyncBatchSize)
	sessionsToUpdate := make([]*SessionData, 0, maxSyncBatchSize)

	for sessionID, session := range sessionsCopy {
		session.mu.RLock()

		// Skip if session hasn't been modified since last sync
		if session.LastSyncedAt >= session.LastUpdated {
			session.mu.RUnlock()
			skippedCount++
			continue
		}

		// Truncate conversation payloads to 100 words for DB storage
		truncatedConvs := make([]ConversationEntry, len(session.Conversations))
		for i, conv := range session.Conversations {
			truncatedConvs[i] = ConversationEntry{
				RequestPayload:  truncateToWords(conv.RequestPayload, 200),
				ResponsePayload: truncateToWords(conv.ResponsePayload, 200),
				RequestID:       conv.RequestID,
				Timestamp:       conv.Timestamp,
			}
		}

		doc := SessionDocument{
			SessionIdentifier: sessionID,
			SessionSummary:    session.LastSummary,
			ConversationInfo:  truncatedConvs,
			BlockedReason:     session.BlockedReason,
			IsMalicious:       session.IsMalicious,
			UpdatedAt:         session.LastUpdated,
			CreatedAt:         session.CreatedAt,
		}
		session.mu.RUnlock()

		docsToSync = append(docsToSync, doc)
		sessionsToUpdate = append(sessionsToUpdate, session)

		if len(docsToSync) >= maxSyncBatchSize {
			sm.logger.Info("Syncing session batch to cyborg", zap.Int("batchSize", len(docsToSync)))

			if err := sm.bulkUpsertSessionContexts(ctx, docsToSync); err != nil {
				sm.logger.Error("Failed to sync session batch to cyborg API", zap.Error(err))
				errorCount += len(docsToSync)
			} else {
				for _, sess := range sessionsToUpdate {
					sess.mu.Lock()
					sess.LastSyncedAt = now
					sess.mu.Unlock()
				}
				syncCount += len(docsToSync)
			}

			docsToSync = make([]SessionDocument, 0, maxSyncBatchSize)
			sessionsToUpdate = make([]*SessionData, 0, maxSyncBatchSize)
		}
	}

	if len(docsToSync) > 0 {
		sm.logger.Info("Syncing remaining sessions to cyborg", zap.Int("batchSize", len(docsToSync)))

		if err := sm.bulkUpsertSessionContexts(ctx, docsToSync); err != nil {
			sm.logger.Error("Failed to sync remaining sessions to cyborg API", zap.Error(err))
			errorCount += len(docsToSync)
		} else {
			// Update LastSyncedAt for successfully synced sessions
			for _, sess := range sessionsToUpdate {
				sess.mu.Lock()
				sess.LastSyncedAt = now
				sess.mu.Unlock()
			}
			syncCount += len(docsToSync)
		}
	}

	sm.logger.Info("Synced sessions to cyborg API",
		zap.Int("totalSessions", len(sessionsCopy)),
		zap.Int("synced", syncCount),
		zap.Int("skipped", skippedCount),
		zap.Int("errors", errorCount),
		zap.Int("removed", len(toRemove)))
}

// bulkUpsertSessionContexts creates or updates multiple session contexts via cyborg API
func (sm *SessionManager) bulkUpsertSessionContexts(ctx context.Context, docs []SessionDocument) error {
	if len(docs) == 0 {
		return nil
	}

	// Wrap the array in an object with "sessionDocuments" key
	requestBody := map[string]interface{}{
		"sessionDocuments": docs,
	}

	jsonData, err := json.Marshal(requestBody)
	if err != nil {
		return fmt.Errorf("failed to marshal session documents: %w", err)
	}

	sm.logger.Info("Bulk update request body",
		zap.String("requestBody", string(jsonData)),
		zap.Int("sessionCount", len(docs)))

	endpoint := sm.cyborgURL + "/api/agenticSessionContext/bulkUpdate"
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if sm.authToken != "" {
		req.Header.Set("Authorization", sm.authToken)
	}

	resp, err := sm.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to call cyborg API: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("cyborg API returned status %d: %s", resp.StatusCode, string(body))
	}

	return nil
}

// getSessionContext retrieves a session context from cyborg API
func (sm *SessionManager) getSessionContext(ctx context.Context, sessionID string) (*SessionDocument, error) {
	endpoint := fmt.Sprintf("%s/api/agenticSessionContext/get?sessionIdentifier=%s", sm.cyborgURL, sessionID)
	req, err := http.NewRequestWithContext(ctx, "GET", endpoint, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	if sm.authToken != "" {
		req.Header.Set("Authorization", sm.authToken)
	}

	resp, err := sm.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to call cyborg API: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, nil // Session not found
	}

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("cyborg API returned status %d: %s", resp.StatusCode, string(body))
	}

	var doc SessionDocument
	if err := json.NewDecoder(resp.Body).Decode(&doc); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &doc, nil
}

// truncateToWords truncates a string to approximately N words
func truncateToWords(s string, maxWords int) string {
	words := strings.Fields(s)
	if len(words) <= maxWords {
		return s
	}
	return strings.Join(words[:maxWords], " ") + "..."
}

// ExtractPromptFromRequestPayload extracts request_body.prompt from the payload JSON
func ExtractPromptFromRequestPayload(payload string) string {
	if payload == "" {
		return ""
	}

	// Parse outer payload
	var payloadObj map[string]interface{}
	if err := json.Unmarshal([]byte(payload), &payloadObj); err != nil {
		return payload // Return original if parsing fails
	}

	// Get request_body string
	requestBodyStr, ok := payloadObj["request_body"].(string)
	if !ok {
		return payload // Return original if request_body not found
	}

	// Parse request_body JSON
	var requestBodyObj map[string]interface{}
	if err := json.Unmarshal([]byte(requestBodyStr), &requestBodyObj); err != nil {
		return payload // Return original if parsing fails
	}

	// Get prompt field
	prompt, ok := requestBodyObj["prompt"].(string)
	if !ok {
		return payload // Return original if prompt not found
	}

	return prompt
}

// ExtractResponseFromResponsePayload extracts response_body.response from the payload JSON
func ExtractResponseFromResponsePayload(payload string) string {
	if payload == "" {
		return ""
	}

	// Parse outer payload
	var payloadObj map[string]interface{}
	if err := json.Unmarshal([]byte(payload), &payloadObj); err != nil {
		return payload // Return original if parsing fails
	}

	// Get response_body string
	responseBodyStr, ok := payloadObj["response_body"].(string)
	if !ok {
		return payload // Return original if response_body not found
	}

	// Parse response_body JSON
	var responseBodyObj map[string]interface{}
	if err := json.Unmarshal([]byte(responseBodyStr), &responseBodyObj); err != nil {
		return payload // Return original if parsing fails
	}

	// Get response field
	response, ok := responseBodyObj["response"].(string)
	if !ok {
		return payload // Return original if response not found
	}

	return response
}

// TrackRequest stores a pending request for later correlation with response
func (sm *SessionManager) TrackRequest(sessionID, requestID, requestPayload string) {
	if sessionID == "" || requestID == "" {
		return
	}

	// Extract only the prompt from request_body.prompt
	prompt := ExtractPromptFromRequestPayload(requestPayload)

	sm.mu.Lock()
	session, exists := sm.sessions[sessionID]
	if !exists {
		now := time.Now().Unix()
		session = &SessionData{
			SessionID:       sessionID,
			Conversations:   make([]ConversationEntry, 0, 10),
			PendingRequests: make(map[string]*ConversationEntry),
			LastUpdated:     now,
			CreatedAt:       now,
		}
		sm.sessions[sessionID] = session
	}
	sm.mu.Unlock()

	session.mu.Lock()
	session.PendingRequests[requestID] = &ConversationEntry{
		RequestPayload: prompt, // Store only the prompt
		Timestamp:      time.Now().Unix(),
		RequestID:      requestID,
	}
	session.mu.Unlock()
}

// TrackResponse correlates a response with its request and adds to conversation history
func (sm *SessionManager) TrackResponse(sessionID, requestID, responsePayload string, isMalicious bool) {
	if sessionID == "" || requestID == "" {
		return
	}

	// Extract only the response from response_body.response
	response := ExtractResponseFromResponsePayload(responsePayload)

	sm.mu.RLock()
	session, exists := sm.sessions[sessionID]
	sm.mu.RUnlock()

	if !exists {
		sm.logger.Warn("Session not found for response", zap.String("sessionID", sessionID))
		return
	}

	session.mu.Lock()
	defer session.mu.Unlock()

	// Find matching pending request
	pendingReq, found := session.PendingRequests[requestID]
	if !found {
		sm.logger.Warn("No pending request found for response",
			zap.String("sessionID", sessionID),
			zap.String("requestID", requestID))
		// Create incomplete conversation entry with just response
		pendingReq = &ConversationEntry{
			RequestID: requestID,
			Timestamp: time.Now().Unix(),
		}
	}

	// Complete the conversation
	pendingReq.ResponsePayload = response // Store only the response

	// Add to conversation history (keep last 10)
	session.Conversations = append(session.Conversations, *pendingReq)
	if len(session.Conversations) > 10 {
		session.Conversations = session.Conversations[len(session.Conversations)-10:]
	}

	// Update malicious status
	if isMalicious {
		session.IsMalicious = true
	}

	// Remove from pending
	delete(session.PendingRequests, requestID)

	session.LastUpdated = time.Now().Unix()
}

// IsSessionMalicious checks if a session has been marked as malicious
func (sm *SessionManager) IsSessionMalicious(sessionID string) bool {
	sm.mu.RLock()
	session, exists := sm.sessions[sessionID]
	sm.mu.RUnlock()

	if exists {
		session.mu.RLock()
		isMalicious := session.IsMalicious
		session.mu.RUnlock()
		return isMalicious
	}

	// Check cyborg API on cache miss
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	doc, err := sm.getSessionContext(ctx, sessionID)
	if err != nil || doc == nil {
		return false
	}

	// Cache the session data
	sm.mu.Lock()
	sm.sessions[sessionID] = &SessionData{
		SessionID:       sessionID,
		Conversations:   doc.ConversationInfo,
		LastSummary:     doc.SessionSummary,
		BlockedReason:   doc.BlockedReason,
		IsMalicious:     doc.IsMalicious,
		LastUpdated:     doc.UpdatedAt,
		CreatedAt:       doc.CreatedAt,
		PendingRequests: make(map[string]*ConversationEntry),
	}
	sm.mu.Unlock()

	return doc.IsMalicious
}

// GetSessionSummary retrieves the session summary from cache or cyborg API
func (sm *SessionManager) GetSessionSummary(sessionID string) (string, error) {
	sm.mu.RLock()
	session, exists := sm.sessions[sessionID]
	sm.mu.RUnlock()

	if exists {
		session.mu.RLock()
		summary := session.LastSummary
		session.mu.RUnlock()
		return summary, nil
	}

	// Load from cyborg API on cache miss
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	doc, err := sm.getSessionContext(ctx, sessionID)
	if err != nil {
		return "", err
	}

	if doc == nil {
		return "", nil // Session not found, return empty
	}

	// Cache the fetched session data in memory
	sm.mu.Lock()
	sm.sessions[sessionID] = &SessionData{
		SessionID:       sessionID,
		Conversations:   doc.ConversationInfo,
		LastSummary:     doc.SessionSummary,
		BlockedReason:   doc.BlockedReason,
		IsMalicious:     doc.IsMalicious,
		LastUpdated:     doc.UpdatedAt,
		CreatedAt:       doc.CreatedAt,
		PendingRequests: make(map[string]*ConversationEntry),
	}
	sm.mu.Unlock()

	sm.logger.Info("Cached session from cyborg API",
		zap.String("sessionID", sessionID),
		zap.Int("conversationCount", len(doc.ConversationInfo)))

	return doc.SessionSummary, nil
}

// UpdateSessionSummary updates the LLM-generated summary for a session
func (sm *SessionManager) UpdateSessionSummary(sessionID, summary string) {
	sm.mu.RLock()
	session, exists := sm.sessions[sessionID]
	sm.mu.RUnlock()

	if exists {
		session.mu.Lock()
		session.LastSummary = summary
		session.LastUpdated = time.Now().Unix()
		session.mu.Unlock()
	}
}

// UpdateBlockedReason updates the blocked reason for a session
func (sm *SessionManager) UpdateBlockedReason(sessionID, reason string) {
	sm.mu.RLock()
	session, exists := sm.sessions[sessionID]
	sm.mu.RUnlock()

	if exists {
		session.mu.Lock()
		session.BlockedReason = reason
		session.LastUpdated = time.Now().Unix()
		session.mu.Unlock()
	}
}

// GetConversations retrieves the conversation history for a session
func (sm *SessionManager) GetConversations(sessionID string) []ConversationEntry {
	sm.mu.RLock()
	session, exists := sm.sessions[sessionID]
	sm.mu.RUnlock()

	if exists {
		session.mu.RLock()
		defer session.mu.RUnlock()
		// Return a copy to avoid race conditions
		return append([]ConversationEntry{}, session.Conversations...)
	}

	return nil
}

// buildSummarizationPrompt creates the system prompt for LLM summarization
// isRequest: true for user requests (focus on user actions), false for system responses (provide context)
func buildSummarizationPrompt(existingSummary, currentItem string, isRequest bool) string {
	if isRequest {
		// Focus on what the user has actually done/tried
		if existingSummary != "" {
			return fmt.Sprintf(`You are summarizing a conversation session. Focus on what the USER has tried so far and what they are attempting to accomplish.

Existing conversation summary:
%s

New user request:
%s

Generate an updated summary (50-75 words max) that:
1. Describes what the USER has tried so far in this session
2. Captures what the USER is attempting to accomplish based on their actual requests
3. Identifies patterns in user behavior (e.g., repeated attempts, exploring different approaches)
4. Does NOT predict future actions - only describe what has happened

Be concise. Respond with ONLY the updated summary text.`, existingSummary, currentItem)
		}

		return fmt.Sprintf(`You are summarizing a conversation session. Focus on what the USER is attempting to accomplish.

First user request:
%s

Generate a brief summary (50-75 words max) that:
1. Describes what the USER is trying to do based on their request
2. Captures the USER'S intent and actions

Be concise. Respond with ONLY the summary text.`, currentItem)
	}

	// Response case: Use response as context to better understand the session
	if existingSummary != "" {
		return fmt.Sprintf(`You are summarizing a conversation session. Focus on what the USER has tried so far and what they are attempting to accomplish.

Existing conversation summary (about what USER has tried):
%s

System response (for context - helps understand the session better):
%s

Generate an updated summary (50-75 words max) that:
1. Describes what the USER has tried so far in this session
2. Uses the response only as context to better understand the overall session
3. Captures what the USER is attempting to accomplish
4. Does NOT predict future actions or describe what the system did
5. Focus on actual USER actions taken, not speculative intent

Be concise. Respond with ONLY the updated summary text about what the USER has tried.`, existingSummary, currentItem)
	}

	return fmt.Sprintf(`You are summarizing a conversation session. Focus on understanding what the USER is attempting based on context.

System response received (provides session context):
%s

Generate a brief summary (50-75 words max) that:
1. Describes what you can infer about what the USER is trying to accomplish
2. Uses this response only as context to understand the session
3. Does NOT predict future actions or describe system behavior

Be concise. Respond with ONLY the summary text.`, currentItem)
}

// GenerateAndUpdateSummary calls cyborg's getLLMResponseV2 to generate a new summary
// isRequest: true for user requests, false for system responses
func (sm *SessionManager) GenerateAndUpdateSummary(ctx context.Context, sessionID string, currentItem string, isRequest bool) error {
	if currentItem == "" {
		return nil
	}

	// Get existing summary and build prompt
	existingSummary, _ := sm.GetSessionSummary(sessionID)
	systemPrompt := buildSummarizationPrompt(existingSummary, currentItem, isRequest)

	// Call cyborg's getLLMResponseV2 endpoint
	requestBody := map[string]interface{}{
		"llmPayload": map[string]interface{}{
			"temperature":       0.1,
			"top_p":             0.9,
			"max_tokens":        150,
			"frequency_penalty": 0.0,
			"presence_penalty":  0.6,
			"messages": []map[string]string{
				{
					"role":    "system",
					"content": systemPrompt,
				},
			},
		},
	}

	jsonData, err := json.Marshal(requestBody)
	if err != nil {
		return fmt.Errorf("failed to marshal LLM request: %w", err)
	}

	endpoint := sm.cyborgURL + "/api/getLLMResponseV2"
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create LLM request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	if sm.authToken != "" {
		req.Header.Set("Authorization", sm.authToken)
	}

	resp, err := sm.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to call LLM API: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("LLM API returned status %d: %s", resp.StatusCode, string(body))
	}

	// Parse LLM response (OpenAI-compatible format)
	var llmResponse struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&llmResponse); err != nil {
		return fmt.Errorf("failed to decode LLM response: %w", err)
	}

	// Extract and update summary
	if len(llmResponse.Choices) > 0 {
		summary := strings.TrimSpace(llmResponse.Choices[0].Message.Content)
		if summary != "" {
			sm.UpdateSessionSummary(sessionID, summary)
			sm.logger.Info("Updated session summary via LLM",
				zap.String("sessionID", sessionID),
				zap.Int("summaryLength", len(summary)),
				zap.String("sessionSummary", summary))
		}
	}

	return nil
}
