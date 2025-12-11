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

# Create cache directory for HuggingFace models
RUN mkdir -p /app/.cache/huggingface

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8093
ENV HF_HOME=/app/.cache/huggingface
ENV TRANSFORMERS_VERBOSITY=error
ENV HF_HUB_VERBOSITY=error

# Pre-download model (non-fatal if it fails)
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('all-MiniLM-L6-v2')" || echo "Model pre-download failed, will download on first request."

EXPOSE 8093

# Run uvicorn directly (PORT env var is already set, defaults to 8093)
CMD ["sh", "-c", "python -m uvicorn src.agent_traffic_analyzer:app --host 0.0.0.0 --port ${PORT:-8093}"]

