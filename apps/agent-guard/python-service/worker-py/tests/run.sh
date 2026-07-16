#!/usr/bin/env bash
# Run the worker test suite via the locked uv environment (pinned deps, no
# ad-hoc ephemeral installs). Offline by default.
# For the live cascade test:  AGW_LIVE=1 ./tests/run.sh
set -euo pipefail
cd "$(dirname "$0")/.."

exec uv run --locked --python 3.12 pytest "$@"
