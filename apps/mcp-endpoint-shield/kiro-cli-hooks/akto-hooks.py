#!/usr/bin/env python3
"""Single dispatch file for all Akto kiro-cli hooks.

Usage: python3 akto-hooks.py <hookEventName>

Per the Kiro CLI hooks docs (https://kiro.dev/docs/cli/hooks/), only preToolUse can
block: exit code 2 rejects the tool and STDERR is returned to the LLM. userPromptSubmit
CANNOT block — it can only add context (exit 0) or show a warning (other exit). So:
  - preToolUse      → exit-code blocking runner (exit 2 blocks)
  - userPromptSubmit → warn runner (ingests for visibility, warns, but proceeds)
  - everything else  → fire-and-forget observability
"""
import os
import sys

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.kiro/akto/logs")

from akto_ingestion_utility import (
    run_exit_code_blocking_hook,
    run_observability_hook,
    run_warn_hook,
)

# Only preToolUse can actually block (exit 2). userPromptSubmit can only warn.
BLOCKING_EVENTS = {"preToolUse"}
WARN_EVENTS = {"userPromptSubmit"}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookEventName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]
    if hook in BLOCKING_EVENTS:
        run_exit_code_blocking_hook(hook)
    elif hook in WARN_EVENTS:
        run_warn_hook(hook)
    else:
        run_observability_hook(hook)
