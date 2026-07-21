FROM python:3.12-slim

WORKDIR /app

COPY src/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src/ .

ENV PYTHONUNBUFFERED=1
ENV PORT=8095
EXPOSE 8095

# One instance per customer — traffic volume per tenant is far below the
# shared embedder-container's, so a single worker is the right default;
# override with CLASSIFIER_WORKERS if a tenant's load warrants more.
CMD exec uvicorn intent_classifier_service:app --host 0.0.0.0 --port 8095 --workers ${CLASSIFIER_WORKERS:-1}
