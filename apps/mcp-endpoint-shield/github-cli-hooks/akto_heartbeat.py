#!/usr/bin/env python3
"""
Heartbeat publisher for Akto GitHub Copilot CLI hooks.

Sends agent registration info (device ID, username, module type) to the
Akto cyborg service (/api/updateModuleInfoForHeartbeat), mirroring the
Go AgentInfoPublisher in mcp-endpoint-shield.

Since hooks are short-lived processes (not long-running), a file-based
timestamp cache is used to rate-limit sends to once every 30 seconds.
"""
import json
import os
import platform
import ssl
import time
import urllib.request

from akto_machine_id import get_machine_id, get_username

MODULE_TYPE = "MCP_ENDPOINT_SHIELD"
VERSION = "1.0.0"
HEARTBEAT_INTERVAL_S = 30

_DB_ABSTRACTOR_URL = os.getenv(
    "DATABASE_ABSTRACTOR_SERVICE_URL", "https://cyborg.akto.io"
).rstrip("/")
_AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
_HEARTBEAT_TIMEOUT = 3.0  # short timeout — must not block the hook


def _agent_id_file(log_dir: str) -> str:
    return os.path.join(log_dir, "agent_id")


def _heartbeat_ts_file(log_dir: str) -> str:
    return os.path.join(log_dir, "last_heartbeat")


def _get_or_create_agent_record(log_dir: str) -> dict:
    """
    Return {'id': <uuid>, 'startedTs': <unix_int>}.
    Persisted in log_dir/agent_id so the same ID survives across hook
    invocations (mirrors Go's globalAgentID which lives for the agent process
    lifetime; here we use the file as the persistent process boundary).
    """
    path = _agent_id_file(log_dir)
    try:
        with open(path, "r", encoding="utf-8") as f:
            record = json.load(f)
            if record.get("id") and record.get("startedTs"):
                return record
    except Exception:
        pass

    record = {"id": str(time.time_ns()), "startedTs": int(time.time())}
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(record, f)
    except Exception:
        pass
    return record


def _should_send(log_dir: str) -> bool:
    """Return True if more than HEARTBEAT_INTERVAL_S seconds have passed since last send."""
    path = _heartbeat_ts_file(log_dir)
    try:
        with open(path, "r", encoding="utf-8") as f:
            last_ts = float(f.read().strip())
        if time.time() - last_ts < HEARTBEAT_INTERVAL_S:
            return False
    except Exception:
        pass
    return True


def _record_send(log_dir: str) -> None:
    path = _heartbeat_ts_file(log_dir)
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(str(time.time()))
    except Exception:
        pass


def _post_heartbeat(payload: dict) -> None:
    url = f"{_DB_ABSTRACTOR_URL}/api/updateModuleInfoForHeartbeat"
    data = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if _AKTO_API_TOKEN:
        headers["Authorization"] = _AKTO_API_TOKEN

    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    ssl_ctx = ssl._create_unverified_context()
    with urllib.request.urlopen(req, context=ssl_ctx, timeout=_HEARTBEAT_TIMEOUT) as resp:
        resp.read()


def send_heartbeat(log_dir: str, connector: str, logger=None) -> None:
    """
    Send a heartbeat to Akto cyborg if the rate-limit window has passed.
    Errors are swallowed — heartbeat failure must never affect hook behaviour.

    Args:
        log_dir:   Resolved (expanded) log directory path used by the hook.
        connector: Connector string, e.g. 'github_copilot_cli' or 'vscode'.
        logger:    Optional logger for debug output.
    """
    try:
        os.makedirs(log_dir, exist_ok=True)

        if not _should_send(log_dir):
            if logger:
                logger.debug("Heartbeat skipped (within rate-limit window)")
            return

        device_id = os.getenv("DEVICE_ID") or get_machine_id()
        username = get_username()
        agent_record = _get_or_create_agent_record(log_dir)

        payload = {
            "moduleInfo": {
                "id": agent_record["id"],
                "name": device_id,
                "moduleType": MODULE_TYPE,
                "currentVersion": VERSION,
                "startedTs": agent_record["startedTs"],
                "lastHeartbeatReceived": int(time.time()),
                "additionalData": {
                    "username": username,
                    "connector": connector,
                    "os": platform.system(),
                },
            }
        }

        _post_heartbeat(payload)
        _record_send(log_dir)

        if logger:
            logger.info(
                f"Heartbeat sent: agentId={agent_record['id']}, "
                f"deviceId={device_id}, username={username}"
            )

    except Exception as e:
        if logger:
            logger.debug(f"Heartbeat skipped: {e}")
