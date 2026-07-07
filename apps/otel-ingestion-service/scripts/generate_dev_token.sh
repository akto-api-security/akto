#!/usr/bin/env bash
# Generate RSA_PUBLIC_KEY + JWT for local auth testing.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="${1:-$ROOT/.dev-keys}"
ACCOUNT_ID="${ACCOUNT_ID:-42}"

mkdir -p "$OUT_DIR"
cd "$ROOT/container/src"

eval "$(go run ./cmd/devtools jwt --account "$ACCOUNT_ID" --out-dir "$OUT_DIR")"

cat >"$OUT_DIR/env.snippet" <<EOF
# Source before starting the service:
#   source $OUT_DIR/env.snippet && cd $ROOT/container/src && go run .
export RSA_PUBLIC_KEY="$RSA_PUBLIC_KEY"
export AKTO_OTLP_AUTHENTICATE=true
export JWT="$JWT"
EOF

echo ""
echo "Wrote keys to $OUT_DIR"
echo "Source env:  source $OUT_DIR/env.snippet"
