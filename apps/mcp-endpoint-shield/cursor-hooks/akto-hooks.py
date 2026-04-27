#!/usr/bin/env python3
import os
import sys

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.cursor/akto/chat-logs")

from akto_ingestion_utility import run_observability_hook

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]

    run_observability_hook(hook)
    print("{}")
    sys.exit(0)
