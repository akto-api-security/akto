"""
Anomaly Detection Scanner Service
Handles pattern-based anomaly detection for single prompts.
"""

import logging
import uuid
from typing import Optional, Dict, Any, Tuple

from src.models import AnomalyDetectionResponse
from src.config import (
    MIN_TRAFFIC_FOR_GUARDRAIL,
    MIN_TOKENS_FOR_GUARDRAIL,
    GUARDRAIL_RISK_THRESHOLD
)
from src.utils.utils import normalize_base_prompt
from src.utils.variable_utils import get_total_variables_for_pattern
from src.core.stores import VariableStore, PatternLearner
from src.api.routes.variable_similarity_checker import check_variable_similarity, calculate_final_variable_risk
from src.utils.embedding_utils import encode_text

logger = logging.getLogger(__name__)


class AnomalyDetectionScanner:
    """
    Scanner that performs pattern-based anomaly detection for single prompts.
    
    Matches prompt against learned patterns to detect structural anomalies.
    Then checks similarity of each variable component against historical variables.
    """

    def __init__(
        self,
        pattern_learner: Optional[PatternLearner] = None,
        variable_store: Optional[VariableStore] = None
    ):
        """
        Initialize anomaly detection scanner.
        
        Args:
            pattern_learner: Optional PatternLearner instance for pattern matching
            variable_store: Optional VariableStore instance for variable similarity checking
        """
        self.pattern_learner = pattern_learner
        self.variable_store = variable_store
        logger.info("AnomalyDetectionScanner initialized")

    def scan(
        self,
        prompt: str,
        base_prompt: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> AnomalyDetectionResponse:
        """
        Scan a single prompt for anomalies using pattern matching and variable similarity.
        
        Args:
            prompt: Input prompt to scan
            base_prompt: Optional base prompt pattern (if provided, skip pattern matching)
            metadata: Optional metadata for the request
            
        Returns:
            AnomalyDetectionResponse with scan results
        """
        prompt = prompt.strip()
        
        if not prompt:
            raise ValueError("Prompt cannot be empty")

        # Check minimum tokens - skip guardrail for very short prompts
        tokens = prompt.split()
        if len(tokens) < MIN_TOKENS_FOR_GUARDRAIL:
            return AnomalyDetectionResponse(
                scanner_name="PatternBasedAnomalyDetection",
                is_valid=True,
                risk_score=0.1,
                sanitized_text=prompt,
                details={
                    "original_length": len(prompt),
                    "token_count": len(tokens),
                    "guardrail_skipped": True,
                    "skip_reason": f"Prompt too short ({len(tokens)} tokens < {MIN_TOKENS_FOR_GUARDRAIL})",
                    "pattern_matched": False
                }
            )

        # Initialize response details
        details = {
            "original_length": len(prompt),
            "token_count": len(tokens),
            "pattern_matched": False,
            "base_prompt": None,
            "pattern_source": None,  # "provided", "matched", or "provided_failed"
            "pattern_match_score": None,
            "variable_components": None,
            "variable_similarities": [],
            "variable_risks": [],
            "variable_details": [],
            "guardrail_skipped": False,
            "skip_reason": None
        }

        # Pattern matching logic
        matched_pattern = None
        extracted_vars = None
        pattern_risk = 0.8
        variable_risks = []
        variable_similarities = []
        variable_details = []

        # Helper function to create cold start response
        def create_cold_start_response(total_vars: int) -> AnomalyDetectionResponse:
            return AnomalyDetectionResponse(
                scanner_name="PatternBasedAnomalyDetection",
                is_valid=True,
                risk_score=0.1,
                sanitized_text=prompt,
                details={
                    **details,
                    "guardrail_skipped": True,
                    "skip_reason": f"Insufficient traffic data ({total_vars} variables < {MIN_TRAFFIC_FOR_GUARDRAIL})",
                    "total_variables_stored": total_vars
                }
            )

        # Check if base_prompt is provided in request
        if base_prompt and base_prompt.strip():
            provided_base_prompt = normalize_base_prompt(base_prompt.strip())

            if self.pattern_learner:
                match_score, extracted_vars = self.pattern_learner._match_tokens(
                    prompt.split(), provided_base_prompt.split()
                )

                if match_score > 0.0:
                    matched_pattern = provided_base_prompt  # Already normalized
                    pattern_risk = 0.3  # Increased from 0.2 to give more weight to pattern matching
                    details.update({
                        "pattern_matched": True,
                        "pattern_source": "provided",
                        "base_prompt": matched_pattern,
                        "variable_components": extracted_vars,
                        "pattern_match_score": match_score
                    })

                    # Check cold start (use normalized pattern)
                    total_vars = get_total_variables_for_pattern(matched_pattern, self.variable_store)
                    if total_vars < MIN_TRAFFIC_FOR_GUARDRAIL:
                        return create_cold_start_response(total_vars)
                else:
                    logger.warning(f"Failed to extract variables from provided base_prompt (match_score: {match_score:.3f})")
                    details.update({
                        "pattern_matched": False,
                        "pattern_source": "provided_failed",
                        "base_prompt": provided_base_prompt,  # Already normalized
                        "pattern_match_score": match_score
                    })
            else:
                logger.warning("Pattern learner not initialized")
                details.update({
                    "pattern_matched": False,
                    "pattern_source": "provided_failed",
                    "base_prompt": provided_base_prompt
                })
        else:
            # Use pattern matching logic
            if self.pattern_learner:
                match_result = self.pattern_learner.match_pattern(prompt)
                if match_result:
                    matched_pattern, extracted_vars = match_result
                    # Normalize matched pattern for consistent querying
                    normalized_matched_pattern = normalize_base_prompt(matched_pattern)
                    pattern_risk = 0.3  # Increased from 0.2 to give more weight to pattern matching
                    details.update({
                        "pattern_matched": True,
                        "pattern_source": "matched",
                        "base_prompt": normalized_matched_pattern,
                        "variable_components": extracted_vars,
                        "pattern_match_score": None
                    })

                    # Check cold start (use normalized pattern)
                    total_vars = get_total_variables_for_pattern(normalized_matched_pattern, self.variable_store)
                    matched_pattern = normalized_matched_pattern  # Update for variable checking
                    if total_vars < MIN_TRAFFIC_FOR_GUARDRAIL:
                        return create_cold_start_response(total_vars)
                else:
                    details["pattern_matched"] = False
            else:
                logger.warning("Pattern learner not initialized")

        # Check similarity for each variable component (works for both provided and matched patterns)
        if matched_pattern and self.variable_store and extracted_vars:
            # Use utility function to check variable similarity
            variable_risks, variable_similarities, variable_details = check_variable_similarity(
                extracted_vars=extracted_vars,
                matched_pattern=matched_pattern,
                variable_store=self.variable_store,
                encode_text_func=encode_text,
                store_variables=True,
                full_prompt=prompt
            )

        # Calculate final risk: pattern_risk (30%) + avg_variable_risk (70%)
        # Variable risk is more important as it detects actual malicious content
        if variable_risks:
            avg_variable_risk = calculate_final_variable_risk(variable_risks)
            final_risk = (pattern_risk * 0.3) + (avg_variable_risk * 0.7)
        else:
            final_risk = pattern_risk

        # Get total variables stored for the matched pattern (if any)
        total_vars = 0
        if matched_pattern:
            total_vars = get_total_variables_for_pattern(matched_pattern, self.variable_store)

        # Update final details
        request_id = str(uuid.uuid4())
        details.update({
            "variable_similarities": variable_similarities,
            "variable_risks": variable_risks,
            "variable_details": variable_details,
            "pattern_risk": pattern_risk,
            "final_risk": final_risk,
            "threshold": GUARDRAIL_RISK_THRESHOLD,
            "request_id": request_id,
            "total_variables_stored": total_vars
        })

        # Block if risk >= threshold (0.5 is considered risky and should be blocked)
        is_valid = final_risk < GUARDRAIL_RISK_THRESHOLD

        return AnomalyDetectionResponse(
            scanner_name="PatternBasedAnomalyDetection",
            is_valid=is_valid,
            risk_score=float(final_risk),
            sanitized_text=prompt,
            details=details
        )

