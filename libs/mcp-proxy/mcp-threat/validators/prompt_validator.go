package validators

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/transformers"
	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
)

type PromptValidator struct{}

// NewPromptValidator creates a new prompt validator
func NewPromptValidator() *PromptValidator {
	return &PromptValidator{}
}

var detector *transformers.Detector

const (
	// These files need to be downloaded separately.
	// ref:
	modelRelPath         = "mcp-threat/transformers/models/prompt-injection/onnx/model.onnx"
	tokenizerRelPath     = "mcp-threat/transformers/models/prompt-injection/onnx/tokenizer.json"
	libonnxRuntimePath   = "/opt/homebrew/lib/libonnxruntime.dylib"
	PromptUpperThreshold = 0.9
	PromptLowerThreshold = 0.4
)

func init() {
	wd, err := os.Getwd()
	if err != nil {
		log.Printf("failed to get working dir: %v", err)
	}

	modelPath := filepath.Join(wd, modelRelPath)
	tokenizerPath := filepath.Join(wd, tokenizerRelPath)

	fmt.Println("Model path:", modelPath)
	fmt.Println("Tokenizer path:", tokenizerPath)

	detector, err = transformers.NewDetector(tokenizerPath, modelPath, libonnxRuntimePath)
	if err != nil {
		log.Printf("failed to create detector: %v", err)
	}
}

func (pv *PromptValidator) Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {

	response := types.NewValidationResponse()
	startTime := time.Now()

	defer func() {
		response.ProcessingTime = float64(time.Since(startTime).Milliseconds())
	}()

	// Expect caller to provide string payload
	payloadStr, ok := request.MCPPayload.(string)
	if !ok {
		response.SetError("prompt validator expects string payload")
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

	isInj, p, err := detector.Detect(payloadStr, PromptUpperThreshold)
	if err != nil {
		response.SetError("detection failed: " + err.Error())
		return response
	}

	verdict := types.NewVerdict()
	verdict.IsMaliciousRequest = isInj
	verdict.Confidence = float64(p)
	if verdict.IsMaliciousRequest {
		verdict.PolicyAction = types.PolicyActionBlock
		verdict.AddCategory(types.ThreatCategoryPromptInjectionTextClassifier)
		verdict.Reasoning = "prompt injection detected"
	}

	response.SetSuccess(verdict, response.ProcessingTime)
	return response
}
