"""
Kafka Producer - Simple and straightforward
Sends events to Kafka topics.
"""

import json
import logging
import os
from typing import Dict, Any, Optional
from datetime import datetime
from kafka import KafkaProducer
from kafka.errors import KafkaError

logger = logging.getLogger(__name__)

# Simple environment variable configuration
KAFKA_BROKER_URL = os.getenv("KAFKA_BROKER_URL", "localhost:9092")
KAFKA_TOPIC_FEEDBACK = os.getenv("KAFKA_TOPIC_FEEDBACK", "akto.agent-traffic.feedback")
KAFKA_TOPIC_DETECTION = os.getenv("KAFKA_TOPIC_DETECTION", "akto.agent-traffic.detection")
KAFKA_ENABLED = os.getenv("KAFKA_ENABLED", "true").lower() == "true"

# Global producer instance
_producer: Optional[KafkaProducer] = None


def get_producer() -> Optional[KafkaProducer]:
    """Get or create Kafka producer."""
    global _producer
    
    if not KAFKA_ENABLED:
        return None
    
    if _producer is not None:
        return _producer
    
    try:
        _producer = KafkaProducer(
            bootstrap_servers=KAFKA_BROKER_URL,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            acks="all",
            retries=3
        )
        logger.info(f"Kafka producer initialized: {KAFKA_BROKER_URL}")
        return _producer
    except Exception as e:
        logger.error(f"Failed to initialize Kafka producer: {e}")
        return None


def send_feedback(prompt: str, label: str, request_id: Optional[str] = None, 
                  pattern_id: Optional[str] = None, metadata: Optional[Dict[str, Any]] = None) -> bool:
    """Send feedback event to Kafka - non-blocking."""
    if not KAFKA_ENABLED:
        return False
    
    producer = get_producer()
    if not producer:
        return False
    
    try:
        event = {
            "event_type": "feedback",
            "timestamp": datetime.utcnow().isoformat(),
            "prompt": prompt,
            "label": label,
            "request_id": request_id,
            "pattern_id": pattern_id,
            "metadata": metadata or {}
        }
        
        key = pattern_id or request_id or "unknown"
        # Non-blocking send - fire and forget
        producer.send(KAFKA_TOPIC_FEEDBACK, key=key, value=event)
        logger.debug(f"Feedback event queued: {label}")
        return True
    except Exception as e:
        logger.error(f"Failed to send feedback event: {e}")
        return False


def send_detection(prompts: list, base_prompt: str, confidence: float, 
                  anomalies: list, main_cluster_prompts: Optional[list] = None,
                  metadata: Optional[Dict[str, Any]] = None) -> bool:
    """Send detection event to Kafka - non-blocking."""
    if not KAFKA_ENABLED:
        return False
    
    producer = get_producer()
    if not producer:
        return False
    
    try:
        event = {
            "event_type": "detection",
            "timestamp": datetime.utcnow().isoformat(),
            "prompts": prompts,
            "base_prompt": base_prompt,
            "confidence": confidence,
            "main_cluster_prompts": main_cluster_prompts or [],  # Include for consumer processing
            "anomalies": [
                {
                    "prompt": a.prompt if hasattr(a, 'prompt') else a.get('prompt', ''),
                    "userInput": a.userInput if hasattr(a, 'userInput') else a.get('userInput', ''),
                    "similarity": a.similarity if hasattr(a, 'similarity') else a.get('similarity', 0.0)
                }
                for a in anomalies
            ],
            "metadata": metadata or {}
        }
        
        import hashlib
        key = hashlib.md5(base_prompt.encode()).hexdigest()
        # Non-blocking send - fire and forget
        producer.send(KAFKA_TOPIC_DETECTION, key=key, value=event)
        logger.debug(f"Detection event queued: {base_prompt[:50]}...")
        return True
    except Exception as e:
        logger.error(f"Failed to send detection event: {e}")
        return False


def send_guardrail(prompt: str, is_valid: bool, risk_score: float, 
                  scanner_name: str, details: Dict[str, Any], 
                  metadata: Optional[Dict[str, Any]] = None) -> bool:
    """Send guardrail event to Kafka."""
    if not KAFKA_ENABLED:
        return False
    
    producer = get_producer()
    if not producer:
        return False
    
    try:
        event = {
            "event_type": "guardrail",
            "timestamp": datetime.utcnow().isoformat(),
            "prompt": prompt,
            "is_valid": is_valid,
            "risk_score": risk_score,
            "scanner_name": scanner_name,
            "details": details,
            "metadata": metadata or {}
        }
        
        key = details.get("request_id")
        if not key:
            import hashlib
            key = hashlib.md5(prompt.encode()).hexdigest()
        
        future = producer.send(KAFKA_TOPIC_DETECTION, key=key, value=event)
        future.get(timeout=10)
        logger.debug(f"Guardrail event sent: valid={is_valid}, risk={risk_score:.2f}")
        return True
    except Exception as e:
        logger.error(f"Failed to send guardrail event: {e}")
        return False


def close():
    """Close Kafka producer."""
    global _producer
    if _producer:
        try:
            _producer.close()
            logger.info("Kafka producer closed")
        except Exception as e:
            logger.error(f"Error closing producer: {e}")
        finally:
            _producer = None

