"""
Embedding utilities for text encoding using OpenAI or SentenceTransformer models.
"""

import os
import logging
import numpy as np
from sentence_transformers import SentenceTransformer

logger = logging.getLogger(__name__)

# Global state for embedding model (singleton pattern)
_embedding_model = None
_embedding_model_type = None
_embedding_model_name = None


def get_embedding_model():
    """
    Get embedding model - OpenAI if configured, otherwise SentenceTransformer.
    
    Returns:
        tuple: (embedding_model, model_type, model_name)
    """
    global _embedding_model, _embedding_model_type, _embedding_model_name
    
    if _embedding_model is not None:
        return _embedding_model, _embedding_model_type, _embedding_model_name
    
    # Check for OpenAI embedding configuration (matching code-analysis pattern)
    embedding_model_name = os.getenv("EMBEDDING_MODEL", "")
    openai_api_key = os.getenv("OPENAI_API_KEY_FOR_EMBEDDINGS") or os.getenv("OPENAI_API_KEY")
    
    if embedding_model_name and openai_api_key:
        try:
            from openai import OpenAI
            client = OpenAI(api_key=openai_api_key)
            _embedding_model = client
            _embedding_model_type = "openai"
            _embedding_model_name = embedding_model_name
            logger.info(f"Embedding model initialized: OpenAI ({embedding_model_name})")
            return _embedding_model, _embedding_model_type, _embedding_model_name
        except Exception as e:
            logger.warning(f"Failed to initialize OpenAI embeddings: {str(e)}, falling back to SentenceTransformer")
    
    # Default to SentenceTransformer (local, fast, free)
    _embedding_model = SentenceTransformer('all-MiniLM-L6-v2')
    _embedding_model_type = "sentence_transformer"
    _embedding_model_name = "all-MiniLM-L6-v2"
    logger.info(f"Embedding model initialized: SentenceTransformer ({_embedding_model_name})")
    return _embedding_model, _embedding_model_type, _embedding_model_name


def encode_text(texts):
    """
    Encode texts using the configured embedding model.
    
    Args:
        texts: Single string or list of strings to encode
        
    Returns:
        numpy.ndarray: Embedding vectors (2D array for multiple texts, 1D for single text)
    """
    embedding_model, model_type, model_name = get_embedding_model()
    
    if model_type == "openai":
        response = embedding_model.embeddings.create(
            model=model_name,
            input=texts if isinstance(texts, list) else [texts]
        )
        embeddings = [item.embedding for item in response.data]
        result = np.array(embeddings[0] if len(embeddings) == 1 else embeddings)
    else:
        result = embedding_model.encode(texts, show_progress_bar=False)
    return np.nan_to_num(result)


def embedding_to_list(embedding):
    """
    Convert embedding array to list format.
    
    Args:
        embedding: numpy array (1D or 2D)
        
    Returns:
        list: Embedding as a list (single list for 1D, list of lists for 2D)
    """
    return embedding[0].tolist() if len(embedding.shape) > 1 else embedding.tolist()


# Initialize model at module import time
model, model_type, model_name = get_embedding_model()

