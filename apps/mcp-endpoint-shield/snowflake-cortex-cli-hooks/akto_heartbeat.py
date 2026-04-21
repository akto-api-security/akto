#!/usr/bin/env python3
"""
Heartbeat publisher for Akto Snowflake Cortex Code CLI hooks.

Sends agent registration info (device ID, username, module type) to the
Akto cyborg service (/api/updateModuleInfoForHeartbeat), mirroring
github-cli-hooks/akto_heartbeat.py.

Since hooks are short-lived processes (not long-running), a file-based
timestamp cache is used to rate-limit sends to once every 30 seconds.
"""
import json
import os
import ssl
import time
import urllib.request

from akto_machine_id import get_machine_id, get_username

MODULE_TYPE = "MCP_ENDPOINT_SHIELD"
VERSION = "1.0.0"
HEARTBEAT_INTERVAL_S = 30
_DEFAULT_CYBORG = "https://cyborg.akto.io"
_HEARTBEAT_TIMEOUT = 3.0


def _db_abstractor_url() -> str:
    raw = (os.getenv("DATABASE_ABSTRACTOR_SERVICE_URL") or "").strip().rstrip("/")
    if not raw or "{{" in raw:
        return _DEFAULT_CYBORG
    return raw


def _api_token() -> str:
    t = (os.getenv("AKTO_API_TOKEN") or os.getenv("AKTO_TOKEN") or "").strip()
    if "{{" in t:
        return ""
    return t


def _agent_id_file(log_dir: str) -> str:
    return os.path.join(log_dir, "agent_id")


def _get_or_create_agent_id(log_dir: str) -> str:
    path = _agent_id_file(log_dir)
    try:
        with open(path, "r", encoding="utf-8") as f:
            agent_id = f.read().strip()
            if agent_id:
                return agent_id
    except OSError:
        pass
    agent_id = str(time.time_ns())
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(agent_id)
    except OSError:
        pass
    return agent_id


def _heartbeat_ts_file(log_dir: str) -> str:
    return os.path.join(log_dir, "last_heartbeat")


def _should_send(log_dir: str) -> bool:
    path = _heartbeat_ts_file(log_dir)
    try:
        with open(path, "r", encoding="utf-8") as f:
            last_ts = float(f.read().strip())
            if time.time() - last_ts < HEARTBEAT_INTERVAL_S:
                return False
    except (OSError, ValueError):
        pass
    return True


def _record_send(log_dir: str) -> None:
    path = _heartbeat_ts_file(log_dir)
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(str(time.time()))
    except OSError:
        pass


def _post_heartbeat(payload: dict) -> None:
    base = _db_abstractor_url()
    url = f"{base}/api/updateModuleInfoForHeartbeat"
    data = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    token = _api_token()
    if token:
        headers["Authorization"] = token

    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    ssl_ctx = ssl._create_unverified_context()
    with urllib.request.urlopen(req, context=ssl_ctx, timeout=_HEARTBEAT_TIMEOUT) as resp:
        resp.read()


def send_heartbeat(log_dir: str, logger=None) -> None:
    """
    Send a heartbeat to Akto cyborg if the rate-limit window has passed.
    Errors are swallowed — heartbeat failure must never affect hook behaviour.
    """
    try:
        os.makedirs(log_dir, exist_ok=True)

        if not _should_send(log_dir):
            if logger:
                logger.debug("Heartbeat skipped (within rate-limit window)")
            return

        device_id = os.getenv("DEVICE_ID") or get_machine_id()
        username = get_username()
        agent_id = _get_or_create_agent_id(log_dir)
        now_s = int(time.time())

        payload = {
            "moduleInfo": {
                "id": agent_id,
                "name": device_id,
                "moduleType": MODULE_TYPE,
                "currentVersion": VERSION,
                "startedTs": now_s,
                "lastHeartbeatReceived": now_s,
                "additionalData": {
                    "username": username,
                    "mcpServers": {},
                },
            }
        }

        _post_heartbeat(payload)
        _record_send(log_dir)

        if logger:
            logger.info(
                "Heartbeat sent: agentId=%s, deviceId=%s, username=%s",
                agent_id,
                device_id,
                username,
            )

    except Exception as e:
        if logger:
            logger.debug("Heartbeat skipped: %s", e)
