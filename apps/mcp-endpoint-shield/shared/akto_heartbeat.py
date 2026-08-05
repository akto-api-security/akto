#!/usr/bin/env python3
"""
Heartbeat publisher shared by all Akto CLI/IDE hook integrations.

Sends agent registration info (device label, username, module type) to the
Akto cyborg service (/api/updateModuleInfoForHeartbeat), mirroring the
Go AgentInfoPublisher in mcp-endpoint-shield.

Why this matters beyond bookkeeping: mini-runtime builds the map that resolves
a device to a user (DbLayer.fetchDeviceUserMap) purely from these heartbeat
records, keyed on moduleInfo.name. AgentQueryRecord drops any Atlas hook event
whose device id is absent from that map and which carries no user_email header —
and most agents' hook payloads (Claude Code among them) never carry an email.
Without a heartbeat the event is discarded before indexing, so the session never
appears under LLM observability / traces even though ingestion itself succeeded.

`name` is therefore set to the same DEVICE_ID the hooks report as the first
label of their request host, so the two always agree by construction.

Since hooks are short-lived processes (not long-running), a file-based
timestamp cache is used to rate-limit sends to once every 30 seconds.

Callers pass their own resolved log_dir; nothing here is agent-specific, so this
module is installed flat alongside the hook scripts exactly like
akto_ingestion_utility.py and akto_machine_id.py.
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

_DB_ABSTRACTOR_URL = os.getenv(
    "DATABASE_ABSTRACTOR_SERVICE_URL", "https://cyborg.akto.io"
).rstrip("/")
_AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
_HEARTBEAT_TIMEOUT = 3.0  # short timeout — must not block the hook

# Set by installers that also deploy the Go agent (mcp-endpoint-shield). The agent's
# AgentInfoPublisher already heartbeats every 30s, and the upsert key is
# (name, moduleType) — so publishing from here too would either overwrite the agent's
# record or, if the two disagree on the device label, register the machine twice.
# Hooks installed without the agent (docs, SentinelOne/RTR) leave this unset.
_AGENT_OWNS_HEARTBEAT = os.getenv("AKTO_AGENT_HEARTBEAT", "").lower() == "true"


def _agent_id_file(log_dir: str) -> str:
    return os.path.join(log_dir, "agent_id")


def _get_or_create_agent_id(log_dir: str) -> str:
    """Return a persistent nanosecond-timestamp agent ID, mirroring Go's time.Now().UnixNano()."""
    path = _agent_id_file(log_dir)
    try:
        with open(path, "r", encoding="utf-8") as f:
            agent_id = f.read().strip()
            if agent_id:
                return agent_id
    except Exception:
        pass
    agent_id = str(time.time_ns())
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(agent_id)
    except Exception:
        pass
    return agent_id


def _heartbeat_ts_file(log_dir: str) -> str:
    return os.path.join(log_dir, "last_heartbeat")


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


def send_heartbeat(log_dir: str, logger=None) -> None:
    """
    Send a heartbeat to Akto cyborg if the rate-limit window has passed.
    Errors are swallowed — heartbeat failure must never affect hook behaviour.

    Args:
        log_dir: Resolved (expanded) log directory path used by the hook.
        logger:  Optional logger for debug output.
    """
    try:
        if _AGENT_OWNS_HEARTBEAT:
            if logger:
                logger.debug("Heartbeat skipped (published by the Akto agent)")
            return

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
                    "deviceId": device_id,
                    "mcpServers": {},
                },
            }
        }

        _post_heartbeat(payload)
        _record_send(log_dir)

        if logger:
            logger.info(
                f"Heartbeat sent: agentId={agent_id}, "
                f"deviceId={device_id}, username={username}"
            )

    except Exception as e:
        if logger:
            logger.debug(f"Heartbeat skipped: {e}")
