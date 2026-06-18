"""
Akto Guardrails Middleware for LangChain.

Class-based AgentMiddleware that intercepts model calls to enforce Akto guardrails.
Uses the flat HTTP proxy payload format consistent with other Akto connectors
(github-cli-hooks, cursor-hooks, etc.).

Usage:
    from akto_middleware import AktoGuardrailsMiddleware
    from langchain.agents import create_agent

    agent = create_agent(
        model="gpt-4.1",
        tools=[...],
        middleware=[AktoGuardrailsMiddleware()],
    )

Environment variables:
    AKTO_DATA_INGESTION_URL  Akto service base URL (required)
    AKTO_SYNC_MODE           "true" to block on violation, "false" to log-only (default: "true")
    AKTO_TIMEOUT             HTTP timeout in seconds (default: "5")
    AKTO_INSTANCE_IP         Source IP in proxy payloads (auto-detected if unset)
    LOG_LEVEL                Logging level (default: "INFO")
    LOG_PAYLOADS             Log full payloads, privacy-sensitive (default: "false")
"""

import json
import logging
import os
import socket
import time
from typing import Any, Optional, Tuple

import httpx

from langchain.agents.middleware import AgentMiddleware, AgentState
from langgraph.runtime import Runtime

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL", "")
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "true").lower() == "true"

AKTO_CONNECTOR = "langchain"
HTTP_PROXY_PATH = "/api/http-proxy"
LANGCHAIN_API_HOST = os.getenv("LANGCHAIN_API_HOST", "api.langchain.com")
LANGCHAIN_API_PATH = os.getenv("LANGCHAIN_API_PATH", "/langchain/chat")

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

_instance_ip: Optional[str] = None


def _get_instance_ip() -> str:
    """Return this host's primary IP for Akto proxy payloads."""
    global _instance_ip
    if _instance_ip is not None:
        return _instance_ip

    configured = os.getenv("AKTO_INSTANCE_IP", "").strip()
    if configured:
        _instance_ip = configured
        return _instance_ip

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            _instance_ip = sock.getsockname()[0]
            return _instance_ip
    except OSError:
        pass

    try:
        _instance_ip = socket.gethostbyname(socket.gethostname())
        return _instance_ip
    except OSError:
        _instance_ip = "127.0.0.1"
        return _instance_ip


# ---------------------------------------------------------------------------
# Middleware class
# ---------------------------------------------------------------------------

class AktoGuardrailsMiddleware(AgentMiddleware):
    """
    LangChain AgentMiddleware that enforces Akto guardrails on model calls.

    In SYNC_MODE (default):
      - before_model: validates prompt; raises ValueError if blocked
      - after_model: validates LLM response; raises ValueError if blocked, then ingests

    In async mode (AKTO_SYNC_MODE=false):
      - All interactions are ingested after the fact (log-only)
    """

    def __init__(self, connector: str = AKTO_CONNECTOR) -> None:
        super().__init__()
        self._connector = connector
        client_headers = {"Authorization": AKTO_API_TOKEN} if AKTO_API_TOKEN else {}
        client_kwargs = {
            "timeout": AKTO_TIMEOUT,
            "headers": client_headers,
        }
        self._sync_client = httpx.Client(**client_kwargs)
        self._async_client = httpx.AsyncClient(
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
            **client_kwargs,
        )
        logger.info(
            f"AktoGuardrailsMiddleware initialized | connector={connector} "
            f"sync_mode={AKTO_SYNC_MODE} url={AKTO_DATA_INGESTION_URL or '(not set)'}"
        )

    # ------------------------------------------------------------------
    # AgentMiddleware hooks
    # ------------------------------------------------------------------

    def before_model(self, state: AgentState, runtime: Runtime) -> Optional[dict[str, Any]]:
        """Validate prompt messages against Akto guardrails before the LLM call."""
        if not AKTO_DATA_INGESTION_URL:
            return None

        messages = self._extract_messages(state)
        if not messages:
            return None

        model = self._extract_model(runtime)

        if AKTO_SYNC_MODE:
            try:
                self._validate_request_and_maybe_block_sync(messages, model)
            except ValueError:
                raise
            except Exception as e:
                logger.error(f"before_model guardrails error (fail-open): {e}")

        return None

    def after_model(self, state: AgentState, runtime: Runtime) -> Optional[dict[str, Any]]:
        """Validate LLM response against Akto guardrails, then ingest for audit."""
        if not AKTO_DATA_INGESTION_URL:
            return None
        if self._is_intermediate_tool_call_step(state):
            return None

        messages = self._extract_messages(state)
        model = self._extract_model(runtime)

        try:
            if AKTO_SYNC_MODE:
                self._validate_response_and_maybe_block_sync(messages, model)
            else:
                payload = self._build_model_payload(
                    messages=messages,
                    model=model,
                    response_body=None,
                    status_code="200",
                )
                self._ingest_sync(payload)
        except ValueError:
            raise
        except Exception as e:
            logger.error(f"after_model guardrails error (fail-open): {e}")

        return None

    # ------------------------------------------------------------------
    # Async AgentMiddleware hooks (used by astream / ainvoke)
    # ------------------------------------------------------------------

    async def abefore_model(self, state: AgentState, runtime: Runtime) -> Optional[dict[str, Any]]:
        """Async version of before_model — called when agent runs asynchronously."""
        if not AKTO_DATA_INGESTION_URL:
            return None
        messages = self._extract_messages(state)
        if not messages:
            return None
        model = self._extract_model(runtime)
        if AKTO_SYNC_MODE:
            try:
                await self._validate_request_and_maybe_block(messages, model)
            except ValueError:
                raise
            except Exception as e:
                logger.error(f"abefore_model guardrails error (fail-open): {e}")
        return None

    async def aafter_model(self, state: AgentState, runtime: Runtime) -> Optional[dict[str, Any]]:
        """Async version of after_model — validate response, then ingest."""
        if not AKTO_DATA_INGESTION_URL:
            return None
        if self._is_intermediate_tool_call_step(state):
            return None

        messages = self._extract_messages(state)
        model = self._extract_model(runtime)
        try:
            if AKTO_SYNC_MODE:
                await self._validate_response_and_maybe_block(messages, model)
            else:
                payload = self._build_model_payload(
                    messages=messages,
                    model=model,
                    response_body=None,
                    status_code="200",
                )
                await self._ingest(payload)
        except ValueError:
            raise
        except Exception as e:
            logger.error(f"aafter_model guardrails error (fail-open): {e}")
        return None

    # ------------------------------------------------------------------
    # Core validation / ingestion helpers
    # ------------------------------------------------------------------

    def _validate_request_and_maybe_block_sync(self, messages: list, model: str) -> None:
        """Validate prompt synchronously; raise ValueError if guardrails block."""
        payload = self._build_model_payload(
            messages=messages,
            model=model,
            response_body=None,
            status_code="200",
        )
        allowed, reason = self._validate_guardrails_sync(payload, phase="request")
        if not allowed:
            blocked_payload = self._build_model_payload(
                messages=messages,
                model=model,
                response_body={"x-blocked-by": "Akto Proxy", "reason": reason},
                status_code="403",
            )
            self._ingest_sync(blocked_payload)
            raise ValueError(f"Blocked by Akto Guardrails: {reason or 'Policy violation'}")

    def _validate_response_and_maybe_block_sync(self, messages: list, model: str) -> None:
        """Validate LLM response synchronously; raise ValueError if guardrails block."""
        payload = self._build_model_payload(
            messages=messages,
            model=model,
            response_body=None,
            status_code="200",
        )
        allowed, reason = self._validate_guardrails_sync(payload, phase="response")
        if not allowed:
            blocked_payload = self._build_model_payload(
                messages=messages,
                model=model,
                response_body={"x-blocked-by": "Akto Proxy", "reason": reason},
                status_code="403",
            )
            self._ingest_sync(blocked_payload)
            raise ValueError(f"Blocked by Akto Guardrails: {reason or 'Policy violation'}")
        self._ingest_sync(payload)

    def _validate_guardrails_sync(self, payload: dict, phase: str = "request") -> Tuple[bool, str]:
        """POST to Akto guardrails endpoint. Returns (allowed, reason). Fail-open."""
        try:
            url = self._build_http_proxy_url(
                guardrails=phase == "request",
                response_guardrails=phase == "response",
                ingest_data=False,
            )
            result = self._post_to_akto_sync(url, payload)
            return self._parse_guardrails_result(result, phase=phase)
        except Exception as e:
            logger.error(f"Guardrails validation error ({phase}, fail-open): {e}")
            return True, ""

    def _ingest_sync(self, payload: dict) -> None:
        """POST to Akto with ingest_data=true. Swallows errors."""
        try:
            url = self._build_http_proxy_url(guardrails=False, ingest_data=True)
            self._post_to_akto_sync(url, payload)
        except Exception as e:
            logger.error(f"Ingestion error: {e}")

    def _post_to_akto_sync(self, url: str, payload: dict) -> dict:
        """Send payload to Akto HTTP proxy endpoint (sync)."""
        logger.info(f"POST {url}")
        if LOG_PAYLOADS:
            logger.debug(f"Payload: {json.dumps(payload, default=str)[:1000]}")

        response = self._sync_client.post(url, json=payload)
        logger.info(f"Response: {response.status_code}")
        try:
            return response.json()
        except Exception:
            return {}

    async def _validate_request_and_maybe_block(self, messages: list, model: str) -> None:
        """Validate prompt; raise ValueError if guardrails block."""
        payload = self._build_model_payload(
            messages=messages,
            model=model,
            response_body=None,
            status_code="200",
        )
        allowed, reason = await self._validate_guardrails(payload, phase="request")
        if not allowed:
            blocked_payload = self._build_model_payload(
                messages=messages,
                model=model,
                response_body={"x-blocked-by": "Akto Proxy", "reason": reason},
                status_code="403",
            )
            await self._ingest(blocked_payload)
            raise ValueError(f"Blocked by Akto Guardrails: {reason or 'Policy violation'}")

    async def _validate_response_and_maybe_block(self, messages: list, model: str) -> None:
        """Validate LLM response; raise ValueError if guardrails block."""
        payload = self._build_model_payload(
            messages=messages,
            model=model,
            response_body=None,
            status_code="200",
        )
        allowed, reason = await self._validate_guardrails(payload, phase="response")
        if not allowed:
            blocked_payload = self._build_model_payload(
                messages=messages,
                model=model,
                response_body={"x-blocked-by": "Akto Proxy", "reason": reason},
                status_code="403",
            )
            await self._ingest(blocked_payload)
            raise ValueError(f"Blocked by Akto Guardrails: {reason or 'Policy violation'}")
        await self._ingest(payload)

    async def _validate_guardrails(self, payload: dict, phase: str = "request") -> Tuple[bool, str]:
        """POST to Akto guardrails endpoint. Returns (allowed, reason). Fail-open."""
        try:
            url = self._build_http_proxy_url(
                guardrails=phase == "request",
                response_guardrails=phase == "response",
                ingest_data=False,
            )
            result = await self._post_to_akto(url, payload)
            return self._parse_guardrails_result(result, phase=phase)
        except Exception as e:
            logger.error(f"Guardrails validation error ({phase}, fail-open): {e}")
            return True, ""

    async def _ingest(self, payload: dict) -> None:
        """POST to Akto with ingest_data=true. Swallows errors."""
        try:
            url = self._build_http_proxy_url(guardrails=False, ingest_data=True)
            await self._post_to_akto(url, payload)
        except Exception as e:
            logger.error(f"Ingestion error: {e}")

    async def _post_to_akto(self, url: str, payload: dict) -> dict:
        """Send payload to Akto HTTP proxy endpoint."""
        logger.info(f"POST {url}")
        if LOG_PAYLOADS:
            logger.debug(f"Payload: {json.dumps(payload, default=str)[:1000]}")

        response = await self._async_client.post(url, json=payload)
        logger.info(f"Response: {response.status_code}")
        try:
            return response.json()
        except Exception:
            return {}

    # ------------------------------------------------------------------
    # Payload builders (flat format matching github-cli-hooks)
    # ------------------------------------------------------------------

    def _build_http_proxy_url(
        self,
        *,
        guardrails: bool = False,
        response_guardrails: bool = False,
        ingest_data: bool = False,
    ) -> str:
        params = [f"akto_connector={self._connector}"]
        if guardrails:
            params.append("guardrails=true")
        if response_guardrails:
            params.append("response_guardrails=true")
        if ingest_data:
            params.append("ingest_data=true")
        return f"{AKTO_DATA_INGESTION_URL}{HTTP_PROXY_PATH}?{'&'.join(params)}"

    def _build_model_payload(
        self,
        messages: list,
        model: str,
        response_body: Any,
        status_code: str,
    ) -> dict:
        tags = {"gen-ai": "Gen AI", "ai-agent": self._connector}

        request_headers = json.dumps({
            "host": LANGCHAIN_API_HOST,
            "x-langchain-hook": "before_model",
            "content-type": "application/json",
        })
        response_headers = json.dumps({
            "x-langchain-hook": "after_model",
            "content-type": "application/json",
        })

        user_messages = [
            {"role": m.get("type", m.get("role", "unknown")), "content": m.get("content", "")}
            for m in messages
            if m.get("type", m.get("role")) in ("human", "user")
        ]
        request_payload = json.dumps({"model": model, "messages": user_messages})

        if isinstance(response_body, dict):
            response_payload = self._serialize_response_payload(response_body)
        else:
            response_body_data = self._build_response_body(model, messages)
            response_payload = (
                self._serialize_response_payload(response_body_data)
                if response_body_data is not None
                else json.dumps({})
            )

        return self._flat_payload(
            path=LANGCHAIN_API_PATH,
            request_headers=request_headers,
            response_headers=response_headers,
            request_payload=request_payload,
            response_payload=response_payload,
            status_code=status_code,
            tags=tags,
        )

    def _serialize_response_payload(self, body: dict[str, Any]) -> str:
        """Akto mirrors LLM HTTP responses with a JSON body wrapper."""
        return json.dumps({"body": json.dumps(body)})

    def _build_response_body(self, model: str, messages: list) -> Optional[dict[str, Any]]:
        for message in reversed(messages):
            role = message.get("type", message.get("role"))
            content = message.get("content", "")
            if role in ("ai", "assistant") and content:
                return {
                    "model": model,
                    "messages": [{"role": "ai", "content": content}],
                }

        tool_messages = [
            {"role": m.get("type", m.get("role", "unknown")), "content": m.get("content", "")}
            for m in messages
            if m.get("type", m.get("role")) not in ("human", "user", "ai", "assistant")
        ]
        if tool_messages:
            return {"model": model, "messages": tool_messages}

        return None

    def _flat_payload(
        self,
        path: str,
        request_headers: str,
        response_headers: str,
        request_payload: str,
        response_payload: str,
        status_code: str,
        tags: dict,
    ) -> dict:
        instance_ip = _get_instance_ip()
        return {
            "path": path,
            "requestHeaders": request_headers,
            "responseHeaders": response_headers,
            "method": "POST",
            "requestPayload": request_payload,
            "responsePayload": response_payload,
            "ip": instance_ip,
            "destIp": instance_ip,
            "time": str(int(time.time() * 1000)),
            "statusCode": status_code,
            "type": "HTTP/1.1",
            "status": status_code,
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

    # ------------------------------------------------------------------
    # Result parsing
    # ------------------------------------------------------------------

    def _parse_guardrails_result(self, result: Any, phase: str = "request") -> Tuple[bool, str]:
        if not isinstance(result, dict):
            return True, ""

        error = result.get("error")
        if isinstance(error, dict):
            error_data = error.get("data", {}) or {}
            if error_data.get("behaviour") == "block":
                reason = error.get("message") or error_data.get("reason") or "Policy violation"
                logger.warning(f"✗ DENIED by guardrails ({phase}): {reason}")
                return False, reason

        guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        if allowed:
            logger.info(f"✓ ALLOWED by guardrails ({phase})")
        else:
            logger.warning(f"✗ DENIED by guardrails ({phase}): {reason}")
        return allowed, reason

    # ------------------------------------------------------------------
    # State extraction helpers
    # ------------------------------------------------------------------

    def _is_intermediate_tool_call_step(self, state: AgentState) -> bool:
        """Return True if the last AI message has tool_calls (intermediate step)."""
        for m in reversed(state.get("messages", [])):
            role = getattr(m, "type", None) or (m.get("type") if isinstance(m, dict) else None)
            if role in ("ai", "assistant"):
                tool_calls = getattr(m, "tool_calls", None)
                return bool(tool_calls)
        return False

    def _extract_messages(self, state: AgentState) -> list:
        """Extract serializable messages list from agent state."""
        raw = state.get("messages", [])
        messages = []
        for m in raw:
            if isinstance(m, dict):
                messages.append(m)
            elif hasattr(m, "model_dump"):
                messages.append(m.model_dump())
            elif hasattr(m, "__dict__"):
                role = getattr(m, "type", getattr(m, "role", "unknown"))
                content = getattr(m, "content", str(m))
                messages.append({"role": role, "content": content})
            else:
                messages.append({"role": "unknown", "content": str(m)})
        return messages

    def _extract_model(self, runtime: Runtime) -> str:
        """Best-effort extraction of model name from runtime."""
        try:
            return getattr(runtime, "model", None) or os.getenv("LANGCHAIN_MODEL", "unknown")
        except Exception:
            return os.getenv("LANGCHAIN_MODEL", "unknown")

    async def aclose(self) -> None:
        """Close the underlying HTTP clients."""
        self._sync_client.close()
        await self._async_client.aclose()