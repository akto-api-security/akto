#!/usr/bin/env python3
"""
Skill-level blocked check for Akto MCP Endpoint Shield.

Calls cyborg POST /api/fetchApiInfosByCollection to get all api_info for an account,
then identifies skills where isSkillBlocked=True.

Collection ID is derived at runtime using the same naming convention as the Go gateway:
  collection_name = "{device_label}.ai-agent.{agent_name}"   (e.g. "macbook-pro-a1b2c3d4.ai-agent.cursor")
  collection_id   = Java String.hashCode(collection_name)     (matches HttpCallParser.createApiCollectionId)

Device label mirrors Go GetDeviceLabel():
  device_label = GetDeviceName() + "-" + GetMachineID()[:8]

Explicit override: set SKILL_COLLECTION_ID env var to skip auto-computation.

Environment Variables:
    DATABASE_ABSTRACTOR_SERVICE_URL : Cyborg base URL (default: https://cyborg.akto.io)
    AKTO_API_TOKEN / AKTO_TOKEN     : Authorization token
    AKTO_AGENT_NAME                 : Agent name used in collection naming (default: cursor)
    SKILL_COLLECTION_ID             : Override: explicit integer collection ID (skips auto-compute)
    SKILL_CHECK_TIMEOUT             : HTTP timeout in seconds (default: 3)
    SKILL_BLOCKED_CACHE_TTL         : Cache TTL in seconds (default: 60)
"""

import json
import os
import re
import ssl
import subprocess
import time
import urllib.request
import uuid
from typing import FrozenSet, Tuple

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

_CYBORG_URL = "https://ultron.akto.io"

def _get_token() -> str:
    # Reuse config.get_token() if available (cursor hook dir), else fall back to env vars
    try:
        from config import get_token
        return get_token()
    except ImportError:
        pass
    return os.getenv("AKTO_API_TOKEN", "") or os.getenv("AKTO_TOKEN", "")
_SKILL_CHECK_TIMEOUT = float(os.getenv("SKILL_CHECK_TIMEOUT", "3"))
_CACHE_TTL = int(os.getenv("SKILL_BLOCKED_CACHE_TTL", "60"))
_AGENT_NAME = os.getenv("AKTO_AGENT_NAME", "cursor")

# In-process cache: (last_fetch_ts, frozenset_of_blocked_skill_names)
_cache: Tuple[float, FrozenSet[str]] = (0.0, frozenset())


# ---------------------------------------------------------------------------
# Java String.hashCode() — matches HttpCallParser.createApiCollectionId
# ---------------------------------------------------------------------------

def _java_hashcode(s: str) -> int:
    """Compute Java's String.hashCode() for s. Returns a signed 32-bit integer."""
    h = 0
    for c in s:
        h = (31 * h + ord(c)) & 0xFFFFFFFF
    return h - 0x100000000 if h >= 0x80000000 else h


# ---------------------------------------------------------------------------
# Device label — mirrors Go GetDeviceLabel() / GetDeviceName() / GetMachineID()
# ---------------------------------------------------------------------------

def _get_raw_machine_id() -> str:
    """
    Matches Go GetMachineID():
      Primary  : IOPlatformUUID via ioreg (same source as denisbrodbeck/machineid on macOS)
      Fallback : first non-empty MAC address via net.Interfaces() equivalent
    Go then does: strings.ReplaceAll(id, "-", "") + strings.ToLower
    So dashes are stripped but colons (in MAC fallback) are kept — we replicate that exactly.
    """
    try:
        result = subprocess.run(
            ["ioreg", "-rd1", "-c", "IOPlatformExpertDevice"],
            capture_output=True, text=True, timeout=5,
        )
        if result.returncode == 0:
            for line in result.stdout.split("\n"):
                if "IOPlatformUUID" in line:
                    parts = line.split('"')
                    if len(parts) >= 4:
                        # Go: strings.ReplaceAll(id, "-", "") + ToLower
                        return parts[3].replace("-", "").lower()
    except Exception:
        pass

    # Fallback: first non-empty MAC address — mirrors Go net.Interfaces() loop.
    # Go returns HardwareAddr.String() which is "aa:bb:cc:dd:ee:ff", then strips
    # only dashes (not colons), so colons remain in the final ID.
    import socket
    try:
        for iface in socket.if_nameindex():
            try:
                import fcntl, struct
                SIOCGIFHWADDR = 0x8927
                s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                info = fcntl.ioctl(s.fileno(), SIOCGIFHWADDR,
                                   struct.pack("256s", iface[1][:15].encode()))
                mac = ":".join("{:02x}".format(b) for b in info[18:24])
                if mac and mac != "00:00:00:00:00:00":
                    # Go strips dashes only; MAC has colons not dashes, so result keeps colons
                    return mac.replace("-", "").lower()
            except Exception:
                continue
    except Exception:
        pass

    # Last resort: uuid.getnode() returns MAC as integer; format to match Go's HardwareAddr
    try:
        node = uuid.getnode()
        if node:
            mac = ":".join("{:02x}".format((node >> (i * 8)) & 0xFF)
                           for i in reversed(range(6)))
            return mac.replace("-", "").lower()
    except Exception:
        pass

    return ""


def _get_device_name() -> str:
    """
    Matches Go GetDeviceName(): ComputerName on macOS, hostname fallback.
    Returns lowercase with non-alphanumeric chars replaced by '-'.
    Reuses akto_machine_id.get_machine_id() if available in the same directory.
    """
    try:
        from akto_machine_id import get_machine_id
        return get_machine_id()
    except ImportError:
        pass

    import platform
    import socket

    raw = ""
    if platform.system() == "Darwin":
        try:
            r = subprocess.run(["scutil", "--get", "ComputerName"],
                               capture_output=True, text=True, timeout=5)
            if r.returncode == 0:
                raw = r.stdout.strip()
        except Exception:
            pass

    if not raw:
        try:
            h = socket.gethostname()
            raw = h[:-len(".local")] if h.endswith(".local") else h
        except Exception:
            pass

    if not raw:
        raw = _get_raw_machine_id()

    return re.sub(r"[^a-zA-Z0-9]", "-", raw.strip()).lower() if raw else ""


def _get_device_label() -> str:
    """
    Matches Go GetDeviceLabel(): "{device_name}-{machine_id[:8]}"
    e.g. "macbook-pro-a1b2c3d4"
    """
    name = _get_device_name()
    raw_id = _get_raw_machine_id()
    short_id = raw_id[:8] if len(raw_id) >= 8 else raw_id
    return f"{name}-{short_id}"


def _compute_collection_id(agent_name: str) -> int:
    """
    Matches Go GetAgentCollectionName() + Java hashCode in createApiCollectionId.
    collection_name = "{device_label}.ai-agent.{agent_name}"
    collection_id   = Java String.hashCode(collection_name)
    """
    collection_name = f"{_get_device_label()}.ai-agent.{agent_name}"
    return _java_hashcode(collection_name)


# ---------------------------------------------------------------------------
# Cyborg API fetch
# ---------------------------------------------------------------------------

def _resolve_collection_id(agent_name: str) -> int:
    """Return collection ID from SKILL_COLLECTION_ID override or auto-computed."""
    override = os.getenv("SKILL_COLLECTION_ID", "").strip()
    if override:
        try:
            return int(override)
        except ValueError:
            pass
    return _compute_collection_id(agent_name)


def _fetch_blocked_skills(agent_name: str) -> FrozenSet[str]:
    """
    POST /api/fetchApiInfosByCollection and return skill names where isSkillBlocked=True.
    Skill name extracted by stripping /skills/ prefix from api_info.id.url.
    """
    if not _CYBORG_URL:
        return frozenset()

    collection_id = _resolve_collection_id(agent_name)
    url = f"{_CYBORG_URL}/api/fetchApiInfosByCollection"
    payload = json.dumps({"apiCollectionId": collection_id}).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    token = _get_token()
    if token:
        headers["Authorization"] = token

    req = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    ssl_ctx = ssl._create_unverified_context()

    with urllib.request.urlopen(req, context=ssl_ctx, timeout=_SKILL_CHECK_TIMEOUT) as resp:
        raw = resp.read().decode("utf-8")

    data = json.loads(raw)
    api_infos = data if isinstance(data, list) else data.get("apiInfos", [])

    blocked: set = set()
    for api_info in api_infos:
        if api_info.get("isSkillBlocked", False):
            skill_url = (api_info.get("id") or {}).get("url", "")
            if skill_url.startswith("/skills/"):
                skill_name = skill_url[len("/skills/"):]
                if skill_name:
                    blocked.add(skill_name)
    return frozenset(blocked)


def _get_blocked_skills(agent_name: str, logger=None) -> FrozenSet[str]:
    """Return cached blocked skill names, refreshing when TTL has elapsed."""
    global _cache
    last_ts, skills = _cache

    if time.time() - last_ts < _CACHE_TTL:
        return skills

    try:
        skills = _fetch_blocked_skills(agent_name)
        _cache = (time.time(), skills)
        if logger:
            logger.info(f"Skill blocked list refreshed ({agent_name}): {len(skills)} blocked skill(s)")
    except Exception as e:
        if logger:
            logger.debug(f"Skill blocked fetch skipped (fail-open): {e}")
        if last_ts > 0:
            _cache = (time.time(), skills)

    return skills


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def is_skill_blocked(skill_name: str, agent_name: str = _AGENT_NAME, logger=None) -> bool:
    """
    Return True if skill_name is blocked by org policy (isSkillBlocked=True in cyborg).

    Collection ID is auto-derived from device label + agent_name at runtime,
    matching the Go gateway naming convention. Override with SKILL_COLLECTION_ID env var.

    Fail-open: returns False on any error or if DATABASE_ABSTRACTOR_SERVICE_URL is unset.

    Args:
        skill_name:  The skill/tool name to check (e.g. "babysit", "shell").
        agent_name:  Agent identifier used to derive the collection (e.g. "cursor", "claude").
        logger:      Optional standard-library logger.
    """
    if not _CYBORG_URL:
        return False

    blocked = _get_blocked_skills(agent_name, logger)
    if skill_name in blocked:
        if logger:
            logger.warning(f"Skill '{skill_name}' is blocked by your organization's policy")
        return True
    return False


async def async_is_skill_blocked(skill_name: str, agent_name: str = _AGENT_NAME, logger=None) -> bool:
    """Async wrapper around is_skill_blocked for use in async hooks."""
    import asyncio
    return await asyncio.to_thread(is_skill_blocked, skill_name, agent_name, logger)
