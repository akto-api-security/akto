#!/usr/bin/env python3
"""Single dispatch file for all Akto VSCode Copilot hooks. Usage: python3 akto-hooks.py <hookName>"""
import os
import sys

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/akto/.github/akto/vscode/logs")

from akto_ingestion_utility import run_observability_hook, run_blocking_hook

_BLOCKING_HOOKS = {
    "Stop",
    "SubagentStop",
}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]

    if hook in _BLOCKING_HOOKS:
        run_blocking_hook(hook)
    else:
        run_observability_hook(hook)
        print("{}")
        sys.exit(0)
