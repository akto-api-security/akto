#!/bin/bash
# One-time setup of the logs-pipeline VM (Ubuntu 24.04, ARM64 or x64). Run ON THE VM as root:
#   sudo bash setup-vm.sh
# What it does: formats + mounts the Azure data disk at /data (XFS), installs Docker + compose plugin,
# sets transparent hugepages to madvise (MongoDB recommendation), creates /data/{mongo,kafka}, writes .env.
# It never touches anything outside this VM. Safe to re-run (it will not reformat an already-formatted disk).
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "== 1/5 data disk -> /data"
DISK=/dev/disk/azure/scsi1/lun0
[ -e "$DISK" ] || { echo "ERROR: $DISK not found. Run 'lsblk' and set DISK=/dev/sdX by hand."; exit 1; }
if ! mountpoint -q /data; then
  apt-get update -qq && apt-get install -y -qq xfsprogs
  if ! blkid "$DISK" >/dev/null 2>&1; then
    echo "formatting $DISK as XFS (empty disk)"; mkfs.xfs -q "$DISK"
  fi
  mkdir -p /data
  UUID="$(blkid -s UUID -o value "$DISK")"
  grep -q "$UUID" /etc/fstab || echo "UUID=$UUID /data xfs defaults,nofail,noatime 0 2" >> /etc/fstab
  mount /data
fi
mkdir -p /data/mongo /data/kafka
chown 1000:1000 /data/kafka          # apache/kafka image runs as uid 1000
df -h /data | tail -1

echo "== 2/5 docker"
if ! command -v docker >/dev/null; then
  apt-get install -y -qq ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
  apt-get update -qq
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker
# make sure the installed versions are current enough (compose v2 >= 2.20, docker >= 25)
DV="$(docker version --format '{{.Server.Version}}')"; CV="$(docker compose version --short)"
echo "docker engine $DV, docker compose $CV"
[ "$(printf '%s\n%s\n' 25.0.0 "$DV" | sort -V | head -1)" = 25.0.0 ] || { echo "ERROR: docker $DV is too old (need >= 25). Remove docker.io/old packages and re-run."; exit 1; }
[ "$(printf '%s\n%s\n' 2.20.0 "${CV#v}" | sort -V | head -1)" = 2.20.0 ] || { echo "ERROR: docker compose $CV is too old (need >= 2.20)."; exit 1; }
[ -n "${SUDO_USER:-}" ] && usermod -aG docker "$SUDO_USER" || true
# keep container logs from filling the OS disk
cat > /etc/docker/daemon.json <<'JSON'
{ "log-driver": "json-file", "log-opts": { "max-size": "50m", "max-file": "3" } }
JSON
systemctl restart docker

echo "== 3/5 transparent hugepages = madvise"
echo madvise > /sys/kernel/mm/transparent_hugepage/enabled || true
cat > /etc/systemd/system/thp-madvise.service <<'UNIT'
[Unit]
Description=Set transparent hugepages to madvise (MongoDB)
After=sysinit.target local-fs.target
[Service]
Type=oneshot
ExecStart=/bin/sh -c 'echo madvise > /sys/kernel/mm/transparent_hugepage/enabled'
[Install]
WantedBy=basic.target
UNIT
systemctl daemon-reload && systemctl enable thp-madvise.service >/dev/null

echo "== kernel settings (Mongo/Kafka recommendations)"
cat > /etc/sysctl.d/99-log-pipeline.conf <<'SYS'
vm.max_map_count = 262144
vm.swappiness = 1
net.core.somaxconn = 4096
SYS
sysctl --system >/dev/null

echo "== 4/5 .env"
if [ ! -s "$HERE/.env" ]; then
  PRIVATE_IP="$(hostname -I | awk '{print $1}')"
  PASS="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  # template is embedded here so this works even if .env.example was not copied (written via temp file, never leaves an empty .env)
  TMP="$(mktemp "$HERE/.env.XXXXXX")"
  cat > "$TMP" <<ENVFILE
PRIVATE_IP=$PRIVATE_IP
MONGO_IMAGE=mongo:8.0.34
MONGO_USER=logs_admin
MONGO_PASSWORD=$PASS
MONGO_MEM_LIMIT=10g
MONGO_CACHE_GB=4
KAFKA_BOOTSTRAP=kafka:19092
KAFKA_MEM_LIMIT=5g
KAFKA_HEAP=-Xms2g -Xmx2g
CONSUMER_IMAGE=log-consumer:local
CONSUMER_REPLICAS=2
LOG_CONSUMER_THREADS=5
CONSUMER_MEM_LIMIT=1536m
CONSUMER_JAVA_OPTS=-Xmx1g
ENVFILE
  chmod 600 "$TMP"
  [ -n "${SUDO_USER:-}" ] && chown "$SUDO_USER" "$TMP" || true
  mv "$TMP" "$HERE/.env"
  echo "created $HERE/.env (private IP $PRIVATE_IP, random Mongo password inside; it is not printed)"
else
  echo ".env already exists, left untouched"
fi

echo "== 5/5 done"
echo "Next: load the consumer image (build-and-ship-consumer.sh from your Mac), then:"
echo "  cd $HERE && docker compose up -d        # add '--profile kafka' only if Kafka should run on this VM"
echo "Log out and back in once so the docker group applies to your user."
