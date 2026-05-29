"""
Validators for Hermes guardrails plugin.
Handles prompt and tool call validation.
"""

import logging
from typing import Any, Dict, Tuple

try:
    from .config import Config
    from .akto_client import AktoClient
except ImportError:
    import sys
    import os
    _plugin_dir = os.path.dirname(os.path.abspath(__file__))
    if _plugin_dir not in sys.path:
        sys.path.insert(0, _plugin_dir)

    from config import Config
    from akto_client import AktoClient

logger = logging.getLogger("hermes_guardrails.validators")


class PromptValidator:
    """Validates user prompts before sending to Claude."""

    def __init__(self, config: Config, akto_client: AktoClient):
        """Initialize prompt validator."""
        self.config = config
        self.akto_client = akto_client
        self.logger = logger

    async def validate(
        self,
        prompt: str,
        session_id: str = "",
        model: str = "",
        platform: str = "",
        **kwargs: Any
    ) -> Tuple[bool, str]:
        """
        Validate prompt against Akto guardrails.

        Args:
            prompt: User prompt text
            session_id: Session identifier
            model: Model name
            platform: Platform name
            **kwargs: Additional context

        Returns:
            (allowed, reason) tuple
        """
        if not prompt.strip():
            return True, ""

        self.logger.debug(f"[PROMPT_VALIDATOR] Validating prompt (length={len(prompt)}, session={session_id[:8]}...)")

        if not self.config.sync_mode:
            # Async mode - just log, don't block
            self.logger.info("[PROMPT_VALIDATOR] Sync mode: DISABLED - not blocking prompts")
            return True, ""

        self.logger.info("[PROMPT_VALIDATOR] Sync mode: ENABLED - validating against guardrails")

        # Call Akto API to validate
        allowed, reason = self.akto_client.validate_prompt(
            prompt=prompt,
            session_id=session_id,
            model=model,
            platform=platform
        )

        self.logger.info(f"[PROMPT_VALIDATOR] Validation result: allowed={allowed}")
        return allowed, reason

    async def log_response(
        self,
        session_id: str = "",
        user_message: str = "",
        assistant_response: str = "",
        conversation_history: list = None,
        model: str = "",
        platform: str = ""
    ) -> bool:
        """
        Log AI response for audit trail.

        Args:
            session_id: Session identifier
            user_message: Original user message
            assistant_response: AI response
            conversation_history: Full conversation
            model: Model name
            platform: Platform name

        Returns:
            True if logging succeeded
        """
        if conversation_history is None:
            conversation_history = []

        self.logger.debug(
            f"[PROMPT_VALIDATOR] Logging response (session={session_id[:8]}..., response_len={len(assistant_response)})"
        )

        self.logger.info(f"[PROMPT_VALIDATOR] Conversation history entries: {len(conversation_history)}")

        # Log to Akto
        result = self.akto_client.log_prompt_response(
            prompt=user_message,
            response=assistant_response,
            session_id=session_id,
            model=model,
            platform=platform
        )

        self.logger.info(f"[PROMPT_VALIDATOR] Log result: {result}")
        return result


class ToolValidator:
    """Validates tool calls before execution."""

    def __init__(self, config: Config, akto_client: AktoClient):
        """Initialize tool validator."""
        self.config = config
        self.akto_client = akto_client
        self.logger = logger

    async def validate_before(
        self,
        tool_name: str,
        args: Dict[str, Any],
        session_id: str = "",
        task_id: str = "",
        **kwargs: Any
    ) -> Tuple[bool, str]:
        """
        Validate tool call before execution.

        Args:
            tool_name: Name of tool
            args: Tool arguments
            session_id: Session identifier
            task_id: Task identifier
            **kwargs: Additional context

        Returns:
            (allowed, reason) tuple
        """
        if not tool_name:
            return True, ""

        self.logger.debug(f"[TOOL_VALIDATOR] Validating tool (tool={tool_name}, session={session_id[:8]}...)")

        if not self.config.sync_mode:
            # Async mode - just log, don't block
            self.logger.info("[TOOL_VALIDATOR] Sync mode: DISABLED - not blocking tools")
            return True, ""

        self.logger.info("[TOOL_VALIDATOR] Sync mode: ENABLED - validating against guardrails")

        # Call Akto API to validate
        allowed, reason = self.akto_client.validate_tool(
            tool_name=tool_name,
            args=args,
            session_id=session_id,
            task_id=task_id
        )

        self.logger.info(f"[TOOL_VALIDATOR] Validation result: allowed={allowed}")
        return allowed, reason

    async def log_after(
        self,
        tool_name: str,
        args: Dict[str, Any],
        result: str = "",
        session_id: str = "",
        task_id: str = "",
        duration_ms: int = 0
    ) -> bool:
        """
        Log tool execution result.

        Args:
            tool_name: Name of tool
            args: Tool arguments
            result: Tool output
            session_id: Session identifier
            task_id: Task identifier
            duration_ms: Execution duration

        Returns:
            True if logging succeeded
        """
        self.logger.debug(
            f"[TOOL_VALIDATOR] Logging tool result (tool={tool_name}, session={session_id[:8]}..., duration={duration_ms}ms)"
        )
        self.logger.info(f"[TOOL_VALIDATOR] Result length: {len(str(result))} chars")

        # Log to Akto
        result_success = self.akto_client.log_tool_call(
            tool_name=tool_name,
            args=args,
            result=result,
            session_id=session_id,
            task_id=task_id,
            duration_ms=duration_ms,
            is_before=False
        )

        self.logger.info(f"[TOOL_VALIDATOR] Log result: {result_success}")
        return result_success
