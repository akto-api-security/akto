#!/usr/bin/env bash
# Refreshes committed dashboard static assets from upstream CDNs (CI + local).
# On failure, existing files under apps/dashboard/web/public/ are left unchanged.
set -euo pipefail

WEB_ROOT="$(cd "$(dirname "$0")/../web" && pwd)"

download() {
  local url="$1"
  local dest="$2"
  if curl -fsSL --compressed "$url" -o "$dest"; then
    echo "Downloaded $(basename "$dest")"
  else
    if [[ -f "$dest" ]]; then
      echo "Warning: failed to download ${url}; using committed $(basename "$dest")" >&2
      return 0
    fi
    echo "Error: failed to download ${url} and no committed copy at ${dest}" >&2
    return 1
  fi
}

mkdir -p \
  "${WEB_ROOT}/public/maps" \
  "${WEB_ROOT}/public/vendor/jquery" \
  "${WEB_ROOT}/public/vendor/codicons" \
  "${WEB_ROOT}/public/vendor/tailwind" \
  "${WEB_ROOT}/public/vendor/google"

download "https://code.highcharts.com/mapdata/custom/world.topo.json" \
  "${WEB_ROOT}/public/maps/world.topo.json"

download "https://ajax.googleapis.com/ajax/libs/jquery/3.6.3/jquery.min.js" \
  "${WEB_ROOT}/public/vendor/jquery/jquery-3.6.3.min.js"

download "https://cdn.jsdelivr.net/npm/vscode-codicons@0.0.16/dist/codicon.min.css" \
  "${WEB_ROOT}/public/vendor/codicons/codicon.min.css"

download "https://cdn.jsdelivr.net/npm/vscode-codicons@0.0.16/dist/codicon.ttf" \
  "${WEB_ROOT}/public/vendor/codicons/codicon.ttf"

download "https://unpkg.com/@tailwindcss/browser@4" \
  "${WEB_ROOT}/public/vendor/tailwind/tailwind.browser.js"

download "https://apis.google.com/js/client:platform.js" \
  "${WEB_ROOT}/public/vendor/google/client-platform.js"

echo "Dashboard vendor assets ready under ${WEB_ROOT}/public/"
