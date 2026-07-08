#!/usr/bin/env python3
"""
Register Cowork device module heartbeats via database-abstractor.

Collections are NOT pre-registered here. mini-runtime creates them on first ingest via
createCollectionForHostAndVpc(host, host.hashCode(), ...) — same as endpoint-shield.
Pre-registering with a client-computed colId duplicates that logic and caused ID mismatches.

This script only calls /api/updateModuleInfoForHeartbeat so the Agentic UI can show
device OS (mac/windows/linux) from moduleInfos.

Collection tags (username, ai-agent, gen-ai, etc.) come from ingest traffic tags and
mini-runtime's tag sync — same as production shield traffic.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if _SCRIPT_DIR not in sys.path:
    sys.path.insert(0, _SCRIPT_DIR)

from replay_aca_logs import load_events

AGENT = "claude_cowork"
MODULE_TYPE = "MCP_ENDPOINT_SHIELD"
MODULE_VERSION = "cowork-otel"


@dataclass
class DeviceMeta:
    host: str
    device_id: str
    email: str
    os_type: str
    os_version: str
    arch: str


def device_prefix(user_id: str) -> str:
    for sep in (".", "-"):
        if sep in user_id:
            seg = user_id.split(sep, 1)[0]
            if len(seg) <= 12:
                return seg
    return user_id[:12] if len(user_id) > 12 else user_id


def collection_host(user_id: str) -> str:
    return f"{device_prefix(user_id)}.ai-agent.{AGENT}"


def stable_agent_id(device_id: str) -> str:
    """Stable module id per device (shield uses nanosecond timestamp once at install)."""
    return str(abs(hash(f"cowork:{device_id}")))


def normalize_os(os_type: str) -> str:
    t = (os_type or "").lower().strip()
    if t in ("darwin", "macos", "mac"):
        return "mac"
    if t in ("windows", "win32", "win"):
        return "windows"
    if t == "linux":
        return "linux"
    return ""


def devices_from_events(events: list[dict[str, Any]]) -> dict[str, DeviceMeta]:
    """host → device metadata (first seen wins for scalar fields)."""
    by_host: dict[str, DeviceMeta] = {}
    for ev in events:
        uid = str(ev.get("user.id") or "")
        if not uid:
            continue
        host = collection_host(uid)
        dev_id = device_prefix(uid)
        email = str(ev.get("user.email") or "").strip()
        os_type = str(ev.get("os.type") or ev.get("os_type") or "")
        os_version = str(ev.get("os.version") or ev.get("os_version") or "")
        arch = str(ev.get("host.arch") or ev.get("host_arch") or "")
        if host not in by_host:
            by_host[host] = DeviceMeta(
                host=host,
                device_id=dev_id,
                email=email,
                os_type=os_type,
                os_version=os_version,
                arch=arch,
            )
            continue
        cur = by_host[host]
        if email and not cur.email:
            cur.email = email
        if os_type and not cur.os_type:
            cur.os_type = os_type
        if os_version and not cur.os_version:
            cur.os_version = os_version
        if arch and not cur.arch:
            cur.arch = arch
    return by_host


def auth_token(raw: str) -> str:
    """database-abstractor expects raw JWT in Authorization (no Bearer prefix)."""
    token = raw.strip()
    if token.lower().startswith("bearer "):
        return token[7:].strip()
    return token


def post_json(url: str, token: str, body: dict[str, Any]) -> None:
    jwt = auth_token(token)
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": jwt,
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        if resp.status != 200:
            raise RuntimeError(f"HTTP {resp.status} for {url}")


def register_module_heartbeat(
    abstractor_url: str,
    token: str,
    meta: DeviceMeta,
) -> None:
    """Shield-parity module row so Agentic UI resolves device OS from moduleInfos."""
    now = int(time.time())
    os_name = normalize_os(meta.os_type)
    additional: dict[str, Any] = {
        "username": meta.email or meta.device_id,
        "deviceId": meta.device_id,
        "version": MODULE_VERSION,
    }
    if os_name:
        additional["os"] = os_name
    if meta.os_version:
        additional["osVersion"] = meta.os_version
    if meta.arch:
        additional["arch"] = meta.arch

    body = {
        "moduleInfo": {
            "id": stable_agent_id(meta.device_id),
            "name": meta.device_id,
            "moduleType": MODULE_TYPE,
            "currentVersion": MODULE_VERSION,
            "startedTs": now,
            "lastHeartbeatReceived": now,
            "additionalData": additional,
        }
    }
    url = abstractor_url.rstrip("/") + "/api/updateModuleInfoForHeartbeat"
    post_json(url, token, body)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Register Cowork module OS heartbeats on database-abstractor"
    )
    parser.add_argument("logs_file", nargs="?", help="ACA otel_event log file")
    parser.add_argument(
        "--abstractor-url",
        default=os.environ.get("DATABASE_ABSTRACTOR_SERVICE_URL", "http://localhost:9000"),
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("DATABASE_ABSTRACTOR_SERVICE_TOKEN", ""),
        help="JWT for database-abstractor (or set DATABASE_ABSTRACTOR_SERVICE_TOKEN)",
    )
    args = parser.parse_args()

    if not args.token:
        print("Skip module heartbeat: DATABASE_ABSTRACTOR_SERVICE_TOKEN not set", file=sys.stderr)
        return 0

    logs_path = args.logs_file
    if not logs_path:
        script_dir = os.path.dirname(os.path.abspath(__file__))
        logs_path = os.path.normpath(
            os.path.join(script_dir, "..", "..", "..", "data-adhoc", "logs-otel-good.txt")
        )

    events = load_events(logs_path, good_only=True)
    by_host = devices_from_events(events)
    if not by_host:
        print("No Cowork hosts found in logs", file=sys.stderr)
        return 0

    for meta in sorted(by_host.values(), key=lambda m: m.host):
        os_label = normalize_os(meta.os_type) or "-"
        try:
            register_module_heartbeat(args.abstractor_url, args.token, meta)
            print(f"Module heartbeat {meta.host}: device={meta.device_id} os={os_label}")
        except urllib.error.URLError as exc:
            print(f"WARN: could not register {meta.host}: {exc}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
