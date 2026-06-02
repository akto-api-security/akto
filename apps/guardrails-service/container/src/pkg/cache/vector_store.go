package cache

import (
	"context"
	"encoding/binary"
	"fmt"
	"math"
	"strconv"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

const (
	indexName = "guardrails_cache_idx"
	keyPrefix = "guardrails:"
	vecField  = "embedding"
)

// VectorStore manages the Redis vector index and handles KNN search + storage.
type VectorStore struct {
	client    *redis.Client
	threshold float64
	logger    *zap.Logger
}

// CacheEntry is returned by Check on a hit within the distance threshold.
type CacheEntry struct {
	Response string
	Distance float64
}

// NewVectorStore connects to Redis and validates connectivity.
// Call EnsureIndex before the first Check/Store.
func NewVectorStore(redisURL string, threshold float64, logger *zap.Logger) (*VectorStore, error) {
	opts, err := redis.ParseURL(redisURL)
	if err != nil {
		return nil, fmt.Errorf("parse redis URL %q: %w", redisURL, err)
	}
	client := redis.NewClient(opts)

	pingCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := client.Ping(pingCtx).Err(); err != nil {
		return nil, fmt.Errorf("redis ping %q: %w", redisURL, err)
	}

	logger.Info("[VectorStore] connected",
		zap.String("url", redisURL),
		zap.Float64("distanceThreshold", threshold),
	)
	return &VectorStore{client: client, threshold: threshold, logger: logger}, nil
}

// EnsureIndex creates the HNSW vector index if it does not already exist.
// Safe to call multiple times — no-ops when the index is present.
func (vs *VectorStore) EnsureIndex(ctx context.Context) error {
	err := vs.client.FTCreate(ctx, indexName,
		&redis.FTCreateOptions{
			OnHash: true,
			Prefix: []any{keyPrefix},
		},
		&redis.FieldSchema{
			FieldName: "policy_hash",
			FieldType: redis.SearchFieldTypeTag,
		},
		&redis.FieldSchema{
			FieldName: "response",
			FieldType: redis.SearchFieldTypeText,
			NoStem:    true,
		},
		&redis.FieldSchema{
			FieldName: vecField,
			FieldType: redis.SearchFieldTypeVector,
			VectorArgs: &redis.FTVectorArgs{
				HNSWOptions: &redis.FTHNSWOptions{
					Dim:            EmbeddingDim,
					Type:           "FLOAT32",
					DistanceMetric: "COSINE",
				},
			},
		},
	).Err()

	if err != nil {
		if strings.Contains(err.Error(), "Index already exists") {
			vs.logger.Info("[VectorStore] index already exists, skipping create",
				zap.String("index", indexName),
			)
			return nil
		}
		return fmt.Errorf("FT.CREATE %q: %w", indexName, err)
	}
	vs.logger.Info("[VectorStore] index created",
		zap.String("index", indexName),
		zap.Int("dims", EmbeddingDim),
		zap.String("metric", "COSINE"),
	)
	return nil
}

// Check runs a KNN search for the nearest stored entry matching policyHash.
// Returns nil (no error) when there is no hit within the distance threshold.
func (vs *VectorStore) Check(ctx context.Context, vec []float32, policyHash string) (*CacheEntry, error) {
	t0 := time.Now()

	// policy_hash is a 16-char hex string — no special chars to escape.
	query := fmt.Sprintf("@policy_hash:{%s}=>[KNN 1 @%s $vec AS dist]", policyHash, vecField)

	vs.logger.Debug("[VectorStore] KNN search",
		zap.String("index", indexName),
		zap.String("policyHash", policyHash),
		zap.String("query", query),
	)

	results, err := vs.client.FTSearchWithArgs(ctx, indexName, query,
		&redis.FTSearchOptions{
			Return: []redis.FTSearchReturn{
				{FieldName: "response"},
				{FieldName: "dist"},
			},
			Params:         map[string]any{"vec": floatsToBytes(vec)},
			DialectVersion: 2,
		},
	).Result()
	elapsed := time.Since(t0)

	if err != nil {
		vs.logger.Error("[VectorStore] FT.SEARCH failed",
			zap.String("policyHash", policyHash),
			zap.Duration("elapsed", elapsed),
			zap.Error(err),
		)
		return nil, fmt.Errorf("FT.SEARCH: %w", err)
	}

	vs.logger.Debug("[VectorStore] KNN search done",
		zap.String("policyHash", policyHash),
		zap.Int("total", int(results.Total)),
		zap.Duration("elapsed", elapsed),
	)

	if results.Total == 0 {
		vs.logger.Info("[VectorStore] MISS — no results for hash",
			zap.String("policyHash", policyHash),
			zap.Duration("elapsed", elapsed),
		)
		return nil, nil
	}

	doc := results.Docs[0]
	distStr, _ := doc.Fields["dist"]
	dist, _ := strconv.ParseFloat(distStr, 64)

	vs.logger.Info("[VectorStore] KNN result",
		zap.String("policyHash", policyHash),
		zap.Float64("distance", dist),
		zap.Float64("threshold", vs.threshold),
		zap.String("redisKey", doc.ID),
		zap.Duration("elapsed", elapsed),
	)

	if dist > vs.threshold {
		vs.logger.Info("[VectorStore] MISS — distance exceeds threshold",
			zap.Float64("distance", dist),
			zap.Float64("threshold", vs.threshold),
		)
		return nil, nil
	}

	vs.logger.Info("[VectorStore] HIT",
		zap.Float64("distance", dist),
		zap.String("policyHash", policyHash),
	)
	return &CacheEntry{
		Response: doc.Fields["response"],
		Distance: dist,
	}, nil
}

// Store writes the embedding + response to Redis and sets a TTL.
// The key is time-based (nanoseconds) to ensure uniqueness.
func (vs *VectorStore) Store(ctx context.Context, vec []float32, policyHash, response string, ttlSec int) error {
	key := fmt.Sprintf("%s%d", keyPrefix, time.Now().UnixNano())

	vs.logger.Info("[VectorStore] storing entry",
		zap.String("key", key),
		zap.String("policyHash", policyHash),
		zap.Int("ttlSeconds", ttlSec),
		zap.Int("responseBytes", len(response)),
		zap.Int("vecDims", len(vec)),
	)

	t0 := time.Now()
	if err := vs.client.HSet(ctx, key,
		"policy_hash", policyHash,
		"response",    response,
		vecField,      floatsToBytes(vec),
	).Err(); err != nil {
		vs.logger.Error("[VectorStore] HSET failed",
			zap.String("key", key),
			zap.Duration("elapsed", time.Since(t0)),
			zap.Error(err),
		)
		return fmt.Errorf("HSET %q: %w", key, err)
	}

	if err := vs.client.Expire(ctx, key, time.Duration(ttlSec)*time.Second).Err(); err != nil {
		vs.logger.Warn("[VectorStore] EXPIRE failed — entry stored without TTL",
			zap.String("key", key),
			zap.Error(err),
		)
		// Non-fatal: entry is stored, just won't expire automatically.
	}

	vs.logger.Debug("[VectorStore] entry stored",
		zap.String("key", key),
		zap.Duration("elapsed", time.Since(t0)),
	)
	return nil
}

// floatsToBytes converts []float32 to little-endian bytes for Redis vector storage.
// Exact implementation from the Redis official Go docs.
func floatsToBytes(fs []float32) []byte {
	buf := make([]byte, len(fs)*4)
	for i, f := range fs {
		binary.NativeEndian.PutUint32(buf[i*4:], math.Float32bits(f))
	}
	return buf
}
