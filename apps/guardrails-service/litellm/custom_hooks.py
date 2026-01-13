from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth, DualCache
from litellm.types.utils import ModelResponseStream
from fastapi import HTTPException
from typing import Any, AsyncGenerator, Optional, Literal
import httpx
import os
import json
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GUARDRAILS_BASE_URL = os.getenv("GUARDRAILS_BASE_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
REQUEST_TIMEOUT = float(os.getenv("GUARDRAILS_TIMEOUT", "10"))


class GuardrailsHandler(CustomLogger):
    def __init__(self):
        super().__init__()
        self.guardrails_url = GUARDRAILS_BASE_URL
        self.sync_mode = SYNC_MODE
        self.timeout = REQUEST_TIMEOUT
        if not self.guardrails_url:
            logger.error("GUARDRAILS_BASE_URL is not set. Guardrails validation will be skipped (or fail).")
        logger.info(f"GuardrailsHandler initialized: url={self.guardrails_url}, sync_mode={self.sync_mode}")

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
        try:
            if self.sync_mode:
                result = await self._validate_request(data, call_type, "pre_call")
                
                if not result.get("allowed", True):
                    reason = result.get("reason", "Request blocked by guardrails policy")
                    logger.warning(f"[PRE-CALL] Request blocked: {reason}")
                    raise HTTPException(
                        status_code=400,
                        detail={
                            "error": reason,
                            "code": "GUARDRAILS_BLOCKED",
                            "type": "guardrails_violation"
                        }
                    )
                
                modified_data = result.get("modified_data")
                if modified_data and result.get("modified", False):
                    logger.info("[PRE-CALL] Request modified by guardrails")
                    return modified_data
            
            return data

        except HTTPException:
            raise
        except Exception as e:
            logger.error(f"[PRE-CALL] Error in validation: {e}")
            return data

    async def _validate_request(self, data: dict, call_type: str, mode: str) -> dict:
        try:
            payload = self._extract_payload(data)
            
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(
                    f"{self.guardrails_url}/api/validate/request",
                    json={
                        "payload": json.dumps(payload) if isinstance(payload, dict) else str(payload),
                        "call_type": call_type,
                        "mode": mode
                    },
                    headers={"Content-Type": "application/json"}
                )
                
                if response.status_code == 200:
                    result = response.json()
                    return {
                        "allowed": result.get("allowed", True),
                        "modified": result.get("modified", False),
                        "modified_data": data,
                        "reason": result.get("reason", "")
                    }
                else:
                    logger.warning(f"[VALIDATE] Guardrails service returned {response.status_code}: {response.text}")
                    return {"allowed": True, "modified_data": data}
                    
        except Exception as e:
            logger.error(f"[VALIDATE] Failed to reach guardrails service at {self.guardrails_url}: {e}")
            # Fail open mechanism: returns allowed=True if guardrails service is down
            return {"allowed": True, "modified_data": data}

    def _extract_payload(self, data: dict) -> dict:
        if "messages" in data:
            return {
                "messages": data.get("messages", []),
                "model": data.get("model", ""),
                "user": data.get("user", "")
            }
        elif "prompt" in data:
            return {
                "prompt": data.get("prompt", ""),
                "model": data.get("model", "")
            }
        return data

    async def async_moderation_hook(
        self,
        data: dict,
        user_api_key_dict: UserAPIKeyAuth,
        call_type: Literal[
            "completion",
            "embeddings",
            "image_generation",
            "moderation",
            "audio_transcription",
        ],
    ):
        if not self.sync_mode:
            logger.info(f"[MODERATION] Running async moderation for {call_type}")
            
            try:
                result = await self._validate_request(data, call_type, "parallel")
                
                if not result.get("allowed", True):
                    reason = result.get("reason", "Request blocked by moderation policy")
                    logger.warning(f"[MODERATION] Request blocked: {reason}")
                    raise HTTPException(
                        status_code=400,
                        detail={
                            "error": reason,
                            "code": "MODERATION_BLOCKED",
                            "type": "guardrails_violation"
                        }
                    )
                    
            except HTTPException:
                raise
            except Exception as e:
                logger.error(f"[MODERATION] Error: {e}")

    async def async_post_call_success_hook(
        self,
        data: dict,
        user_api_key_dict: UserAPIKeyAuth,
        response,
    ):
        logger.info("[POST-CALL SUCCESS] Processing response")
        
        try:
            response_content = self._extract_response_content(response)
            
            if not response_content:
                return

            async with httpx.AsyncClient(timeout=self.timeout) as client:
                validation_response = await client.post(
                    f"{self.guardrails_url}/api/validate/response",
                    json={
                        "payload": response_content
                    },
                    headers={"Content-Type": "application/json"}
                )
                
                if validation_response.status_code == 200:
                    result = validation_response.json()
                    if not result.get("allowed", True):
                        logger.warning(f"[POST-CALL SUCCESS] Response violated policy: {result.get('reason')}")
        except Exception as e:
            logger.error(f"[POST-CALL SUCCESS] Error: {e}")

    def _extract_response_content(self, response) -> str:
        try:
            if hasattr(response, 'choices') and response.choices:
                choice = response.choices[0]
                if hasattr(choice, 'message') and choice.message:
                    return choice.message.content or ""
                elif hasattr(choice, 'text'):
                    return choice.text or ""
            return ""
        except Exception:
            return ""

    async def async_post_call_failure_hook(
        self,
        request_data: dict,
        original_exception: Exception,
        user_api_key_dict: UserAPIKeyAuth,
        traceback_str: Optional[str] = None,
    ) -> Optional[HTTPException]:
        error_message = str(original_exception).lower()
        
        if "context_length" in error_message or "token" in error_message:
            return HTTPException(
                status_code=400,
                detail={
                    "error": "Your prompt is too long. Please reduce the length and try again.",
                    "code": "CONTEXT_LENGTH_EXCEEDED",
                    "type": "invalid_request_error"
                }
            )
        
        if "rate_limit" in error_message or "429" in error_message:
            return HTTPException(
                status_code=429,
                detail={
                    "error": "Rate limit exceeded. Please try again later.",
                    "code": "RATE_LIMITED",
                    "type": "rate_limit_error"
                }
            )
        
        if "authentication" in error_message or "401" in error_message:
            return HTTPException(
                status_code=401,
                detail={
                    "error": "Authentication failed. Please check your API key.",
                    "code": "AUTHENTICATION_ERROR",
                    "type": "authentication_error"
                }
            )
        
        return None

    async def async_post_call_streaming_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        response: str,
    ):
        pass

    async def async_post_call_streaming_iterator_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        response: Any,
        request_data: dict,
    ) -> AsyncGenerator[ModelResponseStream, None]:
        async for item in response:
            yield item

proxy_handler_instance = GuardrailsHandler()
