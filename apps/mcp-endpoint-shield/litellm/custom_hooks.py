from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Dict, Literal, Tuple, Optional, Any
from collections import OrderedDict
import hashlib
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

# The guardrails service reads message content only as a plain string. Anthropic
# Messages style content BLOCKS - content: [{"type": "text", "text": "..."}] - are
# not read, so a prompt injection inside a content block is never seen and the
# service answers Allowed=true. Every Claude Code request uses blocks, so
# prompt-injection enforcement was silently inert for those clients while looking
# healthy. Flatten blocks to text for the verdict call.
#
# `stream` and `tools` in the mirrored body suppress detection the same way, and
# are call metadata rather than content to judge, so they come off too.
#
# Measured by replaying captured verdict envelopes against the live service,
# 4 trials each:
#     content as blocks, with stream + tools   -> allowed 4/4
#     same, tools removed                      -> blocked 4/4
#     same, content flattened to a string      -> blocked 4/4
#     headers reduced / tags reduced           -> allowed (not the cause)
#
# Verdict-only: the request forwarded to the provider and the payload sent for
# ingestion both keep the original structure, so inventory data is unchanged.
VERDICT_FLATTEN_CONTENT = os.getenv("VERDICT_FLATTEN_CONTENT", "true").strip().lower() == "true"
VERDICT_DROP_BODY_KEYS = tuple(
    k.strip() for k in os.getenv("VERDICT_DROP_BODY_KEYS", "stream,tools").split(",") if k.strip()
)

# Akto's guardrail detects reliably on short inputs but not on whole agent
# requests: an identical prompt-injection blocks at 63 chars yet is allowed
# inside an 86KB coding-agent request, where the user's text is buried under a
# large system prompt and tool catalogue. When the payload is big enough for
# that to bite, send only the newest user turn for the VERDICT call. Ingestion
# is never narrowed and always records the complete request.
#   "auto"      (default) narrow to the newest user turn when messages exceed
#               the threshold below
#   "true"      always narrow to the newest user turn
#   "all_user"  narrow to ALL user turns (harness context stripped, tools and
#               system dropped). Keeps the payload small enough for the guardrail
#               to work while still re-checking history, so a message that was
#               blocked once cannot reach the model as history on the next turn.
#               Cost: a session containing blocked content keeps being blocked.
#   "false"     never narrow (identical to upstream behaviour)
VALIDATE_LAST_USER_MESSAGE_ONLY = os.getenv("VALIDATE_LAST_USER_MESSAGE_ONLY", "auto").strip().lower()
GUARDRAIL_NARROW_THRESHOLD_BYTES = int(os.getenv("GUARDRAIL_NARROW_THRESHOLD_BYTES", "8000"))
# Coding agents inline their own scaffolding into the user turn wrapped in
# <system-reminder>. It is harness context, not user input, so it is stripped
# from the narrowed verdict view. Applies only when narrowing is already active.
STRIP_HARNESS_CONTEXT = os.getenv("STRIP_HARNESS_CONTEXT", "true").strip().lower() == "true"
# Diagnostic: log the exact text handed to the guardrail. Off by default because
# it writes prompt content to the container log.
LOG_VERDICT_TEXT = os.getenv("LOG_VERDICT_TEXT", "false").strip().lower() == "true"

# A guardrail can only reject a request; it cannot make the CLIENT forget. Claude
# Code keeps a rejected message in its local transcript and resends it as history
# on the next turn, so content blocked once still reaches the model afterwards.
# When enabled, the connector remembers what it blocked (per session) and strips
# those turns out of history before forwarding, so the model never sees them while
# later benign turns keep working. Bounded in-memory only; nothing is persisted.
QUARANTINE_BLOCKED_HISTORY = os.getenv("QUARANTINE_BLOCKED_HISTORY", "true").strip().lower() == "true"
QUARANTINE_MAX_SESSIONS = int(os.getenv("QUARANTINE_MAX_SESSIONS", "500"))
QUARANTINE_MAX_PER_SESSION = int(os.getenv("QUARANTINE_MAX_PER_SESSION", "50"))

HARNESS_CONTEXT_RE = re.compile(r"<system-reminder>.*?</system-reminder>", re.S | re.I)

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
        request_headers_out = self.build_forwarded_headers(request_headers_raw, host, kwargs)
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
                # Remember this turn so it can be stripped from history later -
                # the client keeps rejected messages and resends them.
                self.remember_blocked(data, kwargs)
                await self.ingest_blocked_request(data, call_type, reason, user_api_key_dict, kwargs)
                raise HTTPException(
                    status_code=403,
                    detail=f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails",
                )
            
            # If Akto returned a redacted version, apply it to the user message.
            # When the verdict ran on a narrowed view, ModifiedPayload contains
            # only that turn, so splice rather than replace the whole body.
            if modified_payload:
                if self._should_narrow(data):
                    data = self._merge_redacted_last_user(data, modified_payload)
                else:
                    data = self.apply_redaction(data, modified_payload)

            # Allowed: remove any turn blocked earlier in this session so the
            # model never sees content the guardrail already rejected.
            data = self.strip_quarantined_history(data, kwargs)
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

    @staticmethod
    def _should_narrow(data: dict) -> bool:
        """Whether this request is large enough that the guardrail would miss the
        user's text. Provider-agnostic: only looks at roles, never at content
        shape, so OpenAI-style string content and Anthropic-style content-block
        lists both work. Anything without a usable user turn is left alone."""
        if VALIDATE_LAST_USER_MESSAGE_ONLY == "false":
            return False
        msgs = data.get("messages")
        if not isinstance(msgs, list) or not msgs:
            return False  # embeddings / image_generation / audio_transcription
        if not any(isinstance(m, dict) and m.get("role") == "user" for m in msgs):
            return False
        if VALIDATE_LAST_USER_MESSAGE_ONLY in ("true", "all_user"):
            return True
        try:
            return len(json.dumps(msgs, default=str)) > GUARDRAIL_NARROW_THRESHOLD_BYTES
        except Exception:
            return False

    @classmethod
    def _strip_harness_context(cls, msg: dict) -> dict:
        """Remove harness-injected <system-reminder> scaffolding from a user turn.

        Coding agents inline their own context into the user message - Claude Code
        sends ~12KB of agent/skill/session reminders wrapped in <system-reminder>
        around a 63-char prompt. That scaffolding is not user input: judging it
        both buries the real prompt and makes the agent's own text trip policies.
        A no-op when no reminders are present, so ordinary apps are unaffected."""
        content = msg.get("content")

        if isinstance(content, str):
            cleaned = HARNESS_CONTEXT_RE.sub("", content).strip()
            if not cleaned or cleaned == content:
                return msg
            out = dict(msg)
            out["content"] = cleaned
            return out

        if isinstance(content, list):
            kept = []
            for block in content:
                if not isinstance(block, dict) or block.get("type") != "text":
                    kept.append(block)
                    continue
                cleaned = HARNESS_CONTEXT_RE.sub("", block.get("text") or "").strip()
                if cleaned:
                    nb = dict(block)
                    nb["text"] = cleaned
                    kept.append(nb)
            if not kept or kept == content:
                return msg  # nothing stripped, or stripping left nothing: keep original
            out = dict(msg)
            out["content"] = kept
            return out

        return msg


    @classmethod
    def _validation_view(cls, data: dict) -> dict:
        """The request as the guardrail should see it. Returns a shallow copy;
        the real request forwarded to the LLM is never modified here."""
        if not cls._should_narrow(data):
            return data
        msgs = data["messages"]

        if VALIDATE_LAST_USER_MESSAGE_ONLY == "all_user":
            # Every user turn, so content blocked earlier is re-checked rather
            # than slipping through as history on a later turn.
            kept = [m for m in msgs if isinstance(m, dict) and m.get("role") == "user"]
            if STRIP_HARNESS_CONTEXT:
                kept = [cls._strip_harness_context(m) for m in kept]
            trimmed = dict(data)
            trimmed["messages"] = kept
            trimmed["tools"] = []
            logger.info(
                f"Narrowed guardrail view: {len(msgs)} messages -> {len(kept)} user turns "
                f"({len(json.dumps(kept, default=str)):,}B after stripping harness context)"
            )
            if LOG_VERDICT_TEXT:
                joined = " || ".join(
                    (" ".join(b.get("text", "") for b in m.get("content") if isinstance(b, dict))
                     if isinstance(m.get("content"), list) else str(m.get("content")))
                    for m in kept
                )
                logger.info(f"Verdict text sent to Akto ({len(joined)} chars): {joined[:400]!r}")
            return trimmed

        last_user = next(m for m in reversed(msgs) if isinstance(m, dict) and m.get("role") == "user")
        if STRIP_HARNESS_CONTEXT:
            last_user = cls._strip_harness_context(last_user)
        trimmed = dict(data)
        trimmed["messages"] = [last_user]
        trimmed["tools"] = []
        logger.info(
            f"Narrowed guardrail view: {len(msgs)} messages -> newest user turn "
            f"({len(json.dumps(last_user, default=str)):,}B after stripping harness context)"
        )
        if LOG_VERDICT_TEXT:
            c = last_user.get("content")
            txt = (" ".join(b.get("text", "") for b in c if isinstance(b, dict))
                   if isinstance(c, list) else str(c))
            logger.info(f"Verdict text sent to Akto ({len(txt)} chars): {txt[:400]!r}")
        return trimmed

    def _merge_redacted_last_user(self, data: dict, modified_payload: Any) -> dict:
        """Splice Akto's redacted turn back in, preserving conversation history.

        We only showed Akto one turn, so ModifiedPayload carries only that turn.
        apply_redaction() replaces the whole body wholesale, which here would
        silently drop every other message."""
        try:
            obj = json.loads(modified_payload) if isinstance(modified_payload, str) else modified_payload
            redacted = ((obj or {}).get("body") or {}).get("messages") or []
            if not redacted:
                return data
            new_turn = redacted[-1]
            msgs = list(data.get("messages") or [])
            for i in range(len(msgs) - 1, -1, -1):
                if isinstance(msgs[i], dict) and msgs[i].get("role") == "user":
                    merged = dict(data)
                    merged["messages"] = msgs[:i] + [new_turn] + msgs[i + 1:]
                    logger.info(f"Applied Akto redaction to newest user turn ({len(msgs)} messages preserved)")
                    return merged
            return data
        except Exception as e:
            logger.error(f"Failed to merge redaction (keeping original): {e}")
            return data

    async def call_guardrails_validation(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> Tuple[bool, str, Optional[str]]:
        if not DATA_INGESTION_SERVICE_URL:
            return True, "", None

        # _validation_view scopes WHAT gets judged; normalize_verdict_payload fixes
        # the SHAPE it is judged in. Both apply to the verdict copy only.
        http_proxy_payload = self.normalize_verdict_payload(
            self.build_payload(self._validation_view(data), call_type, None, user_api_key_dict, kwargs=kwargs)
        )

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

    @staticmethod
    def normalize_verdict_payload(payload: dict) -> dict:
        """Shape the mirrored body so the guardrail can actually read the prompt.

        Flattens Anthropic-style content blocks to plain text and removes call
        metadata (`stream`, `tools`) that suppresses detection. See the comment on
        VERDICT_FLATTEN_CONTENT for the measurements. Verdict path only - the
        ingestion envelope is built separately and keeps the original structure.
        """
        try:
            request_payload = json.loads(payload.get("requestPayload") or "{}")
            body = request_payload.get("body")
            if not isinstance(body, dict):
                return payload

            changed = False
            for key in VERDICT_DROP_BODY_KEYS:
                if key in body:
                    body.pop(key, None)
                    changed = True

            if VERDICT_FLATTEN_CONTENT:
                for message in body.get("messages") or []:
                    if not isinstance(message, dict):
                        continue
                    content = message.get("content")
                    if isinstance(content, list):
                        message["content"] = " ".join(
                            block.get("text", "")
                            for block in content
                            if isinstance(block, dict) and block.get("type", "text") == "text"
                        ).strip()
                        changed = True

            if changed:
                normalized = dict(payload)
                normalized["requestPayload"] = json.dumps(request_payload)
                return normalized
        except (json.JSONDecodeError, TypeError, AttributeError) as e:
            logger.warning(f"Could not normalize guardrail verdict payload: {e}")
        return payload

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

    def client_identity_tags(self, kwargs: Optional[dict] = None) -> dict:
        """Identity the CLIENT supplied, surfaced as queryable Akto tags.

        Reachable without any virtual key, so traffic stays attributable even
        behind a shared key:
          metadata.user_id            Anthropic `metadata.user_id`. Claude Code
                                      puts {device_id, account_uuid, session_id}
                                      here on every request.
          user_agent                  e.g. claude-cli/2.1.252 (...)
          session_id / trace_id       LiteLLM session correlation
          tags                        from the x-litellm-tags header
          spend_logs_metadata         from x-litellm-spend-logs-metadata (JSON)
          user_api_key_end_user_id    from x-litellm-end-user-id
          user_api_key_* identity     user/team/org/project the key belongs to
        """
        md = (kwargs.get("litellm_params", {}) if kwargs else {}).get("metadata", {}) or {}
        tags: Dict[str, str] = {}

        # Anthropic metadata.user_id - a JSON blob for Claude Code, opaque string
        # for other clients. Flatten known sub-fields, else keep it whole.
        raw_user = md.get("user_id")
        if raw_user:
            parsed = None
            if isinstance(raw_user, str):
                try:
                    parsed = json.loads(raw_user)
                except (json.JSONDecodeError, TypeError):
                    parsed = None
            elif isinstance(raw_user, dict):
                parsed = raw_user
            if isinstance(parsed, dict):
                for src, dst in (("account_uuid", "client_account_uuid"),
                                 ("device_id", "client_device_id"),
                                 ("session_id", "client_session_id")):
                    if parsed.get(src):
                        tags[dst] = self._tag_value(parsed[src])
                for k, v in parsed.items():
                    if k not in ("account_uuid", "device_id", "session_id") and v is not None:
                        tags.setdefault(f"client_{k}", self._tag_value(v))
            else:
                tags["client_user_id"] = self._tag_value(raw_user)

        for src, dst in (("user_agent", "client_user_agent"),
                         ("session_id", "session_id"),
                         ("trace_id", "trace_id"),
                         ("user_api_key_end_user_id", "end_user_id"),
                         ("user_api_key_user_id", "user_id"),
                         ("user_api_key_user_email", "user_email"),
                         ("user_api_key_team_alias", "team_alias"),
                         ("user_api_key_org_alias", "org_alias"),
                         ("user_api_key_project_alias", "project_alias")):
            v = md.get(src)
            if v:
                tags.setdefault(dst, self._tag_value(v))

        caller_tags = md.get("tags") or md.get("caller_tags")
        if caller_tags:
            tags.setdefault("caller_tags", ",".join(str(t) for t in caller_tags)
                            if isinstance(caller_tags, (list, tuple)) else self._tag_value(caller_tags))

        # Arbitrary caller-supplied JSON, flattened so each field is queryable.
        slm = md.get("spend_logs_metadata")
        if isinstance(slm, dict):
            for k, v in slm.items():
                if v is not None:
                    tags.setdefault(str(k), self._tag_value(v))

        return tags

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
        # Then client-supplied identity (Claude Code account/device/session,
        # user-agent, caller tags, x-litellm-spend-logs-metadata). setdefault so
        # key metadata always wins on a name clash.
        for k, v in self.client_identity_tags(kwargs).items():
            tags.setdefault(k, v)
        return tags

    # ---- quarantine of previously blocked turns -----------------------------
    # session id -> ordered dict of fingerprint -> None (insertion-ordered FIFO)
    _quarantine: Dict[str, "OrderedDict"] = {}

    @staticmethod
    def _session_key(kwargs: Optional[dict], data: Optional[dict] = None) -> Optional[str]:
        """Session id for quarantine bookkeeping.

        At pre-call time litellm_params.metadata is NOT populated yet (the same
        reason extract_request_path falls back), so the id has to be recovered
        from the request body and headers as well. Claude Code carries it in the
        Anthropic metadata.user_id blob and in x-claude-code-session-id.
        """
        for md in (
            (kwargs.get("litellm_params", {}) if kwargs else {}).get("metadata") or {},
            (data or {}).get("metadata") or {},
            (data or {}).get("litellm_metadata") or {},
        ):
            if not isinstance(md, dict):
                continue
            sid = md.get("session_id") or md.get("trace_id")
            if sid:
                return str(sid)
            # Anthropic metadata.user_id: Claude Code packs session_id inside.
            raw = md.get("user_id")
            if raw:
                try:
                    parsed = json.loads(raw) if isinstance(raw, str) else raw
                except (json.JSONDecodeError, TypeError):
                    parsed = None
                if isinstance(parsed, dict) and parsed.get("session_id"):
                    return str(parsed["session_id"])

        psr = (data or {}).get("proxy_server_request") or {}
        headers = psr.get("headers") or {}
        for h in ("x-claude-code-session-id", "x-litellm-session-id", "x-session-id"):
            if headers.get(h):
                return str(headers[h])
        return None

    @classmethod
    def _turn_text(cls, msg: Any) -> str:
        """Normalized text of one message, harness context stripped, for matching."""
        if not isinstance(msg, dict):
            return ""
        c = msg.get("content")
        if isinstance(c, list):
            txt = " ".join(b.get("text", "") for b in c if isinstance(b, dict) and b.get("type") == "text")
        else:
            txt = str(c or "")
        return " ".join(HARNESS_CONTEXT_RE.sub("", txt).split())

    # Minimum length before a turn is trackable, so short pleasantries can never
    # match something else by accident.
    QUARANTINE_MIN_CHARS = 12
    QUARANTINE_MAX_TEXT = 4000

    @classmethod
    def _fingerprint(cls, msg: Any) -> Optional[str]:
        """Normalized text used both as the quarantine key and for matching.

        Exact hashing is not enough: when a request is rejected, Claude Code keeps
        the message and resends it with its own additions appended (e.g.
        "Continue from where you left off."), so the history copy never hashes to
        the same value. We keep the text and match by containment instead.
        """
        t = cls._turn_text(msg)
        if len(t) < cls.QUARANTINE_MIN_CHARS:
            return None
        return t[: cls.QUARANTINE_MAX_TEXT]

    @classmethod
    def _matches_quarantined(cls, text: str, bucket: Any) -> bool:
        """True if this turn is (or contains, or is contained by) a blocked turn."""
        if len(text) < cls.QUARANTINE_MIN_CHARS:
            return False
        for held in bucket:
            if held == text or held in text or text in held:
                return True
        return False

    @classmethod
    def remember_blocked(cls, data: dict, kwargs: Optional[dict] = None) -> None:
        """Record the user turn(s) that were just blocked, so later requests can
        have them stripped out of history."""
        if not QUARANTINE_BLOCKED_HISTORY:
            return
        key = cls._session_key(kwargs, data)
        if not key:
            logger.info("Quarantine: no session id on this request, cannot track blocked turn")
            return
        msgs = data.get("messages")
        if not isinstance(msgs, list):
            return
        last_user = next((m for m in reversed(msgs)
                          if isinstance(m, dict) and m.get("role") == "user"), None)
        fp = cls._fingerprint(last_user)
        if not fp:
            return
        bucket = cls._quarantine.setdefault(key, OrderedDict())
        bucket[fp] = None
        while len(bucket) > QUARANTINE_MAX_PER_SESSION:
            bucket.popitem(last=False)
        while len(cls._quarantine) > QUARANTINE_MAX_SESSIONS:
            cls._quarantine.pop(next(iter(cls._quarantine)))
        logger.info(f"Quarantined blocked turn for session {key} ({len(bucket)} held)")

    @classmethod
    def strip_quarantined_history(cls, data: dict, kwargs: Optional[dict] = None) -> dict:
        """Drop previously blocked user turns (and any assistant reply that
        immediately followed them) from the request before it reaches the model."""
        if not QUARANTINE_BLOCKED_HISTORY:
            return data
        key = cls._session_key(kwargs, data)
        if not key:
            return data
        bucket = cls._quarantine.get(key)
        if not bucket:
            return data
        msgs = data.get("messages")
        if not isinstance(msgs, list) or len(msgs) < 2:
            return data

        # Never strip the newest user turn - that one is being judged right now.
        last_user_idx = next((i for i in range(len(msgs) - 1, -1, -1)
                              if isinstance(msgs[i], dict) and msgs[i].get("role") == "user"), None)
        drop = set()
        for i, m in enumerate(msgs):
            if i == last_user_idx or not isinstance(m, dict) or m.get("role") != "user":
                continue
            if cls._matches_quarantined(cls._turn_text(m), bucket):
                drop.add(i)
                if i + 1 < len(msgs) and isinstance(msgs[i + 1], dict) and msgs[i + 1].get("role") == "assistant":
                    drop.add(i + 1)
        if not drop:
            return data
        cleaned = dict(data)
        cleaned["messages"] = [m for i, m in enumerate(msgs) if i not in drop]
        logger.info(
            f"Stripped {len(drop)} quarantined message(s) from history before forwarding "
            f"({len(msgs)} -> {len(cleaned['messages'])} messages)"
        )
        return cleaned

    def session_trace_headers(self, kwargs: Optional[dict] = None) -> Dict[str, str]:
        """x-akto-installer-* session/trace headers, matching the convention the
        other Akto connectors use (shared/akto_ingestion_utility.installer_headers)
        so the backend indexes LiteLLM traces the same way.

        Mapping for this connector:
          akto_session_id     LiteLLM metadata.session_id - for Claude Code this is
                              the CLI's own session id, so turns of one conversation
                              group together in traces.
          akto_message_id     litellm_call_id - unique per request, and the same id
                              build_tags() stamps, so a completion and its tool
                              calls join up.
          akto_conversation_id  only when the caller supplied a distinct one.
        Raw field names are forwarded alongside, as the shared helper does.
        """
        md = (kwargs.get("litellm_params", {}) if kwargs else {}).get("metadata", {}) or {}
        headers: Dict[str, str] = {}

        def put(name: str, value: Any) -> None:
            if value is None or value == "":
                return
            headers[f"x-akto-installer-{name}"] = (
                json.dumps(value) if isinstance(value, (dict, list)) else str(value)
            )

        session_id = md.get("session_id") or md.get("trace_id")
        call_id = (kwargs or {}).get("litellm_call_id") or md.get("litellm_call_id")

        # raw field names, as the shared helper forwards them
        put("session_id", md.get("session_id"))
        put("trace_id", md.get("trace_id"))
        put("litellm_call_id", call_id)
        put("user_agent", md.get("user_agent"))

        # normalized ids the backend keys traces on
        put("akto_session_id", session_id)
        put("akto_message_id", call_id)
        conversation = md.get("conversation_id")
        if conversation and conversation != session_id:
            put("akto_conversation_id", conversation)

        return headers

    def build_forwarded_headers(self, request_headers_raw: dict, host: str, kwargs: Optional[dict] = None) -> dict:
        """Mirrors all original client request headers (e.g. x-akto-* passthrough
        headers) to the ingested request, except credentials/cookies (never forwarded)
        and host/content-type (overridden to reflect the mirrored envelope).

        Session/trace identity is added as x-akto-installer-* headers; the client's
        own headers win on a clash so nothing it sent is overwritten."""
        headers_out = {
            k: v for k, v in (request_headers_raw or {}).items()
            if k.lower() not in EXCLUDED_FORWARD_HEADERS
        }
        for k, v in self.session_trace_headers(kwargs).items():
            headers_out.setdefault(k, v)
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

        headers_out = self.build_forwarded_headers(request_headers_raw, host, kwargs)

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