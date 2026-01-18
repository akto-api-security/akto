"""
Agent Traffic Analyzer Service
FastAPI service that detects base prompts with placeholders from a list of prompts
using SentenceTransformer embeddings and clustering.
"""

from fastapi import FastAPI, HTTPException
import logging
import uuid

# Import models
from src.models import (
    DetectionRequest,
    DetectionResponse,
    Anomaly,
    GuardrailRequest,
    GuardrailResponse,
    AnomalyDetectionRequest,
    AnomalyDetectionResponse,
    FeedbackRequest
)

# Import config
from src.config import MAX_PROMPTS_PER_REQUEST
# Import utilities
from src.utils.embedding_utils import model, model_type, model_name
from src.core.scanners import BasePromptDetectionScanner, AnomalyDetectionScanner
# Import guardrail modules
from src.core.stores import RAGStore, PatternLearner, VariableStore
from src.kafka.producer import (
    send_feedback as send_feedback_event,
    send_detection as send_detection_event,
    close as close_producer
)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Agent Traffic Analyzer", version="1.0.0")

# Import similarity match module (needed for set_similarity_dependencies)
from src.api.routes.similarity_match import set_similarity_dependencies

# Initialize RAG store, pattern learner, and variable store
rag_store = None
pattern_learner = None
variable_store = None
try:
    rag_store = RAGStore()
    pattern_learner = PatternLearner()
    variable_store = VariableStore()
    logger.info("Stores initialized")
except Exception as e:
    logger.error(f"❌ Failed to initialize stores or pattern learner: {str(e)}", exc_info=True)
    rag_store = None
    pattern_learner = None
    variable_store = None

# Set dependencies for similarity match module
set_similarity_dependencies(rag_store, model, model_type, model_name)

# Initialize scanners
base_prompt_detection_scanner = BasePromptDetectionScanner(pattern_learner=pattern_learner)
anomaly_detection_scanner = AnomalyDetectionScanner(
    pattern_learner=pattern_learner,
    variable_store=variable_store
)

# Import similarity match module
from src.api.routes.similarity_match import (
    SimilarityMatchRequest, 
    SimilarityMatchResponse,
    router as similarity_router,
    similarity_match_check_function
)


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
        
        # Use base prompt detection scanner to perform detection
        detection_result, main_cluster_prompts = base_prompt_detection_scanner.detect(
            prompts=prompts,
            metadata=request.metadata if hasattr(request, 'metadata') else {}
        )
        
        # Send detection event to Kafka - consumer will handle all storage (variables, patterns, embeddings)
        # Include main_cluster_prompts so consumer can process them
        try:
            send_detection_event(
                prompts=prompts,
                base_prompt=detection_result.basePrompt,
                confidence=detection_result.confidence,
                anomalies=detection_result.anomalies,
                main_cluster_prompts=main_cluster_prompts,  # Include for consumer processing
                metadata=request.metadata if hasattr(request, 'metadata') else {}
            )
        except Exception as e:
            logger.warning(f"Failed to send detection event to Kafka: {str(e)}")
        
        return detection_result
        
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Error during detection: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Detection failed: {str(e)}")


@app.post("/anomaly-detection", response_model=AnomalyDetectionResponse)
def anomaly_detection_check(request: AnomalyDetectionRequest):
    """
    Pattern-based anomaly detection for a single prompt.
    
    Matches prompt against learned patterns to detect structural anomalies.
    Then checks similarity of each variable component against historical variables.
    This is Component 1: Pattern Matching based detection.
    
    INDEPENDENT SCORING: Uses pattern_risk + variable similarity risk.
    This is completely separate from similarity matching scoring.
    
    Performance optimizations:
    - Skips guardrail for short prompts (< MIN_TOKENS_FOR_GUARDRAIL)
    - Skips guardrail during cold start (< MIN_TRAFFIC_FOR_GUARDRAIL variables)
    - Uses embedding-based semantic similarity (works for any content type)
    - Batches embedding generation for better performance
    """
    try:
        # Use anomaly detection scanner to perform scan
        result = anomaly_detection_scanner.scan(
            prompt=request.prompt,
            base_prompt=request.base_prompt,
            metadata=request.metadata
        )
        return result
        
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Anomaly detection failed: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Anomaly detection failed: {str(e)}")


# Include similarity match router (endpoints will be at /similarity-match)
app.include_router(similarity_router)


@app.post("/guardrail", response_model=GuardrailResponse)
def guardrail_check(request: GuardrailRequest):
    """
    Combined guardrail check - returns both components separately.
    
    NOTE: Both components are treated as completely separate with independent scoring.
    This endpoint returns both results but does NOT combine the scores.
    
    For individual checks, use:
    - /anomaly-detection - Pattern matching only (independent scoring)
    - /similarity-match - Feedback loop similarity only (independent scoring)
    """
    try:
        prompt = request.prompt.strip()
        
        if not prompt:
            raise HTTPException(status_code=400, detail="Prompt cannot be empty")
        
        # Call both components independently
        # Component 1: Pattern-based anomaly detection
        anomaly_request = AnomalyDetectionRequest(prompt=prompt, metadata=request.metadata or {})
        anomaly_result = anomaly_detection_check(anomaly_request)
        
        # Component 2: Similarity matching
        similarity_request = SimilarityMatchRequest(prompt=prompt, metadata=request.metadata or {})
        similarity_result = similarity_match_check_function(
            request=similarity_request,
            rag_store=rag_store,
            embedding_model=model,
            model_type=model_type,
            model_name=model_name
        )
        
        # Combine details from both components
        request_id = str(uuid.uuid4())
        details = {
            "original_length": len(prompt),
            "request_id": request_id,
            "anomaly_detection": {
                "scanner_name": anomaly_result.scanner_name,
                "is_valid": anomaly_result.is_valid,
                "risk_score": anomaly_result.risk_score,
                "details": anomaly_result.details
            },
            "similarity_match": {
                "scanner_name": similarity_result.scanner_name,
                "is_valid": similarity_result.is_valid,
                "risk_score": similarity_result.risk_score,
                "details": similarity_result.details
            }
        }
        
        # Decision: Both must be valid, use max risk (most conservative)
        is_valid = anomaly_result.is_valid and similarity_result.is_valid
        max_risk = max(anomaly_result.risk_score, similarity_result.risk_score)
        
        return GuardrailResponse(
            scanner_name="AgentTrafficGuardrail",
            is_valid=is_valid,
            risk_score=float(max_risk),  # Use max risk (most conservative)
            sanitized_text=prompt,
            details=details
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error during guardrail check: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Guardrail check failed: {str(e)}")


@app.post("/feedback")
def submit_feedback(request: FeedbackRequest):
    """
    Submit feedback on a guardrail detection.
    
    Updates ChromaDB with feedback labels for similarity matching.
    
    Note: Feature flag check should be done in UI/Java layer before calling this endpoint.
    """
    
    try:
        if request.label not in ["true_positive", "false_positive", "benign"]:
            raise HTTPException(
                status_code=400,
                detail="Label must be one of: true_positive, false_positive, benign"
            )
        
        prompt = request.prompt.strip()
        if not prompt:
            raise HTTPException(status_code=400, detail="Prompt cannot be empty")
        
        feedback_id = str(uuid.uuid4())
        
        # Send feedback event to Kafka - consumer will handle all ChromaDB updates and storage
        try:
            send_feedback_event(
                prompt=prompt,
                label=request.label,
                request_id=feedback_id,
                pattern_id=request.pattern_id,  # Use pattern_id from request
                metadata=request.metadata
            )
        except Exception as e:
            logger.warning(f"Failed to send feedback event to Kafka: {str(e)}")
        
        return {
            "status": "success",
            "feedback_id": feedback_id,
            "pattern_id": request.pattern_id,  # Return pattern_id from request (consumer will save if needed)
            "message": f"Feedback '{request.label}' recorded successfully"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error processing feedback: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Feedback submission failed: {str(e)}")

@app.get("/health")
def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "model": "all-MiniLM-L6-v2"}



@app.on_event("shutdown")
def shutdown_event():
    """Cleanup on application shutdown."""
    close_producer()


if __name__ == "__main__":
    import uvicorn
    import os
    port = int(os.getenv("PORT", "8093"))
    uvicorn.run(app, host="0.0.0.0", port=port)
