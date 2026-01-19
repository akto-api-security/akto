from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth, DualCache
from fastapi import HTTPException
from typing import Literal, Tuple
import httpx
import os
import json
import logging
import asyncio

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GUARDRAILS_URL = os.getenv("GUARDRAILS_BASE_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("GUARDRAILS_TIMEOUT", "5"))

class GuardrailsHandler(CustomLogger):
    def __init__(self):
        super().__init__()
        self.client = httpx.AsyncClient(
            timeout=TIMEOUT,
            limits=httpx.Limits(
                max_connections=100,
                max_keepalive_connections=20,
            ),
        )

        logger.info(
            f"GuardrailsHandler initialized | "
            f"sync_mode={SYNC_MODE}, timeout={TIMEOUT}"
        )

    async def async_pre_call_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        cache: DualCache,
        data: dict,
        call_type: Literal[
            "completion",
            "text_completion",
            "embeddings",
            "image_generation",
            "moderation",
            "audio_transcription",
        ],
    ) -> dict:
        if SYNC_MODE:
            return await self._validate_and_block(data, call_type)

        asyncio.create_task(
            self._validate_background(data, call_type)
        )
        return data

    async def _validate_and_block(self, data: dict, call_type: str) -> dict:
        try:
            allowed, _ = await self._call_guardrails(data, call_type)
            if not allowed:
                raise HTTPException(
                    status_code=400,
                    detail={
                        "error": "Blocked by guardrails",
                        "code": "GUARDRAILS_BLOCKED",
                    },
                )
            return data

        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"Guardrails error (sync): {e}")
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "Guardrails unavailable",
                    "code": "GUARDRAILS_ERROR",
                },
            )

    async def _validate_background(self, data: dict, call_type: str):
        try:
            allowed, _ = await self._call_guardrails(data, call_type)
            if not allowed:
                logger.warning("Guardrails violation detected (async)")
        except Exception as e:
            logger.error(f"Guardrails error (async): {e}")

    async def _call_guardrails(
        self,
        data: dict,
        call_type: str,
    ) -> Tuple[bool, str]:
        if not GUARDRAILS_URL:
            return True, ""

        query = ""
        if "messages" in data:
            for m in data["messages"]:
                content = m.get("content", "")
                if isinstance(content, list):
                    for item in content:
                        if item.get("type") == "text":
                            query += item.get("text", "") + " "
                elif isinstance(content, str):
                    query += content + " "
        else:
            query = data.get("prompt", "")

        query = query.strip()
        if not query:
            logger.info("No text content found in request; skipping guardrails validation.")
            return True, ""

        payload = {
            "query": query,
            "model": data.get("model", ""),
        }

        resp = await self.client.post(
            f"{GUARDRAILS_URL}/api/validate/request",
            json={
                "payload": json.dumps(payload),
                "call_type": call_type,
            },
        )

        if resp.status_code != 200:
            raise RuntimeError(f"Guardrails HTTP {resp.status_code}")

        result = resp.json()
        return (
            result.get("Allowed", result.get("allowed", True)),
            result.get("Reason", result.get("reason", "")),
        )

    async def async_on_shutdown(self):
        await self.client.aclose()
        logger.info("Guardrails client closed")

proxy_handler_instance = GuardrailsHandler()
