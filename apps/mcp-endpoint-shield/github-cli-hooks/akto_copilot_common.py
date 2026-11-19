#!/usr/bin/env python3
"""GitHub Copilot CLI-specific hook logic (agentStop transcript reading), split out of shared/akto_ingestion_utility.py."""
import json
import logging
import os
import sys
import time
from typing import List, Tuple

from akto_ingestion_utility import _alias_camel_keys, resolve_session_info, send_ingestion_data, setup_logger


def _extract_last_turn(transcript_path: str, logger: logging.Logger) -> Tuple[str, str]:
    """Return (last user prompt, its response) -- scans backward to the last user.message, then reads forward from there."""
    if not transcript_path or not os.path.exists(transcript_path):
        return "", ""
    try:
        with open(transcript_path, encoding="utf-8") as f:
            lines = f.readlines()
    except OSError as e:
        logger.warning(f"Could not read transcript {transcript_path}: {e}")
        return "", ""

    last_user = ""
    last_user_idx = -1
    for i in range(len(lines) - 1, -1, -1):
        line = lines[i].strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError as e:
            logger.warning(f"Skipping malformed transcript line {i}: {e}")
            continue
        if entry.get("type") == "user.message":
            content = (entry.get("data") or {}).get("content", "")
            if content:
                last_user = content
                last_user_idx = i
                break

    if last_user_idx == -1:
        return "", ""

    response_parts: List[str] = []
    for line in lines[last_user_idx + 1:]:
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError as e:
            logger.warning(f"Skipping malformed transcript line: {e}")
            continue
        if entry.get("type") == "assistant.message":
            content = (entry.get("data") or {}).get("content", "")
            if content:
                response_parts.append(content)

    return last_user, "".join(response_parts)


def run_agent_stop_hook() -> None:
    """Read the turn's transcript and ingest it; falls back to metadata-only if empty."""
    logger = setup_logger("hook-executions.log")
    logger.info("=== agentStop hook started ===")
    try:
        input_data = _alias_camel_keys(json.load(sys.stdin))
        logger.info("agentStop input:\n%s", json.dumps(input_data, indent=2))
        session_info = resolve_session_info(input_data, logger)
        transcript_path = os.path.expanduser(input_data.get("transcript_path", ""))
        user_prompt, response_text = _extract_last_turn(transcript_path, logger)

        # transcript write can lag the agentStop event by a beat — retry briefly.
        for _ in range(5):
            if response_text or not user_prompt:
                break
            time.sleep(0.2)
            user_prompt, response_text = _extract_last_turn(transcript_path, logger)

        if user_prompt or response_text:
            logger.info(f"Extracted turn — prompt: {len(user_prompt)} chars, response: {len(response_text)} chars")
            send_ingestion_data(
                hook_name="agentStop",
                request_payload=user_prompt,
                response_payload=response_text,
                session_info=session_info,
                input_data=input_data,
                guardrails=False,
                logger=logger,
            )
        else:
            logger.info("No conversational content found in transcript — ingesting metadata only")
            send_ingestion_data(
                hook_name="agentStop",
                request_payload=input_data,
                response_payload={},
                session_info=session_info,
                input_data=input_data,
                guardrails=False,
                logger=logger,
            )
        logger.info("=== agentStop hook completed ===")
    except Exception as e:
        logger.error(f"Main error: {e}")
    print(json.dumps({}))
    sys.exit(0)
