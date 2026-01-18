"""
Configuration constants and environment variables for Agent Traffic Analyzer.
"""

import os

# Request limits
MAX_PROMPTS_PER_REQUEST = 2000

# Detection thresholds
INTENT_THRESHOLD = 0.45  # Threshold for intent anomaly detection
MIN_CONFIDENCE_THRESHOLD = 0.51  # Minimum confidence to accept base prompt (51% of prompts in main cluster)
# With k=2 clustering, the larger cluster will always be >= 50%, so 0.51 ensures we capture it
GUARDRAIL_RISK_THRESHOLD = 0.5  # Risk threshold for guardrail (0.0-1.0)

# Configurable thresholds (can be overridden via environment variables)
# Lower values for testing/demo, higher values for production
MIN_TRAFFIC_FOR_GUARDRAIL = int(os.getenv("MIN_TRAFFIC_FOR_GUARDRAIL", "30"))  # Default: 30 (was 50)
# Reason: 30 variables = ~5 prompts with 6 variables each, enough for reliable similarity matching
# Lower from 50 to 30 to reduce cold start period while maintaining accuracy

MIN_TOKENS_FOR_GUARDRAIL = int(os.getenv("MIN_TOKENS_FOR_GUARDRAIL", "5"))  # Default: 5 (was 8)
# Reason: 5 tokens allows short but meaningful prompts like "Book flight to Paris" (5 tokens)
# Lower from 8 to 5 to reduce false skips while still filtering very short prompts

# Performance optimization limits
MAX_PROMPTS_FOR_VARIABLE_STORAGE = 20  # Maximum prompts to process for variable storage
MAX_PROMPTS_FOR_RAG_STORAGE = 10  # Maximum prompts to process for RAG pattern storage

