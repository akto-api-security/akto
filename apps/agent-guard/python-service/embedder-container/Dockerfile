FROM fedora:44

WORKDIR /app

RUN dnf update -y && dnf install -y python3 python3-pip && dnf clean all

COPY src/requirements.txt .
RUN python3 -m pip install --no-cache-dir --upgrade pip setuptools wheel
# CPU-only torch (no CUDA wheels) keeps the image small — MiniLM inference is
# CPU-bound and needs no GPU. Install it first so sentence-transformers reuses it.
RUN python3 -m pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && python3 -m pip install --no-cache-dir -r requirements.txt

# Pre-bake the model into a fixed path in the image (mirrors anonymizer-container's
# spacy download step). Loading from a path rather than the HF cache keeps it
# independent of $HOME / runAsUser. Swap models with --build-arg EMBED_MODEL=...
ARG EMBED_MODEL=sentence-transformers/all-MiniLM-L6-v2
ENV EMBED_MODEL=${EMBED_MODEL}
ENV EMBED_MODEL_PATH=/app/model
# save() writes model.safetensors as 0600, so open up read access for non-root
# users; drop the HF cache copy, which is now an unused duplicate.
RUN python3 -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${EMBED_MODEL}').save('${EMBED_MODEL_PATH}')" \
    && chmod -R a+rX ${EMBED_MODEL_PATH} \
    && rm -rf /root/.cache/huggingface
# Regional clusters have no internet: never contact the HF Hub at runtime, or
# every pod start blocks on hub requests that can only time out.
ENV HF_HUB_OFFLINE=1
ENV TRANSFORMERS_OFFLINE=1

COPY src/ .

ENV PYTHONUNBUFFERED=1
ENV PORT=8094
# Pin torch/BLAS to one thread PER worker so N uvicorn workers don't each spawn
# nproc compute threads and oversubscribe the CPU (workers × threads contention).
ENV OMP_NUM_THREADS=1
ENV MKL_NUM_THREADS=1
ENV TORCH_NUM_THREADS=1
EXPOSE 8094

# Multiple workers so embedding (the cache-miss CPU cost) spreads across cores
# instead of one. Each worker loads its own model (~torch+MiniLM), so default to
# a moderate 4; override with EMBEDDER_WORKERS.
CMD exec uvicorn embedder_service:app --host 0.0.0.0 --port 8094 --workers ${EMBEDDER_WORKERS:-4}
