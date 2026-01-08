"""
Feedback Service
Handles processing of feedback events and ChromaDB updates.
"""

import logging
from typing import Dict, Any, Optional
from src.core.stores import RAGStore
from src.utils.embedding_utils import encode_text, embedding_to_list

logger = logging.getLogger(__name__)


class FeedbackService:
    """Service for processing feedback events."""
    
    def __init__(self, rag_store: Optional[RAGStore] = None):
        """Initialize feedback service."""
        self.rag_store = rag_store
    
    def process_feedback(
        self,
        prompt: str,
        label: str,
        request_id: Optional[str] = None,
        pattern_id: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> bool:
        """
        Process feedback event and update ChromaDB.
        
        Args:
            prompt: The prompt text
            label: Feedback label (true_positive, false_positive, benign)
            request_id: Request ID for tracking
            pattern_id: Pattern ID if updating existing pattern
            metadata: Additional metadata
            
        Returns:
            True if processing succeeded, False otherwise
        """
        try:
            if not prompt or label not in ["true_positive", "false_positive", "benign"]:
                logger.debug(f"Invalid feedback: prompt empty or invalid label '{label}'")
                return False
            
            logger.info(f"Processing feedback: {label}, request_id={request_id}")
            
            if not self.rag_store:
                logger.warning("RAG store not initialized, cannot process feedback")
                return False
            
            metadata = metadata or {}
            
            # Save false_positive to RAG store
            if label == "false_positive":
                self._save_false_positive(prompt, request_id, metadata)
            
            # Update ChromaDB feedback
            self._update_feedback(prompt, label, pattern_id)
            
            return True
            
        except Exception as e:
            logger.error(f"Error processing feedback: {e}", exc_info=True)
            return False
    
    def _save_false_positive(
        self,
        prompt: str,
        request_id: Optional[str],
        metadata: Dict[str, Any]
    ) -> None:
        """Save false_positive pattern to ChromaDB."""
        try:
            logger.info(f"Saving false_positive pattern to ChromaDB (prompt length: {len(prompt)})")
            prompt_embedding = embedding_to_list(encode_text([prompt]))
            
            endpoint = metadata.get("endpoint", "")
            method = metadata.get("method", "")
            endpoint_key = f"{method}:{endpoint}" if endpoint and method else "unknown"
            
            saved_pattern_id = self.rag_store.add_pattern(
                embedding=prompt_embedding,
                base_prompt=endpoint_key,
                variable_components=prompt,
                risk_score=0.0,
                feedback_label="false_positive",
                metadata={
                    "endpoint": endpoint,
                    "method": method,
                    "endpoint_key": endpoint_key,
                    "request_id": request_id,
                    **metadata
                }
            )
            logger.info(f"✅ Saved false_positive embedding: pattern_id={saved_pattern_id}, endpoint={endpoint_key}")
        except Exception as e:
            logger.error(f"❌ Failed to save false_positive embedding: {str(e)}", exc_info=True)
    
    def _update_feedback(
        self,
        prompt: str,
        label: str,
        pattern_id: Optional[str]
    ) -> None:
        """Update ChromaDB feedback for existing patterns."""
        if pattern_id:
            try:
                self.rag_store.update_feedback(pattern_id, label)
                logger.info(f"Updated ChromaDB feedback for pattern {pattern_id}: {label}")
            except Exception as e:
                logger.warning(f"Failed to update ChromaDB feedback: {str(e)}")
        else:
            # Try to find and update similar patterns
            try:
                var_embedding = embedding_to_list(encode_text([prompt]))
                similar = self.rag_store.search_similar(var_embedding, top_k=1, min_similarity=0.8)
                if similar:
                    self.rag_store.update_feedback(similar[0]["id"], label)
                    logger.info(f"Updated similar pattern {similar[0]['id']} with feedback: {label}")
            except Exception as e:
                logger.warning(f"Failed to update similar patterns: {str(e)}")

