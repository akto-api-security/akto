FROM python:3.12-slim

WORKDIR /app

# Install deps before copying any source so the (slow) pip layer stays cached and
# only re-runs when the dependency list itself changes — not on every code edit.
RUN pip install --no-cache-dir \
    rsa pyasn1 httpx redis detect-secrets tiktoken fastapi 'uvicorn[standard]' pydantic

COPY pyproject.toml .
COPY src/ ./src/

ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app/src
ENV PORT=8090

EXPOSE 8090

# Run one uvicorn worker per CPU core. A single process pins the cache-serve path
# to one core (the rest of the box sits idle) — the per-instance throughput
# ceiling. The worker is stateless (state lives in Redis), so workers parallelize
# cleanly. Override with UVICORN_WORKERS. `exec` makes uvicorn PID 1 for signals.
CMD exec uvicorn app:app --host 0.0.0.0 --port 8090 --workers ${UVICORN_WORKERS:-$(nproc)}
