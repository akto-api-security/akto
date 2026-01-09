FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY src/ ./src/

# Copy consumer script
COPY run_consumer.py .
RUN chmod +x run_consumer.py

# Create cache directory for HuggingFace models
RUN mkdir -p /app/.cache/huggingface

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8093
ENV HF_HOME=/app/.cache/huggingface
ENV TRANSFORMERS_VERBOSITY=error
ENV HF_HUB_VERBOSITY=error
# SERVICE_MODE is controlled by docker-compose (defaults to 'api' if not set)

# Pre-download model (non-fatal if it fails)
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('all-MiniLM-L6-v2')" || echo "Model pre-download failed, will download on first request."

EXPOSE 8093

# Run either API service or Kafka consumer based on SERVICE_MODE
# SERVICE_MODE=api: Run FastAPI service (default)
# SERVICE_MODE=consumer: Run Kafka consumer
CMD ["sh", "-c", "if [ \"$SERVICE_MODE\" = \"consumer\" ]; then python run_consumer.py; else python -m uvicorn src.api.agent_traffic_analyzer:app --host 0.0.0.0 --port ${PORT:-8093}; fi"]

