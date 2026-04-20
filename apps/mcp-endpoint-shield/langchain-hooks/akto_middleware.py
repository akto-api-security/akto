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
    LOG_LEVEL                Logging level (default: "INFO")
    LOG_PAYLOADS             Log full payloads, privacy-sensitive (default: "false")
"""

import json
import logging
import os
import time
from typing import Any, Optional, Tuple

import httpx

from langchain.agents.middleware import AgentMiddleware, AgentState
from langgraph.runtime import Runtime

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL", "")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_TOKEN = os.getenv("AKTO_TOKEN", "")
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "true").lower() == "true"

AKTO_CONNECTOR = "langchain"
HTTP_PROXY_PATH = "/api/http-proxy"
LANGCHAIN_API_HOST = os.getenv("LANGCHAIN_API_HOST", "api.langchain.com")
LANGCHAIN_API_PATH = os.getenv("LANGCHAIN_API_PATH", "/langchain/chat")

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))


# ---------------------------------------------------------------------------
# Middleware class
# ---------------------------------------------------------------------------

class AktoGuardrailsMiddleware(AgentMiddleware):
    """
    LangChain AgentMiddleware that enforces Akto guardrails on model calls.

    In SYNC_MODE (default):
      - before_model: validates prompt; raises ValueError if blocked
      - after_model: ingests completed interaction for audit

    In async mode (AKTO_SYNC_MODE=false):
      - All interactions are ingested after the fact (log-only)
    """

    def __init__(self, connector: str = AKTO_CONNECTOR) -> None:
        super().__init__()
        self._connector = connector
        self._client = httpx.AsyncClient(
            timeout=AKTO_TIMEOUT,
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
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
            import asyncio
            try:
                loop = asyncio.get_event_loop()
                if loop.is_running():
                    import concurrent.futures
                    with concurrent.futures.ThreadPoolExecutor() as pool:
                        future = pool.submit(asyncio.run, self._validate_and_maybe_block(messages, model))
                        future.result()
                else:
                    loop.run_until_complete(self._validate_and_maybe_block(messages, model))
            except ValueError:
                raise
            except Exception as e:
                logger.error(f"before_model guardrails error (fail-open): {e}")

        return None

    def after_model(self, state: AgentState, runtime: Runtime) -> Optional[dict[str, Any]]:
        """Ingest the completed model interaction for audit."""
        if not AKTO_DATA_INGESTION_URL:
            return None
        if self._is_intermediate_tool_call_step(state):
            return None

        messages = self._extract_messages(state)
        model = self._extract_model(runtime)

        import asyncio
        try:
            loop = asyncio.get_event_loop()
            payload = self._build_model_payload(
                messages=messages,
                model=model,
                response_body=None,
                status_code="200",
            )
            if loop.is_running():
                loop.create_task(self._ingest(payload))
            else:
                loop.run_until_complete(self._ingest(payload))
        except Exception as e:
            logger.error(f"after_model ingestion error: {e}")

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
                await self._validate_and_maybe_block(messages, model)
            except ValueError:
                raise
            except Exception as e:
                logger.error(f"abefore_model guardrails error (fail-open): {e}")
        return None

    async def aafter_model(self, state: AgentState, runtime: Runtime) -> Optional[dict[str, Any]]:
        """Async version of after_model — called when agent runs asynchronously."""
        if not AKTO_DATA_INGESTION_URL:
            return None
        if self._is_intermediate_tool_call_step(state):
            return None

        messages = self._extract_messages(state)
        model = self._extract_model(runtime)
        payload = self._build_model_payload(
            messages=messages,
            model=model,
            response_body=None,
            status_code="200",
        )
        try:
            await self._ingest(payload)
        except Exception as e:
            logger.error(f"aafter_model ingestion error: {e}")
        return None

    # ------------------------------------------------------------------
    # Core validation / ingestion helpers
    # ------------------------------------------------------------------

    async def _validate_and_maybe_block(self, messages: list, model: str) -> None:
        """Validate messages; raise ValueError if guardrails block."""
        payload = self._build_model_payload(
            messages=messages,
            model=model,
            response_body=None,
            status_code="200",
        )
        allowed, reason = await self._validate_guardrails(payload)
        if not allowed:
            blocked_payload = self._build_model_payload(
                messages=messages,
                model=model,
                response_body={"x-blocked-by": "Akto Proxy", "reason": reason},
                status_code="403",
            )
            await self._ingest(blocked_payload)
            raise ValueError(f"Blocked by Akto Guardrails: {reason or 'Policy violation'}")

    async def _validate_guardrails(self, payload: dict) -> Tuple[bool, str]:
        """POST to Akto with guardrails=true. Returns (allowed, reason). Fail-open."""
        try:
            url = self._build_http_proxy_url(guardrails=True, ingest_data=False)
            result = await self._post_to_akto(url, payload)
            return self._parse_guardrails_result(result)
        except Exception as e:
            logger.error(f"Guardrails validation error (fail-open): {e}")
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

        headers = {"authorization": AKTO_TOKEN} if AKTO_TOKEN else None
        response = await self._client.post(url, json=payload, headers=headers)
        logger.info(f"Response: {response.status_code}")
        if response.status_code == 200:
            try:
                return response.json()
            except Exception:
                pass
        return {}

    # ------------------------------------------------------------------
    # Payload builders (flat format matching github-cli-hooks)
    # ------------------------------------------------------------------

    def _build_http_proxy_url(self, guardrails: bool, ingest_data: bool) -> str:
        params = [f"akto_connector={self._connector}"]
        if guardrails:
            params.append("guardrails=true")
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
        })

        # requestPayload: human/user messages only
        user_messages = [
            {"role": m.get("type", m.get("role", "unknown")), "content": m.get("content", "")}
            for m in messages
            if m.get("type", m.get("role")) in ("human", "user")
        ]
        request_payload = json.dumps({
            "body": json.dumps({"model": model, "messages": user_messages}),
        })

        # responsePayload: blocked dict for 403, AI text if available, else tool results
        if isinstance(response_body, dict):
            response_payload = json.dumps({
                "body": json.dumps(response_body),
            })
        else:
            # Prefer final AI text response
            ai_content = None
            for m in reversed(messages):
                role = m.get("type", m.get("role"))
                content = m.get("content", "")
                if role in ("ai", "assistant") and content:
                    ai_content = content
                    break

            if ai_content:
                response_payload = json.dumps({
                    "body": json.dumps({"model": model, "content": ai_content}),
                })
            else:
                # Fall back to tool results
                tool_messages = [
                    {"role": m.get("type", m.get("role", "unknown")), "content": m.get("content", "")}
                    for m in messages
                    if m.get("type", m.get("role")) not in ("human", "user", "ai", "assistant")
                ]
                if tool_messages:
                    response_payload = json.dumps({
                        "body": json.dumps({"model": model, "messages": tool_messages}),
                    })
                else:
                    response_payload = json.dumps({})

        return self._flat_payload(
            path=LANGCHAIN_API_PATH,
            request_headers=request_headers,
            response_headers=response_headers,
            request_payload=request_payload,
            response_payload=response_payload,
            status_code=status_code,
            tags=tags,
        )

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
        return {
            "path": path,
            "requestHeaders": request_headers,
            "responseHeaders": response_headers,
            "method": "POST",
            "requestPayload": request_payload,
            "responsePayload": response_payload,
            "ip": "0.0.0.0",
            "destIp": "127.0.0.1",
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

    def _parse_guardrails_result(self, result: Any) -> Tuple[bool, str]:
        if not isinstance(result, dict):
            return True, ""
        guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        if allowed:
            logger.info("✓ ALLOWED by guardrails")
        else:
            logger.warning(f"✗ DENIED by guardrails: {reason}")
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
        """Close the underlying HTTP client."""
        await self._client.aclose()
