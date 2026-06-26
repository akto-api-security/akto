"""Standalone FastAPI entrypoint for Docker, Kubernetes, and VM deployments."""

import asyncio
from contextlib import asynccontextmanager
from typing import Any, List

from fastapi import FastAPI
from pydantic import BaseModel, Field

import cascade_backpressure
from scan_handler import scan_payload, scanners_metadata
from settings import settings


class ScanRequest(BaseModel):
    scanner_name: str
    scanner_type: str = "prompt"
    text: str = ""
    config: dict[str, Any] = Field(default_factory=dict)


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings.init_from_env()
    cascade_backpressure.configure_from_env()
    yield


app = FastAPI(title="Akto Agent Guard Executor", version="1.0.0", lifespan=lifespan)


def _schedule_background(coro) -> None:
    asyncio.create_task(coro)


@app.get("/health")
def health():
    return {"status": "healthy", "service": "agent-guard-executor"}


@app.get("/scanners")
def scanners():
    return scanners_metadata()


@app.post("/scan")
async def scan(body: ScanRequest):
    return await scan_payload(body.model_dump(), schedule_fn=_schedule_background)


@app.post("/scan/batch")
async def scan_batch(body: List[ScanRequest]):
    return [
        await scan_payload(item.model_dump(), schedule_fn=_schedule_background) for item in body
    ]
