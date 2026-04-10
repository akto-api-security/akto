#!/usr/bin/env python3
"""Single dispatch file for all Akto Cursor hooks. Usage: python3 akto-hooks.py <hookName>"""
import sys
from akto_ingestion_utility import run_observability_hook, run_blocking_hook

_OBSERVABILITY_HOOKS = {
    "sessionStart":        "session.log",
    "sessionEnd":          "session.log",
    "stop":                "session.log",
    "preCompact":          "session.log",
    "postToolUse":         "tools.log",
    "postToolUseFailure":  "tools.log",
    "afterShellExecution": "shell.log",
    "afterFileEdit":       "file.log",
    "afterTabFileEdit":    "file.log",
    "afterAgentThought":   "agent.log",
}

_BLOCKING_HOOKS = {
    "preToolUse":        "tools.log",
    "beforeShellExecution": "shell.log",
    "beforeReadFile":    "file.log",
    "beforeTabFileRead": "file.log",
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
