#!/usr/bin/env python3
"""Dispatch entry for Akto Gemini CLI observability hooks. Usage: akto-hooks.py <hookName>"""
import os

os.environ.setdefault("LOG_DIR", os.path.expanduser("~/.gemini/akto/chat-logs"))

from akto_ingestion_utility import main_observability_dispatch

if __name__ == "__main__":
    main_observability_dispatch()
