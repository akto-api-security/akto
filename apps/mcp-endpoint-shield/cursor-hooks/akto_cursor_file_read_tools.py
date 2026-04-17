"""Shared helpers for Cursor file-read hooks (preToolUse / postToolUse / beforeReadFile / beforeTabFileRead)."""
import json
import os
from typing import Any, Dict, FrozenSet

FILE_READ_TOOL_NAMES: FrozenSet[str] = frozenset({
    "read",
    "glob",
    "read_file",
    "glob_file_search",
})


def parse_tool_input(raw: Any) -> Dict[str, Any]:
    if isinstance(raw, dict):
        return raw
    if isinstance(raw, str):
        s = raw.strip()
        if not s:
            return {}
        try:
            return json.loads(s)
        except json.JSONDecodeError:
            return {}
    return {}


def is_file_read_tool(tool_name: str) -> bool:
    return (tool_name or "").strip().lower() in FILE_READ_TOOL_NAMES


def file_path_from_tool_input(tool_input: Dict[str, Any]) -> str:
    if not tool_input:
        return ""
    for key in ("file_path", "path", "target_file"):
        v = tool_input.get(key)
        if isinstance(v, str) and v.strip():
            return v.strip()
    return ""


def resolve_read_hook_file_path(input_data: Dict[str, Any]) -> str:
    """
    Resolve path for beforeReadFile / beforeTabFileRead payloads.
    Paths are often workspace-relative; join against workspace_roots when needed.
    """
    raw = str(input_data.get("file_path") or input_data.get("path") or "").strip()
    if not raw:
        return ""
    expanded = os.path.normpath(os.path.expanduser(raw))
    if os.path.isabs(expanded):
        return expanded
    roots = input_data.get("workspace_roots") or []
    if not isinstance(roots, list):
        roots = []
    normalized_roots = [
        os.path.normpath(os.path.expanduser(r.strip()))
        for r in roots
        if isinstance(r, str) and r.strip()
    ]
    for root in normalized_roots:
        candidate = os.path.normpath(os.path.join(root, raw))
        if os.path.isfile(candidate):
            return candidate
    if len(normalized_roots) == 1:
        return os.path.normpath(os.path.join(normalized_roots[0], raw))
    if normalized_roots:
        return os.path.normpath(os.path.join(normalized_roots[0], raw))
    return expanded


def cursor_file_guard_allow_response() -> Dict[str, Any]:
    """Stdout JSON for Cursor hooks that can allow/deny file reads (docs: permission + continue)."""
    return {"permission": "allow", "continue": True}


def cursor_file_guard_deny_response(message: str) -> Dict[str, Any]:
    return {
        "permission": "deny",
        "continue": False,
        "user_message": message,
        "agent_message": message,
        "userMessage": message,
        "agentMessage": message,
    }
