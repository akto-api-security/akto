from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Literal, Tuple, Optional, Any
import httpx
import json
import os
import re
import logging
import time
from datetime import datetime, timezone
from urllib.parse import urlparse, quote

try:
    # Same normalizer litellm's own first-party guardrail hooks (Headroom, Lasso,
    # Cato Networks) use - covers chat completions, the Responses API, and
    # Anthropic Messages "tool_use" blocks in one call. Guarded because it lives
    # under litellm_core_utils (not a documented public API) and this repo's
    # litellm dependency is unpinned, so older installs may not have it.
    from litellm.litellm_core_utils.prompt_templates.factory import (
        get_tool_calls_from_response as LITELLM_GET_TOOL_CALLS_FROM_RESPONSE,
        get_attribute_or_key as LITELLM_GET_ATTRIBUTE_OR_KEY,
    )
except ImportError:
    LITELLM_GET_TOOL_CALLS_FROM_RESPONSE = None
    LITELLM_GET_ATTRIBUTE_OR_KEY = None

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
if not logger.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(name)s - %(message)s"))
    logger.addHandler(_handler)

DATA_INGESTION_SERVICE_URL = os.getenv("DATA_INGESTION_SERVICE_URL")
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("TIMEOUT", "5"))
LITELLM_URL = os.getenv("LITELLM_URL", "http://localhost:4000")
AKTO_CONNECTOR_NAME = "litellm"
HTTP_PROXY_PATH = "/api/http-proxy"
# Mirrored path: /mcp matches JsonRpcUtils.isMcpPath; non-MCP uses /{prefix}/{normalized-tool-name}
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")
CALL_HEADERS_CACHE_TTL_SECONDS = float(os.getenv("CALL_HEADERS_CACHE_TTL_SECONDS", str(15 * 60)))

INVALID_AGENT_CHARS = re.compile(r"[^a-z0-9\-._]")
INVALID_TOOL_NAME_CHARS = re.compile(r"[^a-zA-Z0-9._~-]+")
# Never mirror credentials/session cookies to the ingestion service; host/content-type
# are always overridden below to reflect the mirrored envelope, not the original request.
EXCLUDED_FORWARD_HEADERS = {"authorization", "cookie", "host", "content-type", "content-length"}


class GuardrailsHandler(CustomLogger):
    def __init__(self):
        super().__init__()
        self.client = httpx.AsyncClient(
            timeout=TIMEOUT,
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
            headers={"Authorization": AKTO_API_TOKEN} if AKTO_API_TOKEN else {},
        )
        # litellm_call_id -> (request headers, expiry epoch seconds). async_pre_call_hook
        # is the only hook that reliably sees proxy_server_request.headers - litellm
        # doesn't pass it to async_should_run_agentic_loop - so we stash it here for the
        # tool-call hook to pick back up by the same call_id.
        self._call_headers_cache: dict = {}
        logger.info(f"GuardrailsHandler initialized | sync_mode={SYNC_MODE}")

    def _cache_call_headers(self, litellm_call_id: Optional[str], headers: dict) -> None:
        if not litellm_call_id or not headers:
            return
        now = time.monotonic()
        # Opportunistic sweep, so entries for calls that never trigger a tool call
        # (and are therefore never read back) don't linger past their TTL.
        expired = [cid for cid, (_, expiry) in self._call_headers_cache.items() if expiry <= now]
        for cid in expired:
            del self._call_headers_cache[cid]
        self._call_headers_cache[litellm_call_id] = (headers, now + CALL_HEADERS_CACHE_TTL_SECONDS)

    def _get_cached_call_headers(self, litellm_call_id: Optional[str]) -> dict:
        if not litellm_call_id:
            return {}
        entry = self._call_headers_cache.get(litellm_call_id)
        if not entry:
            return {}
        headers, expiry = entry
        if expiry <= time.monotonic():
            del self._call_headers_cache[litellm_call_id]
            return {}
        return headers

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

    def parse_guardrails_result(self, result: Any) -> Tuple[bool, str, Optional[str]]:
        """Parse Akto guardrails response. Returns (allowed, reason, modified_payload)."""
        if not isinstance(result, dict):
            return True, "", None
        
        guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        modified_payload = guardrails_result.get("ModifiedPayload")
        
        return allowed, reason, modified_payload
    
    def apply_redaction(self, data: dict, modified_payload: str) -> dict:
        """Apply PII redactions from Akto's ModifiedPayload to the request data.
        
        Akto returns the ENTIRE redacted request body in ModifiedPayload:
        {"body": {"messages": [...], "model": "...", "stream": false}}
        
        Simply replace the entire data dict with the redacted body.
        """
        try:
            # Parse ModifiedPayload JSON string
            if isinstance(modified_payload, str):
                payload_obj = json.loads(modified_payload)
            else:
                payload_obj = modified_payload
            
            # Extract the redacted body - this is the FULL request with redactions applied
            redacted_body = payload_obj.get("body")
            if not redacted_body:
                logger.info("ModifiedPayload has no 'body' field, skipping redaction")
                return data
            
            # Akto returns the complete redacted request - use it directly
            logger.info(f"Applied Akto redactions: {redacted_body.get('messages', [])}")
            return redacted_body
            
        except Exception as e:
            logger.error(f"Failed to apply redaction from ModifiedPayload: {e}", exc_info=True)
            return data

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

        # 1. metadata.agent_name - explicit per-request override, if the application sends one.
        agent = metadata.get("agent_name")
        if agent:
            return self.sanitize_agent_name(str(agent))

        # 2. Application identity stamped server-side onto the virtual key. LiteLLM looks the
        #    key up in its own store and injects the key's metadata into
        #    litellm_params.metadata.user_api_key_metadata, so application keys carry a stable
        #    identity (key_type/app_name/app_slug) with no application-side change. Prefer the
        #    human-readable app_name, then the app_slug.
        key_metadata = metadata.get("user_api_key_metadata") or {}
        if key_metadata.get("key_type") == "application":
            for field in ("app_name", "app_slug"):
                value = key_metadata.get(field)
                if value:
                    return self.sanitize_agent_name(str(value))

        # 3. Fall back to the key alias, then the team alias.
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
        self._cache_request_headers_from_data(data)
        return await self.handle_validation_hook(data, call_type, user_api_key_dict, kwargs)

    def _cache_request_headers_from_data(self, data: dict) -> None:
        litellm_call_id = data.get("litellm_call_id")
        proxy_server_request = data.get("proxy_server_request") or (data.get("litellm_params") or {}).get("proxy_server_request") or {}
        headers = proxy_server_request.get("headers")
        if headers:
            self._cache_call_headers(litellm_call_id, headers)

    async def async_moderation_hook(
        self,
        data: dict,
        user_api_key_dict: UserAPIKeyAuth,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
        **kwargs,
    ) -> dict:
        return await self.handle_validation_hook(data, call_type, user_api_key_dict, kwargs)

    def _parse_tool_arguments(self, raw_args: Any) -> dict:
        if isinstance(raw_args, dict):
            return raw_args
        if raw_args is None:
            return {}
        if isinstance(raw_args, str):
            try:
                parsed = json.loads(raw_args)
                return parsed if isinstance(parsed, dict) else {"input": parsed}
            except (json.JSONDecodeError, TypeError):
                return {"input": raw_args}
        return {"input": raw_args}

    def _get_attr(self, obj: Any, attr: str, default: Any = None) -> Any:
        """Dict-or-object accessor. Prefers litellm's own get_attribute_or_key so we
        stay consistent with how litellm's first-party guardrail hooks read responses;
        falls back to a local equivalent if that utility isn't importable."""
        if LITELLM_GET_ATTRIBUTE_OR_KEY is not None:
            return LITELLM_GET_ATTRIBUTE_OR_KEY(obj, attr, default)
        if isinstance(obj, dict):
            return obj.get(attr, default)
        return getattr(obj, attr, default)

    def _extract_tool_calls_fallback(self, response: Any) -> list:
        """Used only if the installed litellm version predates get_tool_calls_from_response.
        Mirrors that utility's coverage: chat-completion tool_calls + Anthropic "tool_use" blocks."""
        calls = []
        for choice in getattr(response, "choices", None) or []:
            message = getattr(choice, "message", None)
            for tc in getattr(message, "tool_calls", None) or []:
                fn = getattr(tc, "function", None)
                calls.append({
                    "id": getattr(tc, "id", None),
                    "name": getattr(fn, "name", None) or "unknown",
                    "arguments": self._parse_tool_arguments(getattr(fn, "arguments", None)),
                })

        for block in getattr(response, "content", None) or []:
            if isinstance(block, dict) and block.get("type") == "tool_use":
                calls.append({
                    "id": block.get("id"),
                    "name": block.get("name") or "unknown",
                    "arguments": self._parse_tool_arguments(block.get("input")),
                })

        return calls

    def _extract_server_tool_calls(self, response: Any) -> list:
        """Anthropic's server-executed tools (web_search, code_execution) emit a
        "server_tool_use" content block, not "tool_use" - litellm's
        get_tool_calls_from_response doesn't recognize that block type, so this
        covers the gap regardless of which extraction path ran above."""
        content = self._get_attr(response, "content", None)
        if not isinstance(content, list):
            return []
        calls = []
        for block in content:
            if self._get_attr(block, "type") != "server_tool_use":
                continue
            calls.append({
                "id": self._get_attr(block, "id"),
                "name": self._get_attr(block, "name") or "unknown",
                "arguments": self._parse_tool_arguments(self._get_attr(block, "input", {})),
            })
        return calls

    def _extract_server_tool_results(self, response: Any) -> dict:
        """tool_use_id -> result block, for Anthropic server-executed tools (web_search,
        code_execution). litellm surfaces these on
        choices[].message.provider_specific_fields["<tool>_results"] - a sibling of
        tool_calls on the message, not attached to the tool_calls entry itself - so this
        has to be matched back to a call by tool_use_id after the fact."""
        results = {}
        for choice in self._get_attr(response, "choices", None) or []:
            message = self._get_attr(choice, "message", None)
            provider_specific_fields = self._get_attr(message, "provider_specific_fields", None) or {}
            for key, value in provider_specific_fields.items():
                if not key.endswith("_results") or not isinstance(value, list):
                    continue
                for block in value:
                    tool_use_id = self._get_attr(block, "tool_use_id")
                    if tool_use_id:
                        results[tool_use_id] = block
        return results

    def _extract_tool_calls(self, response: Any) -> list:
        """Extracts {id, name, arguments (dict)} per tool call. The id is the
        tool_use_id needed to pair a server-executed tool call with its result via
        _extract_server_tool_results(). Prefers litellm's own
        get_tool_calls_from_response - the same normalizer litellm's first-party
        guardrail hooks (Headroom, Lasso, Cato Networks) use, covering chat
        completions, the Responses API, and Anthropic Messages "tool_use" blocks -
        falling back to hand-rolled extraction only if that utility isn't
        importable on the installed litellm version. Anthropic's server-executed
        tools aren't covered by either path, so those are always added separately."""
        if LITELLM_GET_TOOL_CALLS_FROM_RESPONSE is not None:
            calls = [
                {"id": c.get("id"), "name": c.get("name") or "unknown", "arguments": c.get("arguments") or {}}
                for c in LITELLM_GET_TOOL_CALLS_FROM_RESPONSE(response)
            ]
        else:
            calls = self._extract_tool_calls_fallback(response)

        calls.extend(self._extract_server_tool_calls(response))
        return calls

    def normalize_tool_name_for_url_path(self, tool_name: str) -> str:
        """RFC 3986 path segment: unreserved + hyphen; collapse repeats."""
        s = (tool_name or "unknown").strip()
        s = INVALID_TOOL_NAME_CHARS.sub("-", s)
        s = re.sub(r"-+", "-", s).strip("-")
        return quote(s or "unknown", safe=".-_~")

    def non_mcp_ingest_path(self, tool_name: str) -> str:
        return f"{NON_MCP_TOOL_PATH_PREFIX}/{self.normalize_tool_name_for_url_path(tool_name)}"

    def parse_mcp_tool_name(self, tool_name: str) -> Tuple[bool, str, str]:
        """Parse a tool_name into (is_mcp, server_name, mcp_tool_name).
        MCP tools follow the mcp__<server>__<tool> convention (tool segment may contain underscores)."""
        if not tool_name or not tool_name.startswith("mcp__"):
            return False, "", ""
        parts = tool_name.split("__")
        if len(parts) < 3:
            return False, "", ""
        server = parts[1]
        mcp_tool = "__".join(parts[2:])
        if not server or not mcp_tool:
            return False, "", ""
        return True, server, mcp_tool

    def build_tool_call_jsonrpc(self, mcp_tool_name: str, tool_args: dict, request_id: int = 1) -> str:
        """JSON-RPC body aligned with MCP tools/call (https://modelcontextprotocol.io)."""
        return json.dumps({
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {"name": mcp_tool_name, "arguments": tool_args},
            "id": request_id,
        })

    def _extract_available_tool_names(self, tools: Any) -> list:
        """Names of ALL tools offered to the model for this call, not just the one(s) invoked -
        gives visibility into the agent's full tool surface. Covers OpenAI-style
        ({"type":"function","function":{"name":...}}) and Anthropic-style ({"name":...})."""
        names = []
        for tool in tools or []:
            if not isinstance(tool, dict):
                continue
            fn = tool.get("function")
            name = fn.get("name") if isinstance(fn, dict) else tool.get("name")
            if name:
                names.append(name)
        return names

    def build_tool_call_tags(
        self,
        *,
        is_mcp: bool,
        tool_name: str,
        mcp_server_name: str,
        mcp_tool_name: str,
        model: str,
        custom_llm_provider: Optional[str] = None,
        litellm_call_id: Optional[str] = None,
        available_tools: Optional[list] = None,
    ) -> dict:
        if is_mcp:
            tags = {"mcp-server": "MCP Server", "mcp-client": AKTO_CONNECTOR_NAME, "mcp_server_name": mcp_server_name, "tool_name": mcp_tool_name}
        else:
            tags = {"gen-ai": "Gen AI", "ai-agent": AKTO_CONNECTOR_NAME, "tool_name": tool_name}
        tags["call_type"] = "tool_call"
        if model:
            tags["model"] = model
        if custom_llm_provider:
            tags["llm_provider"] = custom_llm_provider
        if litellm_call_id:
            tags["litellm_call_id"] = litellm_call_id
        if available_tools:
            tags["available_tools"] = ",".join(available_tools)
        return tags

    def build_tool_call_ingest_payload(
        self,
        tool_name: str,
        tool_args: dict,
        *,
        model: str,
        user_api_key_dict: Optional[UserAPIKeyAuth] = None,
        metadata: Optional[dict] = None,
        custom_llm_provider: Optional[str] = None,
        available_tools: Optional[list] = None,
        kwargs: Optional[dict] = None,
        tool_result: Optional[Any] = None,
    ) -> dict:
        """Builds the mirrored-request payload for a single tool call, using the same
        path convention (/mcp vs /tool/<name>) the Go backend uses to classify tool-call
        traffic - so this shows up distinctly from ordinary prompt/response ingestion."""
        is_mcp, mcp_server_name, mcp_tool_name = self.parse_mcp_tool_name(tool_name)
        path = MCP_INGEST_PATH if is_mcp else self.non_mcp_ingest_path(tool_name)
        request_payload = (
            self.build_tool_call_jsonrpc(mcp_tool_name, tool_args)
            if is_mcp
            else json.dumps({"body": tool_args, "toolName": tool_name})
        )

        litellm_params = (kwargs or {}).get("litellm_params", {})
        litellm_call_id = (kwargs or {}).get("litellm_call_id")
        proxy_server_request = litellm_params.get("proxy_server_request") or {}
        # litellm doesn't pass proxy_server_request.headers into this hook's kwargs at
        # all, so fall back to what async_pre_call_hook cached for this same call_id.
        request_headers_raw = proxy_server_request.get("headers") or self._get_cached_call_headers(litellm_call_id)
        client_ip = (
            request_headers_raw.get("x-forwarded-for", "").split(",")[0].strip()
            or request_headers_raw.get("x-real-ip", "")
            or "0.0.0.0"
        )
        tags = self.build_tool_call_tags(
            is_mcp=is_mcp,
            tool_name=tool_name,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            model=model,
            custom_llm_provider=custom_llm_provider,
            litellm_call_id=litellm_call_id,
            available_tools=available_tools,
        )
        # Surface all virtual-key metadata as tags too, without clobbering the tags above.
        for k, v in self.key_metadata_tags({"metadata": metadata or {}}).items():
            tags.setdefault(k, v)

        host = self._resolve_host({"metadata": metadata or {}}, user_api_key_dict)
        request_headers_out = self.build_forwarded_headers(request_headers_raw, host)
        # Only server-executed tools (web_search, code_execution) have a result at this
        # point - it's already in the same response this hook fired on. Client-executed
        # tools (the caller runs them after receiving the response) genuinely have no
        # result yet; tool_result stays None for those.
        response_payload = json.dumps({"body": tool_result}) if tool_result is not None else None

        return self.build_http_proxy_envelope(
            path=path,
            request_headers=request_headers_out,
            response_headers={"content-type": "application/json"},
            request_payload=request_payload,
            response_payload=response_payload,
            ip=client_ip,
            status_code=200,
            tags=tags,
        )

    async def async_should_run_agentic_loop(
        self,
        response: Any,
        model: str,
        messages: list,
        tools: Any,
        stream: bool,
        custom_llm_provider: Optional[str],
        kwargs: dict,
    ) -> Tuple[bool, dict]:
        """Fires after the model responds but before the response reaches the caller -
        i.e. in between the LLM's tool-call decision and the client's execution of it.
        Ingests each tool call individually for visibility only; never takes over the loop."""
        try:
            if not DATA_INGESTION_SERVICE_URL:
                return False, {}

            tool_calls = self._extract_tool_calls(response)
            if not tool_calls:
                return False, {}

            if stream:
                # Docs say this hook is non-streaming only; if it ever fires with stream=True,
                # our response-shape parsing above may be wrong - flag it loudly rather than
                # silently ingesting something incorrect.
                logger.warning("[tool-call-hook] Fired with stream=True - response parsing assumes non-streaming, verify output")

            available_tools = self._extract_available_tool_names(tools)
            tool_results_by_id = self._extract_server_tool_results(response)

            litellm_params = kwargs.get("litellm_params", {}) if kwargs else {}
            metadata = litellm_params.get("metadata", {})
            user_api_key_dict = metadata.get("user_api_key_dict")

            for call in tool_calls:
                tool_name = call["name"]
                tool_args = call["arguments"]
                tool_result = tool_results_by_id.get(call.get("id"))
                is_mcp, mcp_server_name, mcp_tool_name = self.parse_mcp_tool_name(tool_name)
                logger.info(
                    f"[tool-call-hook] Detected tool_call: name={tool_name} is_mcp={is_mcp} "
                    f"mcp_server={mcp_server_name or None} provider={custom_llm_provider} "
                    f"available_tools={available_tools} arguments={tool_args} "
                    f"has_result={tool_result is not None}"
                )

                http_proxy_payload = self.build_tool_call_ingest_payload(
                    tool_name,
                    tool_args,
                    model=model,
                    user_api_key_dict=user_api_key_dict,
                    metadata=metadata,
                    custom_llm_provider=custom_llm_provider,
                    available_tools=available_tools,
                    kwargs=kwargs,
                    tool_result=tool_result,
                )
                logger.info(
                    f"[tool-call-hook] Ingesting | path={http_proxy_payload.get('path')} "
                    f"requestPayload={http_proxy_payload.get('requestPayload')}"
                )
                resp = await self.post_http_proxy(guardrails=False, ingest_data=True, http_proxy_payload=http_proxy_payload)
                logger.info(f"[tool-call-hook] Ingestion response for {tool_name}: HTTP {resp.status_code}")
        except Exception as e:
            logger.error(f"[tool-call-hook] Tool-call ingestion error (fail-open): {e}")

        return False, {}

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
                "tools": kwargs.get("tools", []),
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
            allowed, reason, modified_payload = await self.call_guardrails_validation(data, call_type, user_api_key_dict, kwargs)
            
            if not allowed:
                await self.ingest_blocked_request(data, call_type, reason, user_api_key_dict, kwargs)
                raise HTTPException(
                    status_code=403,
                    detail=f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails",
                )
            
            # If Akto returned a redacted version, apply it to the user message
            if modified_payload:
                data = self.apply_redaction(data, modified_payload)
            
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
                allowed, reason, _ = self.parse_guardrails_result(response.json())
                if not allowed:
                    logger.info(f"Response flagged by guardrails (async mode, logged only): {reason}")
        except Exception as e:
            logger.error(f"Guardrails async validation error: {e}")

    async def call_guardrails_validation(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> Tuple[bool, str, Optional[str]]:
        if not DATA_INGESTION_SERVICE_URL:
            return True, "", None

        http_proxy_payload = self.build_payload(data, call_type, None, user_api_key_dict, kwargs=kwargs)

        try:
            response = await self.post_http_proxy(guardrails=True, ingest_data=False, http_proxy_payload=http_proxy_payload)
            if response.status_code != 200:
                logger.info(f"Guardrails validation returned HTTP {response.status_code} (fail-open)")
                return True, "", None

            return self.parse_guardrails_result(response.json())
        except (httpx.RequestError, httpx.TimeoutException, ValueError) as e:
            logger.info(f"Guardrails validation failed (fail-open): {e}")
            return True, "", None
        except Exception as e:
            logger.error(f"Guardrails validation error (fail-open): {e}")
            return True, "", None

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

    def _tag_value(self, value: Any) -> str:
        """Tag maps are string-valued; stringify scalars, JSON-encode anything nested."""
        if isinstance(value, str):
            return value
        if isinstance(value, (int, float, bool)):
            return str(value)
        return json.dumps(value)

    def key_metadata_tags(self, data: dict, kwargs: Optional[dict] = None) -> dict:
        """Every key/value LiteLLM injected onto the virtual key
        (litellm_params.metadata.user_api_key_metadata) surfaced as tags, so the key's
        server-side identity (key_type/app_name/app_slug/... for application keys, or any
        custom metadata an admin stamped on the key) is queryable in Akto alongside the
        traffic - no application-side change required."""
        metadata = data.get("metadata") or (kwargs.get("litellm_params", {}) if kwargs else {}).get("metadata") or {}
        key_metadata = metadata.get("user_api_key_metadata") or {}
        return {str(k): self._tag_value(v) for k, v in key_metadata.items() if v is not None}

    def build_tags(self, call_type: str, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None, litellm_call_id: Optional[str] = None, kwargs: Optional[dict] = None) -> dict:
        tags = {"gen-ai": "Gen AI", "litellm": "LiteLLM"}
        if call_type:
            tags["call_type"] = call_type
        model = data.get("model", "")
        if model:
            tags["model"] = model
        # Same litellm_call_id build_tool_call_tags() stamps on every tool-call event
        # fired from this request - lets the dashboard join a completion to its tool calls.
        if litellm_call_id:
            tags["litellm_call_id"] = litellm_call_id
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
        # Surface all virtual-key metadata as tags, without clobbering the core tags above.
        for k, v in self.key_metadata_tags(data, kwargs).items():
            tags.setdefault(k, v)
        return tags

    def build_forwarded_headers(self, request_headers_raw: dict, host: str) -> dict:
        """Mirrors all original client request headers (e.g. x-akto-* passthrough
        headers) to the ingested request, except credentials/cookies (never forwarded)
        and host/content-type (overridden to reflect the mirrored envelope)."""
        headers_out = {
            k: v for k, v in (request_headers_raw or {}).items()
            if k.lower() not in EXCLUDED_FORWARD_HEADERS
        }
        headers_out["host"] = host
        headers_out["content-type"] = "application/json"
        return headers_out

    def _resolve_host(self, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> str:
        agent_name = self.extract_agent_name(data, user_api_key_dict, kwargs)
        if agent_name:
            return agent_name
        parsed = urlparse(LITELLM_URL) if LITELLM_URL else None
        return parsed.netloc if parsed and parsed.netloc else "localhost:4000"

    def build_http_proxy_envelope(
        self,
        *,
        path: str,
        request_headers: dict,
        response_headers: dict,
        request_payload: str,
        response_payload: Optional[str],
        ip: str,
        status_code: int,
        tags: dict,
    ) -> dict:
        """Shared /api/http-proxy envelope - every mirrored request (prompt/response
        ingestion, guardrails validation, tool-call ingestion) shares this shape;
        only path/headers/payloads/ip/status/tags differ per call site."""
        timestamp = str(int(datetime.now(timezone.utc).timestamp() * 1000))
        return {
            "path": path,
            "requestHeaders": json.dumps(request_headers),
            "responseHeaders": json.dumps(response_headers),
            "method": "POST",
            "requestPayload": request_payload,
            "responsePayload": response_payload,
            "ip": ip,
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

    def build_payload(self, data: dict, call_type: str, response_obj: Optional[Any], user_api_key_dict: Optional[UserAPIKeyAuth] = None, status_code: int = 200, kwargs: Optional[dict] = None) -> dict:
        request_body = {
            "model": data.get("model", ""),
            "messages": data.get("messages", []),
            "stream": data.get("stream", False),
            "tools": data.get("tools", []),
        }

        request_path = self.extract_request_path(kwargs)
        litellm_call_id = (kwargs or {}).get("litellm_call_id")
        tags = self.build_tags(call_type, data, user_api_key_dict, litellm_call_id, kwargs)
        host = self._resolve_host(data, user_api_key_dict, kwargs)

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

        headers_out = self.build_forwarded_headers(request_headers_raw, host)

        request_payload = json.dumps({
            "body": request_body,
        })

        if response_obj is not None:
            response_payload = json.dumps({
                "body": response_obj,
            })
        else:
            response_payload = None

        return self.build_http_proxy_envelope(
            path=request_path,
            request_headers=headers_out,
            response_headers={"content-type": "application/json"},
            request_payload=request_payload,
            response_payload=response_payload,
            ip=client_ip,
            status_code=status_code,
            tags=tags,
        )

    async def async_on_shutdown(self) -> None:
        if self.client:
            await self.client.aclose()
        logger.info("Guardrails client closed")

proxy_handler_instance = GuardrailsHandler()