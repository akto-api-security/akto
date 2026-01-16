"""
Kafka Consumer - Simple and straightforward
Reads events from Kafka and delegates to service classes for processing.
"""

import json
import logging
import os
import signal
import sys
from typing import Optional
from kafka import KafkaConsumer

# Import services
try:
    from src.services import DetectionStorageService, FeedbackService
    from src.core.stores import RAGStore, VariableStore, PatternLearner
except ImportError:
    sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
    from src.services import DetectionStorageService, FeedbackService
    from src.core.stores import RAGStore, VariableStore, PatternLearner

logger = logging.getLogger(__name__)

# Simple environment variable configuration
KAFKA_BROKER_URL = os.getenv("KAFKA_BROKER_URL", "localhost:9092")
KAFKA_TOPIC_FEEDBACK = os.getenv("KAFKA_TOPIC_FEEDBACK", "akto.agent-traffic.feedback")
KAFKA_TOPIC_DETECTION = os.getenv("KAFKA_TOPIC_DETECTION", "akto.agent-traffic.detection")
KAFKA_GROUP_ID = os.getenv("KAFKA_GROUP_ID", "agent-traffic-embedding-consumer")
KAFKA_ENABLED = os.getenv("KAFKA_ENABLED", "true").lower() == "true"
KAFKA_MAX_POLL_RECORDS = int(os.getenv("KAFKA_MAX_POLL_RECORDS", "100"))

_running = True
detection_service: Optional[DetectionStorageService] = None
feedback_service: Optional[FeedbackService] = None


def initialize_services():
    """Initialize service classes with stores."""
    global detection_service, feedback_service
    try:
        rag_store = RAGStore()
        variable_store = VariableStore()
        pattern_learner = PatternLearner()
        
        detection_service = DetectionStorageService(
            variable_store=variable_store,
            rag_store=rag_store,
            pattern_learner=pattern_learner
        )
        
        feedback_service = FeedbackService(rag_store=rag_store)
        
        logger.info("✅ Services initialized")
    except Exception as e:
        logger.error(f"Failed to initialize services: {e}")
        raise


def process_feedback_event(event: dict) -> None:
    """Process feedback event - delegates to FeedbackService."""
    if not feedback_service:
        logger.warning("Feedback service not initialized")
        return
    
    feedback_service.process_feedback(
        prompt=event.get("prompt", "").strip(),
        label=event.get("label", ""),
        request_id=event.get("request_id"),
        pattern_id=event.get("pattern_id"),
        metadata=event.get("metadata", {})
    )


def process_detection_event(event: dict) -> None:
    """Process detection event - delegates to DetectionStorageService."""
    if not detection_service:
        logger.warning("Detection service not initialized")
        return
    
    if event.get("event_type") == "detection":
        detection_service.process_detection(
            prompts=event.get("prompts", []),
            base_prompt=event.get("base_prompt", ""),
            confidence=event.get("confidence", 0.0),
            main_cluster_prompts=event.get("main_cluster_prompts", [])
        )


def signal_handler(sig, frame):
    """Handle shutdown signal."""
    global _running
    logger.info("Shutting down...")
    _running = False


def run():
    """Run Kafka consumer."""
    global _running
    
    if not KAFKA_ENABLED:
        logger.warning("Kafka is disabled")
        return
    
    # Initialize services
    initialize_services()
    
    # Create consumer
    try:
        consumer = KafkaConsumer(
            KAFKA_TOPIC_FEEDBACK,
            KAFKA_TOPIC_DETECTION,
            bootstrap_servers=KAFKA_BROKER_URL,
            group_id=KAFKA_GROUP_ID,
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            auto_offset_reset='latest',
            max_poll_records=KAFKA_MAX_POLL_RECORDS
        )
        logger.info(f"Kafka consumer started: {KAFKA_BROKER_URL}")
    except Exception as e:
        logger.error(f"Failed to create consumer: {e}")
        return
    
    # Signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # Consume messages
    try:
        while _running:
            message_pack = consumer.poll(timeout_ms=1000)
            
            for topic_partition, messages in message_pack.items():
                topic = topic_partition.topic
                for message in messages:
                    try:
                        event = message.value
                        if topic == KAFKA_TOPIC_FEEDBACK:
                            process_feedback_event(event)
                        elif topic == KAFKA_TOPIC_DETECTION:
                            process_detection_event(event)
                    except Exception as e:
                        logger.error(f"Error processing message: {e}")
    except KeyboardInterrupt:
        logger.info("Consumer interrupted")
    finally:
        consumer.close()
        logger.info("Consumer stopped")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    run()

