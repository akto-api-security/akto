from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Literal, Tuple, Optional, Any
import httpx
import os
import logging

logger = logging.getLogger(__name__)

DATA_INGESTION_URL = os.getenv("DATA_INGESTION_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("TIMEOUT", "5"))
LITELLM_URL = os.getenv("LITELLM_URL")
AKTO_CONNECTOR_NAME = "litellm"
HTTP_PROXY_PATH = "/api/http-proxy"


class GuardrailsHandler(CustomLogger):
    def __init__(self):
        super().__init__()
        self.client = httpx.AsyncClient(
            timeout=TIMEOUT,
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
        )
        logger.info(f"GuardrailsHandler initialized | sync_mode={SYNC_MODE}")

    def build_http_proxy_params(self, *, guardrails: bool, ingest_data: bool) -> dict:
        params = {"akto_connector": AKTO_CONNECTOR_NAME}
        if guardrails:
            params["guardrails"] = "true"
        if ingest_data:
            params["ingest_data"] = "true"
        return params

    async def post_http_proxy(self, *, guardrails: bool, ingest_data: bool, http_proxy_payload: dict) -> httpx.Response:
        endpoint = f"{DATA_INGESTION_URL}{HTTP_PROXY_PATH}"
        return await self.client.post(
            endpoint,
            params=self.build_http_proxy_params(guardrails=guardrails, ingest_data=ingest_data),
            json=http_proxy_payload,
        )

    def parse_guardrails_result(self, result: Any) -> Tuple[bool, str]:
        if not isinstance(result, dict):
            return True, ""
        guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
        return guardrails_result.get("Allowed", True), guardrails_result.get("Reason", "")

    def extract_request_path(self, kwargs: Optional[dict] = None) -> str:
        """Extract the request path from kwargs or fall back to env variable."""
        fallback = "/v1/chat/completions"
        try:
            if kwargs is not None:
                litellm_params = kwargs.get("litellm_params", {})

                # Method 1: Try to get the path from metadata.user_api_key_request_route (most direct)
                metadata = litellm_params.get("metadata", {})
                request_route = metadata.get("user_api_key_request_route")
                if request_route:
                    logger.info(f"Extracted path from metadata.user_api_key_request_route: {request_route}")
                    return request_route

            logger.info(f"Using fallback path: {fallback}")
            return fallback
        except Exception as e:
            return fallback

    async def handle_validation_hook(
        self,
        data: dict,
        call_type: str,
        user_api_key_dict: Optional[UserAPIKeyAuth],
        kwargs: Optional[dict] = None,
    ) -> dict:
        if SYNC_MODE:
            return await self.validate_and_block(data, call_type, user_api_key_dict, kwargs)

        return data

    async def async_pre_call_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        data: dict,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
        **kwargs,
    ) -> dict:
        return await self.handle_validation_hook(data, call_type, user_api_key_dict, kwargs)

    async def async_moderation_hook(
        self,
        data: dict,
        user_api_key_dict: UserAPIKeyAuth,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
        **kwargs,
    ) -> dict:
        return await self.handle_validation_hook(data, call_type, user_api_key_dict, kwargs)

    async def async_log_success_event(self, kwargs: dict, response_obj: Any, start_time: Any, end_time: Any) -> None:
        try:
            litellm_params = kwargs.get("litellm_params", {})
            metadata = litellm_params.get("metadata", {})
            user_api_key_dict = metadata.get("user_api_key_dict")
            call_type = kwargs.get("call_type", "completion")

            request_data = {
                "model": kwargs.get("model", ""),
                "messages": kwargs.get("messages", []),
                "stream": kwargs.get("stream", False),
            }

            model_response_dict = response_obj.model_dump() if response_obj else None

            if SYNC_MODE:
                await self.ingest_data(request_data, call_type, model_response_dict, user_api_key_dict, kwargs)
            else:
                await self.async_validate_and_ingest(request_data, call_type, model_response_dict, user_api_key_dict, kwargs)
        except Exception as e:
            logger.error(f"Guardrails post-call error: {e}")

    async def validate_and_block(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> dict:
        try:
            allowed, reason = await self.call_guardrails_validation(data, call_type, user_api_key_dict, kwargs)
            if not allowed:
                await self.ingest_blocked_request(data, call_type, reason, user_api_key_dict, kwargs)
                raise HTTPException(
                    status_code=403,
                    detail={"error": "Blocked by Akto Guardrails"},
                )
            return data
        except HTTPException:
            logger.info(f"Guardrails validation failed: {e}")
            raise
        except Exception as e:
            logger.error(f"Guardrails validation error (fail-open): {e}")
            return data

    async def async_validate_and_ingest(self, data: dict, call_type: str, response_dict: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> None:
        if not DATA_INGESTION_URL:
            return

        try:
            http_proxy_payload = self.build_payload(data, call_type, response_dict, user_api_key_dict, wrap_response=True, kwargs=kwargs)
            response = await self.post_http_proxy(guardrails=True, ingest_data=True, http_proxy_payload=http_proxy_payload)
            if response.status_code == 200:
                allowed, reason = self.parse_guardrails_result(response.json())
                if not allowed:
                    logger.info(f"Response flagged by guardrails (async mode, logged only): {reason}")
        except Exception as e:
            logger.error(f"Guardrails async validation error: {e}")

    async def call_guardrails_validation(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> Tuple[bool, str]:
        if not DATA_INGESTION_URL:
            return True, ""

        http_proxy_payload = self.build_payload(data, call_type, None, user_api_key_dict, wrap_response=True, kwargs=kwargs)

        try:
            response = await self.post_http_proxy(guardrails=True, ingest_data=False, http_proxy_payload=http_proxy_payload)
            if response.status_code != 200:
                logger.info(f"Guardrails validation returned HTTP {response.status_code} (fail-open)")
                return True, ""

            return self.parse_guardrails_result(response.json())
        except (httpx.RequestError, httpx.TimeoutException, ValueError) as e:
            logger.info(f"Guardrails validation failed (fail-open): {e}")
            return True, ""
        except Exception as e:
            logger.error(f"Guardrails validation error (fail-open): {e}")
            return True, ""

    async def ingest_data(self, data: dict, call_type: str, response_dict: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> None:
        if not DATA_INGESTION_URL:
            return

        response_payload = {
            "body": response_dict,
            "headers": {"content-type": "application/json"},
            "statusCode": 200,
            "status": "OK"
        }

        http_proxy_payload = self.build_payload(data, call_type, response_payload, user_api_key_dict, wrap_response=False, kwargs=kwargs)
        response = await self.post_http_proxy(guardrails=False, ingest_data=True, http_proxy_payload=http_proxy_payload)
        if response.status_code != 200:
            logger.error(f"Ingestion failed: HTTP {response.status_code}")

    async def ingest_blocked_request(self, data: dict, call_type: str, reason: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> None:
        if not DATA_INGESTION_URL or not SYNC_MODE:
            return

        blocked_response = {
            "body": {"x-blocked-by": "Akto Proxy"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }

        http_proxy_payload = self.build_payload(data, call_type, blocked_response, user_api_key_dict, wrap_response=False, kwargs=kwargs)

        try:
            await self.post_http_proxy(guardrails=False, ingest_data=True, http_proxy_payload=http_proxy_payload)
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

    def build_payload(self, data: dict, call_type: str, response_obj: Optional[dict], user_api_key_dict: Optional[UserAPIKeyAuth] = None, wrap_response: bool = True, kwargs: Optional[dict] = None) -> dict:
        model_body_payload = {
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

        # Dynamically extract the request path from kwargs
        request_path = self.extract_request_path(kwargs)

        return {
            "url": LITELLM_URL,
            "path": request_path,
            "request": {
                "method": "POST",
                "headers": {"content-type": "application/json"},
                "body": model_body_payload,
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