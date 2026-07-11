FROM python:3.12-slim

WORKDIR /app

# Install deps before copying any source so the (slow) pip layer stays cached and
# only re-runs when the dependency list itself changes — not on every code edit.
RUN pip install --no-cache-dir \
    rsa pyasn1 httpx detect-secrets tiktoken fastapi 'uvicorn[standard]' pydantic \
    prometheus-client prometheus-fastapi-instrumentator

COPY pyproject.toml .
COPY src/ ./src/

ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app/src
ENV PORT=8090
# Needed for correct metrics across the multiple uvicorn workers below.
ENV PROMETHEUS_MULTIPROC_DIR=/tmp/prometheus_multiproc

EXPOSE 8090

# Cap workers to avoid CPU oversubscription under load (override with UVICORN_WORKERS).
CMD rm -rf "${PROMETHEUS_MULTIPROC_DIR}" && mkdir -p "${PROMETHEUS_MULTIPROC_DIR}" && \
    exec uvicorn app:app --host 0.0.0.0 --port 8090 --log-level info --workers ${UVICORN_WORKERS:-8}
