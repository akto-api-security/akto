"""
Variable Similarity Checker Utility
Handles variable similarity checking and risk calculation for anomaly detection.
"""

import logging
from typing import List, Dict, Any, Optional, Tuple
import numpy as np

logger = logging.getLogger(__name__)


def check_variable_similarity(
    extracted_vars: List[str],
    matched_pattern: str,
    variable_store,
    encode_text_func,
    store_variables: bool = True,
    full_prompt: Optional[str] = None
) -> Tuple[List[float], List[float], List[Dict[str, Any]]]:
    """
    Check similarity of each variable against historical variables and calculate risks.
    
    Args:
        extracted_vars: List of variable texts extracted from prompt
        matched_pattern: Base prompt pattern (e.g., "Book {} flight from {} to {}")
        variable_store: VariableStore instance for searching similar variables
        encode_text_func: Function to encode text to embeddings (encode_text)
        store_variables: Whether to store variables in variable_store (default: True)
        
    Returns:
        Tuple of (variable_risks, variable_similarities, variable_details)
        - variable_risks: List of risk scores (0.0-1.0) per variable
        - variable_similarities: List of max similarity scores per variable
        - variable_details: List of detailed info per variable
    """
    variable_risks = []
    variable_similarities = []
    variable_details = []
    
    if not extracted_vars or not variable_store:
        return variable_risks, variable_similarities, variable_details
    
    # Batch embedding generation for better performance
    variables_to_check = [var for var in extracted_vars if var and var.strip()]
    if variables_to_check:
        try:
            # Generate all embeddings at once (batch)
            var_embeddings = encode_text_func(variables_to_check)
            # Convert to list format
            if len(var_embeddings.shape) > 1:
                var_embeddings_list = [var_embeddings[i].tolist() for i in range(len(var_embeddings))]
            else:
                var_embeddings_list = [var_embeddings.tolist()]
        except Exception as e:
            logger.warning(f"Failed to generate batch embeddings: {str(e)}, falling back to individual")
            var_embeddings_list = None
    else:
        var_embeddings_list = None
    
    embedding_idx = 0
    for idx, var_text in enumerate(extracted_vars):
        if not var_text.strip():
            # Empty variable - skip
            variable_risks.append(0.5)  # Medium risk for empty
            variable_similarities.append(0.0)
            variable_details.append({
                "position": idx,
                "variable": var_text,
                "similarity": 0.0,
                "risk": 0.5,
                "similar_variables_found": 0
            })
            continue
        
        try:
            # Get embedding (from batch if available, otherwise generate)
            if var_embeddings_list and embedding_idx < len(var_embeddings_list):
                var_embedding = var_embeddings_list[embedding_idx]
                embedding_idx += 1
            else:
                # Fallback: generate individually
                var_embedding_result = encode_text_func([var_text])
                var_embedding = var_embedding_result[0].tolist() if len(var_embedding_result.shape) > 1 else var_embedding_result.tolist()
            
            # Search for similar variables using embedding-based similarity
            # Embedding similarity works across all content types (text, code, encoded, etc.)
            # Content type is stored for informational purposes only
            similar_vars_all = variable_store.search_similar_variables(
                base_prompt=matched_pattern,
                placeholder_index=idx,
                query_embedding=var_embedding,
                min_similarity=0.0,  # Get all matches, filter by similarity threshold
                top_k=5
                # Note: content_type filter removed - embedding similarity handles all types
            )
            
            # Filter by similarity threshold (embedding-based, works for any content type)
            similar_vars = [v for v in similar_vars_all if v["similarity"] >= 0.7]
            
            # Calculate risk based on similarity
            if similar_vars:
                max_similarity = max(v["similarity"] for v in similar_vars)
                # Similar variables found: lower risk (0.1-0.2 range)
                variable_risk = 0.1 + (1.0 - max_similarity) * 0.1
                variable_similarities.append(max_similarity)
                variable_details.append({
                    "position": idx,
                    "variable": var_text,
                    "similarity": max_similarity,
                    "risk": variable_risk,
                    "similar_variables_found": len(similar_vars),
                    "best_match": similar_vars[0]["variable_text"][:50] if similar_vars else None
                })
            else:
                # No similar variables with similarity >= 0.7
                # Use max similarity from all matches for risk calculation
                max_similarity = max(v["similarity"] for v in similar_vars_all) if similar_vars_all else 0.0
                
                # CRITICAL: Check for known attack patterns first
                # This catches malicious content even if embeddings are similar
                # Use more precise patterns to avoid false positives
                attack_patterns = [
                    ("sql_injection", [
                        "'; drop", "'; delete", "'; update", "'; insert", 
                        "union select", "or '1'='1", "'; --", "'; /*",
                        "'; drop table", "'; truncate", "'; union", "union select",
                        "union select", "'; select", "where '1'='1", "or 1=1"
                    ]),
                    ("xss", [
                        "<script>", "</script>", "javascript:alert", "onerror=", 
                        "onclick=", "<img src=x", "onload=", "<iframe",
                        "<img src=x onerror", "alert('xss", "onerror=alert", 
                        "<img src=x onerror=", "javascript:", "onerror"
                    ]),
                    ("command_injection", [
                        "$(rm", "$(cat", "$(ls", "; rm", "; cat", "; ls",
                        "| cat", "| rm", "`rm", "`cat", "&& rm", "|| rm",
                        "exec(", "system(", "popen(", "$(rm -rf"
                    ]),
                    ("path_traversal", [
                        "/etc/passwd", "/etc/shadow", "..\\windows\\system32",
                        "C:\\windows\\system32", "../../../", "..\\..\\..\\",
                        "../etc/passwd", "../etc/shadow"
                    ]),
                    ("ldap_injection", [
                        "${jndi:ldap://", "ldap://evil", "*)(&", "admin)(&(password",
                        "${jndi:rmi://", "${jndi:dns://", "jndi:ldap"
                    ]),
                    ("code_injection", [
                        "import os; os.system", "__import__('os').system",
                        "eval('", "exec('", "compile('", "os.system('rm",
                        "__builtins__", "globals()", "locals()", "__import__('os')",
                        "eval(", "exec(", "os.system", "__import__"
                    ])
                ]
                
                is_malicious = False
                attack_type = None
                var_lower = var_text.lower()
                
                # Check for attack patterns - be more lenient for known attack signatures
                # Attack patterns are usually distinct and don't appear in legitimate content
                for pattern_name, patterns in attack_patterns:
                    for pattern in patterns:
                        pattern_lower = pattern.lower()
                        # Simple substring check - attack patterns are usually distinct enough
                        if pattern_lower in var_lower:
                            # Additional validation only for ambiguous patterns
                            # For most attack patterns, if they exist, they're malicious
                            pattern_idx = var_lower.find(pattern_lower)
                            if pattern_idx >= 0:
                                # For short code injection patterns, check word boundaries to avoid false positives
                                # (e.g., "import" in "important" should not trigger)
                                # But allow if it's clearly code (has parentheses, semicolons, etc.)
                                if pattern_name == "code_injection" and len(pattern) < 8:
                                    # Check if it's clearly code context (has () or ; nearby)
                                    before_char = var_lower[pattern_idx - 1] if pattern_idx > 0 else ' '
                                    after_idx = pattern_idx + len(pattern_lower)
                                    after_char = var_lower[after_idx] if after_idx < len(var_lower) else ' '
                                    
                                    # Allow if it's at word boundary OR if it's clearly code context
                                    is_code_context = (
                                        '(' in var_lower or ')' in var_lower or 
                                        ';' in var_lower or '.' in var_lower[max(0, pattern_idx-2):pattern_idx+len(pattern_lower)+2]
                                    )
                                    
                                    if is_code_context or \
                                       (before_char in ' \t\n.,;:!?()[]{}' or pattern_idx == 0) and \
                                       (after_char in ' \t\n.,;:!?()[]{}' or after_idx >= len(var_lower)):
                                        is_malicious = True
                                        attack_type = pattern_name
                                        logger.warning(f"🚨 Attack pattern '{pattern}' ({pattern_name}) detected in variable: '{var_text[:50]}...'")
                                        break
                                else:
                                    # For other attack patterns, if found, it's malicious
                                    is_malicious = True
                                    attack_type = pattern_name
                                    logger.warning(f"🚨 Attack pattern '{pattern}' ({pattern_name}) detected in variable: '{var_text[:50]}...'")
                                    break
                    if is_malicious:
                        break
                
                if is_malicious:
                    # Known attack pattern detected - assign very high risk
                    variable_risk = 0.95  # Very high risk for known attacks
                    logger.warning(f"🚨 Attack pattern detected ({attack_type}): '{var_text[:50]}...' - assigning high risk")
                elif max_similarity < 0.15:
                    # Extremely different (almost no match): high risk (0.65-0.75)
                    # Only assign high risk if similarity is VERY low (< 0.15)
                    # This catches truly anomalous content while allowing legitimate variations
                    variable_risk = 0.65 + (0.15 - max_similarity) * 0.67  # 0.65 to 0.75
                    variable_risk = min(0.75, max(0.65, variable_risk))
                elif max_similarity < 0.3:
                    # Very different: medium-high risk (0.5-0.65)
                    # Balanced to catch suspicious content without too many false positives
                    variable_risk = 0.5 + (0.3 - max_similarity) * 1.0  # 0.5 to 0.65
                    variable_risk = min(0.65, max(0.5, variable_risk))
                elif max_similarity < 0.5:
                    # Moderately different: medium risk (0.4-0.5)
                    # Reduced to minimize false positives
                    variable_risk = 0.4 + (0.5 - max_similarity) * 0.5  # 0.4 to 0.5
                    variable_risk = min(0.5, max(0.4, variable_risk))
                else:
                    # Somewhat similar but not enough: low-medium risk (0.3-0.4)
                    variable_risk = 0.3 + (0.7 - max_similarity) * 0.25 if max_similarity < 0.7 else 0.2
                    variable_risk = min(0.4, max(0.3, variable_risk))
                
                # Initialize attack detection flags
                detected_attack = is_malicious if 'is_malicious' in locals() else False
                detected_attack_type = attack_type if 'attack_type' in locals() else None
                
                variable_similarities.append(max_similarity)
                variable_details.append({
                    "position": idx,
                    "variable": var_text,
                    "similarity": max_similarity,
                    "risk": variable_risk,
                    "similar_variables_found": 0,
                    "attack_detected": detected_attack,
                    "attack_type": detected_attack_type
                })
            
            variable_risks.append(variable_risk)
            
            # Store variable for future similarity checks
            # base_prompt is stored as metadata, allowing efficient filtering by pattern and position
            if store_variables:
                variable_store.add_variable(
                    base_prompt=matched_pattern,  # Will be normalized inside add_variable
                    placeholder_index=idx,
                    variable_text=var_text,
                    embedding=var_embedding
                )
            
        except Exception as e:
            logger.warning(f"Failed to check similarity for variable {idx}: {str(e)}")
            # Default risk if similarity check fails
            variable_risks.append(0.5)
            variable_similarities.append(0.0)
            variable_details.append({
                "position": idx,
                "variable": var_text,
                "similarity": 0.0,
                "risk": 0.5,
                "error": str(e)
            })
    
    return variable_risks, variable_similarities, variable_details


def calculate_final_variable_risk(variable_risks: List[float]) -> float:
    """
    Calculate final variable risk from individual variable risks.
    
    Uses averaging with special handling for single variables and high-risk cases.
    
    Args:
        variable_risks: List of risk scores per variable
        
    Returns:
        Final average variable risk (0.0-1.0)
    """
    if not variable_risks:
        return 0.5  # Default medium risk
    
    # Step 1: Calculate average (reduces false positives from legitimate variations)
    avg_variable_risk = sum(variable_risks) / len(variable_risks)
    
    # Step 2: Special handling for single variable case
    # Single variable attacks need stricter handling to avoid false negatives
    if len(variable_risks) == 1:
        single_var_risk = variable_risks[0]
        if single_var_risk > 0.5:
            # If single variable has high risk, increase it further
            # This ensures single-variable attacks (e.g., "hack") get flagged
            avg_variable_risk = max(single_var_risk, 0.7)
        # else: use calculated risk as-is (low risk stays low)
    
    # Step 3: Maximum risk check for multiple variables
    # If any variable is very risky, increase average to catch single-variable attacks
    else:
        max_risk = max(variable_risks)
        if max_risk > 0.6:
            # Use weighted combination: 70% average + 30% max
            # This catches cases where one malicious variable gets averaged out
            avg_variable_risk = (avg_variable_risk * 0.7) + (max_risk * 0.3)
    
    return avg_variable_risk

