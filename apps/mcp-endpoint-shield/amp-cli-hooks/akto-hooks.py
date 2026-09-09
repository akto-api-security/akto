#!/usr/bin/env python3
"""Single dispatch file for Akto Amp observability hooks. Usage: python3 akto-hooks.py <hookName>"""
import os
import sys

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.config/amp/akto/logs")

# Set before importing: the shared utility reads AKTO_CONNECTOR at import time.
os.environ.setdefault("AKTO_CONNECTOR", "amp")

from akto_ingestion_utility import run_observability_hook

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]

    run_observability_hook(hook)
    print("{}")
    sys.exit(0)
