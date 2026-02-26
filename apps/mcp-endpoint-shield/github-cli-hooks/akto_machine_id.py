#!/usr/bin/env python3
"""
Machine ID generation utility for device identification.
Uses only Python standard library modules.
"""
import os
import platform
import pwd
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


_username = None


def get_username() -> str:
    """
    Get the current system username using multiple detection methods.
    Mirrors the Go GetUsername() implementation in utils/home.go.

    Priority:
    1. Windows: USERNAME environment variable
    2. SUDO_USER environment variable (when running with sudo)
    3. Root detection with platform-specific methods:
       - macOS: stat -f %Su /dev/console, fallback scutil ConsoleUser
       - Linux: getent passwd (first non-root /home/ user)
    4. pwd.getpwuid(os.getuid()).pw_name
    5. Fallback: "unknown"
    """
    global _username
    if _username is not None:
        return _username

    # Windows: Check USERNAME first
    if platform.system() == "Windows":
        username = os.environ.get("USERNAME", "")
        if username:
            _username = username
            return _username

    # Try SUDO_USER (when running with sudo)
    sudo_user = os.environ.get("SUDO_USER", "")
    if sudo_user and sudo_user != "root":
        _username = sudo_user
        return _username

    # Resolve current user and check for root
    current_user = None
    is_root = False
    try:
        current_uid = os.getuid()
        current_user = pwd.getpwuid(current_uid).pw_name
        is_root = current_user == "root" or current_uid == 0
    except Exception:
        pass

    if is_root:
        system = platform.system()
        if system == "Darwin":
            # macOS: get console user via stat
            try:
                result = subprocess.run(
                    ["stat", "-f", "%Su", "/dev/console"],
                    capture_output=True, text=True, timeout=5
                )
                if result.returncode == 0:
                    username = result.stdout.strip()
                    if username and username != "root":
                        _username = username
                        return _username
            except Exception:
                pass

            # Fallback: scutil for ConsoleUser
            try:
                result = subprocess.run(
                    ["scutil"],
                    capture_output=True, text=True, timeout=5
                )
                if result.returncode == 0:
                    for line in result.stdout.split("\n"):
                        if "ConsoleUser" in line:
                            parts = line.split()
                            if len(parts) >= 3:
                                username = parts[2]
                                if username and username not in ("root", "loginwindow"):
                                    _username = username
                                    return _username
            except Exception:
                pass

        elif system == "Linux":
            # Linux: first non-root user with /home/ prefix from getent passwd
            try:
                result = subprocess.run(
                    ["getent", "passwd"],
                    capture_output=True, text=True, timeout=5
                )
                if result.returncode == 0:
                    for line in result.stdout.split("\n"):
                        parts = line.split(":")
                        if len(parts) >= 6 and parts[0] != "root" and parts[5].startswith("/home/"):
                            _username = parts[0]
                            return _username
            except Exception:
                pass

    if current_user is not None:
        _username = current_user
        return _username

    _username = "unknown"
    return _username


if __name__ == "__main__":
    # Print machine ID when script is executed directly
    print(get_machine_id())
