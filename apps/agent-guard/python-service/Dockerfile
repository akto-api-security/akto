FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY scanner_service.py warmup.py ./

# Pre-download ONNX models (optional - uncomment to warm cache on build)
# RUN python warmup.py

# Expose port
EXPOSE 8092

# Run the application
CMD ["python", "scanner_service.py"]

