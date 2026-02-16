#!/usr/bin/env python3
"""
Machine ID generation utility for device identification.
Uses only Python standard library modules.
"""
import platform
import subprocess
import uuid


_machine_id = None


def _generate_machine_id() -> str:
    """
    Generate a unique machine ID using multiple fallback methods.

    Priority:
    1. macOS: IOPlatformUUID from ioreg
    2. Linux: /etc/machine-id (or /var/lib/dbus/machine-id)
    3. Fallback: uuid.getnode() 48-bit identifier

    Returns:
        Machine ID as a lowercase string without separators.
    """
    system = platform.system()

    if system == "Darwin":
        try:
            result = subprocess.run(
                ["ioreg", "-rd1", "-c", "IOPlatformExpertDevice"],
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
            if result.returncode == 0:
                for line in result.stdout.split("\n"):
                    if "IOPlatformUUID" in line:
                        # Line format: "IOPlatformUUID" = "UUID-VALUE"
                        parts = line.split('"')
                        if len(parts) >= 4:
                            return parts[3].replace("-", "").lower()
        except (OSError, subprocess.TimeoutExpired):
            pass

    if system == "Linux":
        for path in ("/etc/machine-id", "/var/lib/dbus/machine-id"):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    machine_id = f.read().strip().lower().replace("-", "")
                    if machine_id:
                        return machine_id
            except OSError:
                continue

    # Fallback: uuid.getnode() returns a 48-bit identifier.
    try:
        node_id = uuid.getnode()
        if node_id != 0:
            return f"{node_id:012x}"
    except (ValueError, OSError):
        pass

    # Last resort: empty string
    return ""


def get_machine_id() -> str:
    """
    Get the cached machine ID, generating it on first call.

    Returns:
        Machine ID as a lowercase string without dashes
    """
    global _machine_id
    if _machine_id is None:
        _machine_id = _generate_machine_id()
    return _machine_id


if __name__ == "__main__":
    # Print machine ID when script is executed directly
    print(get_machine_id())