"""
Akto Guardrails hooks for Claude Agent SDK.

Provides a factory function that returns four async callbacks bound to a
specific client IP. Use create_hooks() once per request/session, passing
the IP of the client that initiated the agent call.

Usage:
    from hooks import create_hooks
    from claude_agent_sdk import ClaudeAgentOptions, HookMatcher

    user_prompt_submit, stop, pre_tool_use, post_tool_use = create_hooks(
        client_ip=request.remote_addr  # or however you obtain the client IP
    )

    options = ClaudeAgentOptions(
        hooks={
            "UserPromptSubmit": [HookMatcher(hooks=[user_prompt_submit])],
            "Stop":             [HookMatcher(hooks=[stop])],
            "PreToolUse":       [HookMatcher(hooks=[pre_tool_use])],
            "PostToolUse":      [HookMatcher(hooks=[post_tool_use])],
        }
    )

If the client IP cannot be determined, pass nothing or an empty string —
the hooks will fall back to "0.0.0.0".

Environment variables are read at module import time from the process environment.
See akto_guardrails_core.py for the full variable reference.
"""

import asyncio
import logging
import os

from akto_guardrails_core import (
    AKTO_SYNC_MODE,
    DEFAULT_CLIENT_IP,
    apply_warn_resubmit_flow,
    call_guardrails_mcp_async,
    call_guardrails_prompt_async,
    extract_mcp_server_name,
    get_last_user_prompt,
    ingest_blocked_mcp_async,
    ingest_blocked_prompt_async,
    _is_warn_behaviour,
    prompt_fingerprint,
    send_mcp_response_ingestion_async,
    send_stop_ingestion_async,
)

logger = logging.getLogger(__name__)


def create_hooks(client_ip: str = ""):
    """
    Create the four Akto guardrails callbacks bound to a client IP.

    Args:
        client_ip: IP address of the client calling the agent.
                   Falls back to DEFAULT_CLIENT_IP ("0.0.0.0") if empty.

    Returns:
        Tuple of (akto_user_prompt_submit, akto_stop, akto_pre_tool_use, akto_post_tool_use)
    """
    _ip = client_ip or DEFAULT_CLIENT_IP

    async def akto_user_prompt_submit(input_data: dict, tool_use_id, context) -> dict:
        """
        UserPromptSubmit hook — validates the user prompt against Akto guardrails.

        SYNC_MODE=true  : Blocks the prompt if guardrails deny it.
        SYNC_MODE=false : Allows all prompts through.
        Fail-open: any error allows the prompt through.
        """
        prompt = input_data.get("prompt", "")

        if not prompt.strip():
            return {}

        logger.info(f"UserPromptSubmit hook: processing prompt ({len(prompt)} chars)")

        if not AKTO_SYNC_MODE:
            return {}

        gr_allowed, gr_reason, behaviour = await call_guardrails_prompt_async(prompt, _ip)
        fingerprint = prompt_fingerprint(prompt)
        allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint)

        if not allowed:
            if _is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning: prompt blocked, please review it. Send again to bypass. "
                    f"Reason: {gr_reason}"
                )
            else:
                block_reason = f"Blocked by Akto Guardrails: {gr_reason}"

            logger.warning(f"BLOCKING prompt — reason: {gr_reason}")
            asyncio.create_task(ingest_blocked_prompt_async(prompt, gr_reason, _ip))
            return {"continue_": False, "systemMessage": block_reason}

        return {}

    async def akto_stop(input_data: dict, tool_use_id, context) -> dict:
        """
        Stop hook — ingests the completed conversation turn for observability.

        This hook is purely observational and never blocks the agent.
        """
        transcript_path = input_data.get("transcript_path")
        response_text = (input_data.get("last_assistant_message") or "").strip()

        if not transcript_path or not response_text:
            return {}

        transcript_path = os.path.expanduser(transcript_path)
        user_prompt = get_last_user_prompt(transcript_path)

        if user_prompt:
            asyncio.create_task(send_stop_ingestion_async(user_prompt, response_text, _ip))

        return {}

    async def akto_pre_tool_use(input_data: dict, tool_use_id, context) -> dict:
        """
        PreToolUse hook — validates MCP / built-in tool calls against Akto guardrails.

        SYNC_MODE=true  : Blocks the tool call if guardrails deny it.
        SYNC_MODE=false : Allows all tool calls through.
        Fail-open: any error allows the tool call through.
        """
        tool_name = str(input_data.get("tool_name") or "")
        tool_input = input_data.get("tool_input") or {}
        mcp_server_name = extract_mcp_server_name(tool_name)

        logger.info(f"PreToolUse hook: tool={tool_name} server={mcp_server_name}")

        if not AKTO_SYNC_MODE:
            return {}

        allowed, reason = await call_guardrails_mcp_async(tool_name, tool_input, mcp_server_name, _ip)

        if not allowed:
            block_reason = reason or "Policy violation"
            logger.warning(f"BLOCKING tool call — tool={tool_name} reason={block_reason}")
            asyncio.create_task(
                ingest_blocked_mcp_async(tool_name, tool_input, mcp_server_name, block_reason, _ip)
            )
            return {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": f"Blocked by Akto Guardrails: {block_reason}",
                }
            }

        return {}

    async def akto_post_tool_use(input_data: dict, tool_use_id, context) -> dict:
        """
        PostToolUse hook — ingests tool execution results for observability.

        This hook is purely observational and never blocks or modifies the tool result.
        """
        tool_name = str(input_data.get("tool_name") or "")
        tool_input = input_data.get("tool_input") or {}
        tool_response = input_data.get("tool_response") or {}
        mcp_server_name = extract_mcp_server_name(tool_name)

        logger.info(f"PostToolUse hook: tool={tool_name} server={mcp_server_name}")

        asyncio.create_task(
            send_mcp_response_ingestion_async(tool_name, tool_input, tool_response, mcp_server_name, _ip)
        )

        return {}

    return akto_user_prompt_submit, akto_stop, akto_pre_tool_use, akto_post_tool_use
