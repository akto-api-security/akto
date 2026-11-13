from litellm.integrations.custom_logger import CustomLogger
from litellm.proxy.proxy_server import UserAPIKeyAuth
from fastapi import HTTPException
from typing import Dict, Literal, Tuple, Optional, Any
from collections import OrderedDict
import asyncio
import codecs
import contextlib
import difflib
import hashlib
import httpx
import inspect
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
# AGENTIC sends traffic to Argus, ENDPOINT to Atlas.
AKTO_CONTEXT_SOURCE = os.getenv("AKTO_CONTEXT_SOURCE", "AGENTIC").strip().upper()
if AKTO_CONTEXT_SOURCE not in ("AGENTIC", "ENDPOINT"):
    AKTO_CONTEXT_SOURCE = "AGENTIC"


def _json_object_env(name: str) -> Optional[dict]:
    raw = os.getenv(name, "").strip()
    if not raw:
        return None
    try:
        value = json.loads(raw)
    except ValueError:
        value = None
    if not isinstance(value, dict):
        logger.warning(f"{name} is not a JSON object, ignoring it")
        return None
    return value


# Sent as akto_metadata, e.g. {"policy_name": "PII Strict, Secrets"}; unset sends none.
AKTO_METADATA = _json_object_env("AKTO_METADATA")
# Check model replies, streams and MCP tool results; false turns every reply check off.
RESPONSE_GUARDRAILS = os.getenv("RESPONSE_GUARDRAILS", "true").strip().lower() == "true"
STREAM_RESPONSE_GUARDRAILS = RESPONSE_GUARDRAILS and os.getenv("STREAM_RESPONSE_GUARDRAILS", "true").strip().lower() == "true"


def _positive_int_env(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, "").strip() or default)
    except ValueError:
        return default
    return value if value > 0 else default


# Check a stream every N text chunks, and at its end.
STREAMING_SAMPLING_RATE = _positive_int_env("STREAMING_SAMPLING_RATE", 5)
# call_type LiteLLM uses for an MCP tool call.
MCP_CALL_TYPE = "call_mcp_tool"
# The only calls whose replies are checked; their check records them, so the success log skips them.
CHECKED_REPLY_CALL_TYPES = {"completion", "acompletion", "text_completion", "atext_completion", "anthropic_messages",
                            "responses", "aresponses"}
# Credential-like header names, dropped from MCP headers on older LiteLLM.
CREDENTIAL_HEADER_HINTS = ("auth", "key", "token", "secret", "password", "credential", "cookie")

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
# Verdict-only: the full request still goes to the provider and is recorded with the reply or 403.
VERDICT_FLATTEN_CONTENT = os.getenv("VERDICT_FLATTEN_CONTENT", "true").strip().lower() == "true"
VERDICT_DROP_BODY_KEYS = tuple(
    k.strip() for k in os.getenv("VERDICT_DROP_BODY_KEYS", "stream,tools").split(",") if k.strip()
)

# Akto's guardrail detects reliably on short inputs but not on whole agent
# requests: an identical prompt-injection blocks at 63 chars yet is allowed
# inside an 86KB coding-agent request, where the user's text is buried under a
# large system prompt and tool catalogue. When the payload is big enough for
# that to bite, send only the newest user turn for the VERDICT call; the full request is still recorded.
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
EXCLUDED_FORWARD_HEADERS = {"authorization", "cookie", "host", "content-type", "content-length",
                            "x-api-key", "x-litellm-api-key", "api-key", "x-mcp-auth"}


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
        # litellm_call_ids whose block the blocking verdict call already recorded. Raising
        # HTTPException also triggers async_log_failure_event, so without this the
        # connector's own blocks would be recorded twice (measured: 2 ingests).
        self._own_block_ingested: "OrderedDict[str, None]" = OrderedDict()
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

    def build_http_proxy_params(self, *, guardrails: bool, ingest_data: bool, response_guardrails: bool = False) -> dict:
        params = {"akto_connector": AKTO_CONNECTOR_NAME}
        if guardrails:
            params["guardrails"] = "true"
        if response_guardrails:
            params["response_guardrails"] = "true"
        if ingest_data:
            params["ingest_data"] = "true"
        return params

    async def post_http_proxy(self, *, guardrails: bool, ingest_data: bool, http_proxy_payload: dict, response_guardrails: bool = False) -> httpx.Response:
        endpoint = f"{DATA_INGESTION_SERVICE_URL}{HTTP_PROXY_PATH}"
        return await self.client.post(
            endpoint,
            params=self.build_http_proxy_params(guardrails=guardrails, ingest_data=ingest_data, response_guardrails=response_guardrails),
            json=http_proxy_payload,
        )

    def parse_guardrails_result(self, result: Any) -> Tuple[bool, str, Optional[str]]:
        """Parse Akto guardrails response. Returns (allowed, reason, modified_payload)."""
        if not isinstance(result, dict):
            return True, "", None
        
        guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
        allowed = guardrails_result.get("Allowed", True)
        # Like native, only a "block" (or unset) behaviour blocks; alert, warn and approval verdicts pass.
        if str(guardrails_result.get("behaviour") or guardrails_result.get("Behaviour") or "").strip().lower() not in ("block", ""):
            allowed = True
        reason = guardrails_result.get("Reason", "")
        modified_payload = guardrails_result.get("ModifiedPayload")
        # /validate/response echoes the payload with Modified=false when nothing changed.
        if guardrails_result.get("Modified") is False:
            modified_payload = None

        return allowed, reason, modified_payload
    
    def apply_redaction(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth], kwargs: dict,
                        modified_payload: Any) -> Tuple[dict, bool]:
        """Akto's masking applied as text substitutions, keeping every other field and block; False if it can't be."""
        try:
            judged = json.loads(self.normalize_verdict_payload(self.build_payload(
                self._validation_view(data), call_type, None, user_api_key_dict, kwargs=kwargs))["requestPayload"])["body"]
            spans = self._masked_spans(judged, self._modified_body(modified_payload))
        except Exception as e:
            logger.error(f"Unreadable request masking: {e}")
            return data, False
        keys = [k for k in ("messages", "system", "input") if k in data]
        masked = {**data, **{k: self._replace_text(data[k], spans) for k in keys}}
        if not spans or any(old in text for old, _ in spans for text in self._texts([masked[k] for k in keys])):
            return data, False
        logger.info(f"Applied Akto masking to the request ({len(spans)} spans)")
        return masked, True

    def extract_request_path(self, kwargs: Optional[dict] = None, user_api_key_dict: Optional[UserAPIKeyAuth] = None) -> str:
        fallback = "/chat/completions"
        try:
            if kwargs is not None:
                litellm_params = kwargs.get("litellm_params", {})

                metadata = litellm_params.get("metadata", {})
                request_route = metadata.get("user_api_key_request_route")
                if request_route:
                    logger.info(f"Extracted path from metadata.user_api_key_request_route: {request_route}")
                    return request_route
            # Pass-through routes have no route in metadata at pre-call; the key auth has it.
            key_route = getattr(user_api_key_dict, "request_route", None)
            if isinstance(key_route, str) and key_route:
                return key_route

            logger.info(f"Using fallback path: {fallback}")
            return fallback
        except Exception as e:
            return fallback

    def sanitize_agent_name(self, name: str) -> Optional[str]:
        name = INVALID_AGENT_CHARS.sub("-", name.strip().lower())
        return name[:200] or None

    @staticmethod
    def _litellm_metadata(data: dict, kwargs: Optional[dict] = None) -> dict:
        """LiteLLM's request metadata (litellm_metadata on /v1/messages and /v1/responses)."""
        return (data.get("litellm_metadata") or data.get("metadata")
                or (kwargs.get("litellm_params", {}) if kwargs else {}).get("metadata") or {})

    def extract_agent_name(self, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth] = None, kwargs: Optional[dict] = None) -> Optional[str]:
        metadata = self._litellm_metadata(data, kwargs)

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
        record: bool = True,
    ) -> dict:
        if SYNC_MODE:
            return await self.validate_and_block(data, call_type, user_api_key_dict, kwargs, record=record)

        return data

    async def async_pre_call_hook(
        self,
        user_api_key_dict: UserAPIKeyAuth,
        data: dict,
        call_type: Literal["completion", "text_completion", "embeddings", "image_generation", "moderation", "audio_transcription"],
        **kwargs,
    ) -> dict:
        self._cache_request_headers_from_data(data)
        # Record LiteLLM's MCP tool check, not the MCP REST route's duplicate one.
        record = not (call_type == MCP_CALL_TYPE and "mcp_tool_name" not in data)
        return await self.handle_validation_hook(data, call_type, user_api_key_dict, kwargs, record=record)

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
        # Re-checks what async_pre_call_hook already checked and recorded.
        return await self.handle_validation_hook(data, call_type, user_api_key_dict, kwargs, record=False)

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
        # Client identity too (e.g. Claude Code's device/session ids), so tool calls are
        # attributed to the same client as the completion they came from.
        for k, v in self.client_identity_tags(kwargs).items():
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

    @staticmethod
    def _safe_mcp_headers(raw: dict) -> dict:
        """Raw MCP headers without credentials, cleaned as LiteLLM cleans them for logging."""
        try:
            from litellm.proxy._experimental.mcp_server.utils import logging_safe_mcp_headers
            return dict(logging_safe_mcp_headers(raw))
        except ImportError:
            # Older LiteLLM: drop upstream MCP credentials (x-mcp-*) and credential-like headers.
            return {k: v for k, v in raw.items() if not k.lower().startswith("x-mcp-")
                    and not any(hint in k.lower() for hint in CREDENTIAL_HEADER_HINTS)}

    async def _mcp_request_headers(self, data: dict, kwargs: Optional[dict] = None) -> dict:
        """Client headers of an MCP call, from LiteLLM's MCP auth context when available."""
        try:
            from litellm.proxy._experimental.mcp_server.server import get_auth_context
            context = get_auth_context()
            if inspect.isawaitable(context):  # async in older LiteLLM versions
                context = await context
            if context and context[5]:
                return self._safe_mcp_headers(dict(context[5]))
        except Exception:
            pass
        psr = data.get("proxy_server_request") or ((kwargs or {}).get("litellm_params") or {}).get("proxy_server_request") or {}
        return psr.get("headers") or (data.get("metadata") or {}).get("headers") or {}

    async def build_mcp_payload(
        self,
        data: dict,
        kwargs: Optional[dict] = None,
        user_api_key_dict: Optional[UserAPIKeyAuth] = None,
        *,
        result_texts: Optional[list] = None,
    ) -> dict:
        """An MCP tool call as /mcp JSON-RPC, like the native guardrail."""
        tool = str(data.get("mcp_tool_name") or data.get("name") or "unknown")
        server = str(data.get("mcp_server_name") or "unknown")
        arguments = data.get("mcp_arguments") or data.get("arguments")
        arguments = arguments if isinstance(arguments, dict) else {}
        metadata = data.get("metadata") if isinstance(data.get("metadata"), dict) else {}
        headers = await self._mcp_request_headers(data, kwargs)

        tags = self.build_tool_call_tags(is_mcp=True, tool_name=tool, mcp_server_name=server, mcp_tool_name=tool, model="")
        request_payload = self.build_tool_call_jsonrpc(tool, arguments)
        for k, v in {**self._key_identity_tags(user_api_key_dict), **self.key_metadata_tags({"metadata": metadata}),
                     **self.client_identity_tags(kwargs, data)}.items():
            tags.setdefault(k, v)
        response_body = None
        if result_texts is not None:
            response_body = {"jsonrpc": "2.0", "id": 1, "result": {"content": [{"type": "text", "text": t} for t in result_texts]}}
        client_ip = headers.get("x-forwarded-for", "").split(",")[0].strip() or headers.get("x-real-ip", "") or "0.0.0.0"
        return self.build_http_proxy_envelope(
            path=MCP_INGEST_PATH,
            request_headers=self.build_forwarded_headers(headers, self._resolve_host({"metadata": metadata}, user_api_key_dict), kwargs),
            response_headers={"content-type": "application/json"},
            request_payload=request_payload,
            response_payload=json.dumps(response_body) if response_body is not None else None,
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
                if is_mcp and self._response_checks_on(STREAM_RESPONSE_GUARDRAILS if stream else RESPONSE_GUARDRAILS):
                    continue  # the reply's tool-call check records it, with its verdict
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

            request_data = self._logged_request(kwargs)

            model_response_dict = self._dump(response_obj) if response_obj else None

            if SYNC_MODE:
                if self._recorded_by_checks(call_type, bool(kwargs.get("stream")), model_response_dict):
                    return
                await self.ingest_data(request_data, call_type, model_response_dict, user_api_key_dict, kwargs)
            else:
                await self.async_validate_and_ingest(request_data, call_type, model_response_dict, user_api_key_dict, kwargs)
        except Exception as e:
            logger.error(f"Guardrails post-call error: {e}")

    async def async_log_failure_event(self, kwargs: dict, response_obj: Any, start_time: Any, end_time: Any) -> None:
        """Ingest requests that never completed, so they are still visible in Akto.

        Without this, anything that does not reach the provider successfully is
        invisible: the only other ingestion paths are async_log_success_event
        (fires on success only) and the recorded verdict calls (THIS connector's
        own checks). So a request rejected by another guardrail - e.g. an
        AWS Bedrock guardrail returning 400 - was recorded nowhere, and neither
        were provider errors, rate limits or timeouts.

        Verified against a live Bedrock guardrail block: this callback fires with
        the original messages, the model, the raising exception (status 400) and
        litellm's top-level applied_guardrails list.
        """
        if not DATA_INGESTION_SERVICE_URL:
            return
        try:
            exception = kwargs.get("exception")
            call_id = kwargs.get("litellm_call_id")

            # Our own 403 is already recorded by the blocking verdict call; recording
            # it again here would double-count every block.
            if call_id and str(call_id) in self._own_block_ingested:
                self._own_block_ingested.pop(str(call_id), None)
                return
            detail = getattr(exception, "detail", None)
            if isinstance(detail, str) and detail.startswith("Blocked by Akto Guardrails"):
                return

            litellm_params = kwargs.get("litellm_params", {}) or {}
            metadata = litellm_params.get("metadata", {}) or {}
            user_api_key_dict = metadata.get("user_api_key_dict")
            call_type = kwargs.get("call_type", "completion")

            request_data = self._logged_request(kwargs)

            status_code = getattr(exception, "status_code", None)
            try:
                status_code = int(status_code)
            except (TypeError, ValueError):
                status_code = 500

            failure_body = {"error": str(exception)[:2000] if exception else "request failed"}
            applied = kwargs.get("applied_guardrails") or metadata.get("applied_guardrails")
            if applied:
                failure_body["applied_guardrails"] = applied

            await self.ingest_response(
                request_data,
                call_type,
                failure_body,
                status_code,
                user_api_key_dict,
                kwargs,
            )
            logger.info(
                f"Ingested failed request (status {status_code}"
                + (f", blocked by {applied}" if applied else "") + ")"
            )
        except Exception as e:
            logger.error(f"Guardrails failure-event ingestion error: {e}")

    def _response_checks_on(self, enabled: bool) -> bool:
        return SYNC_MODE and enabled and bool(DATA_INGESTION_SERVICE_URL)

    @staticmethod
    def _checkable_reply(response_dict: Any) -> bool:
        """True for a model reply (chat, Anthropic or Responses API), not embeddings, images etc."""
        return isinstance(response_dict, dict) and bool(
            response_dict.get("choices") or response_dict.get("content") or response_dict.get("output"))

    def _recorded_by_checks(self, call_type: str, stream: bool, response_dict: Any) -> bool:
        """True when a response check records this call, so the success log skips it."""
        if not DATA_INGESTION_SERVICE_URL:
            return False
        if call_type == MCP_CALL_TYPE:
            return RESPONSE_GUARDRAILS
        if call_type not in CHECKED_REPLY_CALL_TYPES:
            return False
        if stream:
            return STREAM_RESPONSE_GUARDRAILS
        return RESPONSE_GUARDRAILS and self._checkable_reply(response_dict)

    async def async_post_call_success_hook(self, data: dict, user_api_key_dict: UserAPIKeyAuth, response: Any) -> Any:
        """Checks a non-streamed reply and its MCP tool calls; blocks with 403 or applies masking."""
        if not self._response_checks_on(RESPONSE_GUARDRAILS):
            return response
        try:
            response_dict = response.model_dump() if hasattr(response, "model_dump") else response
        except Exception:
            return response
        if not self._checkable_reply(response_dict):
            return response
        modified = await self._check_response(data, response_dict, user_api_key_dict)
        if modified and not self._apply_reply_redaction(response, modified):
            raise await self._block_reply(data, user_api_key_dict, "sensitive data in the response")
        try:
            tool_calls = self._extract_tool_calls(response)
        except Exception as e:
            logger.error(f"Reply tool calls unreadable, not checked (fail-open): {e}")
            tool_calls = []
        await self._check_reply_tool_calls(data, tool_calls, user_api_key_dict)
        return response

    async def async_post_call_streaming_iterator_hook(self, user_api_key_dict: UserAPIKeyAuth, response: Any, request_data: dict):
        """Checks a streamed reply every N text chunks and at its end; only checked text goes out."""
        if not self._response_checks_on(STREAM_RESPONSE_GUARDRAILS) or self._reply_call_type(request_data) not in CHECKED_REPLY_CALL_TYPES:
            async for chunk in response:  # streams it can't read (e.g. pass-through) pass as before
                yield chunk
            return
        parts: list = []
        tool_calls: dict = {}
        unchecked = 0
        # Chunks wait for a check covering their text; the last one waits for the end check.
        held: list = []
        # Raw SSE events can span chunks, so only complete events are read.
        decoder, sse = codecs.getincrementaldecoder("utf-8")(errors="ignore"), ""

        def args_len() -> int:
            return sum(len(c["arguments"]) for c in tool_calls.values())

        def read(readable: Any) -> bool:
            before = args_len()
            try:
                text = self._stream_text(readable)
                self._collect_stream_tool_calls(readable, tool_calls)
            except Exception as e:
                logger.error(f"Unreadable stream chunk, passed unchecked (fail-open): {e}")
                text = ""
            if text:
                parts.append(text)
            return bool(text) or args_len() > before  # tool arguments are checked as they stream too

        ended = False
        # A mid-stream check runs while chunks keep arriving, with how many held chunks it covers.
        pending: Optional[Tuple[asyncio.Future, int]] = None
        try:
            async for chunk in response:
                readable = chunk
                if isinstance(chunk, (bytes, str)):
                    sse += (decoder.decode(chunk) if isinstance(chunk, bytes) else chunk).replace("\r\n", "\n")
                    readable, _, sse = sse.rpartition("\n\n")
                if read(readable):
                    unchecked += 1
                held.append(chunk)
                if pending and pending[0].done():
                    await pending[0]
                    for c in held[:pending[1]]:
                        yield c
                    held, pending = held[pending[1]:], None
                if pending is None and unchecked >= STREAMING_SAMPLING_RATE:
                    unchecked = 0
                    pending = (asyncio.ensure_future(self._check_stream(
                        request_data, "".join(parts), {k: dict(v) for k, v in tool_calls.items()}, user_api_key_dict,
                        final=False)), len(held) - 1)
            if sse:
                read(sse)
            ended = True
            if pending:
                await pending[0]
            await self._check_stream(request_data, "".join(parts), tool_calls, user_api_key_dict)
            await self._check_reply_tool_calls(request_data, list(tool_calls.values()), user_api_key_dict)
            for c in held:
                yield c
        except GeneratorExit:
            if not ended:  # the client left mid-stream, so record the reply so far
                with contextlib.suppress(Exception):
                    await self._check_response(request_data, self._text_reply("".join(parts)), user_api_key_dict, can_mask=False)
            raise
        finally:
            if pending and not pending[0].done():
                pending[0].cancel()

    async def async_post_mcp_tool_call_hook(self, kwargs: dict, response_obj: Any, start_time: Any, end_time: Any) -> Any:
        """Checks an MCP tool result; a blocked one is replaced, as LiteLLM ignores errors here."""
        if not self._response_checks_on(RESPONSE_GUARDRAILS):
            return None
        try:
            result = response_obj.mcp_tool_call_response or []
            # LiteLLM <= 1.89 hands over the CallToolResult as (field, value) pairs and ignores this hook's
            # return, so its content list is changed in place.
            fields = dict(p for p in result if isinstance(p, tuple) and len(p) == 2)
            content = fields.get("content") if isinstance(fields.get("content"), list) else None
            items = list(content if content is not None else result)

            def put(new_items: list) -> Any:
                if content is not None:
                    content[:] = new_items
                else:
                    response_obj.mcp_tool_call_response = new_items
                return response_obj

            texts = [t for t in (self._get_attr(c, "text") for c in items) if isinstance(t, str)]
            data = self._mcp_call_from_kwargs(kwargs)
            payload = await self.build_mcp_payload(data, kwargs, result_texts=texts)
            allowed, reason, modified = await self._verdict(payload, response_side=True, record=True)
            if allowed and not modified:
                return None
            masked = self._masked_mcp_texts(modified) if allowed else None
            if masked is not None and len(masked) == len(texts):
                replacements = iter(masked)
                logger.info(f"Masked MCP tool result: {data['mcp_tool_name']}")
                return put([self._with_text(c, next(replacements)) if isinstance(self._get_attr(c, "text"), str) else c for c in items])
            message = f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails"
            await self._ingest_blocked(lambda: payload, reason)
            logger.info(f"Blocked MCP tool result: {data['mcp_tool_name']} ({reason})")
            try:
                from mcp.types import TextContent
                return put([TextContent(type="text", text=message)])
            except ImportError:
                return put([{"type": "text", "text": message}])
        except Exception as e:
            logger.error(f"MCP tool result guardrail error (fail-open): {e}")
            return None

    async def _check_response(self, data: dict, response_dict: dict, user_api_key_dict: Optional[UserAPIKeyAuth],
                              *, record: bool = True, can_mask: bool = True) -> Optional[str]:
        """Checks and records a reply; raises 403 when blocked, else returns any masked payload."""
        kwargs = self._kwargs_from_data(data)
        call_type = self._reply_call_type(data)
        try:
            payload = self.build_payload(data, call_type, response_dict, user_api_key_dict, kwargs=kwargs)
        except Exception as e:
            logger.error(f"Response guardrail payload error (fail-open): {e}")
            return None
        allowed, reason, modified = await self._verdict(payload, response_side=True, record=record)
        if allowed and not (modified and not can_mask):
            return modified
        reason = reason if not allowed else "sensitive data in the response"
        raise (await self._block_reply(data, user_api_key_dict, reason)) if record else self._blocked(reason)

    async def _check_stream(self, data: dict, text: str, tool_calls: dict, user_api_key_dict: Optional[UserAPIKeyAuth],
                            *, final: bool = True) -> None:
        """Only the final check records, or a mid-stream check that blocks."""
        reply = self._text_reply(text, list(tool_calls.values()))
        try:
            # A streamed reply can't be masked once sent, so masking blocks it.
            await self._check_response(data, reply, user_api_key_dict, record=final, can_mask=False)
        except HTTPException:
            if not final:
                with contextlib.suppress(HTTPException):
                    await self._check_response(data, reply, user_api_key_dict, can_mask=False)
            raise

    async def _check_reply_tool_calls(self, data: dict, tool_calls: list, user_api_key_dict: Optional[UserAPIKeyAuth]) -> None:
        """Checks and records the reply's mcp__<server>__<tool> calls; a blocked one blocks the reply."""
        kwargs = self._kwargs_from_data(data)
        for call in tool_calls:
            is_mcp, server, tool = self.parse_mcp_tool_name(call.get("name") or "")
            if not is_mcp:
                continue
            try:
                mcp_call = {
                    "mcp_tool_name": tool,
                    "mcp_arguments": self._parse_tool_arguments(call.get("arguments") or None),
                    "mcp_server_name": server,
                    "metadata": (kwargs.get("litellm_params") or {}).get("metadata") or {},
                    "proxy_server_request": data.get("proxy_server_request"),
                }
                payload = await self.build_mcp_payload(mcp_call, kwargs, user_api_key_dict)
            except Exception as e:
                logger.error(f"MCP tool call guardrail payload error (fail-open): {e}")
                continue
            allowed, reason, modified = await self._verdict(payload, record=True)
            if not allowed or modified:
                reason = reason or "sensitive data in an MCP tool call"
                await self._ingest_blocked(lambda: payload, reason)
                raise self._blocked(reason)

    @staticmethod
    def _blocked(reason: str) -> HTTPException:
        return HTTPException(status_code=403, detail=f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails")

    async def _ingest_blocked(self, build: Any, reason: str) -> None:
        """Records a block as a 403, like other Akto connectors; never raises."""
        try:
            payload = build()
            if inspect.isawaitable(payload):
                payload = await payload
            blocked_by = {"x-blocked-by": "Akto Proxy"}
            await self._ingest_only({**payload, "statusCode": "403", "status": "403",
                                     "responseHeaders": json.dumps({"content-type": "application/json", **blocked_by}),
                                     "responsePayload": json.dumps({"body": {**blocked_by, "reason": reason or "Policy violation"}})})
        except Exception as e:
            logger.error(f"Blocked request ingestion failed: {e}")

    async def _block_reply(self, data: dict, user_api_key_dict: Optional[UserAPIKeyAuth], reason: str) -> HTTPException:
        """Records a blocked reply as a 403 and returns the error to raise."""
        kwargs = self._kwargs_from_data(data)
        await self._ingest_blocked(
            lambda: self.build_payload(data, self._reply_call_type(data), None, user_api_key_dict, kwargs=kwargs), reason)
        return self._blocked(reason)

    @staticmethod
    def _reply_call_type(data: dict) -> str:
        return str(getattr(data.get("litellm_logging_obj"), "call_type", None) or data.get("call_type") or "completion")

    @staticmethod
    def _modified_body(modified_payload: Any) -> Any:
        """The body of a ModifiedPayload ({"body": ...}, or a bare JSON document), or None."""
        try:
            body = json.loads(modified_payload) if isinstance(modified_payload, str) else modified_payload
            body = body.get("body", body) if isinstance(body, dict) else body
            return json.loads(body) if isinstance(body, str) else body
        except (TypeError, ValueError):
            return None

    def _apply_reply_redaction(self, response: Any, modified_payload: Any) -> bool:
        """Writes masked text into the reply (chat, text completion, Anthropic, Responses); all or nothing."""
        body = self._modified_body(modified_payload)
        get = self._get_attr
        try:
            # Chat choices hold a message; text completion choices hold text.
            targets = [(get(c, "message"), "content", body["choices"][i]["message"]["content"]) if get(c, "message") is not None
                       else (c, "text", body["choices"][i]["text"]) for i, c in enumerate(get(response, "choices") or [])]
            content = get(response, "content")
            targets += [(b, "text", body["content"][j]["text"])
                        for j, b in enumerate(content if isinstance(content, list) else []) if get(b, "type") == "text"]
            targets += [(p, "text", body["output"][k]["content"][m]["text"])
                        for k, item in enumerate(get(response, "output") or [])
                        for m, p in enumerate(get(item, "content") or []) if get(p, "type") == "output_text"]
        except (KeyError, IndexError, TypeError):
            return False
        if not targets or not all(c is not None and isinstance(t, (str, type(None))) for c, _, t in targets):
            return False
        for container, key, text in targets:
            if isinstance(container, dict):
                container[key] = text
            else:
                setattr(container, key, text)
        logger.info("Applied Akto masking to the reply")
        return True

    @staticmethod
    def _string_paths(value: Any, path: tuple = ()) -> dict:
        if isinstance(value, str):
            return {path: value}
        items = value.items() if isinstance(value, dict) else enumerate(value) if isinstance(value, list) else ()
        return {p: text for k, v in items for p, text in GuardrailsHandler._string_paths(v, path + (k,)).items()}

    @staticmethod
    def _masked_spans(judged: Any, masked: Any) -> list:
        """(original, masked) word spans Akto changed between the text it judged and its masked copy."""
        before, after = GuardrailsHandler._string_paths(judged), GuardrailsHandler._string_paths(masked)
        spans = []
        for path in before.keys() & after.keys():
            old, new = before[path].split(" "), after[path].split(" ")
            for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, old, new, autojunk=False).get_opcodes():
                if tag != "equal" and i2 > i1:
                    spans.append((" ".join(old[i1:i2]), " ".join(new[j1:j2])))
        return spans

    @staticmethod
    def _replace_text(value: Any, spans: list) -> Any:
        """value with each span substituted in its text: string content, text blocks and nested content."""
        if isinstance(value, str):
            for old, new in spans:
                value = value.replace(old, new)
            return value
        if isinstance(value, list):
            return [GuardrailsHandler._replace_text(v, spans) for v in value]
        if isinstance(value, dict):
            return {k: GuardrailsHandler._replace_text(v, spans) if k in ("content", "text") else v for k, v in value.items()}
        return value

    @staticmethod
    def _texts(value: Any) -> list:
        """The strings _replace_text edits."""
        if isinstance(value, str):
            return [value]
        if isinstance(value, list):
            return [t for v in value for t in GuardrailsHandler._texts(v)]
        if isinstance(value, dict):
            return [t for k in ("content", "text") for t in GuardrailsHandler._texts(value.get(k))]
        return []

    def _masked_mcp_texts(self, modified_payload: Any) -> Optional[list]:
        body = self._modified_body(modified_payload)
        try:
            texts = [c.get("text") for c in body["result"]["content"]]
        except (KeyError, TypeError, AttributeError):
            return None
        return texts if all(isinstance(t, str) for t in texts) else None

    def _mcp_call_from_kwargs(self, kwargs: dict) -> dict:
        """An MCP tool call's data from LiteLLM's logging kwargs."""
        call = kwargs.get("mcp_tool_call_metadata") or {}
        litellm_params = kwargs.get("litellm_params") or {}
        return {
            "mcp_tool_name": self._get_attr(call, "name"),
            "mcp_arguments": self._get_attr(call, "arguments"),
            "mcp_server_name": self._get_attr(call, "mcp_server_name"),
            "metadata": litellm_params.get("metadata") or {},
            "proxy_server_request": litellm_params.get("proxy_server_request"),
        }

    @staticmethod
    def _mcp_texts(response_dict: Any) -> list:
        """Text items of a logged MCP tool result (a content list, or a result holding one)."""
        items = response_dict.get("content") if isinstance(response_dict, dict) else response_dict
        return [i["text"] for i in items or [] if isinstance(i, dict) and isinstance(i.get("text"), str)]

    @staticmethod
    def _with_text(item: Any, text: str) -> Any:
        if isinstance(item, dict):
            return {**item, "text": text}
        return item.model_copy(update={"text": text}) if hasattr(item, "model_copy") else item

    @staticmethod
    def _kwargs_from_data(data: dict) -> dict:
        """kwargs-shaped view of a call hook's request; the MCP pre-check's comes from its logger."""
        details = getattr(data.get("litellm_logging_obj"), "model_call_details", None)
        if not data.get("litellm_call_id") and isinstance(details, dict) and details.get("litellm_call_id"):
            return details
        md = data.get("litellm_metadata")
        if not isinstance(md, dict) or not md:
            md = data.get("metadata") if isinstance(data.get("metadata"), dict) else {}
        return {
            "litellm_params": {"metadata": md, "proxy_server_request": data.get("proxy_server_request")},
            "litellm_call_id": data.get("litellm_call_id"),
        }

    @staticmethod
    def _dump(value: Any) -> Any:
        """JSON-ready response; an MCP tool call logs a list of content items."""
        if isinstance(value, list):
            return [GuardrailsHandler._dump(v) for v in value]
        return value.model_dump() if hasattr(value, "model_dump") else value

    @staticmethod
    def _text_reply(text: str, tool_calls: Optional[list] = None) -> dict:
        message = {"role": "assistant", "content": text}
        if tool_calls:
            message["tool_calls"] = [{"type": "function", "function": c} for c in tool_calls]
        return {"choices": [{"message": message}]}

    @staticmethod
    def _sse_events(chunk: Any) -> Optional[list]:
        """Events of a raw SSE chunk (Anthropic /v1/messages) or a dict chunk; None for a typed chunk."""
        if isinstance(chunk, dict):
            return [chunk]
        if not isinstance(chunk, (bytes, str)):
            return None
        raw = chunk.decode("utf-8", "ignore") if isinstance(chunk, bytes) else chunk
        events = []
        for line in raw.splitlines():
            if line.startswith("data:"):
                try:
                    event = json.loads(line[5:].strip())
                except ValueError:
                    continue
                if isinstance(event, dict):
                    events.append(event)
        return events

    @staticmethod
    def _stream_text(chunk: Any) -> str:
        """Reply text in a stream chunk (chat, text completion, Responses API or Anthropic SSE)."""
        events = GuardrailsHandler._sse_events(chunk)
        if events is None:
            choices = getattr(chunk, "choices", None)
            if choices:
                # Chat chunks hold a delta, text completion chunks hold text.
                content = getattr(getattr(choices[0], "delta", None), "content", None) or getattr(choices[0], "text", None)
                return content if isinstance(content, str) else ""
            # Responses API: reply text only, not reasoning or function call argument deltas.
            if getattr(chunk, "type", None) != "response.output_text.delta":
                return ""
            delta = getattr(chunk, "delta", None)
            return delta if isinstance(delta, str) else ""
        return "".join(e["delta"]["text"] for e in events
                       if isinstance(e.get("delta"), dict) and isinstance(e["delta"].get("text"), str))

    def _collect_stream_tool_calls(self, chunk: Any, calls: dict) -> None:
        """Assembles streamed tool calls (chat deltas or Anthropic tool_use blocks) into calls."""
        events = self._sse_events(chunk)
        if events is None:
            choices = getattr(chunk, "choices", None) or []
            delta = getattr(choices[0], "delta", None) if choices else None
            for tc in getattr(delta, "tool_calls", None) or []:
                fn = self._get_attr(tc, "function")
                entry = calls.setdefault(("chat", self._get_attr(tc, "index", 0)), {"name": "", "arguments": ""})
                entry["name"] = entry["name"] or self._get_attr(fn, "name") or ""
                entry["arguments"] += self._get_attr(fn, "arguments") or ""
            return
        for event in events:
            block = event.get("content_block") if isinstance(event.get("content_block"), dict) else {}
            delta = event.get("delta") if isinstance(event.get("delta"), dict) else {}
            key = ("messages", event.get("index"))
            if event.get("type") == "content_block_start" and block.get("type") == "tool_use":
                calls[key] = {"name": block.get("name") or "", "arguments": ""}
            elif delta.get("type") == "input_json_delta" and key in calls:
                calls[key]["arguments"] += delta.get("partial_json") or ""

    @staticmethod
    def _logged_request(kwargs: dict) -> dict:
        """The request as LiteLLM logs it; the Anthropic system prompt only survives in the original body."""
        body = ((kwargs.get("litellm_params") or {}).get("proxy_server_request") or {}).get("body") or {}
        return {
            "model": kwargs.get("model", ""),
            "messages": kwargs.get("messages", []),
            "stream": kwargs.get("stream", False),
            "tools": kwargs.get("tools", []),
            "system": body.get("system") if isinstance(body, dict) else None,
        }

    async def validate_and_block(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None,
                                 kwargs: Optional[dict] = None, *, record: bool = True) -> dict:
        # Call hooks get no litellm_params, so path and identity come from the request.
        verdict_kwargs = kwargs if (kwargs or {}).get("litellm_params") else self._kwargs_from_data(data)
        try:
            allowed, reason, modified_payload = await self.call_guardrails_validation(
                data, call_type, user_api_key_dict, verdict_kwargs, record=record)
            is_mcp = call_type == MCP_CALL_TYPE
            if is_mcp and modified_payload:
                # MCP arguments can't be masked, so masking blocks them.
                allowed = False
            elif allowed and modified_payload:
                data, masked = self.apply_redaction(data, call_type, user_api_key_dict, verdict_kwargs, modified_payload)
                allowed = masked  # masking that can't be applied blocks
                reason = reason or ("" if masked else "sensitive data that could not be masked")

            if not allowed:
                # Remember this turn so it can be stripped from history later -
                # the client keeps rejected messages and resends them.
                if not is_mcp:
                    self.remember_blocked(data, kwargs)
                # Record the verdict if this re-check didn't, then the 403.
                if not record:
                    await self.call_guardrails_validation(data, call_type, user_api_key_dict, verdict_kwargs, record=True)
                await self._ingest_blocked(
                    lambda: self.build_mcp_payload(data, verdict_kwargs, user_api_key_dict) if is_mcp
                    else self.build_payload(data, call_type, None, user_api_key_dict, kwargs=verdict_kwargs), reason)
                own_id = verdict_kwargs.get("litellm_call_id")
                if own_id:
                    self._own_block_ingested[str(own_id)] = None
                    while len(self._own_block_ingested) > 500:
                        self._own_block_ingested.popitem(last=False)
                raise HTTPException(
                    status_code=403,
                    detail=f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails",
                )
            
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
            if call_type == MCP_CALL_TYPE:
                http_proxy_payload = await self.build_mcp_payload(
                    self._mcp_call_from_kwargs(kwargs or {}), kwargs, user_api_key_dict, result_texts=self._mcp_texts(response_dict))
            else:
                http_proxy_payload = self.build_payload(data, call_type, response_dict, user_api_key_dict, status_code=200, kwargs=kwargs)
            # The reply is checked too, so its violations are reported; nothing is blocked in async mode.
            response = await self.post_http_proxy(guardrails=True, response_guardrails=RESPONSE_GUARDRAILS and response_dict is not None,
                                                  ingest_data=True, http_proxy_payload=http_proxy_payload)
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
            # Coding agents' large system prompts count too.
            return len(json.dumps([msgs, data.get("system")], default=str)) > GUARDRAIL_NARROW_THRESHOLD_BYTES
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
            trimmed.pop("system", None)
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
        trimmed.pop("system", None)
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

    async def call_guardrails_validation(self, data: dict, call_type: str, user_api_key_dict: Optional[UserAPIKeyAuth] = None,
                                         kwargs: Optional[dict] = None, *, record: bool = True) -> Tuple[bool, str, Optional[str]]:
        if not DATA_INGESTION_SERVICE_URL:
            return True, "", None

        if call_type == MCP_CALL_TYPE:
            return await self._verdict(await self.build_mcp_payload(data, kwargs, user_api_key_dict), record=record)
        # _validation_view scopes WHAT gets judged; normalize_verdict_payload fixes
        # the SHAPE it is judged in. Both apply to the verdict copy only.
        return await self._verdict(self.normalize_verdict_payload(
            self.build_payload(self._validation_view(data), call_type, None, user_api_key_dict, kwargs=kwargs)
        ), record=record)

    async def _verdict(self, http_proxy_payload: dict, *, response_side: bool = False, record: bool = False) -> Tuple[bool, str, Optional[str]]:
        """One request- or response-side verdict, failing open; record also records the traffic."""
        try:
            response = await self.post_http_proxy(
                guardrails=not response_side, response_guardrails=response_side, ingest_data=record,
                http_proxy_payload=http_proxy_payload)
        except Exception as e:
            # Unreachable: recording would only wait out a second timeout.
            logger.info(f"Guardrails validation failed (fail-open): {e}")
            return True, "", None
        if response.status_code != 200:
            logger.info(f"Guardrails validation returned HTTP {response.status_code} (fail-open)")
            if record:
                await self._ingest_only(http_proxy_payload)  # the check didn't record it
            return True, "", None
        try:
            return self.parse_guardrails_result(response.json())
        except Exception as e:
            logger.error(f"Guardrails validation error (fail-open): {e}")
            return True, "", None

    async def _ingest_only(self, http_proxy_payload: dict) -> None:
        """Records traffic without a check; never raises."""
        try:
            await self.post_http_proxy(guardrails=False, ingest_data=True, http_proxy_payload=http_proxy_payload)
        except Exception as e:
            logger.error(f"Ingestion failed: {e}")

    @staticmethod
    def _blocks_text(blocks: list) -> str:
        """Text in content blocks: text, plain-text documents and search results."""
        texts = []
        for block in blocks:
            if not isinstance(block, dict):
                continue
            kind = block.get("type", "text")
            if kind == "text":
                texts.append(block.get("text") or "")
            elif kind in ("document", "search_result"):
                source = block.get("source")
                if isinstance(source, dict) and source.get("type") == "text":
                    texts.append(source.get("data") or "")
                elif isinstance(source, dict) and source.get("type") == "content":
                    texts.append(GuardrailsHandler._blocks_text(source.get("content") or []))
                if isinstance(block.get("content"), list):
                    texts.append(GuardrailsHandler._blocks_text(block["content"]))
                texts += [block.get(k) for k in ("title", "context") if isinstance(block.get(k), str)]
        return " ".join(t for t in texts if t).strip()

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
                        message["content"] = GuardrailsHandler._blocks_text(content)
                        changed = True
                if isinstance(body.get("system"), list):
                    body["system"] = GuardrailsHandler._blocks_text(body["system"])
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
        metadata = self._litellm_metadata(data, kwargs)
        key_metadata = metadata.get("user_api_key_metadata") or {}
        return {str(k): self._tag_value(v) for k, v in key_metadata.items() if v is not None}

    def client_identity_tags(self, kwargs: Optional[dict] = None, data: Optional[dict] = None) -> dict:
        """Identity the CLIENT supplied, surfaced as queryable Akto tags.

        Reachable without any virtual key, so traffic stays attributable even
        behind a shared key:
          metadata.user_id            Anthropic `metadata.user_id`. Claude Code
                                      puts {device_id, account_uuid, session_id}
                                      here on every request. Read from the request
                                      body (data) when litellm_params.metadata is
                                      not populated yet, as at pre-call time, so the
                                      guardrail verdict and blocked-request ingestion
                                      carry the same identity as the post-call ingest.
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
        raw_user = md.get("user_id") or ((data or {}).get("metadata") or {}).get("user_id")
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

    @staticmethod
    def _key_identity_tags(user_api_key_dict: Optional[UserAPIKeyAuth]) -> dict:
        """The LiteLLM key's alias, team, user and email as tags."""
        tags = {}
        for attr in ("key_alias", "team_id", "user_id", "user_email"):
            value = getattr(user_api_key_dict, attr, None) if user_api_key_dict else None
            if value:
                tags[attr] = value
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
        tags.update(self._key_identity_tags(user_api_key_dict))
        # Surface all virtual-key metadata as tags, without clobbering the core tags above.
        for k, v in self.key_metadata_tags(data, kwargs).items():
            tags.setdefault(k, v)
        # Then client-supplied identity (Claude Code account/device/session,
        # user-agent, caller tags, x-litellm-spend-logs-metadata). setdefault so
        # key metadata always wins on a name clash.
        for k, v in self.client_identity_tags(kwargs, data).items():
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
            # Also any *authorization header, e.g. x-mcp-<server>-authorization.
            if k.lower() not in EXCLUDED_FORWARD_HEADERS and "authorization" not in k.lower()
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
        envelope = {
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
            "contextSource": AKTO_CONTEXT_SOURCE,
        }
        if AKTO_METADATA:
            envelope["akto_metadata"] = json.dumps(AKTO_METADATA)
        return envelope

    def build_payload(self, data: dict, call_type: str, response_obj: Optional[Any], user_api_key_dict: Optional[UserAPIKeyAuth] = None, status_code: int = 200, kwargs: Optional[dict] = None) -> dict:
        request_body = {
            "model": data.get("model", ""),
            "messages": data.get("messages", []),
            "stream": data.get("stream", False),
            "tools": data.get("tools", []),
        }
        # Responses API prompt (input) and Anthropic system prompt (system).
        for key in ("input", "system"):
            if data.get(key) is not None:
                request_body[key] = data[key]

        request_path = self.extract_request_path(kwargs, user_api_key_dict)
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