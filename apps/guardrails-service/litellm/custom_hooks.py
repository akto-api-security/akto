from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth, DualCache
from fastapi import HTTPException
from typing import Literal
import httpx
import os
import json
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GUARDRAILS_URL = os.getenv("GUARDRAILS_BASE_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("GUARDRAILS_TIMEOUT", "10"))


class GuardrailsHandler(CustomLogger):
    
    def __init__(self):
        super().__init__()
        if not GUARDRAILS_URL:
            logger.error("GUARDRAILS_BASE_URL not set")
        else:
            logger.info(f"GuardrailsHandler: url={GUARDRAILS_URL}, sync_mode={SYNC_MODE}")

    # Sync mode: validate before LLM call
    async def async_pre_call_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        cache: DualCache,
        data: dict,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
    ) -> dict:
        if not SYNC_MODE:
            return data
        return await self._validate_and_block(data, call_type, fail_closed=True)

    # Async mode: validate in parallel with LLM call
    async def async_moderation_hook(self, data: dict):
        if SYNC_MODE:
            return
        await self._validate_and_block(data, "completion", fail_closed=False)

    async def _validate_and_block(self, data: dict, call_type: str, fail_closed: bool) -> dict:
        try:
            allowed, reason = await self._call_guardrails(data, call_type)
            if not allowed:
                logger.info(f"Blocked: {reason}")
                raise HTTPException(status_code=400, detail={"error": reason or "Blocked by guardrails", "code": "GUARDRAILS_BLOCKED"})
            return data
        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"Validation error: {e}")
            if fail_closed:
                raise HTTPException(status_code=503, detail={"error": "Guardrails unavailable", "code": "GUARDRAILS_ERROR"})
            return data

    async def _call_guardrails(self, data: dict, call_type: str) -> tuple[bool, str]:
        if not GUARDRAILS_URL:
            return True, ""
        
        # OpenAI format: messages[].content or prompt
        query = data.get("prompt", "")
        if "messages" in data:
            query = " ".join(m.get("content", "") for m in data["messages"] if isinstance(m.get("content"), str))
        
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            resp = await client.post(
                f"{GUARDRAILS_URL}/api/validate/request",
                json={"payload": json.dumps({"query": query, "model": data.get("model", "")}), "call_type": call_type}
            )
        
        if resp.status_code != 200:
            logger.error(f"Guardrails returned {resp.status_code}")
            return True, ""
        
        result = resp.json()
        allowed = result.get("Allowed", result.get("allowed", True))
        reason = result.get("Reason", result.get("reason", ""))
        logger.info(f"Guardrails: allowed={allowed}")
        return allowed, reason

    # Post-call: audit response
    async def async_post_call_success_hook(self, data: dict, user_api_key_dict: UserAPIKeyAuth, response):
        if not GUARDRAILS_URL:
            return
        try:
            # OpenAI format: response.choices[0].message.content
            content = response.choices[0].message.content if response.choices else ""
            if not content:
                return
            
            async with httpx.AsyncClient(timeout=TIMEOUT) as client:
                resp = await client.post(f"{GUARDRAILS_URL}/api/validate/response", json={"payload": content})
            
            if resp.status_code == 200:
                result = resp.json()
                if not result.get("Allowed", result.get("allowed", True)):
                    logger.info(f"Response flagged: {result.get('Reason', result.get('reason', ''))}")
        except Exception as e:
            logger.error(f"Response validation error: {e}")


proxy_handler_instance = GuardrailsHandler()
