from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Literal, Tuple, Optional, Any
import httpx
import os
import logging
import asyncio

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

DATA_INGESTION_URL = os.getenv("DATA_INGESTION_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("TIMEOUT", "5"))
LITELLM_URL = os.getenv("LITELLM_URL")
LITELLM_PATH = os.getenv("LITELLM_PATH")


class GuardrailsHandler(CustomLogger):
    def __init__(self):
        super().__init__()
        self.client = httpx.AsyncClient(
            timeout=TIMEOUT,
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
        )
        self.background_tasks: set = set()
        logger.info(f"GuardrailsHandler initialized | sync_mode={SYNC_MODE}")

    def get_client(self) -> httpx.AsyncClient:
        return self.client

    async def handle_validation_hook(
        self,
        data: dict,
        call_type: str,
        user_api_key_dict: Optional[UserAPIKeyAuth],
    ) -> dict:
        if SYNC_MODE:
            return await self.validate_and_block(data, call_type, user_api_key_dict)
        
        task = asyncio.create_task(self.validate_background(data, call_type, user_api_key_dict))
        self.background_tasks.add(task)
        task.add_done_callback(self.background_tasks.discard)
        return data

    async def async_pre_call_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        data: dict,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
        **kwargs,
    ) -> dict:
        return await self.handle_validation_hook(data, call_type, user_api_key_dict)

    async def async_moderation_hook(
        self,
        data: dict,
        user_api_key_dict: UserAPIKeyAuth,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
        **kwargs,
    ) -> dict:
        return await self.handle_validation_hook(data, call_type, user_api_key_dict)

    async def validate_background(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth]) -> None:
        try:
            allowed, reason = await self.call_guardrails_validation(data, call_type, user_api_key_dict)
            if not allowed:
                logger.info(f"Guardrails violation detected (async pre-call, logged only): {reason}")
        except Exception as e:
            logger.error(f"Guardrails background validation error: {e}")

    async def async_log_success_event(self, kwargs: dict, response_obj: Any, start_time: Any, end_time: Any) -> None:
        try:
            litellm_params = kwargs.get("litellm_params", {})
            metadata = litellm_params.get("metadata", {})
            user_api_key_dict = metadata.get("user_api_key_dict")
            call_type = kwargs.get("call_type", "completion")
            
            data = {
                "model": kwargs.get("model", ""),
                "messages": kwargs.get("messages", []),
                "stream": kwargs.get("stream", False),
            }
            
            response_dict = response_obj.model_dump() if response_obj else None
            
            if SYNC_MODE:
                await self.ingest_data(data, call_type, response_dict, user_api_key_dict)
            else:
                await self.async_validate_and_ingest(data, call_type, response_dict, user_api_key_dict)
        except Exception as e:
            logger.error(f"Guardrails post-call error: {e}")

    async def validate_and_block(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> dict:
        try:
            allowed, reason = await self.call_guardrails_validation(data, call_type, user_api_key_dict)
            if not allowed:
                await self.ingest_blocked_request(data, call_type, reason, user_api_key_dict)
                raise HTTPException(
                    status_code=403,
                    detail={"error": "Blocked by Akto Guardrails"},
                )
            return data
        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"Guardrails validation error: {e}")
            raise HTTPException(status_code=503, detail={"error": "Guardrails unavailable"})

    async def async_validate_and_ingest(self, data: dict, call_type: str, response_dict: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> None:
        if not DATA_INGESTION_URL:
            return
        
        await self.ingest_data(data, call_type, response_dict, user_api_key_dict)
        
        try:
            payload = self.build_payload(data, call_type, response_dict, user_api_key_dict)
            url = f"{DATA_INGESTION_URL}/api/http-proxy"
            params = {"guardrails": "true", "akto_connector": "litellm"}
            
            resp = await self.get_client().post(url, params=params, json=payload)
            if resp.status_code == 200:
                result = resp.json()
                allowed = result.get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
                if not allowed:
                    reason = result.get("data", {}).get("guardrailsResult", {}).get("Reason", "")
                    logger.info(f"Response flagged by guardrails (async mode, logged only): {reason}")
        except Exception as e:
            logger.error(f"Guardrails async validation error: {e}")

    async def call_guardrails_validation(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> Tuple[bool, str]:
        if not DATA_INGESTION_URL:
            return True, ""
        
        payload = self.build_payload(data, call_type, None, user_api_key_dict)
        url = f"{DATA_INGESTION_URL}/api/http-proxy"
        params = {"guardrails": "true", "akto_connector": "litellm"}
        
        resp = await self.get_client().post(url, params=params, json=payload)
        if resp.status_code != 200:
            raise RuntimeError(f"Guardrails HTTP {resp.status_code}")
        
        result = resp.json()
        guardrails_result = result.get("data", {}).get("guardrailsResult", {})
        return guardrails_result.get("Allowed", True), guardrails_result.get("Reason", "")

    async def ingest_data(self, data: dict, call_type: str, response_dict: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> None:
        if not DATA_INGESTION_URL:
            return
        
        response_payload = {
            "body": response_dict,
            "headers": {"content-type": "application/json"},
            "statusCode": 200,
            "status": "OK"
        }
        
        payload = self.build_payload(data, call_type, response_payload, user_api_key_dict, wrap_response=False)
        url = f"{DATA_INGESTION_URL}/api/http-proxy"
        params = {"akto_connector": "litellm", "ingest_data": "true"}
        
        resp = await self.get_client().post(url, params=params, json=payload)
        if resp.status_code != 200:
            logger.error(f"Ingestion failed: HTTP {resp.status_code}")

    async def ingest_blocked_request(self, data: dict, call_type: str, reason: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> None:
        if not DATA_INGESTION_URL or not SYNC_MODE:
            return
            
        blocked_response = {
            "body": {"x-blocked-by": "Akto Proxy"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }
        
        payload = self.build_payload(data, call_type, blocked_response, user_api_key_dict, wrap_response=False)
        url = f"{DATA_INGESTION_URL}/api/http-proxy"
        params = {"akto_connector": "litellm", "ingest_data": "true"}
        
        try:
            await self.get_client().post(url, params=params, json=payload)
        except Exception as e:
            logger.error(f"Failed to ingest blocked request: {e}")

    def build_request_litellm_metadata(self, call_type: str, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> dict:
        request_metadata = {
            "call_type": call_type,
            "model": data.get("model", ""),
            "tag": {"gen-ai": "Gen AI"}
        }
        
        if user_api_key_dict:
            try:
                request_metadata["litellm_auth"] = {
                    "api_key": getattr(user_api_key_dict, "api_key", ""),
                    "key_alias": getattr(user_api_key_dict, "key_alias", None),
                    "user_id": getattr(user_api_key_dict, "user_id", None),
                    "team_id": getattr(user_api_key_dict, "team_id", None),
                    "org_id": getattr(user_api_key_dict, "org_id", None),
                    "models": getattr(user_api_key_dict, "models", []),
                    "blocked_models": getattr(user_api_key_dict, "blocked_models", []),
                    "permissions": getattr(user_api_key_dict, "permissions", {}),
                    "limits": {
                        "rpm_limit": getattr(user_api_key_dict, "rpm_limit", None),
                        "tpm_limit": getattr(user_api_key_dict, "tpm_limit", None),
                        "max_tokens": getattr(user_api_key_dict, "max_tokens", None),
                        "budget_limit": getattr(user_api_key_dict, "max_budget", None),
                        "budget_duration": getattr(user_api_key_dict, "budget_duration", None)
                    },
                    "usage": {
                        "request_count": getattr(user_api_key_dict, "request_count", 0),
                        "total_tokens": getattr(user_api_key_dict, "total_tokens", 0),
                        "spend": getattr(user_api_key_dict, "spend", 0.0),
                    },
                }
            except Exception as e:
                logger.error(f"Failed to enrich metadata: {e}")
        return request_metadata

    def build_payload(self, data: dict, call_type: str, response_obj: Optional[dict], user_api_key_dict: Optional[UserAPIKeyAuth] = None, wrap_response: bool = True) -> dict:
        data_clean = {
            "model": data.get("model", ""),
            "messages": data.get("messages", []),
            "stream": data.get("stream", False)
        }
        
        request_metadata = self.build_request_litellm_metadata(call_type, data, user_api_key_dict)

        if response_obj is None:
            response_payload = None
        elif wrap_response:
            response_payload = {
                "body": response_obj,
                "headers": {"content-type": "application/json"},
                "statusCode": 200,
                "status": "OK"
            }
        else:
            response_payload = response_obj

        return {
            "url": LITELLM_URL,
            "path": LITELLM_PATH,
            "request": {
                "method": "POST",
                "headers": {"content-type": "application/json"},
                "body": data_clean,
                "queryParams": {},
                "metadata": request_metadata
            },
            "response": response_payload
        }

    async def async_on_shutdown(self) -> None:
        if self.client:
            await self.client.aclose()
        logger.info("Guardrails client closed")

proxy_handler_instance = GuardrailsHandler()