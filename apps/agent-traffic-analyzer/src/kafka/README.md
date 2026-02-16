# Kafka Module

Simple Kafka integration for agent traffic analyzer.

## Files

- `producer.py` - Kafka producer for sending events
- `consumer.py` - Kafka consumer for processing events

## Usage

### Producer

```python
from src.kafka.producer import send_feedback, send_detection, send_guardrail

# Send feedback
send_feedback(prompt="...", label="false_positive", metadata={...})

# Send detection
send_detection(prompts=[...], base_prompt="...", confidence=0.9, anomalies=[])

# Send guardrail
send_guardrail(prompt="...", is_valid=True, risk_score=0.3, scanner_name="...", details={...})
```

### Consumer

```bash
# Run consumer
python run_consumer.py

# Or directly
python -m src.kafka.consumer
```

## Configuration

All configuration via environment variables:

- `KAFKA_BROKER_URL` - Kafka broker (default: localhost:9092)
- `KAFKA_TOPIC_FEEDBACK` - Feedback topic
- `KAFKA_TOPIC_DETECTION` - Detection topic
- `KAFKA_GROUP_ID` - Consumer group ID
- `KAFKA_ENABLED` - Enable/disable Kafka (default: true)
- `KAFKA_MAX_POLL_RECORDS` - Max records per poll (default: 100)

Simple and straightforward - no config files needed!

