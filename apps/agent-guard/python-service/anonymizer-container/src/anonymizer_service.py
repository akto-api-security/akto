from typing import List, Optional

from fastapi import FastAPI
from presidio_analyzer import AnalyzerEngine
from presidio_anonymizer import AnonymizerEngine
from pydantic import BaseModel

app = FastAPI(title="Akto Anonymizer Service", version="1.0.0")

analyzer = AnalyzerEngine()
anonymizer = AnonymizerEngine()


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
    return {"ok": True}


@app.post("/anonymize", response_model=AnonymizeResponse)
def anonymize(req: AnonymizeRequest):
    results = analyzer.analyze(
        text=req.text,
        language=req.language,
        entities=req.entities,
    )
    anonymized = anonymizer.anonymize(text=req.text, analyzer_results=results)
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
