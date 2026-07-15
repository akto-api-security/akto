FROM python:3.12-slim

WORKDIR /app

# Install deps before copying any source so the (slow) pip layer stays cached and
# only re-runs when the dependency list itself changes — not on every code edit.
RUN pip install --no-cache-dir \
    rsa==4.9.1 pyasn1==0.6.3 httpx==0.28.1 detect-secrets==1.5.0 tiktoken==0.13.0 \
    fastapi==0.139.0 'uvicorn[standard]==0.50.2' pydantic==2.13.4 psutil==7.2.2

COPY pyproject.toml .
COPY src/ ./src/

ENV PYTHONUNBUFFERED=1
ENV PYTHONPATH=/app/src
ENV PORT=8090

EXPOSE 8090

# Cap workers to avoid CPU oversubscription under load (override with UVICORN_WORKERS).
CMD exec uvicorn app:app --host 0.0.0.0 --port 8090 --log-level info --workers ${UVICORN_WORKERS:-8}
