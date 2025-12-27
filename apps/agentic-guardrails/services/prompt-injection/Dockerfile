# Prompt Injection Detection Service Dockerfile
FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -u 1000 appuser

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY --chown=appuser:appuser service.py warmup.py ./

# Create cache directory for HuggingFace models
RUN mkdir -p /app/.cache/huggingface && \
    chown -R appuser:appuser /app/.cache

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8091
ENV HF_HOME=/app/.cache/huggingface
ENV TRANSFORMERS_VERBOSITY=error
ENV HF_HUB_VERBOSITY=error

# Switch to non-root user
USER appuser

# Pre-download models during build
RUN python warmup.py

# Expose the service port
EXPOSE 8091

# Run the FastAPI service
CMD ["python", "-m", "uvicorn", "service:app", "--host", "0.0.0.0", "--port", "8091"]
