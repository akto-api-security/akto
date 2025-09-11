package validators

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"strings"
	"time"

	// faiss "github.com/DataIntelligenceCrew/go-faiss"

	tok "github.com/daulet/tokenizers"
	"github.com/tidwall/gjson"
	ort "github.com/yalue/onnxruntime_go"

	"github.com/akto-api-security/akto/libs/mcp-proxy/mcp-threat/types"
)

const (
	maxLen    = 256 // SBERT typical max sequence length
	hiddenDim = 384 // MiniLM-L6-v2 embedding dimension
	batchSize = 100
)

// SemanticDetector handles semantic keyword detection
// Implements the Validator interface
type SemanticDetector struct {
	tokenizer     *tok.Tokenizer
	session       *ort.AdvancedSession
	inputIDs      []int64
	attentionMask []int64
	outputEmbeds  []float32
	tokenTypeIDs  []int64
	keywordEmbeds map[string][]float32
	threshold     float32

	//faissIndex  faiss.Index // inner product for cosine similarity
	faissLabels []string
	batchSize   int
}

// NewSemanticDetector creates a new semantic detector with the validator pattern
func NewSemanticDetector(tokenizerPath, modelPath, libonnxPath string, keywords []string, threshold float32) (*SemanticDetector, error) {
	return newSemanticDetector(tokenizerPath, modelPath, libonnxPath, keywords, threshold)
}

// newSemanticDetector initializes the detector (internal implementation)
func newSemanticDetector(tokenizerPath, modelPath, libonnxPath string, keywords []string, threshold float32) (*SemanticDetector, error) {
	// Load tokenizer
	tk, err := tok.FromFile(tokenizerPath)
	if err != nil {
		return nil, fmt.Errorf("load tokenizer: %w", err)
	}

	// Initialize ONNX Runtime
	ort.SetSharedLibraryPath(libonnxPath)
	ort.SetEnvironmentLogLevel(ort.LoggingLevelWarning)
	if err := ort.InitializeEnvironment(); err != nil {
		tk.Close()
		return nil, err
	}

	// Allocate slices
	inputIDs := make([]int64, maxLen)
	attentionMask := make([]int64, maxLen)
	tokenTypeIDs := make([]int64, maxLen) // all zeros for single-sentence
	outputEmbeds := make([]float32, maxLen*hiddenDim)

	// Wrap slices into tensors
	inputTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen)), inputIDs)
	attnTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen)), attentionMask)
	tokenTypeTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen)), tokenTypeIDs)
	outputTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen), int64(hiddenDim)), outputEmbeds)

	// Create session
	sess, err := ort.NewAdvancedSession(
		modelPath,
		[]string{"input_ids", "attention_mask", "token_type_ids"},
		[]string{"last_hidden_state"},
		[]ort.Value{inputTensor, attnTensor, tokenTypeTensor},
		[]ort.Value{outputTensor},
		nil,
	)
	if err != nil {
		tk.Close()
		return nil, err
	}

	d := &SemanticDetector{
		tokenizer:     tk,
		session:       sess,
		inputIDs:      inputIDs,
		attentionMask: attentionMask,
		tokenTypeIDs:  tokenTypeIDs,
		outputEmbeds:  outputEmbeds,
		keywordEmbeds: make(map[string][]float32),
		threshold:     threshold,
	}
	return d, nil
}

func NewSemanticDetectorFromGob(tokenizerPath, modelPath, libonnxPath, gobPath string, threshold float32) (*SemanticDetector, error) {
	// Load tokenizer
	tk, err := tok.FromFile(tokenizerPath)
	if err != nil {
		return nil, fmt.Errorf("load tokenizer: %w", err)
	}

	// Initialize ONNX Runtime
	ort.SetSharedLibraryPath(libonnxPath)
	ort.SetEnvironmentLogLevel(ort.LoggingLevelWarning)
	if err := ort.InitializeEnvironment(); err != nil {
		tk.Close()
		return nil, err
	}

	// Allocate slices
	// inputIDs := make([]int64, maxLen)
	// attentionMask := make([]int64, maxLen)
	// tokenTypeIDs := make([]int64, maxLen)
	// outputEmbeds := make([]float32, maxLen*hiddenDim)

	// // Wrap slices into tensors
	// inputTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen)), inputIDs)
	// attnTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen)), attentionMask)
	// tokenTypeTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen)), tokenTypeIDs)
	// outputTensor, _ := ort.NewTensor(ort.NewShape(1, int64(maxLen), int64(hiddenDim)), outputEmbeds)

	inputIDs := make([]int64, batchSize*maxLen)
	attentionMask := make([]int64, batchSize*maxLen)
	tokenTypeIDs := make([]int64, batchSize*maxLen)
	outputEmbeds := make([]float32, batchSize*maxLen*hiddenDim)

	// Wrap slices into tensors
	inputTensor, _ := ort.NewTensor(ort.NewShape(int64(batchSize), int64(maxLen)), inputIDs)
	attnTensor, _ := ort.NewTensor(ort.NewShape(int64(batchSize), int64(maxLen)), attentionMask)
	tokenTypeTensor, _ := ort.NewTensor(ort.NewShape(int64(batchSize), int64(maxLen)), tokenTypeIDs)
	outputTensor, _ := ort.NewTensor(ort.NewShape(int64(batchSize), int64(maxLen), int64(hiddenDim)), outputEmbeds)

	// Create session
	sess, err := ort.NewAdvancedSession(
		modelPath,
		[]string{"input_ids", "attention_mask", "token_type_ids"},
		//[]string{"input_ids", "attention_mask"},
		[]string{"last_hidden_state"},
		[]ort.Value{inputTensor, attnTensor, tokenTypeTensor},
		//[]ort.Value{inputTensor, attnTensor},
		[]ort.Value{outputTensor},
		nil,
	)
	if err != nil {
		tk.Close()
		return nil, err
	}

	// Load embeddings from gob file
	embeds, err := LoadEmbeddings(gobPath)
	if err != nil {
		tk.Close()
		sess.Destroy()
		return nil, fmt.Errorf("failed to load embeddings from gob: %w", err)
	}

	d := &SemanticDetector{
		tokenizer:     tk,
		session:       sess,
		inputIDs:      inputIDs,
		attentionMask: attentionMask,
		tokenTypeIDs:  tokenTypeIDs,
		outputEmbeds:  outputEmbeds,
		keywordEmbeds: embeds,
		threshold:     threshold,
		batchSize:     batchSize,
	}
	return d, nil
}

// Close resources
func (d *SemanticDetector) Close() {
	d.session.Destroy()
	d.tokenizer.Close()
	ort.DestroyEnvironment()
}

// Validate validates content using semantic similarity detection and returns a ValidationResponse
func (d *SemanticDetector) Validate(ctx context.Context, request *types.ValidationRequest) *types.ValidationResponse {
	response := types.NewValidationResponse()
	startTime := time.Now()

	defer func() {
		response.ProcessingTime = float64(time.Since(startTime).Milliseconds())
	}()

	// Expect caller to provide string payload
	payloadStr, ok := request.MCPPayload.(string)
	if !ok {
		response.SetError("semantic detector expects string payload")
		return response
	}

	// Validate input
	if strings.TrimSpace(payloadStr) == "" {
		response.SetError("text cannot be empty")
		return response
	}

	// Use FAISS-based detection if available, otherwise fall back to simple detection
	var matchedKeyword string
	var score float32
	var isMatch bool
	var err error

	// if d.faissIndex != nil {
	// 	matchedKeyword, score, isMatch, err = d.DetectFAISS(payloadStr)
	// } else {
	// 	matchedKeyword, score, isMatch, err = d.Detect(payloadStr)
	// }

	matchedKeyword, score, isMatch, err = d.DetectBatchOpt(payloadStr)

	if err != nil {
		response.SetError(fmt.Sprintf("semantic detection failed: %v", err))
		return response
	}

	// Create verdict based on detection results
	verdict := types.NewVerdict()
	verdict.IsMaliciousRequest = isMatch
	verdict.Confidence = float64(score)

	if isMatch {
		verdict.PolicyAction = types.PolicyActionBlock
		verdict.AddEvidence(fmt.Sprintf("matched_keyword: %s", matchedKeyword))
		verdict.AddEvidence(fmt.Sprintf("similarity_score: %.4f", score))
		verdict.AddCategory(types.ThreatCategorySuspiciousKeyword)
		verdict.Reasoning = fmt.Sprintf("Semantic similarity detected with keyword '%s' (score: %.4f)", matchedKeyword, score)
	} else {
		verdict.PolicyAction = types.PolicyActionAllow
		verdict.Reasoning = "No semantic similarity with threat keywords detected"
	}

	response.SetSuccess(verdict, response.ProcessingTime)
	return response
}

//With Mean pooled

func (d *SemanticDetector) ComputeEmbedding(text string) ([]float32, error) {
	enc := d.tokenizer.EncodeWithOptions(text, true, tok.WithReturnAttentionMask())
	ids := enc.IDs
	attn := enc.AttentionMask

	padID := uint32(0)
	for i := 0; i < maxLen; i++ {
		if i < len(ids) {
			d.inputIDs[i] = int64(ids[i])
			d.attentionMask[i] = int64(attn[i])
		} else {
			d.inputIDs[i] = int64(padID)
			d.attentionMask[i] = 0
		}
		d.tokenTypeIDs[i] = 0 // single-sentence input
	}

	// Run inference
	if err := d.session.Run(); err != nil {
		return nil, err
	}

	// Mean pooling
	embedding := make([]float32, hiddenDim)
	var count float32
	for i := 0; i < maxLen; i++ {
		if d.attentionMask[i] == 1 {
			for j := 0; j < hiddenDim; j++ {
				embedding[j] += d.outputEmbeds[i*hiddenDim+j]
			}
			count++
		}
	}
	if count > 0 {
		for i := range embedding {
			embedding[i] /= count
		}
	}

	// L2 normalize
	var norm float32
	for _, v := range embedding {
		norm += v * v
	}
	norm = float32(math.Sqrt(float64(norm)))
	if norm > 0 {
		for i := range embedding {
			embedding[i] /= norm
		}
	}

	return embedding, nil
}

func cosineSimilarity(a, b []float32) float32 {
	var dot, normA, normB float32
	for i := range a {
		dot += a[i] * b[i]
		normA += a[i] * a[i]
		normB += b[i] * b[i]
	}
	if normA == 0 || normB == 0 {
		return 0
	}
	return dot / (float32(math.Sqrt(float64(normA))) * float32(math.Sqrt(float64(normB))))
}

func (d *SemanticDetector) ComputeEmbeddingBatch(texts []string) ([][]float32, error) {
	batchSize := len(texts)
	if batchSize > d.batchSize {
		return nil, fmt.Errorf("batch too large: got %d, max %d", batchSize, d.batchSize)
	}

	// Fill slices
	for i, text := range texts {
		enc := d.tokenizer.EncodeWithOptions(text, true, tok.WithReturnAttentionMask())
		ids := enc.IDs
		attn := enc.AttentionMask

		for j := 0; j < maxLen; j++ {
			offset := i*maxLen + j
			if j < len(ids) {
				d.inputIDs[offset] = int64(ids[j])
				d.attentionMask[offset] = int64(attn[j])
			} else {
				d.inputIDs[offset] = 0
				d.attentionMask[offset] = 0
			}
			d.tokenTypeIDs[offset] = 0
		}
	}

	// Run inference
	startTime := time.Now()
	log.Println("Running inference")
	if err := d.session.Run(); err != nil {
		return nil, err
	}
	log.Println("Inference time: ", time.Since(startTime))
	// Collect embeddings
	results := make([][]float32, batchSize)
	for i := 0; i < batchSize; i++ {
		sentenceEmb := d.outputEmbeds[i*maxLen*hiddenDim : (i+1)*maxLen*hiddenDim]

		embedding := make([]float32, hiddenDim)
		var count float32
		for j := 0; j < maxLen; j++ {
			if d.attentionMask[i*maxLen+j] == 1 {
				for k := 0; k < hiddenDim; k++ {
					embedding[k] += sentenceEmb[j*hiddenDim+k]
				}
				count++
			}
		}

		if count > 0 {
			for k := 0; k < hiddenDim; k++ {
				embedding[k] /= count
			}
		}

		// Normalize
		var norm float32
		for _, v := range embedding {
			norm += v * v
		}
		norm = float32(math.Sqrt(float64(norm)))
		if norm > 0 {
			for k := range embedding {
				embedding[k] /= norm
			}
		}

		results[i] = embedding
	}

	return results, nil
}

func (d *SemanticDetector) ComputeEmbeddingBatchOpt(texts []string) ([][]float32, error) {
	bSize := len(texts)
	if bSize > d.batchSize {
		return nil, fmt.Errorf("batch too large: got %d, max %d", bSize, d.batchSize)
	}

	// Fill model inputs
	for i, text := range texts {
		enc := d.tokenizer.EncodeWithOptions(text, true, tok.WithReturnAttentionMask())
		ids := enc.IDs
		attn := enc.AttentionMask
		offset := i * maxLen

		for j := 0; j < maxLen; j++ {
			if j < len(ids) {
				d.inputIDs[offset+j] = int64(ids[j])
				d.attentionMask[offset+j] = int64(attn[j])
			} else {
				d.inputIDs[offset+j] = 0
				d.attentionMask[offset+j] = 0
			}
			d.tokenTypeIDs[offset+j] = 0
		}
	}

	// Run inference
	if err := d.session.Run(); err != nil {
		return nil, err
	}

	// Collect embeddings
	results := make([][]float32, bSize)
	for i := 0; i < bSize; i++ {
		sentenceEmb := d.outputEmbeds[i*maxLen*hiddenDim : (i+1)*maxLen*hiddenDim]
		embedding := make([]float32, hiddenDim)

		var count int32
		for j := 0; j < maxLen; j++ {
			if d.attentionMask[i*maxLen+j] == 1 {
				start := j * hiddenDim
				for k := 0; k < hiddenDim; k++ {
					embedding[k] += sentenceEmb[start+k]
				}
				count++
			}
		}

		if count > 0 {
			invCount := 1.0 / float32(count)
			var norm float32
			for k := 0; k < hiddenDim; k++ {
				v := embedding[k] * invCount
				norm += v * v
				embedding[k] = v
			}
			// Normalize
			norm = float32(math.Sqrt(float64(norm)))
			if norm > 0 {
				invNorm := 1.0 / norm
				for k := 0; k < hiddenDim; k++ {
					embedding[k] *= invNorm
				}
			}
		}

		results[i] = embedding
	}

	return results, nil
}

func (d *SemanticDetector) DetectBatchOpt(text string) (matchedKeyword string, score float32, isMatch bool, err error) {
	processedText := text
	if gjson.Valid(text) {
		processedText = extractJSONValuesSafe(text)
	}

	ngrams := generateNGramsInRange(processedText, 2, 3)
	if len(ngrams) == 0 {
		return "", 0, false, nil
	}

	// Convert map to slices once for faster iteration
	keywords := make([]string, 0, len(d.keywordEmbeds))
	keywordVecs := make([][]float32, 0, len(d.keywordEmbeds))
	for kw, emb := range d.keywordEmbeds {
		keywords = append(keywords, kw)
		keywordVecs = append(keywordVecs, emb)
	}

	// Batch embedding of all n-grams in chunks
	for i := 0; i < len(ngrams); i += d.batchSize {
		end := i + d.batchSize
		if end > len(ngrams) {
			end = len(ngrams)
		}
		batch := ngrams[i:end]

		batchEmbeddings, err := d.ComputeEmbeddingBatchOpt(batch)
		if err != nil {
			return "", 0, false, err
		}

		for j, emb := range batchEmbeddings {
			bestScore := float32(-1.0)
			bestIdx := -1
			_ = j

			// Compare with all keywords
			for k, kwEmb := range keywordVecs {
				sim := dotProduct(emb, kwEmb) // keywords are pre-normalized
				if sim > bestScore {
					bestScore = sim
					bestIdx = k
				}
			}

			if bestScore >= d.threshold {
				return keywords[bestIdx], bestScore, true, nil
			}
		}
	}

	return "", 0, false, nil
}

func dotProduct(a, b []float32) float32 {
	var sum float32
	for i := range a {
		sum += a[i] * b[i]
	}
	return sum
}

func normalize(vec []float32) []float32 {
	var norm float32
	for _, v := range vec {
		norm += v * v
	}
	if norm == 0 {
		return vec
	}
	norm = 1 / float32(math.Sqrt(float64(norm)))
	for i := range vec {
		vec[i] *= norm
	}
	return vec
}

func (d *SemanticDetector) DetectBatch(text string) (matchedKeyword string, score float32, isMatch bool, err error) {
	processedText := text
	if gjson.Valid(text) {
		processedText = extractJSONValuesSafe(text)
		log.Println("Detected JSON input, extracted values:", processedText)
	} else {
		log.Println("Processing as plain text")
	}

	ngrams := generateNGramsInRange(processedText, 2, 3)
	log.Println("Total n-grams generated:", len(ngrams))
	if len(ngrams) == 0 {
		return "", 0, false, nil
	}

	// Batch embedding of all n-grams in chunks
	for i := 0; i < len(ngrams); i += d.batchSize {
		end := i + d.batchSize
		if end > len(ngrams) {
			end = len(ngrams)
		}
		batch := ngrams[i:end]

		batchEmbeddings, err := d.ComputeEmbeddingBatch(batch)
		if err != nil {
			return "", 0, false, err
		}

		for j, emb := range batchEmbeddings {
			ngram := batch[j]
			_ = ngram
			for kw, kwEmb := range d.keywordEmbeds {
				sim := cosineSimilarity(emb, kwEmb)
				if sim > d.threshold {
					return kw, sim, true, nil
				}
			}
		}
	}

	return "", 0, false, nil
}

func (d *SemanticDetector) Detect(text string) (matchedKeyword string, score float32, isMatch bool, err error) {
	// Check if the input is valid JSON and extract values using gjson
	log.Println("Payload to process: ", text)
	processedText := text

	if gjson.Valid(text) {
		// It's valid JSON - extract all values using the helper method
		processedText = extractJSONValuesSafe(text)
		log.Println("Detected JSON input, extracted values:", processedText)
	} else {
		// Not JSON, use the text as-is
		log.Println("Processing as plain text")
	}

	// Define n-gram sizes (1 to 4 words)
	ngrams := generateNGramsInRange(processedText, 2, 3)

	log.Println("Total n-grams generated:", len(ngrams))

	var maxScore float32
	var bestKeyword string

	// Run detection on all n-grams
	for _, ngram := range ngrams {
		textEmb, err := d.ComputeEmbedding(ngram)
		if err != nil {
			return "", 0, false, err
		}

		isDetected := false

		for kw, kwEmb := range d.keywordEmbeds {
			sim := cosineSimilarity(textEmb, kwEmb)
			//log.Printf("N-gram '%s' vs Keyword '%s' similarity: %.4f\n", ngram, kw, sim)
			if sim > d.threshold {
				maxScore = sim
				bestKeyword = kw
				isDetected = true
				break
			}
		}

		if isDetected {
			break
		}
	}

	return bestKeyword, maxScore, maxScore >= d.threshold, nil
}

func extractJSONValuesSafe(jsonText string) string {
	var values []string
	result := gjson.Parse(jsonText)

	// If root JSON invalid, just return raw text
	if !result.Exists() {
		return jsonText
	}

	var extract func(r gjson.Result)
	extract = func(r gjson.Result) {
		if r.IsArray() || r.IsObject() {
			r.ForEach(func(_, v gjson.Result) bool {
				extract(v)
				return true
			})
		} else if r.Type == gjson.String {
			raw := r.String()
			// validate string as JSON first
			var js interface{}
			if json.Unmarshal([]byte(raw), &js) == nil {
				inner := gjson.Parse(raw)
				if inner.IsArray() || inner.IsObject() {
					extract(inner)
					return
				}
			}
			// fallback: keep raw string
			values = append(values, raw)
		} else if r.Type != gjson.Null {
			values = append(values, r.String())
		}
	}

	extract(result)
	return strings.Join(values, " ")
}

func generateNGramsInRange(text string, minN, maxN int) []string {
	// Split the text into words
	words := strings.Fields(text)
	var ngrams []string

	for n := minN; n <= maxN; n++ {
		if len(words) < n {
			break // no sequences possible for this length
		}
		for i := 0; i <= len(words)-n; i++ {
			ngram := strings.Join(words[i:i+n], " ")
			ngrams = append(ngrams, ngram)
		}
	}

	return ngrams
}

// func (d *SemanticDetector) DetectFAISS(text string) (matchedKeyword string, score float32, isMatch bool, err error) {
// 	if d.faissIndex == nil {
// 		return "", 0, false, fmt.Errorf("FAISS index not initialized")
// 	}

// 	processedText := text

// 	if gjson.Valid(text) {
// 		// It's valid JSON - extract all values using the helper method
// 		processedText = extractJSONValuesSafe(text)
// 		log.Println("Detected JSON input, extracted values:", processedText)
// 	} else {
// 		// Not JSON, use the text as-is
// 		log.Println("Processing as plain text")
// 	}

// 	// Define n-gram sizes (1 to 4 words)
// 	ngrams := generateNGramsInRange(processedText, 2, 5)
// 	for _, ngram := range ngrams {
// 		//log.Println("Processing n-gram:", ngram)
// 		emb, err := d.ComputeEmbedding(ngram)
// 		if err != nil {
// 			continue
// 		}

// 		// Normalize embedding
// 		norm := float32(0)
// 		for _, v := range emb {
// 			norm += v * v
// 		}
// 		norm = float32(math.Sqrt(float64(norm)))
// 		for i := range emb {
// 			emb[i] /= norm
// 		}

// 		// Search in FAISS index

// 		// The Search method returns (ids, distances, error)
// 		distances, ids, err := d.faissIndex.Search(emb, 10)
// 		if err != nil {
// 			continue
// 		}

// 		log.Printf("FAISS search results - ngram: %s - IDs: %v, Distances: %v\n", ngram, ids, distances)

// 		if len(ids) > 0 && distances[0] >= d.threshold {
// 			idx := ids[0]
// 			if idx >= 0 && int(idx) < len(d.faissLabels) {
// 				return d.faissLabels[idx], distances[0], true, nil
// 			}
// 		}
// 	}
// 	return "", 0, false, nil
// }

// Detect checks input text against all keywords
// func (d *SemanticDetector) DetectSimple(text string) (matchedKeyword string, score float32, isMatch bool, err error) {
// 	textEmb, err := d.ComputeEmbedding(text)
// 	if err != nil {
// 		return "", 0, false, err
// 	}

// 	var maxScore float32
// 	var bestKeyword string
// 	for kw, kwEmb := range d.keywordEmbeds {
// 		sim := cosineSimilarity(textEmb, kwEmb)
// 		if sim > maxScore {
// 			maxScore = sim
// 			bestKeyword = kw
// 		}
// 	}

// 	return bestKeyword, maxScore, maxScore >= d.threshold, nil
// }

// Detect checks input text (JSON or plain text) against all keywords
// chunk
// func (d *SemanticDetector) DetectChunk(text string) (matchedKeyword string, score float32, isMatch bool, err error) {
// 	// 1. Parse JSON string into map
// 	var jsonData map[string]interface{}
// 	if err := json.Unmarshal([]byte(text), &jsonData); err != nil {
// 		return "", 0, false, fmt.Errorf("invalid JSON: %w", err)
// 	}

// 	// 2. Flatten JSON
// 	flat := make(map[string]string)
// 	flattenJSON(jsonData, "", flat)

// 	fmt.Println("Flattened JSON:")
// 	for k, v := range flat {
// 		fmt.Printf("%s : %s\n", k, v)
// 	}

// 	// 3. Process each flattened value
// 	for _, value := range flat {
// 		// Chunk large text values (e.g., 50 words)
// 		chunks := chunkText(value, 50)
// 		for _, chunk := range chunks {
// 			log.Println("Processing chunk:", chunk)
// 			emb, err := d.ComputeEmbedding(chunk)
// 			if err != nil {
// 				continue
// 			}

// 			// Compare with all keyword embeddings
// 			for kw, kwEmb := range d.keywordEmbeds {
// 				sim := cosineSimilarity(emb, kwEmb)
// 				log.Printf("Chunk vs Keyword '%s' similarity: %.4f\n", kw, sim)
// 				if sim >= d.threshold {
// 					// Early stop: return immediately
// 					return kw, sim, true, nil
// 				}
// 			}
// 		}
// 	}

//		// No match found
//		return "", 0, false, nil
//	}

// func generateNGrams(text string, n int) []string {
// 	// Split the text into words
// 	words := strings.Fields(text)
// 	if len(words) < n {
// 		return []string{text} // fallback: return whole text
// 	}

// 	var ngrams []string
// 	for i := 0; i <= len(words)-n; i++ {
// 		ngram := strings.Join(words[i:i+n], " ")
// 		ngrams = append(ngrams, ngram)
// 	}

// 	return ngrams
// }

// func chunkText(text string, chunkSize int) []string {
// 	words := strings.Fields(text)
// 	if len(words) == 0 {
// 		return []string{}
// 	}

// 	var chunks []string
// 	step := chunkSize / 2 // 50% overlap to avoid missing phrases
// 	if step == 0 {
// 		step = 1
// 	}

// 	for i := 0; i < len(words); i += step {
// 		end := i + chunkSize
// 		if end > len(words) {
// 			end = len(words)
// 		}
// 		chunks = append(chunks, strings.Join(words[i:end], " "))
// 		if end == len(words) {
// 			break
// 		}
// 	}
// 	return chunks
// }
