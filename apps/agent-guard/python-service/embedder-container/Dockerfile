FROM python:3.12-slim

WORKDIR /app

COPY src/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-bake the model into the image so the first request isn't blocked on a
# HuggingFace download (mirrors anonymizer-container's spacy download step).
ARG EMBED_MODEL=sentence-transformers/all-MiniLM-L6-v2
ENV EMBED_MODEL=${EMBED_MODEL}
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${EMBED_MODEL}')"

COPY src/ .

ENV PYTHONUNBUFFERED=1
ENV PORT=8094
EXPOSE 8094

CMD ["uvicorn", "embedder_service:app", "--host", "0.0.0.0", "--port", "8094"]
