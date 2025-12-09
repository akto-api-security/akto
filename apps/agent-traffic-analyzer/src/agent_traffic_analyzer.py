"""
Agent Traffic Analyzer Service
FastAPI service that detects base prompts with placeholders from a list of prompts
using SentenceTransformer embeddings and clustering.
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List
import numpy as np
from sentence_transformers import SentenceTransformer
from sklearn.cluster import MiniBatchKMeans
from sklearn.metrics import silhouette_score
from sklearn.metrics.pairwise import cosine_similarity
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Agent Traffic Analyzer", version="1.0.0")

# Load model once at startup
logger.info("Loading SentenceTransformer model...")
model = SentenceTransformer('all-MiniLM-L6-v2')
logger.info("Model loaded successfully")

# Constants
MAX_PROMPTS_PER_REQUEST = 2000
INTENT_THRESHOLD = 0.45  # Threshold for intent anomaly detection
MIN_CONFIDENCE_THRESHOLD = 0.70  # Minimum confidence to accept base prompt (70% of prompts in main cluster)


class DetectionRequest(BaseModel):
    prompts: List[str] = Field(..., min_items=1, max_items=MAX_PROMPTS_PER_REQUEST)


class Anomaly(BaseModel):
    prompt: str
    userInput: str
    similarity: float


class DetectionResponse(BaseModel):
    basePrompt: str
    confidence: float
    anomalies: List[Anomaly] = []


def longest_common_prefix_tokenwise(prompt_list: List[str]) -> str:
    """
    Finds longest common prefix across prompts within a cluster (token-wise).
    This prefix corresponds to the base prompt.
    Handles multiple placeholders by finding common token sequences.
    """
    if not prompt_list:
        return ""
    
    if len(prompt_list) == 1:
        return prompt_list[0]
    
    # Split all prompts into tokens (words)
    split_prompts = [p.split() for p in prompt_list]
    
    if not split_prompts or not split_prompts[0]:
        return prompt_list[0]
    
    prefix = []
    
    # Find common prefix token-by-token
    for tokens in zip(*split_prompts):
        if all(t == tokens[0] for t in tokens):  # all equal
            prefix.append(tokens[0])
        else:
            break
    
    return " ".join(prefix)


def extract_user_input_v2(full_prompt: str, prefix: str) -> str:
    """
    Extracts user input by removing the shared prefix.
    Works even if user input appears in the middle of the prompt.
    """
    if not prefix:
        return full_prompt
    
    tok_prompt = full_prompt.split()
    tok_prefix = prefix.split()
    
    # Remove prefix tokens from beginning of prompt
    remainder = tok_prompt[len(tok_prefix):]
    
    return " ".join(remainder).strip()


def extract_base_prompt_from_cluster(prompts: List[str]) -> str:
    """
    Extract base prompt pattern from a cluster of similar prompts.
    Uses token-wise longest common prefix (tested algorithm).
    """
    if not prompts:
        return ""
    
    if len(prompts) == 1:
        return prompts[0]
    
    # Find longest common prefix (token-wise) - this is the base prompt
    prefix = longest_common_prefix_tokenwise(prompts)
    
    if not prefix:
        return prompts[0]
    
    # Check if there are variable parts after prefix
    prefix_tokens = prefix.split()
    max_len = max(len(p.split()) for p in prompts)
    
    if max_len > len(prefix_tokens):
        # There are tokens after the prefix - need to handle multiple placeholders
        # Build pattern token-by-token
        pattern_tokens = prefix_tokens.copy()
        
        for i in range(len(prefix_tokens), max_len):
            tokens_at_i = []
            for prompt in prompts:
                prompt_tokens = prompt.split()
                if i < len(prompt_tokens):
                    tokens_at_i.append(prompt_tokens[i])
            
            if not tokens_at_i:
                break
            
            # If all tokens at this position are the same, add to pattern
            if len(set(tokens_at_i)) == 1 and len(tokens_at_i) == len(prompts):
                pattern_tokens.append(tokens_at_i[0])
            else:
                # Different tokens - add placeholder (but avoid consecutive placeholders)
                if not pattern_tokens or pattern_tokens[-1] != "{}":
                    pattern_tokens.append("{}")
        
        result = " ".join(pattern_tokens)
        # Clean up consecutive placeholders
        import re
        result = re.sub(r'\{\}\s+\{\}', '{}', result)
        return result.strip()
    
    return prefix


def detect_anomalies(prompts: List[str], embeddings: np.ndarray, cluster_labels: np.ndarray, 
                     main_cluster_id: int, base_prompt: str) -> List[Anomaly]:
    """
    Detect anomalous prompts using intent anomaly detection (tested algorithm).
    Extracts user inputs and checks cosine similarity to cluster centroid.
    """
    anomalies = []
    
    # Get all prompts in main cluster
    main_cluster_indices = [i for i in range(len(prompts)) if cluster_labels[i] == main_cluster_id]
    main_cluster_prompts = [prompts[i] for i in main_cluster_indices]
    
    if not main_cluster_prompts:
        return anomalies
    
    # Extract base prompt prefix (token-wise)
    prefix = longest_common_prefix_tokenwise(main_cluster_prompts)
    
    # Extract user inputs for all prompts in main cluster
    user_inputs = []
    
    for i in main_cluster_indices:
        user_input = extract_user_input_v2(prompts[i], prefix)
        user_inputs.append(user_input)
    
    if not user_inputs:
        return anomalies
    
    # Embed user inputs
    user_embeddings = model.encode(user_inputs, show_progress_bar=False)
    user_embeddings = np.nan_to_num(user_embeddings)
    
    # Compute cluster-level user intent centroid
    centroid = user_embeddings.mean(axis=0, keepdims=True)
    
    # Calculate cosine similarity for each user input to centroid
    similarities = cosine_similarity(user_embeddings, centroid).flatten()
    
    # Detect anomalies (low similarity indicates different intent)
    for prompt_idx, prompt, user_input, similarity in zip(
        main_cluster_indices, main_cluster_prompts, user_inputs, similarities
    ):
        if similarity < INTENT_THRESHOLD:
            anomalies.append(Anomaly(
                prompt=prompt,  # Full prompt for accurate anomaly reporting
                userInput=user_input,  # Full user input for accurate anomaly reporting
                similarity=float(similarity)
            ))
    
    return anomalies


@app.get("/health")
def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "model": "all-MiniLM-L6-v2"}


@app.post("/detect", response_model=DetectionResponse)
def detect_base_prompt(request: DetectionRequest):
    """
    Detect base prompt pattern from a list of prompts.
    
    Uses SentenceTransformer embeddings and MiniBatchKMeans clustering to identify
    the most common prompt pattern and extract placeholders.
    """
    try:
        prompts = request.prompts
        
        # Validate input size
        if len(prompts) > MAX_PROMPTS_PER_REQUEST:
            raise HTTPException(
                status_code=400,
                detail=f"Too many prompts. Maximum {MAX_PROMPTS_PER_REQUEST} allowed."
            )
        
        if len(prompts) < 2:
            # Not enough prompts for detection
            return DetectionResponse(
                basePrompt=prompts[0] if prompts else "",
                confidence=1.0,
                anomalies=[]
            )
        
        # Generate embeddings
        logger.info(f"Generating embeddings for {len(prompts)} prompts...")
        embeddings = model.encode(prompts, show_progress_bar=False)
        embeddings = np.nan_to_num(embeddings)
        
        # Determine optimal number of clusters
        # For 100 prompts: try 2 to min(8, len/10) to favor finding main pattern
        # This ensures we don't over-cluster and split the main 80 prompts
        max_clusters = min(8, max(2, len(prompts) // 10))
        if max_clusters < 2:
            max_clusters = 2
        
        best_k = 2
        best_score = -1
        
        # Try different k values
        for k in range(2, max_clusters + 1):
            km = MiniBatchKMeans(n_clusters=k, random_state=42, n_init=5, batch_size=64)
            labels = km.fit_predict(embeddings)
            
            if len(set(labels)) < 2:
                continue
            
            score = silhouette_score(embeddings, labels)
            if score > best_score:
                best_score = score
                best_k = k
        
        # Perform clustering with optimal k
        logger.info(f"Clustering with k={best_k}...")
        kmeans = MiniBatchKMeans(n_clusters=best_k, random_state=42, n_init=5, batch_size=64)
        cluster_labels = kmeans.fit_predict(embeddings)
        
        # Find the largest cluster (main pattern)
        unique, counts = np.unique(cluster_labels, return_counts=True)
        main_cluster_id = unique[np.argmax(counts)]
        main_cluster_size = counts[np.argmax(counts)]
        
        # Extract prompts in main cluster
        main_cluster_prompts = [prompts[i] for i in range(len(prompts)) if cluster_labels[i] == main_cluster_id]
        
        # Extract base prompt pattern using token-wise longest common prefix
        base_prompt = extract_base_prompt_from_cluster(main_cluster_prompts)
        
        # Calculate confidence based on cluster size
        confidence = main_cluster_size / len(prompts)
        
        # Validate confidence meets minimum threshold
        if confidence < MIN_CONFIDENCE_THRESHOLD:
            logger.warning(f"Low confidence ({confidence:.2f}) below threshold ({MIN_CONFIDENCE_THRESHOLD}). "
                         f"Main cluster has {main_cluster_size}/{len(prompts)} prompts. "
                         f"This may indicate mixed legitimate and malicious prompts.")
        
        # Detect intent anomalies (using user input extraction and cosine similarity)
        anomalies = detect_anomalies(prompts, embeddings, cluster_labels, main_cluster_id, base_prompt)
        
        logger.info(f"Detection complete: basePrompt length={len(base_prompt)}, confidence={confidence:.2f}, anomalies={len(anomalies)}")
        
        return DetectionResponse(
            basePrompt=base_prompt,
            confidence=float(confidence),
            anomalies=anomalies
        )
        
    except Exception as e:
        logger.error(f"Error during detection: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Detection failed: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    import os
    port = int(os.getenv("PORT", "8093"))
    uvicorn.run(app, host="0.0.0.0", port=port)
