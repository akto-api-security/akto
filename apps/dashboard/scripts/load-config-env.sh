#!/usr/bin/env bash
# Source apps/dashboard/scripts/config.env and normalize AWS credential names.
# Supports both AWS CLI credential-file style (lowercase) and SDK env style (UPPERCASE),
# including temporary STS creds with session token.
#
# Usage (from another script):
#   # shellcheck disable=SC1091
#   source "$(dirname "$0")/load-config-env.sh"

_AKTO_DASHBOARD_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_AKTO_DASHBOARD_ENV_FILE="${_AKTO_DASHBOARD_SCRIPT_DIR}/config.env"

if [[ ! -f "${_AKTO_DASHBOARD_ENV_FILE}" ]]; then
  echo "WARN: ${_AKTO_DASHBOARD_ENV_FILE} not found — using defaults. Copy config.env.example → config.env"
  unset _AKTO_DASHBOARD_SCRIPT_DIR _AKTO_DASHBOARD_ENV_FILE
  return 0 2>/dev/null || exit 0
fi

set -a
# shellcheck disable=SC1090
source "${_AKTO_DASHBOARD_ENV_FILE}"
set +a

# Map credential-file style keys → AWS SDK / DefaultCredentialsProvider env vars.
if [[ -n "${aws_access_key_id:-}" && -z "${AWS_ACCESS_KEY_ID:-}" ]]; then
  export AWS_ACCESS_KEY_ID="${aws_access_key_id}"
fi
if [[ -n "${aws_secret_access_key:-}" && -z "${AWS_SECRET_ACCESS_KEY:-}" ]]; then
  export AWS_SECRET_ACCESS_KEY="${aws_secret_access_key}"
fi
if [[ -n "${aws_session_token:-}" && -z "${AWS_SESSION_TOKEN:-}" ]]; then
  export AWS_SESSION_TOKEN="${aws_session_token}"
fi
# Common alternate spellings from pasted AWS console exports.
if [[ -n "${AWS_SECURITY_TOKEN:-}" && -z "${AWS_SESSION_TOKEN:-}" ]]; then
  export AWS_SESSION_TOKEN="${AWS_SECURITY_TOKEN}"
fi

# Region: prefer explicit AWS_REGION, else bucket helper region.
if [[ -z "${AWS_REGION:-}" && -n "${AKTO_ENDPOINT_AGENTS_REGION:-}" ]]; then
  export AWS_REGION="${AKTO_ENDPOINT_AGENTS_REGION}"
fi
if [[ -z "${AWS_DEFAULT_REGION:-}" && -n "${AWS_REGION:-}" ]]; then
  export AWS_DEFAULT_REGION="${AWS_REGION}"
fi

echo "Loaded env: ${_AKTO_DASHBOARD_ENV_FILE}"
if [[ -n "${AWS_ACCESS_KEY_ID:-}" ]]; then
  if [[ -n "${AWS_SESSION_TOKEN:-}" ]]; then
    echo "AWS:       temporary credentials (access key + session token)"
  else
    echo "AWS:       long-lived access key"
  fi
fi

unset _AKTO_DASHBOARD_SCRIPT_DIR _AKTO_DASHBOARD_ENV_FILE
