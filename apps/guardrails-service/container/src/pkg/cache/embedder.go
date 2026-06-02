package cache

import (
	"context"
	"fmt"
	"time"

	"github.com/knights-analytics/hugot"
	"github.com/knights-analytics/hugot/pipelines"
	"go.uber.org/zap"
)

// Embedder wraps a hugot feature-extraction pipeline for generating
// sentence embeddings using sentence-transformers/all-MiniLM-L6-v2.
// The Go backend (GoMLX) is used — no CGO, no system ONNX Runtime library.
// On first startup, DownloadModel fetches the ONNX model from HuggingFace
// (~90 MB, one-time). Subsequent starts load from modelDir instantly.
type Embedder struct {
	session  *hugot.Session
	pipeline *pipelines.FeatureExtractionPipeline
	logger   *zap.Logger
}

const (
	embeddingModel = "sentence-transformers/all-MiniLM-L6-v2"
	// onnxFilename selects the base float32 model from the repo's onnx/ directory.
	// The repo ships several variants (quantized, optimised); model.onnx is the
	// canonical full-precision file and gives the most accurate embeddings.
	onnxFilename = "onnx/model.onnx"
	pipelineName = "guardrails-embedder"
	EmbeddingDim = 384 // dimensions for all-MiniLM-L6-v2
)

// NewEmbedder creates a hugot session, downloads (or loads from cache) the
// sentence-transformer model, and initialises the feature extraction pipeline.
// Blocks at startup; all subsequent Embed calls are fast (~5–15 ms on CPU).
func NewEmbedder(ctx context.Context, modelDir string, logger *zap.Logger) (*Embedder, error) {
	logger.Info("[CacheEmbedder] creating Go session (pure-Go backend, no CGO)")

	session, err := hugot.NewGoSession(ctx)
	if err != nil {
		return nil, fmt.Errorf("hugot NewGoSession: %w", err)
	}

	logger.Info("[CacheEmbedder] downloading / loading model",
		zap.String("model", embeddingModel),
		zap.String("modelDir", modelDir),
		zap.String("onnxFile", onnxFilename),
	)
	t0 := time.Now()
	downloadOpts := hugot.NewDownloadOptions()
	downloadOpts.OnnxFilePath = onnxFilename // select the base float32 model; repo has multiple .onnx variants
	modelPath, err := hugot.DownloadModel(ctx, embeddingModel, modelDir, downloadOpts)
	if err != nil {
		_ = session.Destroy()
		return nil, fmt.Errorf("hugot DownloadModel(%q): %w", embeddingModel, err)
	}
	logger.Info("[CacheEmbedder] model ready",
		zap.String("modelPath", modelPath),
		zap.Duration("elapsed", time.Since(t0)),
	)

	logger.Info("[CacheEmbedder] creating feature extraction pipeline",
		zap.String("name", pipelineName),
		zap.String("onnxFile", onnxFilename),
	)
	pipeline, err := hugot.NewPipeline[*pipelines.FeatureExtractionPipeline](
		session,
		hugot.FeatureExtractionConfig{
			ModelPath:    modelPath,
			Name:         pipelineName,
			OnnxFilename: onnxFilename,
			// WithNormalization produces unit-norm vectors — required for
			// cosine similarity to work correctly via dot product.
			Options: []hugot.FeatureExtractionOption{
				pipelines.WithNormalization(),
			},
		},
	)
	if err != nil {
		_ = session.Destroy()
		return nil, fmt.Errorf("hugot NewPipeline: %w", err)
	}

	logger.Info("[CacheEmbedder] pipeline ready",
		zap.String("model", embeddingModel),
		zap.Int("dims", EmbeddingDim),
	)
	return &Embedder{session: session, pipeline: pipeline, logger: logger}, nil
}

// Embed generates a 384-dim float32 embedding for the given text.
// Thread-safe; the hugot pipeline handles concurrent calls internally.
func (e *Embedder) Embed(ctx context.Context, text string) ([]float32, error) {
	t0 := time.Now()
	output, err := e.pipeline.RunPipeline(ctx, []string{text})
	elapsed := time.Since(t0)

	if err != nil {
		e.logger.Error("[CacheEmbedder] RunPipeline failed",
			zap.Duration("elapsed", elapsed),
			zap.Error(err),
		)
		return nil, fmt.Errorf("RunPipeline: %w", err)
	}
	if len(output.Embeddings) == 0 {
		return nil, fmt.Errorf("RunPipeline returned empty embeddings")
	}

	vec := output.Embeddings[0]
	e.logger.Debug("[CacheEmbedder] embedded text",
		zap.Int("textLen", len(text)),
		zap.Int("dims", len(vec)),
		zap.Duration("elapsed", elapsed),
	)
	return vec, nil
}

// Destroy cleans up the hugot session and model resources.
func (e *Embedder) Destroy() {
	if e.session != nil {
		if err := e.session.Destroy(); err != nil {
			e.logger.Warn("[CacheEmbedder] error destroying session", zap.Error(err))
		}
	}
}
