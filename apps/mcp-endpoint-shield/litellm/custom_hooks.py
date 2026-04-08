from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Literal, Tuple, Optional, Any
import httpx
import json
import os
import re
import logging
from datetime import datetime, timezone
from urllib.parse import urlparse

logger = logging.getLogger(__name__)

DATA_INGESTION_SERVICE_URL = os.getenv("DATA_INGESTION_SERVICE_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("TIMEOUT", "5"))
LITELLM_URL = os.getenv("LITELLM_URL", "http://localhost:4000")
AKTO_CONNECTOR_NAME = "litellm"
HTTP_PROXY_PATH = "/api/http-proxy"

INVALID_AGENT_CHARS = re.compile(r"[^a-z0-9\-._]")


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
        endpoint = f"{DATA_INGESTION_SERVICE_URL}{HTTP_PROXY_PATH}"
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
        fallback = "/chat/completions"
        try:
            if kwargs is not None:
                litellm_params = kwargs.get("litellm_params", {})

                metadata = litellm_params.get("metadata", {})
                request_route = metadata.get("user_api_key_request_route")
                if request_route:
                    logger.info(f"Extracted path from metadata.user_api_key_request_route: {request_route}")
                    return request_route

            logger.info(f"Using fallback path: {fallback}")
            return fallback
        except Exception as e:
            return fallback

    def sanitize_agent_name(self, name: str) -> Optional[str]:
        name = INVALID_AGENT_CHARS.sub("-", name.strip().lower())
        return name[:200] or None

    def extract_agent_name(self, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> Optional[str]:
        metadata = data.get("metadata") or (kwargs.get("litellm_params", {}) if kwargs else {}).get("metadata") or {}

        # 1. metadata.agent_name
        agent = metadata.get("agent_name")
        if agent:
            return self.sanitize_agent_name(str(agent))

        # 2. user_api_key_alias / user_api_key_team_alias
        for key in ("user_api_key_alias", "user_api_key_team_alias"):
            value = metadata.get(key)
            if value:
                return self.sanitize_agent_name(str(value))

        return None

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
                    detail=f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails",
                )
            return data
        except HTTPException as e:
            logger.info(f"Guardrails validation failed: {e}")
            raise
        except Exception as e:
            logger.error(f"Guardrails validation error (fail-open): {e}")
            return data

    async def async_validate_and_ingest(self, data: dict, call_type: str, response_dict: Optional[dict], user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> None:
        if not DATA_INGESTION_SERVICE_URL:
            return

        try:
            http_proxy_payload = self.build_payload(data, call_type, response_dict, user_api_key_dict, status_code=200, kwargs=kwargs)
            response = await self.post_http_proxy(guardrails=True, ingest_data=True, http_proxy_payload=http_proxy_payload)
            if response.status_code == 200:
                allowed, reason = self.parse_guardrails_result(response.json())
                if not allowed:
                    logger.info(f"Response flagged by guardrails (async mode, logged only): {reason}")
        except Exception as e:
            logger.error(f"Guardrails async validation error: {e}")

    async def call_guardrails_validation(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> Tuple[bool, str]:
        if not DATA_INGESTION_SERVICE_URL:
            return True, ""

        http_proxy_payload = self.build_payload(data, call_type, None, user_api_key_dict, kwargs=kwargs)

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

    async def ingest_response(
        self,
        data: dict,
        call_type: str,
        response_body: Any,
        status_code: int,
        user_api_key_dict: Optional[UserAPIKeyAuth] = None,
        kwargs: Optional[dict] = None,
        *,
        log_http_error: bool = False,
    ) -> None:
        if not DATA_INGESTION_SERVICE_URL:
            return

        http_proxy_payload = self.build_payload(
            data,
            call_type,
            response_body,
            user_api_key_dict,
            status_code=status_code,
            kwargs=kwargs,
        )

        try:
            response = await self.post_http_proxy(guardrails=False, ingest_data=True, http_proxy_payload=http_proxy_payload)
            if log_http_error and response.status_code != 200:
                logger.error(f"Ingestion failed: HTTP {response.status_code}")
        except Exception as e:
            logger.error(f"Ingestion failed: {e}")

    async def ingest_data(self, data: dict, call_type: str, response_dict: Optional[dict], user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> None:
        await self.ingest_response(
            data,
            call_type,
            response_dict,
            200,
            user_api_key_dict,
            kwargs,
            log_http_error=True,
        )

    async def ingest_blocked_request(self, data: dict, call_type: str, reason: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> None:
        if not DATA_INGESTION_SERVICE_URL or not SYNC_MODE:
            return

        await self.ingest_response(
            data,
            call_type,
            {"x-blocked-by": "Akto Proxy", "reason": reason},
            403,
            user_api_key_dict,
            kwargs,
        )

    def build_tags(self, call_type: str, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> dict:
        tags = {"gen-ai": "Gen AI", "litellm": "LiteLLM"}
        if call_type:
            tags["call_type"] = call_type
        model = data.get("model", "")
        if model:
            tags["model"] = model
        if user_api_key_dict:
            try:
                key_alias = getattr(user_api_key_dict, "key_alias", None)
                team_id = getattr(user_api_key_dict, "team_id", None)
                user_id = getattr(user_api_key_dict, "user_id", None)
                if key_alias:
                    tags["key_alias"] = key_alias
                if team_id:
                    tags["team_id"] = team_id
                if user_id:
                    tags["user_id"] = user_id
            except Exception as e:
                logger.error(f"Failed to enrich tags: {e}")
        return tags

    def build_payload(self, data: dict, call_type: str, response_obj: Optional[Any], user_api_key_dict: Optional[UserAPIKeyAuth] = None, status_code: int = 200, kwargs: Optional[dict] = None) -> dict:
        request_body = {
            "model": data.get("model", ""),
            "messages": data.get("messages", []),
            "stream": data.get("stream", False),
        }

        request_path = self.extract_request_path(kwargs)
        tags = self.build_tags(call_type, data, user_api_key_dict)
        parsed = urlparse(LITELLM_URL) if LITELLM_URL else None
        hosted_url = parsed.netloc if parsed and parsed.netloc else "localhost:4000"

        agent_name = self.extract_agent_name(data, user_api_key_dict, kwargs)
        host = agent_name if agent_name else hosted_url

        timestamp = str(int(datetime.now(timezone.utc).timestamp() * 1000))
        proxy_server_request = (
            data.get("proxy_server_request")
            or (kwargs.get("litellm_params", {}) if kwargs else {}).get("proxy_server_request")
            or {}
        )
        request_headers_raw = proxy_server_request.get("headers", {})
        client_ip = (
            request_headers_raw.get("x-forwarded-for", "").split(",")[0].strip()
            or request_headers_raw.get("x-real-ip", "")
            or "0.0.0.0"
        )

        request_headers = json.dumps({
            "host": host,
            "content-type": "application/json",
        })

        response_headers = json.dumps({
            "content-type": "application/json",
        })

        request_payload = json.dumps({
            "body": request_body,
        })

        if response_obj is not None:
            response_payload = json.dumps({
                "body": response_obj,
            })
        else:
            response_payload = None

        return {
            "path": request_path,
            "requestHeaders": request_headers,
            "responseHeaders": response_headers,
            "method": "POST",
            "requestPayload": request_payload,
            "responsePayload": response_payload,
            "ip": client_ip,
            "destIp": "127.0.0.1",
            "time": timestamp,
            "statusCode": str(status_code),
            "type": None,
            "status": str(status_code),
            "akto_account_id": "1000000",
            "akto_vxlan_id": "0",
            "is_pending": "false",
            "source": "MIRRORING",
            "direction": None,
            "process_id": None,
            "socket_id": None,
            "daemonset_id": None,
            "enabled_graph": None,
            "tag": json.dumps(tags),
            "metadata": json.dumps(tags),
            "contextSource": "AGENTIC",
        }

    async def async_on_shutdown(self) -> None:
        if self.client:
            await self.client.aclose()
        logger.info("Guardrails client closed")

proxy_handler_instance = GuardrailsHandler()