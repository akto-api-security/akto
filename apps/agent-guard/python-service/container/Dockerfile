FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY src/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY src/scanner_service.py src/warmup.py ./

# Pre-download ONNX models - required for Cloudflare containers (no persistent volumes)
# Note: Commented out due to torch/optimum version conflicts. Models will download on first request.
# RUN mkdir -p /app/.cache/huggingface && \
#     python warmup.py

# Expose port
EXPOSE 8092

# Run the application
CMD ["python", "scanner_service.py"]

