"""
RAG Store Module
Manages vector database (ChromaDB) for storing and searching prompt patterns
with feedback labels for anomaly detection.
"""

import chromadb
from chromadb.config import Settings
import logging
from typing import List, Dict, Any, Optional
import os
from datetime import datetime

logger = logging.getLogger(__name__)

# Constants
CHROMA_PERSIST_DIR = os.getenv("CHROMA_PERSIST_DIR", "./chroma_db")
COLLECTION_NAME = "agent_traffic_patterns"
SIMILARITY_THRESHOLD = 0.7
TOP_K_RESULTS = 5


class RAGStore:
    """Manages ChromaDB vector store for prompt pattern similarity search."""
    
    def __init__(self, persist_directory: str = CHROMA_PERSIST_DIR):
        """Initialize ChromaDB client and collection."""
        try:
            self.client = chromadb.PersistentClient(
                path=persist_directory,
                settings=Settings(anonymized_telemetry=False)
            )
            self.collection = self.client.get_or_create_collection(
                name=COLLECTION_NAME,
                metadata={"description": "Agent traffic patterns with feedback labels"}
            )
            logger.info(f"Initialized ChromaDB collection: {COLLECTION_NAME}")
        except Exception as e:
            logger.error(f"Failed to initialize ChromaDB: {str(e)}")
            raise
    
    def add_pattern(
        self,
        embedding: List[float],
        base_prompt: str,
        variable_components: str,
        risk_score: float,
        feedback_label: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Add a new pattern to the vector store.
        
        Args:
            embedding: Vector embedding of variable components
            base_prompt: Base prompt pattern
            variable_components: Extracted variable/user input
            risk_score: Initial risk score
            feedback_label: Optional feedback label (true_positive, false_positive, benign)
            metadata: Additional metadata
            
        Returns:
            Pattern ID
        """
        try:
            pattern_id = f"pattern_{datetime.now().timestamp()}_{hash(variable_components) % 10000}"
            
            doc_metadata = {
                "base_prompt": base_prompt,
                "variable_components": variable_components,
                "risk_score": str(risk_score),
                "feedback_label": feedback_label or "none",
                "timestamp": str(datetime.now().isoformat())
            }
            
            if metadata:
                doc_metadata.update(metadata)
            
            self.collection.add(
                ids=[pattern_id],
                embeddings=[embedding],
                documents=[variable_components],
                metadatas=[doc_metadata]
            )
            
            logger.info(f"Added pattern to vector store: {pattern_id}")
            return pattern_id
            
        except Exception as e:
            logger.error(f"Failed to add pattern: {str(e)}")
            raise
    
    def search_similar(
        self,
        query_embedding: List[float],
        top_k: int = TOP_K_RESULTS,
        feedback_filter: Optional[str] = None,
        min_similarity: float = SIMILARITY_THRESHOLD
    ) -> List[Dict[str, Any]]:
        """
        Search for similar patterns using vector similarity.
        
        Args:
            query_embedding: Query embedding vector
            top_k: Number of results to return
            feedback_filter: Optional filter by feedback label
            min_similarity: Minimum similarity threshold
            
        Returns:
            List of similar patterns with metadata
        """
        try:
            where_clause = None
            if feedback_filter:
                where_clause = {"feedback_label": feedback_filter}
            
            results = self.collection.query(
                query_embeddings=[query_embedding],
                n_results=top_k,
                where=where_clause
            )
            
            similar_patterns = []
            if results['ids'] and len(results['ids'][0]) > 0:
                for i in range(len(results['ids'][0])):
                    distance = results['distances'][0][i] if 'distances' in results else None
                    # ChromaDB uses L2 distance, convert to similarity (1 - normalized distance)
                    similarity = 1.0 - (distance / 2.0) if distance is not None else 0.0
                    
                    if similarity >= min_similarity:
                        similar_patterns.append({
                            "id": results['ids'][0][i],
                            "similarity": similarity,
                            "variable_components": results['documents'][0][i],
                            "metadata": results['metadatas'][0][i]
                        })
            
            logger.debug(f"Found {len(similar_patterns)} similar patterns")
            return similar_patterns
            
        except Exception as e:
            logger.error(f"Failed to search similar patterns: {str(e)}")
            return []
    
    def update_feedback(
        self,
        pattern_id: str,
        feedback_label: str
    ) -> bool:
        """
        Update feedback label for an existing pattern.
        
        Args:
            pattern_id: Pattern ID to update
            feedback_label: New feedback label (true_positive, false_positive, benign)
            
        Returns:
            True if successful
        """
        try:
            # Get existing pattern
            results = self.collection.get(ids=[pattern_id])
            
            if not results['ids']:
                logger.warning(f"Pattern not found: {pattern_id}")
                return False
            
            # Update metadata
            metadata = results['metadatas'][0] if results['metadatas'] else {}
            metadata['feedback_label'] = feedback_label
            metadata['feedback_updated_at'] = str(datetime.now().isoformat())
            
            # ChromaDB doesn't support direct update, so we need to delete and re-add
            # For now, we'll use update with new metadata
            self.collection.update(
                ids=[pattern_id],
                metadatas=[metadata]
            )
            
            logger.info(f"Updated feedback for pattern {pattern_id}: {feedback_label}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to update feedback: {str(e)}")
            return False
    
    def get_pattern_count(self) -> int:
        """Get total number of patterns in the store."""
        try:
            return self.collection.count()
        except Exception as e:
            logger.error(f"Failed to get pattern count: {str(e)}")
            return 0


