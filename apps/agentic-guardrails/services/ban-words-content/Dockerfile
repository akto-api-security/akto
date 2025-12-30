# Ban Words & Content Filter Service Dockerfile
FROM python:3.11-slim

# Install system dependencies (lighter than other services)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
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

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8093

# Switch to non-root user
USER appuser

# Run warmup (lightweight, just prints info)
RUN python warmup.py

# Expose the service port
EXPOSE 8093

# Run the FastAPI service
CMD ["python", "-m", "uvicorn", "service:app", "--host", "0.0.0.0", "--port", "8093"]
