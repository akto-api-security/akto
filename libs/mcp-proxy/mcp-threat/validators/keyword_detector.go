package validators

import (
	"context"
	"regexp"
	"strings"
	"time"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
)

// KeywordDetector provides fast keyword-based threat detection
// Implements the Validator interface
type KeywordDetector struct{}

// NewKeywordDetector creates a new keyword detector
func NewKeywordDetector() *KeywordDetector {
	return &KeywordDetector{}
}

// Proof represents the evidence found by keyword detection
type Proof struct {
	Keyword string `json:"keyword"`
	Snippet string `json:"snippet"`
}

// Threat keywords and patterns for fast detection
var regexPattern = `(?i)(?:/etc/passwd[\W_]+|/etc/shadow[\W_]+|/etc/sudoers[\W_]+|/etc/hosts[\W_]+|/etc/crontab[\W_]+|/root/[\W_]+|~/\.ssh/[\W_]+|/proc/[\W_]+|/sys/[\W_]+|/dev/random[\W_]+|/dev/urandom[\W_]+|/\.env[\W_]+|\.env[\W_]+|/etc/environment[\W_]+|C:\\\\Windows\\\\System32[\W_]+|C:\\\\Users\\\\Administrator[\W_]+|%SYSTEMROOT%[\W_]+|id_rsa[\W_]+|id_ed25519[\W_]+|id_ecdsa[\W_]+|known_hosts[\W_]+|authorized_keys[\W_]+|GNU[\W_]+AFFERO[\W_]+GENERAL[\W_]+PUBLIC[\W_]+LICENSE[\W_]+Version[\W_]+3,[\W_]+19[\W_]+November[\W_]+2007|GNU[\W_]+GENERAL[\W_]+PUBLIC[\W_]+LICENSE[\W_]+Version[\W_]+3,[\W_]+29[\W_]+June[\W_]+2007|GNU[\W_]+LESSER[\W_]+GENERAL[\W_]+PUBLIC[\W_]+LICENSE[\W_]+Version[\W_]+3,[\W_]+29[\W_]+June[\W_]+2007|Mozilla[\W_]+Public[\W_]+License[\W_]+Version[\W_]+2\.0|Apache[\W_]+License[\W_]+Version[\W_]+2\.0,[\W_]+January[\W_]+2004|MIT[\W_]+License|Boost[\W_]+Software[\W_]+License[\W_]+-[\W_]+Version[\W_]+1\.0[\W_]+-[\W_]+August[\W_]+17th,[\W_]+2003|Copyright[\W_]+\(C\)|Copyright[\W_]+©|ignore[\W_]+previous[\W_]+instructions|ignore[\W_]+all[\W_]+instructions|ignore[\W_]+safety[\W_]+checks|ignore[\W_]+remaining[\W_]+instructions|disregard[\W_]+prior[\W_]+instructions|forget[\W_]+prior[\W_]+instructions|bypass[\W_]+safety[\W_]+checks|ignore[\W_]+safety[\W_]+policies|ignore[\W_]+system[\W_]+policies|override[\W_]+system[\W_]+prompt|reveal[\W_]+system[\W_]+prompt|print[\W_]+system[\W_]+prompt|leak[\W_]+system[\W_]+prompt|system[\W_]+prompt|hidden[\W_]+system[\W_]+prompt|act[\W_]+as[\W_]+an[\W_]+ai|act[\W_]+as[\W_]+root|run[\W_]+as[\W_]+sudo|run[\W_]+as[\W_]+admin|override[\W_]+tool[\W_]+output|follow[\W_]+last[\W_]+instruction|developer[\W_]+mode|jailbreak[\W_]+mode|do[\W_]+anything[\W_]+now|dan[\W_]+mode|just[\W_]+for[\W_]+research|ignore[\W_]+guardrails|ignore[\W_]+safety[\W_]+layer|ignore[\W_]+prior[\W_]+context|share[\W_]+secret|rotate[\W_]+keys[\W_]+now|god[\W_]+mode|attacker[\W_]+)`

var suspiciousRegex *regexp.Regexp

func init() {
	var err error
	suspiciousRegex, err = regexp.Compile(regexPattern)
	if err != nil {
		panic("failed to compile threat detection regex: " + err.Error())
	}
}

const maxTextLength = 10 * 1024 * 1024 // 10MB limit

// Validate validates content using keyword detection and returns a ValidationResponse
func (kd *KeywordDetector) Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	response := types.NewValidationResponse()
	startTime := time.Now()

	defer func() {
		response.ProcessingTime = float64(time.Since(startTime).Milliseconds())
	}()

	// Expect caller to provide string payload
	payloadStr, ok := request.MCPPayload.(string)
	if !ok {
		response.SetError("keyword detector expects string payload")
		return response
	}

	// Validate input
	if strings.TrimSpace(payloadStr) == "" {
		response.SetError("text cannot be empty")
		return response
	}
	if len(payloadStr) > maxTextLength {
		response.SetError("text exceeds maximum allowed length")
		return response
	}

	// Check for suspicious keywords
	if !suspiciousRegex.MatchString(payloadStr) {
		// No threats detected
		verdict := types.NewVerdict()
		verdict.IsMaliciousRequest = false
		verdict.Confidence = 1.0
		verdict.PolicyAction = types.PolicyActionAllow
		verdict.Reasoning = "No suspicious keywords detected"
		response.SetSuccess(verdict, response.ProcessingTime)
		return response
	}

	// Threats detected
	loc := suspiciousRegex.FindStringIndex(payloadStr)
	start, end := loc[0], loc[1]

	// Extract snippet with context
	contextSize := 20
	before := start - contextSize
	if before < 0 {
		before = 0
	}
	after := end + contextSize
	if after > len(payloadStr) {
		after = len(payloadStr)
	}

	snippet := payloadStr[before:after]
	keyword := payloadStr[start:end]

	verdict := types.NewVerdict()
	verdict.IsMaliciousRequest = true
	verdict.Confidence = 1.0
	verdict.PolicyAction = types.PolicyActionBlock
	verdict.AddEvidence("keyword: " + keyword)
	verdict.AddEvidence("snippet: " + snippet)
	verdict.AddCategory(types.ThreatCategorySuspiciousKeyword)
	verdict.Reasoning = "Suspicious keywords detected by keyword detector"

	response.SetSuccess(verdict, response.ProcessingTime)
	return response
}
