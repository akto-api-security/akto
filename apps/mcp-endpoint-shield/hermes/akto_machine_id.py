"""
Machine ID generation for Hermes Akto Guardrails plugin.
Generates unique identifiers for device tracking.
"""

import os
import hashlib
import uuid
import platform
import getpass
from typing import Optional


_MACHINE_ID_CACHE: Optional[str] = None
_USERNAME_CACHE: Optional[str] = None


def get_machine_id() -> str:
    """
    Get or generate machine ID for device tracking.
    Uses cached value to ensure consistency across plugin invocations.

    Returns:
        Unique machine identifier
    """
    global _MACHINE_ID_CACHE

    if _MACHINE_ID_CACHE:
        return _MACHINE_ID_CACHE

    # Try environment variable first
    env_id = os.getenv("DEVICE_ID", "").strip()
    if env_id:
        _MACHINE_ID_CACHE = env_id
        return env_id

    # Try to read from cache file
    cache_file = os.path.expanduser("~/.config/hermes/akto/machine_id")
    if os.path.exists(cache_file):
        try:
            with open(cache_file, "r") as f:
                cached_id = f.read().strip()
                if cached_id:
                    _MACHINE_ID_CACHE = cached_id
                    return cached_id
        except (OSError, IOError):
            pass

    # Generate new machine ID
    try:
        # Use hostname + username + platform as base
        hostname = platform.node() or "unknown"
        username = getpass.getuser() or "unknown"
        system = platform.system() or "unknown"

        id_base = f"{hostname}:{username}:{system}:{uuid.getnode()}"
        machine_id = hashlib.sha256(id_base.encode()).hexdigest()[:16]
    except Exception:
        # Fallback to random UUID
        machine_id = str(uuid.uuid4())[:16]

    # Save to cache file
    try:
        cache_dir = os.path.expanduser("~/.config/hermes/akto")
        os.makedirs(cache_dir, exist_ok=True)
        with open(cache_file, "w") as f:
            f.write(machine_id)
    except (OSError, IOError):
        pass

    _MACHINE_ID_CACHE = machine_id
    return machine_id


def get_username() -> str:
    """
    Get current username for request logging.

    Returns:
        Current username or fallback identifier
    """
    global _USERNAME_CACHE

    if _USERNAME_CACHE:
        return _USERNAME_CACHE

    try:
        username = getpass.getuser()
        if username:
            _USERNAME_CACHE = username
            return username
    except Exception:
        pass

    _USERNAME_CACHE = "unknown"
    return "unknown"
