"""
Model Router Configuration
Maps scanners to their corresponding model services
"""
import os
from typing import Dict, List

# Scanner to service mapping
SCANNER_SERVICE_MAP: Dict[str, str] = {
    # Prompt Injection Detection Service
    "PromptInjection": "PROMPT_INJECTION",

    # Toxic Speech Detection Service
    "Toxicity": "TOXIC_SPEECH",
    "Bias": "TOXIC_SPEECH",

    # Ban Words & Content Filter Service
    "BanCode": "BAN_WORDS",
    "BanTopics": "BAN_WORDS",
    "BanCompetitors": "BAN_WORDS",
    "BanSubstrings": "BAN_WORDS",
    "Anonymize": "BAN_WORDS",
    "Deanonymize": "BAN_WORDS",
    "Secrets": "BAN_WORDS",
    "Code": "BAN_WORDS",
    "Language": "BAN_WORDS",
    "Gibberish": "BAN_WORDS",
    "TokenLimit": "BAN_WORDS",

    # Intent & Semantic Analysis Service
    "IntentAnalysis": "INTENT_ANALYSIS",
    "Sentiment": "INTENT_ANALYSIS",

    # Output Quality & Safety Service
    "Relevance": "OUTPUT_QUALITY",
    "NoRefusal": "OUTPUT_QUALITY",
    "MaliciousURLs": "OUTPUT_QUALITY",
    "Sensitive": "OUTPUT_QUALITY",
}

# Service endpoint configuration
def get_service_url(service_name: str) -> str:
    """Get service URL from environment variables"""
    env_var = f"{service_name}_URL"
    default_ports = {
        "PROMPT_INJECTION": "http://prompt-injection:8091",
        "TOXIC_SPEECH": "http://toxic-speech:8092",
        "BAN_WORDS": "http://ban-words-content:8093",
        "INTENT_ANALYSIS": "http://intent-analysis:8094",
        "OUTPUT_QUALITY": "http://output-quality:8095",
    }
    return os.getenv(env_var, default_ports.get(service_name, ""))

# Build service URL map
SERVICE_URLS: Dict[str, str] = {
    "PROMPT_INJECTION": get_service_url("PROMPT_INJECTION"),
    "TOXIC_SPEECH": get_service_url("TOXIC_SPEECH"),
    "BAN_WORDS": get_service_url("BAN_WORDS"),
    "INTENT_ANALYSIS": get_service_url("INTENT_ANALYSIS"),
    "OUTPUT_QUALITY": get_service_url("OUTPUT_QUALITY"),
}

# Health check configuration
HEALTH_CHECK_INTERVAL = int(os.getenv("HEALTH_CHECK_INTERVAL", "30"))  # seconds
HEALTH_CHECK_TIMEOUT = int(os.getenv("HEALTH_CHECK_TIMEOUT", "5"))  # seconds

# Request timeout configuration
REQUEST_TIMEOUT = int(os.getenv("REQUEST_TIMEOUT", "30"))  # seconds

# Retry configuration
MAX_RETRIES = int(os.getenv("MAX_RETRIES", "2"))
RETRY_DELAY = float(os.getenv("RETRY_DELAY", "0.5"))  # seconds

# Circuit breaker configuration
CIRCUIT_BREAKER_THRESHOLD = int(os.getenv("CIRCUIT_BREAKER_THRESHOLD", "5"))
CIRCUIT_BREAKER_TIMEOUT = int(os.getenv("CIRCUIT_BREAKER_TIMEOUT", "60"))  # seconds
