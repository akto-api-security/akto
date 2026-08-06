FROM fedora:44

WORKDIR /app

# Pinned to python3.13 (not fedora's default python3/3.14) because spacy has never published a cp314 wheel.
RUN dnf update -y && dnf install -y python3.13 && dnf clean all && \
    python3.13 -m ensurepip --upgrade

COPY src/requirements.txt .
RUN python3.13 -m pip install --no-cache-dir --upgrade pip setuptools wheel \
    && python3.13 -m pip install --no-cache-dir -r requirements.txt

# Presidio defaults to en_core_web_lg; bake the small model and pin via SPACY_MODEL.
ENV SPACY_MODEL=en_core_web_sm
RUN python3.13 -m spacy download ${SPACY_MODEL}

COPY src/ .

# Fail the image build if Presidio cannot load the baked model (no runtime pip).
RUN python3.13 -c "from anonymizer_service import analyzer; print('presidio ready', analyzer)"

ENV PYTHONUNBUFFERED=1
ENV PORT=8093
# Avoid BLAS thread oversubscription when multiple uvicorn workers run spaCy.
ENV OMP_NUM_THREADS=1
ENV MKL_NUM_THREADS=1
ENV OPENBLAS_NUM_THREADS=1
# Runtime tuning (visible in `docker exec … env`; override in compose if needed).
ENV ANONYMIZER_WORKERS=4
ENV ANONYMIZER_LOG_LEVEL=info
ENV ANONYMIZER_DIAGNOSTICS=true
ENV ANONYMIZER_SLOW_MS=200
EXPOSE 8093

# Multiple workers so anonymization isn't pinned to one core. Each worker loads
# its own spaCy model (small); default 4, override with ANONYMIZER_WORKERS.
CMD exec python3.13 -m uvicorn anonymizer_service:app --host 0.0.0.0 --port 8093 --log-level info --workers ${ANONYMIZER_WORKERS:-4}
