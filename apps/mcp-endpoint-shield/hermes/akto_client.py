"""
Akto API client for Hermes guardrails plugin.
Handles all communication with Akto server for validation and logging.
"""

import json
import logging
import time
import ssl
import urllib.request
import urllib.error
from typing import Any, Dict, Optional, Union

try:
    from .config import Config
    from .mcp_util import MCPTool, parse_mcp_tool, build_jsonrpc_tool_call, build_jsonrpc_tool_response
except ImportError:
    import sys
    import os
    _plugin_dir = os.path.dirname(os.path.abspath(__file__))
    if _plugin_dir not in sys.path:
        sys.path.insert(0, _plugin_dir)

    from config import Config
    from mcp_util import MCPTool, parse_mcp_tool, build_jsonrpc_tool_call, build_jsonrpc_tool_response

logger = logging.getLogger("hermes_guardrails.akto_client")


class AktoClient:
    """Client for communicating with Akto server."""

    def __init__(self, config: Config):
        """Initialize Akto client with configuration."""
        self.config = config
        self.logger = logger

    def _create_ssl_context(self) -> ssl.SSLContext:
        """Create SSL context for unverified connections."""
        return ssl._create_unverified_context()

    def _post(
        self,
        url: str,
        payload: Dict[str, Any],
        timeout: Optional[float] = None
    ) -> Union[Dict[str, Any], str, None]:
        """
        POST request to Akto server.

        Args:
            url: Full URL to POST to
            payload: JSON payload
            timeout: Request timeout in seconds

        Returns:
            Response JSON (dict/str) or None on error
        """
        if not url:
            self.logger.warning("No URL provided for POST request")
            return None

        if timeout is None:
            timeout = self.config.timeout

        self.logger.info(f"[API] POST {url}")
        self.logger.debug(f"[API] Full URL: {url}")

        if self.config.log_payloads:
            payload_str = json.dumps(payload, indent=2)
            if len(payload_str) > 2000:
                self.logger.debug(f"[API] Request payload (truncated):\n{payload_str[:2000]}...\n[Payload continues, total size: {len(payload_str)} chars]")
            else:
                self.logger.debug(f"[API] Request payload:\n{payload_str}")

        headers = self.config.get_headers()
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers=headers,
            method="POST"
        )

        start_time = time.time()
        try:
            ssl_context = self._create_ssl_context()
            with urllib.request.urlopen(
                request,
                context=ssl_context,
                timeout=timeout
            ) as response:
                duration_ms = int((time.time() - start_time) * 1000)
                status_code = response.getcode()
                raw = response.read().decode("utf-8")

                self.logger.info(
                    f"[API] RESPONSE: Status {status_code}, Duration: {duration_ms}ms, Size: {len(raw)} bytes"
                )

                if self.config.log_payloads:
                    try:
                        response_json = json.loads(raw)
                        response_str = json.dumps(response_json, indent=2)
                        if len(response_str) > 2000:
                            self.logger.debug(f"[API] Response body (truncated):\n{response_str[:2000]}...\n[Response continues, total size: {len(response_str)} chars]")
                        else:
                            self.logger.debug(f"[API] Response body:\n{response_str}")
                    except:
                        self.logger.debug(f"[API] Response body (raw): {raw[:1000]}...")

                try:
                    return json.loads(raw)
                except json.JSONDecodeError:
                    return raw

        except urllib.error.HTTPError as e:
            duration_ms = int((time.time() - start_time) * 1000)
            self.logger.error(f"[API] HTTP ERROR after {duration_ms}ms: {e.code} {e.reason}")
            try:
                error_body = e.read().decode("utf-8")
                if self.config.log_payloads:
                    self.logger.debug(f"[API] Error response body: {error_body[:500]}...")
            except:
                pass
            return None
        except urllib.error.URLError as e:
            duration_ms = int((time.time() - start_time) * 1000)
            self.logger.error(f"[API] URL ERROR after {duration_ms}ms: {e.reason}")
            return None
        except Exception as e:
            import traceback
            duration_ms = int((time.time() - start_time) * 1000)
            self.logger.error(f"[API] CALL FAILED after {duration_ms}ms: {e}")
            self.logger.error(f"[API] Traceback: {traceback.format_exc()}")
            return None

    def _ingest_data(
        self,
        url: str,
        payload: Dict[str, Any],
        timeout: Optional[float] = None
    ) -> Union[Dict[str, Any], str, None]:
        """
        POST request for audit trail ingestion.
        Used AFTER validation decisions are made.
        Calls /api/http-proxy?ingest_data=true endpoint.

        Args:
            url: Full URL with ?ingest_data=true parameter
            payload: JSON payload
            timeout: Request timeout in seconds

        Returns:
            Response JSON (dict/str) or None on error
        """
        self.logger.info(f"[INGEST] POST {url}")
        self.logger.debug(f"[INGEST] Sending data to audit trail")

        if self.config.log_payloads:
            payload_str = json.dumps(payload, indent=2)
            if len(payload_str) > 2000:
                self.logger.debug(f"[INGEST] Request payload (truncated):\n{payload_str[:2000]}...")
            else:
                self.logger.debug(f"[INGEST] Request payload:\n{payload_str}")

        # Delegate to _post() with ingest endpoint
        return self._post(url, payload, timeout)

    def validate_prompt(
        self,
        prompt: str,
        session_id: str = "",
        model: str = "",
        platform: str = ""
    ) -> tuple[bool, str]:
        """
        Validate prompt against Akto guardrails.

        Args:
            prompt: User prompt text
            session_id: Session identifier
            model: Model name
            platform: Platform (cli, api, etc.)

        Returns:
            (allowed, reason) tuple
        """
        if not self.config.is_configured():
            self.logger.warning("[VALIDATE_PROMPT] AKTO_DATA_INGESTION_URL not configured - allowing (fail-open)")
            return True, ""

        if not prompt.strip():
            return True, ""

        self.logger.info("[VALIDATE_PROMPT] ===== PROMPT VALIDATION =====")
        self.logger.info(f"[VALIDATE_PROMPT] Session: {session_id[:8] if session_id else '?'}")
        self.logger.info(f"[VALIDATE_PROMPT] Model: {model or '?'}")
        self.logger.info(f"[VALIDATE_PROMPT] Platform: {platform or '?'}")
        self.logger.info(f"[VALIDATE_PROMPT] Prompt length: {len(prompt)} chars")

        # Build validation request
        payload = self._build_prompt_validation_request(
            prompt=prompt,
            session_id=session_id,
            model=model,
            platform=platform
        )

        # Call Akto API
        url = self.config.get_api_url("/api/http-proxy?guardrails=true&akto_connector=hermes")
        self.logger.info(f"[VALIDATE_PROMPT] Sending validation request to: {url}")
        result = self._post(url, payload)

        if result is None:
            self.logger.warning("[VALIDATE_PROMPT] API call failed - allowing (fail-open)")
            return True, ""

        # Parse response
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            self.logger.info("[VALIDATE_PROMPT] ===== RESULT: ALLOWED =====")
        else:
            self.logger.warning(f"[VALIDATE_PROMPT] ===== RESULT: DENIED =====")
            self.logger.warning(f"[VALIDATE_PROMPT] Denial reason: {reason}")

            # Ingest blocked prompt to audit trail
            self.logger.info("[VALIDATE_PROMPT] Ingesting blocked prompt to audit trail...")
            ingest_url = self.config.get_api_url("/api/http-proxy?ingest_data=true&akto_connector=hermes")
            self._ingest_data(ingest_url, payload)
            self.logger.info("[VALIDATE_PROMPT] Blocked prompt ingested")

        return allowed, reason

    def log_prompt_response(
        self,
        prompt: str,
        response: str,
        session_id: str = "",
        model: str = "",
        platform: str = ""
    ) -> bool:
        """
        Log prompt and response for audit trail.

        Args:
            prompt: Original user prompt
            response: AI response
            session_id: Session identifier
            model: Model name
            platform: Platform

        Returns:
            True if logging succeeded, False otherwise
        """
        if not self.config.is_configured():
            self.logger.debug("[LOG_PROMPT_RESPONSE] AKTO_DATA_INGESTION_URL not configured - skipping")
            return True

        self.logger.info("[LOG_PROMPT_RESPONSE] ===== LOGGING PROMPT-RESPONSE =====")
        self.logger.info(f"[LOG_PROMPT_RESPONSE] Session: {session_id[:8] if session_id else '?'}")
        self.logger.info(f"[LOG_PROMPT_RESPONSE] Prompt length: {len(prompt)} chars")
        self.logger.info(f"[LOG_PROMPT_RESPONSE] Response length: {len(response)} chars")
        self.logger.info(f"[LOG_PROMPT_RESPONSE] Model: {model or '?'}")
        self.logger.info(f"[LOG_PROMPT_RESPONSE] Platform: {platform or '?'}")

        # Build logging request
        payload = self._build_logging_request(
            prompt=prompt,
            response=response,
            session_id=session_id,
            model=model,
            platform=platform,
            hook_type="prompt-response"
        )

        # Ingest response to audit trail (no validation, just logging)
        url = self.config.get_api_url("/api/http-proxy?ingest_data=true&akto_connector=hermes")
        self.logger.info(f"[LOG_PROMPT_RESPONSE] Sending to: {url}")
        result = self._ingest_data(url, payload)

        if result is None:
            self.logger.error("[LOG_PROMPT_RESPONSE] API call failed - audit logging may have been lost")
            return False

        self.logger.info("[LOG_PROMPT_RESPONSE] ===== LOGGED SUCCESSFULLY =====")
        return True

    def validate_tool(
        self,
        tool_name: str,
        args: Dict[str, Any],
        session_id: str = "",
        task_id: str = ""
    ) -> tuple[bool, str]:
        """
        Validate tool call against Akto guardrails.
        Handles both MCP and non-MCP tools.

        Args:
            tool_name: Name of tool
            args: Tool arguments
            session_id: Session identifier
            task_id: Task identifier

        Returns:
            (allowed, reason) tuple
        """
        if not self.config.is_configured():
            self.logger.warning("[VALIDATE_TOOL] AKTO_DATA_INGESTION_URL not configured - allowing (fail-open)")
            return True, ""

        if not tool_name:
            return True, ""

        self.logger.info("[VALIDATE_TOOL] ===== TOOL VALIDATION =====")

        # Parse tool type (MCP vs non-MCP)
        mcp_tool = parse_mcp_tool(tool_name)

        if mcp_tool.is_mcp:
            self.logger.info(f"[VALIDATE_TOOL] Tool type: MCP")
            self.logger.info(f"[VALIDATE_TOOL] Tool name: {tool_name}")
            self.logger.info(f"[VALIDATE_TOOL] MCP Server: {mcp_tool.server}")
            self.logger.info(f"[VALIDATE_TOOL] MCP Tool: {mcp_tool.tool}")
        else:
            self.logger.info(f"[VALIDATE_TOOL] Tool type: NON-MCP")
            self.logger.info(f"[VALIDATE_TOOL] Tool name: {tool_name}")

        self.logger.info(f"[VALIDATE_TOOL] Session: {session_id[:8] if session_id else '?'}")
        self.logger.info(f"[VALIDATE_TOOL] Task: {task_id[:8] if task_id else '?'}")
        self.logger.info(f"[VALIDATE_TOOL] Arguments count: {len(args)} args")

        # Build validation request
        payload = self._build_tool_validation_request(
            tool_name=tool_name,
            args=args,
            session_id=session_id,
            task_id=task_id,
            mcp_tool=mcp_tool
        )

        # Call Akto API
        url = self.config.get_api_url("/api/http-proxy?guardrails=true&akto_connector=hermes")
        self.logger.info(f"[VALIDATE_TOOL] Sending validation request to: {url}")
        result = self._post(url, payload)

        if result is None:
            self.logger.warning("[VALIDATE_TOOL] API call failed - allowing (fail-open)")
            return True, ""

        # Parse response
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            self.logger.info(f"[VALIDATE_TOOL] ===== RESULT: ALLOWED =====")
        else:
            self.logger.warning(f"[VALIDATE_TOOL] ===== RESULT: DENIED =====")
            self.logger.warning(f"[VALIDATE_TOOL] Denial reason: {reason}")

            # Ingest blocked tool to audit trail
            self.logger.info("[VALIDATE_TOOL] Ingesting blocked tool to audit trail...")
            ingest_url = self.config.get_api_url("/api/http-proxy?ingest_data=true&akto_connector=hermes")
            self._ingest_data(ingest_url, payload)
            self.logger.info("[VALIDATE_TOOL] Blocked tool ingested")

        return allowed, reason

    def log_tool_call(
        self,
        tool_name: str,
        args: Dict[str, Any],
        result: str = "",
        session_id: str = "",
        task_id: str = "",
        duration_ms: int = 0,
        is_before: bool = True
    ) -> bool:
        """
        Log tool call (before or after execution).
        Handles both MCP and non-MCP tools.

        Args:
            tool_name: Name of tool
            args: Tool arguments
            result: Tool output (for post-execution logging)
            session_id: Session identifier
            task_id: Task identifier
            duration_ms: Execution duration
            is_before: True for pre-execution, False for post-execution

        Returns:
            True if logging succeeded, False otherwise
        """
        if not self.config.is_configured():
            self.logger.debug("[LOG_TOOL_CALL] AKTO_DATA_INGESTION_URL not configured - skipping")
            return True

        hook_type = "tool-before" if is_before else "tool-after"

        self.logger.info(f"[LOG_TOOL_CALL] ===== LOGGING TOOL {hook_type.upper()} =====")
        self.logger.info(f"[LOG_TOOL_CALL] Hook type: {hook_type}")
        self.logger.info(f"[LOG_TOOL_CALL] Tool name: {tool_name}")
        self.logger.info(f"[LOG_TOOL_CALL] Session: {session_id[:8] if session_id else '?'}")
        self.logger.info(f"[LOG_TOOL_CALL] Task: {task_id[:8] if task_id else '?'}")
        self.logger.info(f"[LOG_TOOL_CALL] Arguments count: {len(args)} args")

        if not is_before:
            self.logger.info(f"[LOG_TOOL_CALL] Duration: {duration_ms}ms")
            self.logger.info(f"[LOG_TOOL_CALL] Result length: {len(str(result))} chars")

        # Parse tool type
        mcp_tool = parse_mcp_tool(tool_name)
        if mcp_tool.is_mcp:
            self.logger.info(f"[LOG_TOOL_CALL] Tool type: MCP (server={mcp_tool.server}, tool={mcp_tool.tool})")
        else:
            self.logger.info(f"[LOG_TOOL_CALL] Tool type: NON-MCP")

        # Build logging request
        payload = self._build_logging_request(
            tool_name=tool_name,
            tool_args=args,
            tool_result=result,
            session_id=session_id,
            task_id=task_id,
            duration_ms=duration_ms,
            hook_type=hook_type,
            mcp_tool=mcp_tool
        )

        # Ingest tool call to audit trail (no validation, just logging)
        url = self.config.get_api_url("/api/http-proxy?ingest_data=true&akto_connector=hermes")
        self.logger.info(f"[LOG_TOOL_CALL] Sending to: {url}")
        result_response = self._ingest_data(url, payload)

        if result_response is None:
            self.logger.error(f"[LOG_TOOL_CALL] API call failed - audit logging may have been lost")
            return False

        self.logger.info(f"[LOG_TOOL_CALL] ===== LOGGED SUCCESSFULLY =====")
        return True

    def _build_prompt_validation_request(
        self,
        prompt: str,
        session_id: str = "",
        model: str = "",
        platform: str = ""
    ) -> Dict[str, Any]:
        """Build prompt validation request payload."""
        from .akto_machine_id import get_machine_id, get_username

        device_id = get_machine_id()
        username = get_username()

        tags = {
            "gen-ai": "Gen AI",
            "ai-agent": "hermes",
            "source": self.config.context_source
        }

        # Build host header with device_id prefix (like OpenCode does in atlas mode)
        host_header = f"{device_id}.hermes.agent"

        return {
            "path": "/v1/messages",
            "method": "POST",
            "requestPayload": json.dumps({"body": prompt.strip()}),
            "responsePayload": json.dumps({}),
            "requestHeaders": json.dumps({
                "host": host_header,
                "x-hermes-hook": "pre_llm_call",
                "content-type": "application/json"
            }),
            "responseHeaders": json.dumps({
                "x-hermes-hook": "pre_llm_call"
            }),
            "time": str(int(time.time() * 1000)),
            "statusCode": "200",
            "type": "HTTP/1.1",
            "status": "200",
            "akto_account_id": "1000000",
            "akto_vxlan_id": device_id,
            "is_pending": "false",
            "source": "MIRRORING",
            "ip": device_id,
            "destIp": "127.0.0.1",
            "tag": json.dumps(tags),
            "metadata": json.dumps(tags),
            "contextSource": self.config.context_source,
            "session_id": session_id
        }

    def _build_tool_validation_request(
        self,
        tool_name: str,
        args: Dict[str, Any],
        session_id: str = "",
        task_id: str = "",
        mcp_tool: Optional[MCPTool] = None
    ) -> Dict[str, Any]:
        """
        Build tool validation request payload.
        Handles both MCP (JSON-RPC) and non-MCP tools.
        """
        from .akto_machine_id import get_machine_id, get_username

        device_id = get_machine_id()
        username = get_username()

        # Determine path and payload format based on tool type
        if mcp_tool and mcp_tool.is_mcp:
            # MCP tool - use JSON-RPC format and /mcp endpoint
            path = "/mcp"
            jsonrpc_payload = build_jsonrpc_tool_call(tool_name, args, mcp_tool)
            request_payload = jsonrpc_payload
            tags = {
                "gen-ai": "Gen AI",
                "mcp-server": "MCP Server",
                "mcp-tool": mcp_tool.tool,
                "mcp-client": "hermes",
                "tool-name": tool_name,
                "source": self.config.context_source
            }
            request_headers = {
                "host": f"{device_id}.hermes.{mcp_tool.server}",
                "x-hermes-hook": "pre_tool_call",
                "x-mcp-server": "MCP Server",
                "x-mcp-tool": mcp_tool.tool,
                "content-type": "application/json"
            }
        else:
            # Non-MCP tool - regular format
            path = "/v1/tools/execute"
            request_payload = json.dumps({"tool": tool_name, "args": args})
            tags = {
                "gen-ai": "Gen AI",
                "tool-use": "Tool Execution",
                "tool-name": tool_name,
                "source": self.config.context_source
            }
            # Build host header with device_id prefix
            host_header = f"{device_id}.hermes.agent"
            request_headers = {
                "host": host_header,
                "x-hermes-hook": "pre_tool_call",
                "content-type": "application/json"
            }

        return {
            "path": path,
            "method": "POST",
            "requestPayload": request_payload,
            "responsePayload": json.dumps({}),
            "requestHeaders": json.dumps(request_headers),
            "responseHeaders": json.dumps({
                "x-hermes-hook": "pre_tool_call"
            }),
            "time": str(int(time.time() * 1000)),
            "statusCode": "200",
            "type": "HTTP/1.1",
            "status": "200",
            "akto_account_id": "1000000",
            "akto_vxlan_id": device_id,
            "is_pending": "false",
            "source": "MIRRORING",
            "ip": device_id,
            "destIp": "127.0.0.1",
            "tag": json.dumps(tags),
            "metadata": json.dumps(tags),
            "contextSource": self.config.context_source,
            "session_id": session_id,
            "task_id": task_id
        }

    def _build_logging_request(
        self,
        prompt: str = "",
        response: str = "",
        tool_name: str = "",
        tool_args: Dict[str, Any] = None,
        tool_result: str = "",
        session_id: str = "",
        task_id: str = "",
        model: str = "",
        platform: str = "",
        duration_ms: int = 0,
        hook_type: str = "",
        mcp_tool: Optional[MCPTool] = None
    ) -> Dict[str, Any]:
        """
        Build generic logging request payload.
        Handles both MCP (JSON-RPC) and non-MCP tools.
        """
        from .akto_machine_id import get_machine_id, get_username

        device_id = get_machine_id()
        username = get_username()

        if tool_args is None:
            tool_args = {}

        # Determine request/response payloads based on hook type
        if hook_type == "post_llm_call":
            request_payload = json.dumps({"body": prompt.strip()})
            response_payload = json.dumps({"body": response})
            path = "/v1/messages"
            hook_header = "post_llm_call"
        elif hook_type == "prompt-response":
            # Prompt-response logging - include actual prompt and response content
            request_payload = json.dumps({"body": prompt.strip()})
            response_payload = json.dumps({"body": response})
            path = "/v1/log"
            hook_header = "post_llm_call"
        elif hook_type in ("pre_tool_call", "post_tool_call"):
            # Handle both MCP and non-MCP tools
            if mcp_tool and mcp_tool.is_mcp:
                # MCP tool - use JSON-RPC format
                path = "/mcp"
                request_payload = build_jsonrpc_tool_call(tool_name, tool_args, mcp_tool)
                response_payload = build_jsonrpc_tool_response(tool_name, tool_result, mcp_tool)
                hook_header = "pre_tool_call" if hook_type == "pre_tool_call" else "post_tool_call"
            else:
                # Non-MCP tool - regular format
                path = "/v1/tools/execute"
                request_payload = json.dumps({"tool": tool_name, "args": tool_args})
                response_payload = json.dumps({"result": tool_result, "duration_ms": duration_ms})
                hook_header = "pre_tool_call" if hook_type == "pre_tool_call" else "post_tool_call"
        else:
            request_payload = json.dumps({})
            response_payload = json.dumps({})
            path = "/v1/log"
            hook_header = "Log"

        # Build tags with MCP info if applicable
        tags = {
            "gen-ai": "Gen AI",
            "source": self.config.context_source,
            "hook-type": hook_type
        }

        if tool_name:
            tags["tool-name"] = tool_name

        if mcp_tool and mcp_tool.is_mcp:
            tags["mcp-server"] = "MCP Server"
            tags["mcp-tool"] = mcp_tool.tool
            tags["mcp-client"] = "hermes"

        # Build headers with MCP info if applicable
        request_headers_dict = {
            "content-type": "application/json",
            "x-hermes-hook": hook_header
        }

        if mcp_tool and mcp_tool.is_mcp:
            request_headers_dict["host"] = f"{device_id}.hermes.{mcp_tool.server}"
            request_headers_dict["x-mcp-server"] = mcp_tool.server
            request_headers_dict["x-mcp-tool"] = mcp_tool.tool
        else:
            # Non-MCP tool - include device_id in host header
            request_headers_dict["host"] = f"{device_id}.hermes.agent"

        return {
            "path": path,
            "method": "POST",
            "requestPayload": request_payload,
            "responsePayload": response_payload,
            "requestHeaders": json.dumps(request_headers_dict),
            "responseHeaders": json.dumps({
                "x-hermes-hook": hook_header
            }),
            "time": str(int(time.time() * 1000)),
            "statusCode": "200",
            "type": "HTTP/1.1",
            "status": "200",
            "akto_account_id": "1000000",
            "akto_vxlan_id": device_id,
            "is_pending": "false",
            "source": "MIRRORING",
            "ip": device_id,
            "destIp": "127.0.0.1",
            "tag": json.dumps(tags),
            "metadata": json.dumps(tags),
            "contextSource": self.config.context_source,
            "session_id": session_id,
            "task_id": task_id
        }
