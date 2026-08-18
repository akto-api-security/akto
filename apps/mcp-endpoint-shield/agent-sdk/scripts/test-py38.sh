#!/usr/bin/env bash
# Run the agent-sdk test suite on the Python 3.8 floor (see SPEC.md §7) via Docker.
#
# Why Docker: 3.8 is the customer floor but isn't installed locally, and passing on a
# newer local interpreter does not prove 3.8 compatibility. python:3.8-slim is a clean
# 3.8, closest to what CI runs.
#
# Why mount the PARENT (mcp-endpoint-shield), not agent-sdk: the characterization tests
# import shared/ and github-cli-hooks/ via conftest, resolved relative to the parent.
#
# Usage:  ./scripts/test-py38.sh            # run the suite on 3.8
#         ./scripts/test-py38.sh -k contract   # pass extra args through to pytest
set -euo pipefail

SDK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # .../agent-sdk
SHIELD_DIR="$(dirname "$SDK_DIR")"                           # .../mcp-endpoint-shield
PYTEST_VERSION="8.3.4"                                       # last pytest supporting 3.8

if ! docker info >/dev/null 2>&1; then
  echo "error: docker daemon not running (start Docker/OrbStack and retry)" >&2
  exit 1
fi

exec docker run --rm \
  -v "$SHIELD_DIR:/work" \
  -w /work/agent-sdk \
  python:3.8-slim \
  bash -c "python --version && pip install -q pytest==$PYTEST_VERSION && python -m pytest $*"
