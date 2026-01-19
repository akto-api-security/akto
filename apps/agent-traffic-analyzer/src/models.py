"""
Pydantic models for Agent Traffic Analyzer API.
"""

from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
from src.config import MAX_PROMPTS_PER_REQUEST


class DetectionRequest(BaseModel):
    prompts: List[str] = Field(..., min_items=1, max_items=MAX_PROMPTS_PER_REQUEST)


class Anomaly(BaseModel):
    prompt: str
    userInput: str
    similarity: float


class DetectionResponse(BaseModel):
    basePrompt: str
    confidence: float
    anomalies: List[Anomaly] = []


class GuardrailRequest(BaseModel):
    prompt: str
    metadata: Optional[Dict[str, Any]] = {}


class GuardrailResponse(BaseModel):
    scanner_name: str
    is_valid: bool
    risk_score: float
    sanitized_text: str
    details: Dict[str, Any] = {}


class AnomalyDetectionRequest(BaseModel):
    prompt: str
    metadata: Optional[Dict[str, Any]] = {}
    base_prompt: Optional[str] = None  # Optional: If provided, skip pattern matching and use this pattern directly


class AnomalyDetectionResponse(BaseModel):
    scanner_name: str
    is_valid: bool
    risk_score: float
    sanitized_text: str
    details: Dict[str, Any] = {}


class FeedbackRequest(BaseModel):
    prompt: str
    label: str = Field(..., description="Feedback label: true_positive, false_positive, or benign")
    request_id: Optional[str] = None
    pattern_id: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = {}

