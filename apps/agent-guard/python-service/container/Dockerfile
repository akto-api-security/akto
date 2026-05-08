# Agent Guard Python Service Dockerfile
# Exposes ML model scanners for LLM guardrails
FROM python:3.11-slim

# Install system dependencies required for compilation
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY scanner_service.py .
COPY intent_analyzer.py .
COPY warmup.py .

# Create cache directory for HuggingFace models
RUN mkdir -p /app/.cache/huggingface

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8092
ENV HF_HOME=/app/.cache/huggingface
ENV TRANSFORMERS_VERBOSITY=error
ENV HF_HUB_VERBOSITY=error

# Pre-download all models during build (makes container work offline and first request fast)
RUN python warmup.py

# Expose the service port
EXPOSE 8092

# Run the FastAPI service
CMD ["python", "-m", "uvicorn", "scanner_service:app", "--host", "0.0.0.0", "--port", "8092"]
