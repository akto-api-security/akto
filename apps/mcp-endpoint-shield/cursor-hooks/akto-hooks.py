#!/usr/bin/env python3
"""Dispatch entry for Akto Cursor observability hooks. Usage: akto-hooks.py <hookName>"""
import os

os.environ.setdefault("LOG_DIR", os.path.expanduser("~/.cursor/akto/chat-logs"))

from akto_ingestion_utility import main_observability_dispatch

if __name__ == "__main__":
    main_observability_dispatch()
