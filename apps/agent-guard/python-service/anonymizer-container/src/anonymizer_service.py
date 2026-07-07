"""Akto anonymizer — Presidio PII redaction.

Presidio's default AnalyzerEngine() pulls en_core_web_lg (~400MB). The image
bakes en_core_web_sm instead; we configure the NLP engine explicitly so workers
never pip-download at runtime (which was failing under parallel uvicorn startup).
"""

import logging
import os
import time
from contextlib import asynccontextmanager
from typing import List, Optional

import anonymizer_diag
import spacy
from fastapi import FastAPI, Request
from presidio_analyzer import AnalyzerEngine
from presidio_analyzer.nlp_engine import NlpEngineProvider
from presidio_anonymizer import AnonymizerEngine
from presidio_anonymizer.entities import OperatorConfig
from pydantic import BaseModel

SPACY_MODEL = os.environ.get("SPACY_MODEL", "en_core_web_sm")

REDACTION_PLACEHOLDER = "[REDACTED]"


def _build_analyzer() -> AnalyzerEngine:
    if not spacy.util.is_package(SPACY_MODEL):
        raise RuntimeError(
            f"spaCy model {SPACY_MODEL!r} is not installed. "
            "Bake it into the image at build time (see Dockerfile); "
            "runtime pip download is not supported."
        )
    nlp_configuration = {
        "nlp_engine_name": "spacy",
        "models": [{"lang_code": "en", "model_name": SPACY_MODEL}],
    }
    nlp_engine = NlpEngineProvider(nlp_configuration=nlp_configuration).create_engine()
    return AnalyzerEngine(nlp_engine=nlp_engine, supported_languages=["en"])


@asynccontextmanager
async def lifespan(_: FastAPI):
    anonymizer_diag.configure_process_logging()
    anonymizer_diag.log_startup_banner(spacy_model=SPACY_MODEL)
    yield


app = FastAPI(title="Akto Anonymizer Service", version="1.0.0", lifespan=lifespan)

analyzer = _build_analyzer()
anonymizer = AnonymizerEngine()


@app.middleware("http")
async def anonymize_inflight_middleware(request: Request, call_next):
    if request.url.path != "/anonymize":
        return await call_next(request)
    anonymizer_diag.track_inflight(entering=True)
    try:
        return await call_next(request)
    finally:
        anonymizer_diag.track_inflight(entering=False)


class AnonymizeRequest(BaseModel):
    text: str
    language: str = "en"
    entities: Optional[List[str]] = None


class EntityHit(BaseModel):
    entity_type: str
    start: int
    end: int
    score: float


class AnonymizeResponse(BaseModel):
    sanitized_text: str
    entities_found: List[EntityHit]


@app.get("/health")
def health():
    return {
        "ok": True,
        "spacy_model": SPACY_MODEL,
        "pid": os.getpid(),
        "inflight": anonymizer_diag.inflight_count(),
        "diagnostics": anonymizer_diag.diagnostics_enabled(),
        "log_level": logging.getLevelName(anonymizer_diag.resolve_log_level()),
    }


@app.post("/anonymize", response_model=AnonymizeResponse)
def anonymize(req: AnonymizeRequest, request: Request):
    started = time.perf_counter()
    client = request.client.host if request.client else None

    analyze_start = time.perf_counter()
    results = analyzer.analyze(
        text=req.text,
        language=req.language,
        entities=req.entities,
    )
    analyze_ms = (time.perf_counter() - analyze_start) * 1000

    anonymize_start = time.perf_counter()
    anonymized = anonymizer.anonymize(
        text=req.text,
        analyzer_results=results,
        operators={
            "DEFAULT": OperatorConfig("replace", {"new_value": REDACTION_PLACEHOLDER})
        },
    )
    anonymize_ms = (time.perf_counter() - anonymize_start) * 1000
    total_ms = (time.perf_counter() - started) * 1000

    anonymizer_diag.log_anonymize_complete(
        text_len=len(req.text),
        entity_count=len(results),
        analyze_ms=analyze_ms,
        anonymize_ms=anonymize_ms,
        total_ms=total_ms,
        client=client,
    )

    return AnonymizeResponse(
        sanitized_text=anonymized.text,
        entities_found=[
            EntityHit(
                entity_type=r.entity_type,
                start=r.start,
                end=r.end,
                score=r.score,
            )
            for r in results
        ],
    )
