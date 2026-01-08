"""
Similarity Match Service
Feedback loop based similarity matching using RAG/ChromaDB.
Component 2: Similarity Matching based on feedback.
"""

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from typing import Dict, Any, Optional
import numpy as np
import logging
import uuid

from src.core.stores import RAGStore

# Configure logging
logger = logging.getLogger(__name__)

# Create router for similarity match endpoints
router = APIRouter(prefix="/similarity-match", tags=["similarity-match"])

# Constants
GUARDRAIL_RISK_THRESHOLD = 0.5  # Risk threshold for guardrail (0.0-1.0)

# Global state for dependencies (will be set by main app)
_similarity_dependencies = {
    "rag_store": None,
    "embedding_model": None,
    "model_type": "sentence_transformer",
    "model_name": "all-MiniLM-L6-v2"
}

def set_similarity_dependencies(rag_store, embedding_model, model_type, model_name):
    """Set dependencies from main app."""
    _similarity_dependencies["rag_store"] = rag_store
    _similarity_dependencies["embedding_model"] = embedding_model
    _similarity_dependencies["model_type"] = model_type
    _similarity_dependencies["model_name"] = model_name


class SimilarityMatchRequest(BaseModel):
    prompt: str
    metadata: Optional[Dict[str, Any]] = {}


class SimilarityMatchResponse(BaseModel):
    scanner_name: str
    is_valid: bool
    risk_score: float
    sanitized_text: str
    details: Dict[str, Any] = {}


def encode_text_for_similarity(texts, embedding_model, model_type, model_name):
    """Encode texts using the provided embedding model."""
    if model_type == "openai":
        # OpenAI embeddings
        try:
            response = embedding_model.embeddings.create(
                model=model_name,
                input=texts if isinstance(texts, list) else [texts]
            )
            embeddings = [item.embedding for item in response.data]
            result = np.array(embeddings[0] if len(embeddings) == 1 else embeddings)
            return np.nan_to_num(result)
        except Exception as e:
            logger.error(f"OpenAI embedding failed: {str(e)}")
            raise
    else:
        # SentenceTransformer embeddings
        embeddings = embedding_model.encode(texts, show_progress_bar=False)
        return np.nan_to_num(embeddings)


def _similarity_match_logic(
    prompt: str,
    metadata: Optional[Dict[str, Any]],
    rag_store: Optional[RAGStore] = None,
    embedding_model=None,
    model_type: str = "sentence_transformer",
    model_name: str = "all-MiniLM-L6-v2"
) -> SimilarityMatchResponse:
    """
    Core similarity matching logic (internal function).
    
    INDEPENDENT SCORING: Uses variable_risk only (based on similarity to feedback patterns).
    This is completely separate from pattern matching scoring.
    """
    # Initialize response details
    details = {
        "original_length": len(prompt),
        "similarity_scores": [],
        "false_positive_matched": False,
        "false_positive_similarity": None,
    }
    
    # Similarity matching logic using RAG
    variable_risk = 0.5  # Default medium risk
    similar_cases = []
    
    # Extract endpoint info from metadata for filtering
    endpoint = metadata.get("endpoint", "") if metadata else ""
    method = metadata.get("method", "") if metadata else ""
    endpoint_key = f"{method}:{endpoint}" if endpoint and method else None
    
    # Use full prompt for similarity matching
    text_to_analyze = prompt
    
    if rag_store and text_to_analyze and embedding_model:
        try:
            # Embed text to analyze
            text_embedding = encode_text_for_similarity([text_to_analyze], embedding_model, model_type, model_name)
            text_embedding = text_embedding[0].tolist() if len(text_embedding.shape) > 1 else text_embedding.tolist()
            
            # Search for similar malicious patterns (true_positive)
            malicious_similar = rag_store.search_similar(
                query_embedding=text_embedding,
                feedback_filter="true_positive",
                top_k=3
            )
            
            # Search for similar benign patterns
            benign_similar = rag_store.search_similar(
                query_embedding=text_embedding,
                feedback_filter="benign",
                top_k=3
            )
            
            # Search for similar false_positive patterns (should reduce risk)
            false_positive_similar_all = rag_store.search_similar(
                query_embedding=text_embedding,
                feedback_filter="false_positive",
                top_k=5,  # Get more to filter by endpoint
                min_similarity=0.7  # Lower threshold to catch matches
            )
            logger.debug(f"Found {len(false_positive_similar_all)} false_positive patterns (threshold=0.7)")
            
            # Filter by endpoint if provided
            false_positive_similar = false_positive_similar_all
            if endpoint_key and false_positive_similar_all:
                endpoint_filtered = [
                    fp for fp in false_positive_similar_all
                    if fp.get("metadata", {}).get("endpoint_key") == endpoint_key
                ]
                if endpoint_filtered:
                    false_positive_similar = endpoint_filtered
                else:
                    # If no endpoint-specific match, use all matches
                    false_positive_similar = false_positive_similar_all
            
            # Calculate weighted risk based on similarities
            malicious_weight = sum(m["similarity"] for m in malicious_similar) if malicious_similar else 0.0
            benign_weight = sum(b["similarity"] for b in benign_similar) if benign_similar else 0.0
            false_positive_weight = sum(fp["similarity"] for fp in false_positive_similar) if false_positive_similar else 0.0
            
            # If false_positive match found, significantly reduce risk
            if false_positive_similar:
                # Strong match with false_positive -> very low risk (0.0 to 0.2)
                best_fp_match = max(false_positive_similar, key=lambda x: x["similarity"])
                variable_risk = 0.2 * (1.0 - best_fp_match["similarity"])  # 0.0 to 0.2 range
                logger.info(f"False positive pattern matched (similarity={best_fp_match['similarity']:.3f}), reducing risk to {variable_risk:.3f}")
            elif malicious_similar or benign_similar:
                total_weight = malicious_weight + benign_weight
                if total_weight > 0:
                    # Higher risk if more similar to malicious, lower if similar to benign
                    variable_risk = malicious_weight / total_weight
                else:
                    variable_risk = 0.5
            
            similar_cases = [
                {
                    "similarity": m["similarity"],
                    "variable": m.get("variable_components", ""),
                    "label": m["metadata"].get("feedback_label", "none")
                }
                for m in (malicious_similar + benign_similar + false_positive_similar)[:5]
            ]
            
            # Add false_positive matches to details
            if false_positive_similar:
                details["false_positive_matched"] = True
                details["false_positive_similarity"] = max(fp["similarity"] for fp in false_positive_similar)
            
            details["similarity_scores"] = similar_cases
            
        except Exception as e:
            logger.error(f"RAG search failed: {str(e)}")
    else:
        if not rag_store:
            logger.warning("RAG store not initialized, cannot perform similarity matching")
        if not embedding_model:
            logger.warning("Embedding model not provided, cannot perform similarity matching")
    
    # For similarity-based detection, risk = variable_risk (no pattern risk component)
    final_risk = variable_risk
    
    # Determine if valid (safe)
    is_valid = final_risk < GUARDRAIL_RISK_THRESHOLD
    
    details["variable_risk"] = variable_risk
    details["final_risk"] = final_risk
    details["threshold"] = GUARDRAIL_RISK_THRESHOLD
    
    # Generate request_id for feedback tracking
    request_id = str(uuid.uuid4())
    details["request_id"] = request_id
    
    logger.info(f"Similarity match: prompt_length={len(prompt)}, false_positive_matched={details['false_positive_matched']}, "
               f"risk={final_risk:.2f}, is_valid={is_valid}, request_id={request_id}")
    
    return SimilarityMatchResponse(
        scanner_name="FeedbackLoopSimilarityMatch",
        is_valid=is_valid,
        risk_score=float(final_risk),
        sanitized_text=prompt,
        details=details
    )


@router.post("", response_model=SimilarityMatchResponse)
def similarity_match_check(request: SimilarityMatchRequest):
    """
    Feedback loop based similarity matching for a single prompt.
    
    Uses RAG-based similarity search against false_positive/true_positive/benign patterns
    from the feedback loop. This is Component 2: Similarity Matching based on feedback.
    
    INDEPENDENT SCORING: Uses variable_risk only (based on similarity to feedback patterns).
    This is completely separate from pattern matching scoring.
    """
    try:
        prompt = request.prompt.strip()
        
        if not prompt:
            raise HTTPException(status_code=400, detail="Prompt cannot be empty")
        
        return _similarity_match_logic(
            prompt=prompt,
            metadata=request.metadata,
            rag_store=_similarity_dependencies["rag_store"],
            embedding_model=_similarity_dependencies["embedding_model"],
            model_type=_similarity_dependencies["model_type"],
            model_name=_similarity_dependencies["model_name"]
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Similarity match failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Similarity match failed: {str(e)}")


# Also provide a function version for use by other modules
def similarity_match_check_function(
    request: SimilarityMatchRequest,
    rag_store: Optional[RAGStore] = None,
    embedding_model=None,
    model_type: str = "sentence_transformer",
    model_name: str = "all-MiniLM-L6-v2"
):
    """
    Function version of similarity match check (for use by other modules).
    """
    try:
        prompt = request.prompt.strip()
        
        if not prompt:
            raise HTTPException(status_code=400, detail="Prompt cannot be empty")
        
        return _similarity_match_logic(
            prompt=prompt,
            metadata=request.metadata,
            rag_store=rag_store,
            embedding_model=embedding_model,
            model_type=model_type,
            model_name=model_name
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Similarity match failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Similarity match failed: {str(e)}")

