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
	modelRelPath               = "mcp-threat/transformers/models/prompt-injection/onnx/model.onnx"
	tokenizerRelPath           = "mcp-threat/transformers/models/prompt-injection/onnx/tokenizer.json"
	libonnxRuntimePathForMacOS = "/opt/homebrew/lib/libonnxruntime.dylib"
	PromptUpperThreshold       = 0.9
	PromptLowerThreshold       = 0.4
)

func getLibonnxRuntimePath() string {
	if path := os.Getenv("LIBONNX_RUNTIME_PATH"); path != "" {
		return path
	}
	return libonnxRuntimePathForMacOS
}

func init() {
	wd, err := os.Getwd()
	fmt.Println("Working dir:", wd)
	if err != nil {
		log.Printf("failed to get working dir: %v", err)
	}

	modelPath := filepath.Join(wd, modelRelPath)
	tokenizerPath := filepath.Join(wd, tokenizerRelPath)

	listFilesInRelDir(wd, "mcp-threat/transformers/models/prompt-injection/onnx/")
	listFilesInAbsDir("/usr/local/lib/")

	fmt.Println("Model path:", modelPath)
	fmt.Println("Tokenizer path:", tokenizerPath)

	detector, err = transformers.NewDetector(tokenizerPath, modelPath, getLibonnxRuntimePath())
	if err != nil {
		log.Printf("failed to create detector: %v", err)
	}
}

func listFilesInRelDir(wd, dir string) {
	absDir := filepath.Join(wd, dir)
	listFilesInAbsDir(absDir)
}

func listFilesInAbsDir(absDir string) {
	fmt.Println("Absolute dir:", absDir)
	// List files in absDir
	files, err := os.ReadDir(absDir)
	if err != nil {
		fmt.Printf("Error reading %s directory: %v\n", absDir, err)
	} else {
		fmt.Printf("Files in %s directory:\n", absDir)
		for _, file := range files {
			fmt.Printf("  - %s\n", file.Name())
		}
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
