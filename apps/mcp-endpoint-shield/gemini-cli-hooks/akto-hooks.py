#!/usr/bin/env python3
"""Single dispatch file for all Akto Cursor hooks. Usage: python3 akto-hooks.py <hookName>"""
import os
import sys
import json

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.gemini/akto/chat-logs")

from akto_ingestion_utility import run_observability_hook, run_blocking_hook

_OBSERVABILITY_HOOKS = {
    "SessionStart":             "session.log",
    "SessionEnd":               "session.log",
    "AfterAgent":               "agent.log",
    "BeforeToolSelection":      "tools.log",
    "Notification":             "session.log"
    "PreCompress"               "session.log"
}

_BLOCKING_HOOKS = {
    "BeforeAgent":        "agent.log",
    "BeforeTool":         "tools.log",
    "AfterTool":          "tools.log"
}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]

    if hook in _OBSERVABILITY_HOOKS:
        run_observability_hook(hook, _OBSERVABILITY_HOOKS[hook])
    elif hook in _BLOCKING_HOOKS:
        run_blocking_hook(hook, _BLOCKING_HOOKS[hook])
    else:
        print("{}")
        sys.exit(0)
