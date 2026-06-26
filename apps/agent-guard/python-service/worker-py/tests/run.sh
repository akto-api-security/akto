#!/usr/bin/env bash
# Run the worker test suite under CPython 3.12 with ephemeral deps via uv.
# Offline by default. For the live cascade test:  AGW_LIVE=1 ./tests/run.sh
set -euo pipefail
cd "$(dirname "$0")/.."

exec uv run --python 3.12 \
  --with pytest --with pytest-asyncio \
  --with detect-secrets --with tiktoken \
  --with rsa --with pyasn1 --with httpx --with cryptography --with redis \
  pytest "$@"
