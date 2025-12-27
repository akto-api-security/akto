# Model Router Service Dockerfile
# Lightweight routing service with no ML models
FROM python:3.11-slim

# Create non-root user
RUN useradd -m -u 1000 appuser

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY --chown=appuser:appuser router_service.py config.py ./

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV PORT=8090

# Switch to non-root user
USER appuser

# Expose the service port
EXPOSE 8090

# Run the FastAPI service
CMD ["python", "-m", "uvicorn", "router_service:app", "--host", "0.0.0.0", "--port", "8090"]
