#!/usr/bin/env bash
# Fail if guardrails-service does not pin akto-gateway/mcp-endpoint-shield
# to HEAD of akto-gateway's default branch.
set -euo pipefail

REPO="${AKTO_GATEWAY_REPO:-akto-api-security/akto-gateway}"
GOMOD="${1:-apps/guardrails-service/container/src/go.mod}"
GOSUM="${GOMOD%.mod}.sum"
ALIAS_MODULE="github.com/akto-api-security/akto-endpoint-shield"
SHIELD_MODULE="github.com/${REPO}/mcp-endpoint-shield"

if [[ ! -f "$GOMOD" ]]; then
  echo "error: go.mod not found: $GOMOD" >&2
  exit 1
fi

extract_pseudo() {
  local pattern="$1"
  grep -E "$pattern" "$GOMOD" | head -n1 | awk '{print $NF}'
}

REQUIRE_VER="$(extract_pseudo "^[[:space:]]*${ALIAS_MODULE}[[:space:]]+v0\.0\.0-")"
REPLACE_VER="$(extract_pseudo "${SHIELD_MODULE}[[:space:]]+v0\.0\.0-")"

if [[ -z "$REQUIRE_VER" || -z "$REPLACE_VER" ]]; then
  echo "error: could not parse ${ALIAS_MODULE} pseudo-versions from $GOMOD" >&2
  exit 1
fi

if [[ "$REQUIRE_VER" != "$REPLACE_VER" ]]; then
  echo "error: require and replace versions must match" >&2
  echo "  require: $REQUIRE_VER" >&2
  echo "  replace: $REPLACE_VER" >&2
  exit 1
fi

PINNED_SHA="${REQUIRE_VER##*-}"
if [[ ! "$PINNED_SHA" =~ ^[0-9a-f]{12}$ ]]; then
  echo "error: unexpected pseudo-version suffix (want 12 hex chars): $REQUIRE_VER" >&2
  exit 1
fi

if [[ -z "${GH_TOKEN:-}" && -n "${GITHUB_TOKEN:-}" ]]; then
  export GH_TOKEN="$GITHUB_TOKEN"
fi
if [[ -z "${GH_TOKEN:-}" ]] && ! gh auth status >/dev/null 2>&1; then
  echo "error: set GH_TOKEN or authenticate with gh to read $REPO" >&2
  exit 1
fi

DEFAULT_BRANCH="$(gh api "repos/${REPO}" --jq '.default_branch')"
LATEST_SHA="$(gh api "repos/${REPO}/commits/${DEFAULT_BRANCH}" --jq '.sha')"

if [[ ! "$LATEST_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "error: could not resolve HEAD of ${REPO}@${DEFAULT_BRANCH}" >&2
  exit 1
fi

echo "akto-gateway default branch: ${DEFAULT_BRANCH}"
echo "akto-gateway HEAD: ${LATEST_SHA}"
echo "${GOMOD} pin: ${REQUIRE_VER}"

if [[ "$LATEST_SHA" != "${PINNED_SHA}"* ]]; then
  echo >&2
  echo "error: ${GOMOD} is not pinned to the latest ${REPO} commit" >&2
  echo "  pinned:  ${PINNED_SHA}" >&2
  echo "  latest:  ${LATEST_SHA}" >&2
  echo >&2
  echo "To fix, from $(dirname "$GOMOD"):" >&2
  echo "  GOPROXY=direct go mod download -json ${SHIELD_MODULE}@${LATEST_SHA} | jq -r .Version" >&2
  echo "  # set that pseudo-version on both the require and replace lines in go.mod" >&2
  echo "  GOPROXY=direct go mod tidy" >&2
  exit 1
fi

if [[ -f "$GOSUM" ]]; then
  if ! grep -q "${SHIELD_MODULE} ${REQUIRE_VER} " "$GOSUM"; then
    echo "error: $GOSUM is missing ${REQUIRE_VER} for ${SHIELD_MODULE}" >&2
    exit 1
  fi
fi

echo "ok: pin matches ${REPO}@${DEFAULT_BRANCH} (${LATEST_SHA:0:12})"
