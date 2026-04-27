#!/usr/bin/env python3
"""Single dispatch file for all Akto Cursor hooks. Usage: python3 akto-hooks.py <hookName>"""
import os
import sys
import json

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.gemini/akto/chat-logs")

from akto_ingestion_utility import run_observability_hook

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]

    run_observability_hook(hook)
    print("{}")
    sys.exit(0)
