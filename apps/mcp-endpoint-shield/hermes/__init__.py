"""
Akto Guardrails Plugin for Hermes Agent

Validates prompts and tool calls, logs all activity to Akto for audit trail.
Supports prompt blocking, tool execution validation, and comprehensive audit logging.

Hook Points:
- pre_llm_call: Validate user message before sending to LLM
- post_llm_call: Log AI response for audit trail
- pre_tool_call: Validate tool call before execution
- post_tool_call: Log tool execution results

Environment Variables:
- AKTO_DATA_INGESTION_URL: Akto server URL (required)
- AKTO_SYNC_MODE: "true" for blocking mode, "false" for logging only
- AKTO_TIMEOUT: Request timeout in seconds (default: 5)
- LOG_LEVEL: DEBUG, INFO, WARNING, ERROR (default: INFO)
- LOG_PAYLOADS: "true" to log full payloads (default: false)
"""

import os
import json
import logging
import time
from typing import Any, Dict, Optional, Tuple

try:
    # Try relative imports first (when installed as a package)
    from .config import Config
    from .akto_client import AktoClient
    from .validators import PromptValidator, ToolValidator
    from .logging_util import setup_logger
    from .mcp_util import parse_mcp_tool
except ImportError:
    # Fall back to direct imports (when loaded as a plugin directory)
    import sys
    _plugin_dir = os.path.dirname(os.path.abspath(__file__))
    if _plugin_dir not in sys.path:
        sys.path.insert(0, _plugin_dir)

    from config import Config
    from akto_client import AktoClient
    from validators import PromptValidator, ToolValidator
    from logging_util import setup_logger
    from mcp_util import parse_mcp_tool

__version__ = "1.0.0"

# Lazy initialization to avoid issues during plugin loading
_initialized = False
_config = None
_logger = None
_akto_client = None
_prompt_validator = None
_tool_validator = None


def _ensure_initialized():
    """Lazy initialization - called when plugin actually runs, not on import."""
    global _initialized, _config, _logger, _akto_client, _prompt_validator, _tool_validator

    if _initialized:
        return _config, _logger, _akto_client, _prompt_validator, _tool_validator

    try:
        _config = Config()
        _logger = setup_logger("hermes_guardrails", _config.log_level, _config.log_dir)
        _akto_client = AktoClient(_config)
        _prompt_validator = PromptValidator(_config, _akto_client)
        _tool_validator = ToolValidator(_config, _akto_client)
        _initialized = True

        _logger.info("=== Hermes Akto Guardrails Plugin Initializing ===")
        _logger.info(f"Mode: {_config.mode}, Sync: {_config.sync_mode}, Timeout: {_config.timeout}s")

        if not _config.akto_url:
            _logger.warning("AKTO_DATA_INGESTION_URL not set - plugin running in fail-open mode")

        _logger.info("=== Hermes Akto Guardrails Plugin Ready ===")

    except Exception as e:
        import traceback
        try:
            os.makedirs(os.path.expanduser("~/.config/hermes/akto/logs"), exist_ok=True)
            with open(os.path.expanduser("~/.config/hermes/akto/logs/hermes_plugin_error.log"), "w") as f:
                f.write(f"Initialization error: {e}\n")
                f.write(traceback.format_exc())
        except:
            pass
        raise

    return _config, _logger, _akto_client, _prompt_validator, _tool_validator


def pre_llm_call_handler(**kwargs: Any) -> Optional[Dict[str, Any]]:
    """Validate prompt before sending to AI."""
    try:
        import asyncio
        config, logger, akto_client, prompt_validator, _ = _ensure_initialized()

        user_message = kwargs.get("user_message", "")
        session_id = kwargs.get("session_id", "")
        model = kwargs.get("model", "")
        platform = kwargs.get("platform", "")

        if not user_message.strip():
            logger.debug("Empty user message, allowing")
            return None

        logger.info("=" * 80)
        logger.info("[HOOK: pre_llm_call] ===== HOOK TRIGGERED =====")
        logger.info(f"[HOOK: pre_llm_call] Session ID: {session_id[:8] if session_id else '?'}")
        logger.info(f"[HOOK: pre_llm_call] Model: {model or '?'}")
        logger.info(f"[HOOK: pre_llm_call] Platform: {platform or '?'}")
        logger.info(f"[HOOK: pre_llm_call] Message length: {len(user_message)} chars")

        if config.log_payloads:
            logger.debug(f"[HOOK: pre_llm_call] Message content: {user_message[:500]}...")

        logger.info("[HOOK: pre_llm_call] Calling guardrails validation API...")

        # Validate against guardrails (don't pass **kwargs to avoid duplicate session_id)
        allowed, reason = asyncio.run(prompt_validator.validate(
            user_message,
            session_id=session_id,
            model=model,
            platform=platform
        ))

        if not allowed:
            logger.warning(f"[HOOK: pre_llm_call] ===== PROMPT BLOCKED =====")
            logger.warning(f"[HOOK: pre_llm_call] Reason: {reason}")
            logger.warning(f"[HOOK: pre_llm_call] Injecting tool restriction notice to LLM")

            # Tell LLM that tools are not available due to security policy
            # This prevents LLM from attempting blocked tool calls
            tool_restriction = f"\n[SECURITY NOTICE] Tool usage has been restricted by security guardrails due to policy: {reason}. Please answer the user's question without using any tools."
            logger.info("=" * 80)
            return tool_restriction

        logger.info(f"[HOOK: pre_llm_call] ===== PROMPT ALLOWED =====")
        logger.info("[HOOK: pre_llm_call] Continuing to AI...")
        logger.info("=" * 80)
        return None

    except Exception as e:
        import traceback
        _, logger, _, _, _ = _ensure_initialized()
        logger.error(f"[HOOK: pre_llm_call] ===== HOOK ERROR =====")
        logger.error(f"[HOOK: pre_llm_call] Exception: {e}")
        logger.error(f"[HOOK: pre_llm_call] Traceback: {traceback.format_exc()}")
        logger.error("=" * 80)
        return None


def post_llm_call_handler(**kwargs: Any) -> None:
    """Log AI response for audit trail."""
    try:
        import asyncio
        config, logger, _, prompt_validator, _ = _ensure_initialized()

        session_id = kwargs.get("session_id", "")
        user_message = kwargs.get("user_message", "")
        assistant_response = kwargs.get("assistant_response", "")
        model = kwargs.get("model", "")
        platform = kwargs.get("platform", "")

        logger.info("=" * 80)
        logger.info("[HOOK: post_llm_call] ===== HOOK TRIGGERED =====")
        logger.info(f"[HOOK: post_llm_call] Session ID: {session_id[:8] if session_id else '?'}")
        logger.info(f"[HOOK: post_llm_call] Model: {model or '?'}")
        logger.info(f"[HOOK: post_llm_call] Platform: {platform or '?'}")
        logger.info(f"[HOOK: post_llm_call] Response length: {len(assistant_response)} chars")

        if config.log_payloads:
            logger.debug(f"[HOOK: post_llm_call] User message: {user_message[:500]}...")
            logger.debug(f"[HOOK: post_llm_call] AI response: {assistant_response[:500]}...")

        logger.info("[HOOK: post_llm_call] Calling audit logging API...")

        result = asyncio.run(prompt_validator.log_response(
            session_id=session_id,
            user_message=user_message,
            assistant_response=assistant_response,
            conversation_history=kwargs.get("conversation_history", []),
            model=model,
            platform=platform
        ))

        if result:
            logger.info("[HOOK: post_llm_call] Response logged successfully to audit trail")
        else:
            logger.warning("[HOOK: post_llm_call] Failed to log response (fail-open)")

        logger.info("=" * 80)

    except Exception as e:
        import traceback
        _, logger, _, _, _ = _ensure_initialized()
        logger.error(f"[HOOK: post_llm_call] ===== HOOK ERROR =====")
        logger.error(f"[HOOK: post_llm_call] Exception: {e}")
        logger.error(f"[HOOK: post_llm_call] Traceback: {traceback.format_exc()}")
        logger.error("=" * 80)


def pre_tool_call_handler(**kwargs: Any) -> Optional[Dict[str, Any]]:
    """Validate tool call before execution."""
    try:
        import asyncio
        config, logger, _, _, tool_validator = _ensure_initialized()

        tool_name = kwargs.get("tool_name", "")
        args = kwargs.get("args", {})
        session_id = kwargs.get("session_id", "")
        task_id = kwargs.get("task_id", "")

        if not tool_name:
            logger.debug("No tool name provided, allowing")
            return None

        logger.info("=" * 80)
        logger.info("[HOOK: pre_tool_call] ===== HOOK TRIGGERED =====")
        logger.info(f"[HOOK: pre_tool_call] Session ID: {session_id[:8] if session_id else '?'}")
        logger.info(f"[HOOK: pre_tool_call] Task ID: {task_id[:8] if task_id else '?'}")
        logger.info(f"[HOOK: pre_tool_call] Tool name: {tool_name}")

        # Detect tool type (MCP vs non-MCP)
        mcp_tool = parse_mcp_tool(tool_name)
        tool_type = "MCP" if mcp_tool.is_mcp else "NON-MCP"

        if mcp_tool.is_mcp:
            logger.info(f"[HOOK: pre_tool_call] Tool type: {tool_type}")
            logger.info(f"[HOOK: pre_tool_call] MCP Server: {mcp_tool.server}")
            logger.info(f"[HOOK: pre_tool_call] MCP Tool: {mcp_tool.tool}")
        else:
            logger.info(f"[HOOK: pre_tool_call] Tool type: {tool_type}")

        logger.info(f"[HOOK: pre_tool_call] Arguments count: {len(args)} args")
        if config.log_payloads:
            logger.debug(f"[HOOK: pre_tool_call] Arguments: {json.dumps(args)[:500]}...")

        logger.info("[HOOK: pre_tool_call] Calling guardrails validation API...")

        # Validate tool call (don't pass **kwargs to avoid duplicate arguments)
        allowed, reason = asyncio.run(tool_validator.validate_before(
            tool_name=tool_name,
            args=args,
            session_id=session_id,
            task_id=task_id
        ))

        if not allowed:
            block_reason = f"Tool blocked by guardrails: {reason}"
            logger.warning(f"[HOOK: pre_tool_call] ===== TOOL BLOCKED =====")
            logger.warning(f"[HOOK: pre_tool_call] Reason: {reason}")
            logger.warning(f"[HOOK: pre_tool_call] ===== RETURNING BLOCK ACTION =====")
            logger.info("=" * 80)
            # Use Hermes' proper blocking mechanism for pre_tool_call
            return {"action": "block", "message": block_reason}

        logger.info(f"[HOOK: pre_tool_call] ===== TOOL ALLOWED =====")
        logger.info(f"[HOOK: pre_tool_call] Executing tool: {tool_name}")
        logger.info("=" * 80)
        return None

    except Exception as e:
        import traceback
        _, logger, _, _, _ = _ensure_initialized()
        logger.error(f"[HOOK: pre_tool_call] ===== HOOK ERROR =====")
        logger.error(f"[HOOK: pre_tool_call] Exception: {e}")
        logger.error(f"[HOOK: pre_tool_call] Traceback: {traceback.format_exc()}")
        logger.error("=" * 80)
        return None


def post_tool_call_handler(**kwargs: Any) -> None:
    """Log tool result for audit trail."""
    try:
        import asyncio
        config, logger, _, _, tool_validator = _ensure_initialized()

        tool_name = kwargs.get("tool_name", "")
        args = kwargs.get("args", {})
        result = kwargs.get("result", "")
        session_id = kwargs.get("session_id", "")
        task_id = kwargs.get("task_id", "")
        duration_ms = kwargs.get("duration_ms", 0)

        logger.info("=" * 80)
        logger.info("[HOOK: post_tool_call] ===== HOOK TRIGGERED =====")
        logger.info(f"[HOOK: post_tool_call] Session ID: {session_id[:8] if session_id else '?'}")
        logger.info(f"[HOOK: post_tool_call] Task ID: {task_id[:8] if task_id else '?'}")
        logger.info(f"[HOOK: post_tool_call] Tool name: {tool_name}")
        logger.info(f"[HOOK: post_tool_call] Duration: {duration_ms}ms")
        logger.info(f"[HOOK: post_tool_call] Result length: {len(result)} chars")

        if config.log_payloads:
            logger.debug(f"[HOOK: post_tool_call] Arguments: {json.dumps(args)[:500]}...")
            logger.debug(f"[HOOK: post_tool_call] Tool result: {str(result)[:500]}...")

        # Detect tool type for logging
        mcp_tool = parse_mcp_tool(tool_name)
        tool_type = "MCP" if mcp_tool.is_mcp else "NON-MCP"
        if mcp_tool.is_mcp:
            logger.info(f"[HOOK: post_tool_call] Tool type: {tool_type} (server={mcp_tool.server}, tool={mcp_tool.tool})")
        else:
            logger.info(f"[HOOK: post_tool_call] Tool type: {tool_type}")

        logger.info("[HOOK: post_tool_call] Calling audit logging API...")

        success = asyncio.run(tool_validator.log_after(
            tool_name=tool_name,
            args=args,
            result=result,
            session_id=session_id,
            task_id=task_id,
            duration_ms=duration_ms
        ))

        if success:
            logger.info("[HOOK: post_tool_call] Tool result logged successfully to audit trail")
        else:
            logger.warning("[HOOK: post_tool_call] Failed to log tool result (fail-open)")

        logger.info("=" * 80)

    except Exception as e:
        import traceback
        _, logger, _, _, _ = _ensure_initialized()
        logger.error(f"[HOOK: post_tool_call] ===== HOOK ERROR =====")
        logger.error(f"[HOOK: post_tool_call] Exception: {e}")
        logger.error(f"[HOOK: post_tool_call] Traceback: {traceback.format_exc()}")
        logger.error("=" * 80)


# Hermes plugin entry point - MUST be named 'register'
def register(ctx: Any) -> None:
    """
    Hermes plugin registration function.
    Called when plugin is discovered and loaded by Hermes.

    This function name is required by Hermes plugin discovery system.
    It registers all hooks with the Hermes plugin manager.
    """
    try:
        # Initialize on first call
        _ensure_initialized()

        # Register all hooks
        ctx.register_hook("pre_llm_call", pre_llm_call_handler)
        ctx.register_hook("post_llm_call", post_llm_call_handler)
        ctx.register_hook("pre_tool_call", pre_tool_call_handler)
        ctx.register_hook("post_tool_call", post_tool_call_handler)

        _, logger, _, _, _ = _ensure_initialized()
        logger.info("Plugin hooks registered successfully!")

    except Exception as e:
        import traceback
        # Write error to file for debugging
        try:
            os.makedirs(os.path.expanduser("~/.config/hermes/akto/logs"), exist_ok=True)
            with open(os.path.expanduser("~/.config/hermes/akto/logs/hermes_plugin_register_error.log"), "w") as f:
                f.write(f"Error in register(): {e}\n")
                f.write(traceback.format_exc())
        except:
            pass
        raise
