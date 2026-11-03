#!/bin/bash
# Run on YOUR MAC (arm64) from anywhere. Builds the consumer image for the VM's architecture and copies it
# to the VM over SSH (no registry, no GitHub access needed on the VM).
#   bash log-pipeline-prod/build-and-ship-consumer.sh ubuntu@<vm-private-ip> [linux/arm64|linux/amd64]
set -euo pipefail
TARGET="${1:?usage: $0 user@vm-ip [platform]}"
PLATFORM="${2:-linux/arm64}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
docker build --platform "$PLATFORM" -f log-pipeline-prod/Dockerfile.consumer -t log-consumer:local .
docker save log-consumer:local | gzip | ssh "$TARGET" 'gunzip | docker load'
echo "image loaded on $TARGET"
