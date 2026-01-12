"""
Detection Storage Service
Handles storage of detection events: variables, patterns, embeddings.
"""

import logging
from typing import List, Dict, Any, Optional
from src.core.stores import RAGStore, VariableStore, PatternLearner
from src.utils.embedding_utils import encode_text, embedding_to_list
from src.utils.utils import (
    normalize_base_prompt,
    extract_variables_by_position,
    longest_common_prefix_tokenwise,
    extract_user_input_v2
)

logger = logging.getLogger(__name__)

# Constants
MAX_PROMPTS_FOR_VARIABLE_STORAGE = 20
MAX_PROMPTS_FOR_RAG_STORAGE = 10
MIN_CONFIDENCE_THRESHOLD = 0.51


class DetectionStorageService:
    """Service for processing and storing detection events."""
    
    def __init__(
        self,
        variable_store: Optional[VariableStore] = None,
        rag_store: Optional[RAGStore] = None,
        pattern_learner: Optional[PatternLearner] = None
    ):
        """Initialize detection storage service."""
        self.variable_store = variable_store
        self.rag_store = rag_store
        self.pattern_learner = pattern_learner
    
    def process_detection(
        self,
        prompts: List[str],
        base_prompt: str,
        confidence: float,
        main_cluster_prompts: Optional[List[str]] = None
    ) -> bool:
        """
        Process detection event and store variables, patterns, and embeddings.
        
        Args:
            prompts: All prompts from detection
            base_prompt: Detected base prompt pattern
            confidence: Confidence score (0.0-1.0)
            main_cluster_prompts: Prompts from main cluster (if available)
            
        Returns:
            True if processing succeeded, False otherwise
        """
        try:
            if not prompts or not base_prompt or confidence < MIN_CONFIDENCE_THRESHOLD:
                logger.debug(f"Skipping detection storage: confidence {confidence:.2f} < {MIN_CONFIDENCE_THRESHOLD}")
                return False
            
            logger.info(f"Processing detection: {base_prompt[:50]}... (confidence: {confidence:.2f})")
            normalized_base_prompt = normalize_base_prompt(base_prompt)
            
            # Use main_cluster_prompts if provided, otherwise use all prompts
            prompts_to_process = main_cluster_prompts if main_cluster_prompts else prompts
            
            # Learn pattern for guardrail
            if self.pattern_learner:
                self.pattern_learner.learn_pattern(normalized_base_prompt, confidence)
            
            # Store variables
            self._store_variables(prompts_to_process, normalized_base_prompt)
            
            # Store patterns in RAG store
            self._store_patterns(prompts_to_process, normalized_base_prompt)
            
            return True
            
        except Exception as e:
            logger.error(f"Error processing detection: {e}", exc_info=True)
            return False
    
    def _store_variables(self, prompts: List[str], normalized_base_prompt: str) -> None:
        """Extract and store variables from prompts."""
        if not self.variable_store:
            return
        
        try:
            variables_to_store = []
            prompts_processed = 0
            prompts_failed = 0
            expected_vars = normalized_base_prompt.count('{}')
            
            logger.info(f"Starting variable extraction for pattern: '{normalized_base_prompt}' (expecting {expected_vars} variables per prompt)")
            
            for prompt_idx, prompt in enumerate(prompts[:MAX_PROMPTS_FOR_VARIABLE_STORAGE]):
                variables = extract_variables_by_position(prompt, normalized_base_prompt)
                extracted_count = len(variables) if variables else 0
                
                if variables and extracted_count > 0:
                    prompts_processed += 1
                    for idx, var_text in enumerate(variables):
                        if var_text and var_text.strip():
                            if idx < expected_vars:
                                variables_to_store.append({
                                    "base_prompt": normalized_base_prompt,
                                    "placeholder_index": idx,
                                    "variable_text": var_text
                                })
                else:
                    prompts_failed += 1
                    if prompt_idx < 3:
                        logger.warning(f"Failed to extract variables from prompt {prompt_idx}: extracted {extracted_count}, expected {expected_vars}")
            
            logger.info(f"Variable extraction complete: processed={prompts_processed}, failed={prompts_failed}, total_vars={len(variables_to_store)}")
            
            # Batch generate embeddings and store variables
            if variables_to_store:
                var_texts = [v["variable_text"] for v in variables_to_store]
                try:
                    var_embeddings = encode_text(var_texts)
                    if len(var_embeddings.shape) > 1:
                        var_embeddings_list = [var_embeddings[i].tolist() for i in range(len(var_embeddings))]
                    else:
                        var_embeddings_list = [var_embeddings.tolist()]
                    
                    for i, var_info in enumerate(variables_to_store):
                        if i < len(var_embeddings_list):
                            try:
                                self.variable_store.add_variable(
                                    base_prompt=var_info["base_prompt"],
                                    placeholder_index=var_info["placeholder_index"],
                                    variable_text=var_info["variable_text"],
                                    embedding=var_embeddings_list[i]
                                )
                            except Exception as e:
                                logger.warning(f"Failed to store variable at position {var_info['placeholder_index']}: {str(e)}")
                    
                    logger.info(f"✅ Successfully stored {len(variables_to_store)} variables for pattern")
                except Exception as e:
                    logger.warning(f"Failed to generate batch embeddings: {str(e)}, falling back to individual")
                    for var_info in variables_to_store:
                        try:
                            var_embedding = embedding_to_list(encode_text([var_info["variable_text"]]))
                            self.variable_store.add_variable(
                                base_prompt=var_info["base_prompt"],
                                placeholder_index=var_info["placeholder_index"],
                                variable_text=var_info["variable_text"],
                                embedding=var_embedding
                            )
                        except Exception as e2:
                            logger.warning(f"Failed to store variable at position {var_info['placeholder_index']}: {str(e2)}")
        except Exception as e:
            logger.warning(f"Failed to store variables in variable store: {str(e)}")
    
    def _store_patterns(self, prompts: List[str], normalized_base_prompt: str) -> None:
        """Store patterns in RAG store for similarity search."""
        if not self.rag_store:
            return
        
        try:
            prefix = longest_common_prefix_tokenwise(prompts[:MAX_PROMPTS_FOR_RAG_STORAGE])
            for prompt in prompts[:MAX_PROMPTS_FOR_RAG_STORAGE]:
                var_components = extract_user_input_v2(prompt, prefix)
                if var_components:
                    var_embedding = embedding_to_list(encode_text([var_components]))
                    self.rag_store.add_pattern(
                        embedding=var_embedding,
                        base_prompt=normalized_base_prompt,
                        variable_components=var_components,
                        risk_score=0.2,
                        feedback_label=None
                    )
            logger.info(f"✅ Stored patterns in RAG store")
        except Exception as e:
            logger.warning(f"Failed to store patterns in RAG store: {str(e)}")

