#!/usr/bin/env bash
# Deploy agent-guard images to Azure Container Apps.
#
# Usage:
#   ./deploy/azure-deploy.sh                          # :latest + force new revision
#   IMAGE_TAG=a-my-branch ./deploy/azure-deploy.sh
#   RG=rg-agent-guard-prod WORKER_APPS="app1 app2" ./deploy/azure-deploy.sh
#
# :latest — Azure does not auto-pull when the tag is overwritten on Docker Hub.
# This script bumps DEPLOYED_AT so each run creates a new revision and re-pulls.
#
# Optional:
#   WORKER_IMAGE=docker.io/aktosecurity/akto-agent-guard-worker
#   ANONYMIZER_IMAGE=docker.io/aktosecurity/akto-agent-guard-anonymizer
#   EMBEDDER_IMAGE=docker.io/aktosecurity/akto-agent-guard-embedder
#   EMBEDDER_APP=agent-guard-embedder   # empty to skip the embedder roll
#   SKIP_BUMP=1   # only if using a unique tag per release (not :latest)
#
# Redis (RediSearch) for the semantic cache is provisioned once and not rolled
# here — it's stateful; see deploy/azure.md.

set -euo pipefail

RG="${RG:-rg-agent-guard-prod}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
WORKER_APPS="${WORKER_APPS:-agent-guard-executor-v2 agent-guard-executor}"
ANONYMIZER_APP="${ANONYMIZER_APP:-agent-guard-anonymizer}"
EMBEDDER_APP="${EMBEDDER_APP:-agent-guard-embedder}"
WORKER_IMAGE="${WORKER_IMAGE:-docker.io/aktosecurity/akto-agent-guard-worker}"
ANONYMIZER_IMAGE="${ANONYMIZER_IMAGE:-docker.io/aktosecurity/akto-agent-guard-anonymizer}"
EMBEDDER_IMAGE="${EMBEDDER_IMAGE:-docker.io/aktosecurity/akto-agent-guard-embedder}"
DEPLOYED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "Deploying ${WORKER_IMAGE}:${IMAGE_TAG} + ${ANONYMIZER_IMAGE}:${IMAGE_TAG} + ${EMBEDDER_IMAGE}:${IMAGE_TAG} to RG ${RG}"

bump_args() {
  if [[ "${SKIP_BUMP:-}" == "1" ]] && [[ "${IMAGE_TAG}" != "latest" ]]; then
    echo ""
  else
    echo "--set-env-vars DEPLOYED_AT=${DEPLOYED_AT}"
  fi
}

BUMP="$(bump_args)"

for app in ${WORKER_APPS}; do
  echo "→ worker ${app}"
  # shellcheck disable=SC2086
  az containerapp update --name "${app}" --resource-group "${RG}" \
    --image "${WORKER_IMAGE}:${IMAGE_TAG}" ${BUMP}
done

echo "→ anonymizer ${ANONYMIZER_APP}"
# shellcheck disable=SC2086
az containerapp update --name "${ANONYMIZER_APP}" --resource-group "${RG}" \
  --image "${ANONYMIZER_IMAGE}:${IMAGE_TAG}" ${BUMP}

if [[ -n "${EMBEDDER_APP}" ]]; then
  echo "→ embedder ${EMBEDDER_APP}"
  # shellcheck disable=SC2086
  az containerapp update --name "${EMBEDDER_APP}" --resource-group "${RG}" \
    --image "${EMBEDDER_IMAGE}:${IMAGE_TAG}" ${BUMP}
fi

echo "Done (DEPLOYED_AT=${DEPLOYED_AT}). Check revision health:"
echo "  az containerapp revision list -g ${RG} -n ${WORKER_APPS%% *} -o table"
