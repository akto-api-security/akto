"""
Prompt processing utilities: pattern extraction and anomaly detection.
"""

from typing import List
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from src.utils.utils import longest_common_prefix_tokenwise, extract_user_input_v2, normalize_base_prompt
from src.config import INTENT_THRESHOLD
from src.models import Anomaly


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
        # Build pattern using a smarter algorithm that handles variable-length segments
        pattern_tokens = prefix_tokens.copy()
        
        # Track current position for each prompt (handles variable-length segments)
        prompt_positions = [len(prefix_tokens)] * len(prompts)
        split_prompts = [p.split() for p in prompts]
        
        while True:
            # Get tokens at current positions for all prompts
            tokens_at_positions = []
            for prompt_idx in range(len(prompts)):
                pos = prompt_positions[prompt_idx]
                if pos < len(split_prompts[prompt_idx]):
                    tokens_at_positions.append((prompt_idx, split_prompts[prompt_idx][pos]))
            
            if not tokens_at_positions:
                break
            
            # Check if all prompts have the same token at their current positions
            if len(tokens_at_positions) == len(prompts):
                token_values = [token for _, token in tokens_at_positions]
                if len(set(token_values)) == 1:
                    # All prompts have the same token - add to pattern
                    pattern_tokens.append(token_values[0])
                    # Advance all prompts
                    for prompt_idx in range(len(prompts)):
                        prompt_positions[prompt_idx] += 1
                    continue
            
            # Different tokens or not all prompts have tokens - this is a variable segment
            # Add placeholder if we don't already have one
            if not pattern_tokens or pattern_tokens[-1] != "{}":
                pattern_tokens.append("{}")
            
            # Look ahead to find the next common token across all prompts
            # This handles cases like "business class flight" vs "economy flight"
            # We need to find tokens that appear in all prompts, even at different positions
            found_common = False
            max_lookahead = min(10, max_len - min(prompt_positions))  # Look ahead up to 10 tokens
            
            # Collect all tokens from all prompts in the lookahead window
            all_tokens_in_window = {}
            for prompt_idx in range(len(prompts)):
                start_pos = prompt_positions[prompt_idx]
                end_pos = min(start_pos + max_lookahead, len(split_prompts[prompt_idx]))
                for pos in range(start_pos, end_pos):
                    token = split_prompts[prompt_idx][pos]
                    if token not in all_tokens_in_window:
                        all_tokens_in_window[token] = []
                    all_tokens_in_window[token].append((prompt_idx, pos))
            
            # Find tokens that appear in all prompts (can be at different positions)
            for token, occurrences in all_tokens_in_window.items():
                # Check if this token appears in all prompts
                prompt_indices_found = set(occ[0] for occ in occurrences)
                if len(prompt_indices_found) == len(prompts):
                    # Found a common token - advance each prompt to its occurrence position
                    for prompt_idx in range(len(prompts)):
                        # Find the first occurrence of this token in this prompt
                        token_positions = [occ[1] for occ in occurrences if occ[0] == prompt_idx]
                        if token_positions:
                            # Advance to the position after this token
                            prompt_positions[prompt_idx] = token_positions[0] + 1
                        else:
                            # Shouldn't happen, but handle gracefully
                            prompt_positions[prompt_idx] = len(split_prompts[prompt_idx])
                    pattern_tokens.append(token)
                    found_common = True
                    break
            
            if not found_common:
                # No common token found ahead - advance all prompts by 1
                for prompt_idx in range(len(prompts)):
                    if prompt_positions[prompt_idx] < len(split_prompts[prompt_idx]):
                        prompt_positions[prompt_idx] += 1
                    else:
                        # This prompt is done, but others might continue
                        pass
                
                # Check if all prompts are exhausted
                if all(prompt_positions[idx] >= len(split_prompts[idx]) for idx in range(len(prompts))):
                    break
        
        result = " ".join(pattern_tokens)
        # Clean up consecutive placeholders
        import re
        result = re.sub(r'(\{\}\s+)+', '{} ', result).strip()
        return result.strip()
    
    return prefix


def detect_anomalies(
    prompts: List[str], 
    embeddings: np.ndarray, 
    cluster_labels: np.ndarray, 
    main_cluster_id: int, 
    base_prompt: str,
    encode_text_func
) -> List[Anomaly]:
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
    user_embeddings = encode_text_func(user_inputs)
    
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

