"""
Variable Store Module
Manages ChromaDB collection for storing and searching variable components
per pattern and placeholder position for anomaly detection.
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
VARIABLE_COLLECTION_NAME = "agent_traffic_variables"
SIMILARITY_THRESHOLD = 0.7
TOP_K_RESULTS = 5


class VariableStore:
    """Manages ChromaDB vector store for variable component similarity search."""
    
    def __init__(self, persist_directory: str = CHROMA_PERSIST_DIR):
        """Initialize ChromaDB client and collection for variables."""
        try:
            self.client = chromadb.PersistentClient(
                path=persist_directory,
                settings=Settings(anonymized_telemetry=False)
            )
            self.collection = self.client.get_or_create_collection(
                name=VARIABLE_COLLECTION_NAME,
                metadata={"description": "Variable components per pattern and placeholder position"}
            )
            logger.info(f"Initialized ChromaDB variable collection: {VARIABLE_COLLECTION_NAME}")
        except Exception as e:
            logger.error(f"Failed to initialize ChromaDB variable store: {str(e)}")
            raise
    
    def normalize_base_prompt(self, base_prompt: str) -> str:
        """Normalize base_prompt string for consistent matching (remove extra whitespace)."""
        if not base_prompt:
            return ""
        # Normalize whitespace: multiple spaces to single space, strip
        return " ".join(base_prompt.split())
    
    def add_variable(
        self,
        base_prompt: str,
        placeholder_index: int,
        variable_text: str,
        embedding: List[float]
    ) -> str:
        """
        Add a new variable component to the vector store.
        
        Variables are stored with base_prompt and placeholder_index as metadata,
        allowing efficient filtering by pattern and position.
        
        Args:
            base_prompt: Base prompt pattern (e.g., "Book flight from {} to {} on {}")
            placeholder_index: Position of placeholder (0, 1, 2, ...)
            variable_text: The actual variable value
            embedding: Vector embedding of the variable text
            
        Returns:
            Variable ID
        """
        try:
            # Normalize base_prompt before storing to ensure consistent matching
            normalized_base_prompt = self.normalize_base_prompt(base_prompt)
            
            variable_id = f"var_{datetime.now().timestamp()}_{hash(f'{normalized_base_prompt}_{placeholder_index}_{variable_text}') % 10000}"
            
            doc_metadata = {
                "base_prompt": normalized_base_prompt,  # Store normalized version
                "placeholder_index": str(placeholder_index),
                "variable_text": variable_text,
                "timestamp": str(datetime.now().isoformat()),
                "usage_count": "1"
            }
            
            self.collection.add(
                ids=[variable_id],
                embeddings=[embedding],
                documents=[variable_text],
                metadatas=[doc_metadata]
            )
            
            logger.debug(f"✅ Stored variable: {variable_id}")
            logger.debug(f"   base_prompt (normalized): '{normalized_base_prompt}' (repr: {repr(normalized_base_prompt)})")
            logger.debug(f"   placeholder_index: {placeholder_index}")
            logger.debug(f"   variable_text: {variable_text[:50]}...")
            return variable_id
            
        except Exception as e:
            logger.error(f"Failed to add variable: {str(e)}")
            raise
    
    def search_similar_variables(
        self,
        base_prompt: str,
        placeholder_index: int,
        query_embedding: List[float],
        min_similarity: float = SIMILARITY_THRESHOLD,
        top_k: int = TOP_K_RESULTS,
        content_type: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        Search for similar variables for a specific pattern and placeholder position.
        
        Variables are filtered by base_prompt (metadata) and placeholder_index (metadata),
        then ranked by embedding similarity. This allows efficient search within a specific
        pattern and position context.
        
        Uses embedding-based semantic similarity which works across all content types
        (text, code, encoded data, mixed content, etc.).
        
        Args:
            base_prompt: Base prompt pattern to filter by (will be normalized)
            placeholder_index: Placeholder position to filter by
            query_embedding: Query embedding vector
            min_similarity: Minimum similarity threshold (applied after search)
            top_k: Number of results to return
            content_type: DEPRECATED - kept for backward compatibility but not used for filtering
            
        Returns:
            List of similar variables with metadata
        """
        try:
            # Normalize base_prompt for consistent matching
            normalized_base_prompt = self.normalize_base_prompt(base_prompt)
            
            # Filter by base_prompt (metadata) and placeholder_index (metadata)
            # ChromaDB requires $and operator for multiple conditions
            where_clause = {
                "$and": [
                    {"base_prompt": normalized_base_prompt},
                    {"placeholder_index": str(placeholder_index)}
                ]
            }
            
            # Note: content_type filter removed - embedding similarity works for any content type
            
            results = self.collection.query(
                query_embeddings=[query_embedding],
                n_results=top_k,
                where=where_clause
            )
            
            similar_variables = []
            if results['ids'] and len(results['ids'][0]) > 0:
                for i in range(len(results['ids'][0])):
                    distance = results['distances'][0][i] if 'distances' in results else None
                    # ChromaDB uses L2 distance, convert to similarity (1 - normalized distance)
                    similarity = 1.0 - (distance / 2.0) if distance is not None else 0.0
                    
                    if similarity >= min_similarity:
                        similar_variables.append({
                            "id": results['ids'][0][i],
                            "similarity": similarity,
                            "variable_text": results['documents'][0][i],
                            "metadata": results['metadatas'][0][i]
                        })
            
            logger.debug(f"Found {len(similar_variables)} similar variables for pattern '{base_prompt[:30]}...' at index {placeholder_index}")
            return similar_variables
            
        except Exception as e:
            logger.error(f"Failed to search similar variables: {str(e)}")
            return []
    
    def get_variable_count(
        self,
        base_prompt: Optional[str] = None,
        placeholder_index: Optional[int] = None
    ) -> int:
        """
        Get count of variables, optionally filtered by pattern and position.
        
        Uses base_prompt and placeholder_index from metadata for filtering.
        ChromaDB requires EXACT string match, so base_prompt is normalized.
        
        Args:
            base_prompt: Optional base prompt to filter by (will be normalized)
            placeholder_index: Optional placeholder index to filter by
            
        Returns:
            Count of variables
        """
        try:
            if base_prompt is not None and placeholder_index is not None:
                # Normalize base_prompt for consistent matching
                normalized_base_prompt = self.normalize_base_prompt(base_prompt)
                # ChromaDB requires $and operator for multiple conditions
                where_clause = {
                    "$and": [
                        {"base_prompt": normalized_base_prompt},
                        {"placeholder_index": str(placeholder_index)}
                    ]
                }
                logger.info(f"🔍 Querying ChromaDB: base_prompt='{normalized_base_prompt}' (repr: {repr(normalized_base_prompt)}), placeholder_index={placeholder_index} (repr: {repr(str(placeholder_index))})")
                
                # Try using get() with where clause
                try:
                    results = self.collection.get(where=where_clause)
                    count = len(results['ids']) if results.get('ids') else 0
                    logger.info(f"   Result from get(): {count} variables found")
                except Exception as e:
                    logger.warning(f"   get() failed: {str(e)}, trying alternative approach")
                    count = 0
                
                # If get() returned 0, try fetching all and filtering manually
                if count == 0:
                    logger.info(f"   get() returned 0, trying manual filter...")
                    try:
                        all_results = self.collection.get()
                        if all_results and all_results.get('metadatas'):
                            manual_count = 0
                            for i, metadata in enumerate(all_results['metadatas']):
                                stored_bp = metadata.get('base_prompt', '')
                                stored_idx = metadata.get('placeholder_index', '')
                                # Normalize stored values for comparison
                                norm_stored_bp = self.normalize_base_prompt(stored_bp)
                                if norm_stored_bp == normalized_base_prompt and str(stored_idx) == str(placeholder_index):
                                    manual_count += 1
                            
                            if manual_count > 0:
                                logger.warning(f"   ⚠️  Manual filter found {manual_count} variables but get() returned 0!")
                                logger.warning(f"   This indicates ChromaDB get() where clause is not working correctly")
                                logger.warning(f"   Query: base_prompt='{normalized_base_prompt}', placeholder_index='{placeholder_index}'")
                                # Show first matching entry
                                for i, metadata in enumerate(all_results['metadatas']):
                                    stored_bp = metadata.get('base_prompt', '')
                                    stored_idx = metadata.get('placeholder_index', '')
                                    norm_stored_bp = self.normalize_base_prompt(stored_bp)
                                    if norm_stored_bp == normalized_base_prompt and str(stored_idx) == str(placeholder_index):
                                        logger.warning(f"   Example match: stored_bp='{stored_bp}' (normalized: '{norm_stored_bp}'), stored_idx='{stored_idx}'")
                                        break
                                count = manual_count  # Use manual count
                    except Exception as e2:
                        logger.error(f"   Manual filter also failed: {str(e2)}")
                
                if count == 0:
                    # Debug: check what's actually stored
                    logger.warning(f"⚠️  No variables found for base_prompt='{normalized_base_prompt}', placeholder_index={placeholder_index}")
                    all_results = self.collection.get()
                    if all_results and all_results.get('metadatas'):
                        # Find all unique base_prompts stored
                        stored_base_prompts = {}
                        # Also track what indices are stored for this base_prompt
                        indices_for_base_prompt = set()
                        for metadata in all_results['metadatas']:
                            stored_bp = metadata.get('base_prompt', '')
                            stored_idx = metadata.get('placeholder_index', '')
                            if stored_bp:
                                key = f"{stored_bp}|{stored_idx}"
                                if key not in stored_base_prompts:
                                    stored_base_prompts[key] = 0
                                stored_base_prompts[key] += 1
                                
                                # Check if this base_prompt matches (normalized)
                                norm_stored_bp = self.normalize_base_prompt(stored_bp)
                                if norm_stored_bp == normalized_base_prompt:
                                    indices_for_base_prompt.add(stored_idx)
                        
                        # Check if our query matches any stored pattern
                        query_key = f"{normalized_base_prompt}|{placeholder_index}"
                        if query_key in stored_base_prompts:
                            logger.error(f"❌ BUG: Query key '{query_key}' exists in stored patterns but query returned 0!")
                            logger.error(f"   This indicates a ChromaDB query issue")
                        else:
                            logger.warning(f"   Query key '{query_key}' not found in stored patterns")
                            
                            # Show what indices ARE stored for this base_prompt
                            if indices_for_base_prompt:
                                sorted_indices = sorted([int(idx) for idx in indices_for_base_prompt if idx.isdigit()])
                                logger.warning(f"   ⚠️  Variables exist for this base_prompt but at different indices:")
                                logger.warning(f"      Stored indices: {sorted_indices}")
                                logger.warning(f"      Querying for index: {placeholder_index}")
                                logger.warning(f"      This suggests variables weren't stored for all placeholder positions")
                            else:
                                logger.warning(f"   No variables found for this base_prompt at any index")
                            
                            logger.warning(f"   Stored patterns (first 5):")
                            for key, count in list(stored_base_prompts.items())[:5]:
                                logger.warning(f"     - '{key}' ({count} variables)")
                            
                            # Check if normalized versions match
                            for stored_bp, stored_idx in [(k.split('|')[0], k.split('|')[1]) for k in stored_base_prompts.keys()]:
                                if stored_idx == str(placeholder_index):
                                    norm_stored = self.normalize_base_prompt(stored_bp)
                                    if norm_stored == normalized_base_prompt:
                                        logger.error(f"❌ BUG: Normalized versions match but query failed!")
                                        logger.error(f"   Query: '{normalized_base_prompt}' (repr: {repr(normalized_base_prompt)})")
                                        logger.error(f"   Stored: '{stored_bp}' -> normalized: '{norm_stored}' (repr: {repr(norm_stored)})")
                return count
            else:
                return self.collection.count()
        except Exception as e:
            logger.error(f"Failed to get variable count: {str(e)}")
            return 0

