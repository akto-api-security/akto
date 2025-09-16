package policies

import (
	"context"
	"fmt"
	"log"
	"regexp"
	"strings"
	"time"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
)

// PolicyValidator implements the Validator interface for policy-based validation
type PolicyValidator struct {
	policyManager PolicyManager
}

// NewPolicyValidator creates a new policy validator
func NewPolicyValidator(policyManager PolicyManager) *PolicyValidator {
	return &PolicyValidator{
		policyManager: policyManager,
	}
}

// Validate validates MCP payloads against loaded policies (defaults to request)
func (pv *PolicyValidator) Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	return pv.validatePayload(ctx, request, true) // Default to request for backward compatibility
}

// ValidateRequest validates request payloads against loaded policies
func (pv *PolicyValidator) ValidateRequest(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	return pv.validatePayload(ctx, request, true)
}

// ValidateResponse validates response payloads against loaded policies
func (pv *PolicyValidator) ValidateResponse(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	return pv.validatePayload(ctx, request, false)
}

// validatePayload is the internal method that handles the actual validation logic
func (pv *PolicyValidator) validatePayload(ctx context.Context, request *types.ValidationRequest, isRequest bool) *types.ValidationResponse {
	response := types.NewValidationResponse()
	startTime := time.Now()

	defer func() {
		response.ProcessingTime = float64(time.Since(startTime).Milliseconds())
	}()

	// Get all enabled policies
	policies, err := pv.policyManager.LoadPolicies()
	if err != nil {
		response.SetError(fmt.Sprintf("Failed to load policies: %v", err))
		return response
	}

	// Convert payload to string
	payloadStr, ok := request.MCPPayload.(string)
	if !ok {
		response.SetError("policy validator expects string payload")
		return response
	}

	// Validate input
	if strings.TrimSpace(payloadStr) == "" {
		response.SetError("payload cannot be empty")
		return response
	}

	// Check each policy
	for _, policy := range policies {
		var validators []PayloadValidator

		if isRequest && len(policy.Filter.RequestPayload) > 0 {
			validators = policy.Filter.RequestPayload
		} else if !isRequest && len(policy.Filter.ResponsePayload) > 0 {
			validators = policy.Filter.ResponsePayload
		} else {
			continue // Skip policies that don't apply to this payload type
		}

		if result := pv.evaluateValidators(validators, payloadStr, policy.ID); result != nil {
			response.SetSuccess(result, response.ProcessingTime)
			return response
		}
	}

	// No policy violations found
	verdict := types.NewVerdict()
	verdict.IsMaliciousRequest = false
	verdict.Confidence = 1.0
	verdict.PolicyAction = types.PolicyActionAllow
	verdict.Reasoning = "No policy violations detected"

	response.SetSuccess(verdict, response.ProcessingTime)
	return response
}

// evaluateValidators runs all validators for a policy and returns the first violation
func (pv *PolicyValidator) evaluateValidators(validators []PayloadValidator, payload string, policyID string) *types.Verdict {
	for _, validator := range validators {
		if result := pv.evaluateValidator(validator, payload, policyID); result != nil {
			return result
		}
	}
	return nil
}

// evaluateValidator evaluates a single validator against the payload
func (pv *PolicyValidator) evaluateValidator(validator PayloadValidator, payload string, policyID string) *types.Verdict {
	// Check which validator type is present and execute accordingly
	switch {
	case validator.Regex != "":
		if pv.matchesRegex(validator.Regex, payload) {
			return pv.createViolationVerdict(policyID, "regex", validator.Regex)
		}

	case len(validator.ContainsEither) > 0:
		if pv.containsAny(payload, validator.ContainsEither) {
			return pv.createViolationVerdict(policyID, "contains_either", strings.Join(validator.ContainsEither, ", "))
		}

	case validator.NLPClassification != nil:
		if pv.matchesNLPClassification(payload, *validator.NLPClassification) {
			return pv.createViolationVerdict(policyID, "nlp_classification",
				fmt.Sprintf("category: %s, threshold: %.2f",
					validator.NLPClassification.Category,
					validator.NLPClassification.GT))
		}
	}

	return nil
}

// matchesRegex checks if the payload matches the given regex pattern
func (pv *PolicyValidator) matchesRegex(pattern string, payload string) bool {
	regex, err := regexp.Compile(pattern)
	if err != nil {
		log.Printf("Invalid regex pattern: %s, error: %v", pattern, err)
		return false
	}
	return regex.MatchString(payload)
}

// containsAny checks if the payload contains any of the specified keywords
func (pv *PolicyValidator) containsAny(payload string, keywords []string) bool {
	payloadLower := strings.ToLower(payload)
	for _, keyword := range keywords {
		if strings.Contains(payloadLower, strings.ToLower(keyword)) {
			return true
		}
	}
	return false
}

// matchesNLPClassification checks if the payload matches NLP classification criteria
// This is a placeholder implementation - will be integrated with existing NLP models later
func (pv *PolicyValidator) matchesNLPClassification(payload string, nlp NLPClassification) bool {
	// TODO: Integrate with existing NLP models in mcp-threat/models/
	// For now, return false as placeholder
	log.Printf("NLP Classification not yet implemented: category=%s, threshold=%.2f, payload_length=%d",
		nlp.Category, nlp.GT, len(payload))
	return false
}

// createViolationVerdict creates a verdict for a policy violation
func (pv *PolicyValidator) createViolationVerdict(policyID, validatorType, evidence string) *types.Verdict {
	verdict := types.NewVerdict()
	verdict.IsMaliciousRequest = true
	verdict.Confidence = 1.0
	verdict.PolicyAction = types.PolicyActionBlock
	verdict.Reasoning = fmt.Sprintf("Policy violation in %s", policyID)
	verdict.AddCategory(types.ThreatCategorySuspiciousKeyword)
	verdict.AddEvidence(fmt.Sprintf("policy: %s, type: %s, match: %s", policyID, validatorType, evidence))

	return verdict
}
