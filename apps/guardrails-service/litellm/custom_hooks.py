from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Literal, Tuple, Optional
import httpx
import os
import json
import logging
import asyncio

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GUARDRAILS_URL = os.getenv("GUARDRAILS_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("GUARDRAILS_TIMEOUT", "5"))
LITELLM_URL = os.getenv("LITELLM_URL", "http://localhost:4000")
LITELLM_PATH = os.getenv("LITELLM_PATH", "/chat/completions")


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
        logger.info(f"GuardrailsHandler initialized | sync_mode={SYNC_MODE}, timeout={TIMEOUT}")

    async def async_pre_call_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
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
            return await self._validate_and_block(data, call_type, user_api_key_dict)

        asyncio.create_task(self._validate_background(data, call_type, user_api_key_dict))
        return data

    async def async_post_call_success_hook(
        self,
        data: dict,
        user_api_key_dict: UserAPIKeyAuth,
        response,
    ):
        try:
            allowed, reason = await self._call_guardrails(data, "completion", user_api_key_dict, response_obj=response)
            if not allowed:
                raise HTTPException(
                    status_code=400,
                    detail={
                        "error": f"Response blocked by guardrails: {reason}",
                        "code": "GUARDRAILS_RESPONSE_BLOCKED",
                    },
                )
        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"Guardrails post-call error: {e}")
            raise HTTPException(
                status_code=503,
                detail={
                    "error": "Guardrails unavailable",
                    "code": "GUARDRAILS_ERROR",
                },
            )

    async def _validate_and_block(self, data: dict, call_type: str, user_api_key_dict: UserAPIKeyAuth) -> dict:
        try:
            allowed, _ = await self._call_guardrails(data, call_type, user_api_key_dict)
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

    async def _validate_background(self, data: dict, call_type: str, user_api_key_dict: UserAPIKeyAuth):
        try:
            allowed, _ = await self._call_guardrails(data, call_type, user_api_key_dict)
            if not allowed:
                logger.info("Guardrails violation detected (async)")
        except Exception as e:
            logger.error(f"Guardrails error (async): {e}")

    async def _call_guardrails(
        self,
        data: dict,
        call_type: str,
        user_api_key_dict: UserAPIKeyAuth,
        response_obj: Optional[dict] = None,
    ) -> Tuple[bool, str]:
        if not GUARDRAILS_URL:
            return True, ""

        request_body = json.dumps(data)

        auth_metadata = {}
        if user_api_key_dict is not None:
            auth_metadata = {
                "user_id": getattr(user_api_key_dict, "user_id", None),
                "user_role": getattr(user_api_key_dict, "user_role", None),
                "team_id": getattr(user_api_key_dict, "team_id", None),
                "team_metadata": getattr(user_api_key_dict, "team_metadata", None),
                "end_user_id": getattr(user_api_key_dict, "end_user_id", None),
                "key_alias": getattr(user_api_key_dict, "key_alias", None),
                "allowed_model_region": getattr(user_api_key_dict, "allowed_model_region", None),
            }
            auth_metadata = {k: v for k, v in auth_metadata.items() if v is not None}

        if response_obj is None:
            response_payload = None
        else:
            response_payload = {
                "headers": {},
                "body": json.dumps(response_obj),
                "protocol": "HTTP/1.1",
                "statusCode": 200,
                "status": "SUCCESS",
                "metadata": auth_metadata
            }

        payload = {
            "url": LITELLM_URL,
            "path": LITELLM_PATH,
            "request": {
                "method": "POST",
                "headers": {},
                "body": request_body,
                "queryParams": {},
                "metadata": {
                    "call_type": call_type,
                    "model": data.get("model", ""),
                    **auth_metadata
                }
            },
            "response": response_payload
        }

        params = {
            "enabled_guardrails": "true",
            "ingest_data": "true",
            "validate_response": "true",
            "akto_connector": "lightllm"
        }

        url = f"{GUARDRAILS_URL}/api/http-proxy"

        resp = await self.client.post(
            url,
            params=params,
            json=payload,
        )

        if resp.status_code != 200:
            raise RuntimeError(f"Guardrails HTTP {resp.status_code}")

        result = resp.json()
        return (
            result.get("allowed", True),
            result.get("reason", ""),
        )

    async def async_on_shutdown(self):
        await self.client.aclose()
        logger.info("Guardrails client closed")


proxy_handler_instance = GuardrailsHandler()
