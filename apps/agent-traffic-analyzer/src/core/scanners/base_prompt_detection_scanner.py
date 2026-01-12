"""
Base Prompt Detection Scanner Service
Handles base prompt detection from a list of prompts using clustering.
"""

import logging
import numpy as np
from sklearn.cluster import MiniBatchKMeans
from typing import List, Optional, Dict, Any

from src.models import DetectionResponse, Anomaly
from src.config import MAX_PROMPTS_PER_REQUEST, MIN_CONFIDENCE_THRESHOLD
from src.utils.utils import normalize_base_prompt
from src.utils.prompt_utils import extract_base_prompt_from_cluster, detect_anomalies
from src.utils.embedding_utils import encode_text
from src.core.stores import PatternLearner

logger = logging.getLogger(__name__)


class BasePromptDetectionScanner:
    """
    Scanner that detects base prompt patterns from a list of prompts.
    Uses clustering to identify common patterns and extract placeholders.
    """

    def __init__(self, pattern_learner: Optional[PatternLearner] = None):
        """
        Initialize base prompt detection scanner.
        
        Args:
            pattern_learner: Optional PatternLearner instance for learning patterns
        """
        self.pattern_learner = pattern_learner
        logger.info("BasePromptDetectionScanner initialized")

    def detect(
        self,
        prompts: List[str],
        metadata: Optional[Dict[str, Any]] = None
    ) -> DetectionResponse:
        """
        Detect base prompt pattern from a list of prompts.
        
        Args:
            prompts: List of prompts to analyze
            metadata: Optional metadata for the request
            
        Returns:
            DetectionResponse with basePrompt, confidence, and anomalies
        """
        # Validate input size
        if len(prompts) > MAX_PROMPTS_PER_REQUEST:
            raise ValueError(f"Too many prompts. Maximum {MAX_PROMPTS_PER_REQUEST} allowed.")

        if len(prompts) < 2:
            # Not enough prompts for detection
            return DetectionResponse(
                basePrompt=prompts[0] if prompts else "",
                confidence=1.0,
                anomalies=[]
            )

        # Generate embeddings
        embeddings = encode_text(prompts)

        # Simple approach: Use fixed k=2 and pick the larger cluster
        # This avoids over-clustering and keeps similar prompts together
        k = 2
        kmeans = MiniBatchKMeans(n_clusters=k, random_state=42, n_init=5, batch_size=64)
        cluster_labels = kmeans.fit_predict(embeddings)

        # Find the largest cluster (main pattern)
        unique, counts = np.unique(cluster_labels, return_counts=True)
        main_cluster_id = unique[np.argmax(counts)]
        main_cluster_size = counts[np.argmax(counts)]

        # Extract prompts in main cluster
        main_cluster_prompts = [prompts[i] for i in range(len(prompts)) if cluster_labels[i] == main_cluster_id]

        # Extract base prompt pattern using token-wise longest common prefix
        base_prompt = extract_base_prompt_from_cluster(main_cluster_prompts)

        # CRITICAL: Normalize base_prompt immediately after extraction to ensure consistency
        # This ensures the same normalized string is used everywhere (storage, queries, responses)
        base_prompt = normalize_base_prompt(base_prompt)

        # Calculate confidence based on cluster size
        confidence = main_cluster_size / len(prompts)

        # Validate confidence meets minimum threshold
        if confidence < MIN_CONFIDENCE_THRESHOLD:
            logger.warning(f"Low confidence ({confidence:.2f}) below threshold ({MIN_CONFIDENCE_THRESHOLD})")

        # Detect intent anomalies (using user input extraction and cosine similarity)
        anomalies = detect_anomalies(prompts, embeddings, cluster_labels, main_cluster_id, base_prompt, encode_text)

        # Learn pattern for guardrail (if confidence is high enough) - lightweight, keep in API
        if self.pattern_learner and confidence >= MIN_CONFIDENCE_THRESHOLD:
            self.pattern_learner.learn_pattern(base_prompt, confidence)

        result = DetectionResponse(
            basePrompt=base_prompt,  # Already normalized above
            confidence=float(confidence),
            anomalies=anomalies
        )
        
        return result, main_cluster_prompts

