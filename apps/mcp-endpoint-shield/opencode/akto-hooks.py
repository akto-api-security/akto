#!/usr/bin/env python3
"""Dispatch entry for Akto OpenCode observability hooks. Usage: akto-hooks.py <hookName>"""
import os

os.environ.setdefault("AKTO_CONNECTOR", "opencode")
os.environ.setdefault("LOG_DIR", os.path.expanduser("~/.config/opencode/akto/logs"))

from akto_ingestion_utility import main_observability_dispatch

if __name__ == "__main__":
    main_observability_dispatch()
