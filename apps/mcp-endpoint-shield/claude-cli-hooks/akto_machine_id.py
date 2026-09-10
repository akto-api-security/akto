#!/usr/bin/env python3
"""
Machine ID generation utility for device identification.
Mimics the Go implementation for generating unique device identifiers.
"""
import json
import os
import platform
import subprocess
import uuid
import re
import socket

try:
    import pwd
except ImportError:
    pwd = None



_machine_id = None
_user_email = None


def _resolve_device_name_source() -> str:
    """
    Match Go GetDeviceName: resolve name then ToLower + [^a-zA-Z0-9] -> '-'.

    1. macOS: scutil --get ComputerName
    2. Hostname with .local stripped
    3. _generate_machine_id() (IOPlatformUUID / MAC fallback)
    """
    raw = ""
    if platform.system() == "Darwin":
        try:
            result = subprocess.run(
                ["scutil", "--get", "ComputerName"],
                capture_output=True,
                text=True,
                timeout=5,
            )
            if result.returncode == 0:
                raw = (result.stdout or "").strip()
        except (FileNotFoundError, subprocess.TimeoutExpired, Exception):
            pass

    if not raw:
        try:
            h = socket.gethostname()
            if h:
                if h.endswith(".local"):
                    h = h[: -len(".local")]
                raw = h
        except Exception:
            pass

    if not raw:
        raw = _generate_machine_id()

    if raw and raw.strip():
        return re.sub(r"[^a-zA-Z0-9]", "-", raw.strip()).lower()
    return ""


def _generate_machine_id() -> str:
    """
    Generate a unique machine ID using multiple fallback methods.

    Priority:
    1. macOS: IOPlatformUUID from ioreg (matches Go implementation)
    2. Fallback: UUID-based node ID (MAC address)

    Returns:
        Machine ID as a lowercase string without dashes
    """
    # Try macOS ioreg first (matches Go denisbrodbeck/machineid implementation) 
    try:
        result = subprocess.run(
            ["ioreg", "-rd1", "-c", "IOPlatformExpertDevice"],
            capture_output=True,
            text=True,
            timeout=5
        )
        if result.returncode == 0:
            for line in result.stdout.split('\n'):
                if 'IOPlatformUUID' in line:
                    # Extract UUID from line: "IOPlatformUUID" = "UUID-VALUE"
                    parts = line.split('"')
                    if len(parts) >= 4:
                        uuid_val = parts[3].replace('-', '').lower()
                        return uuid_val
    except (FileNotFoundError, subprocess.TimeoutExpired, Exception):
        pass

    # Fallback: Try uuid.getnode() - returns MAC address as integer
    try:
        node_id = uuid.getnode()
        if node_id != 0:
            # Convert to MAC address format
            mac = ':'.join(['{:02x}'.format((node_id >> i) & 0xff)
                           for i in range(0, 48, 8)][::-1])
            return mac.replace('-', '').replace(':', '').lower()
    except Exception:
        pass

    # Last resort: empty string
    return ""


def get_machine_id() -> str:
    """
    Get the cached device label, generating it on first call.

    Mirrors the Go GetDeviceLabel() in utils/device.go: "{device-name}-{first8ofMachineID}"
    (e.g. "macbook-pro-a1b2c3d4"). The installers bake the same value into DEVICE_ID, so a
    hook that falls back to this function reports the label the agent heartbeat uses.

    Returns:
        Device label as a lowercase string
    """
    global _machine_id
    if _machine_id is None:
        device_name = _resolve_device_name_source()
        machine_id = _generate_machine_id()
        short_id = machine_id[:8]
        if device_name and short_id:
            _machine_id = f"{device_name}-{short_id}"
        elif device_name:
            _machine_id = device_name
        else:
            _machine_id = machine_id
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


def get_user_email() -> str:
    """
    Email of the account currently signed in to Claude CLI.

    Source of truth is ~/.claude.json's oauthAccount.emailAddress - the same key
    the agent reads in login_detector.go. The CLI rewrites that file on
    login/logout and every hook runs as a fresh process, so this always reflects
    the CURRENT account with no cache to invalidate.

    Returns "" when signed out or unreadable. Callers must treat "" as "no user",
    never as "unchanged", or a stale address survives a logout.
    """
    global _user_email
    if _user_email is not None:
        return _user_email

    # MDM/demo pin, matching the AKTO_HOSTNAME / AKTO_DEVICE_ID override
    # convention. Deliberately not exported by the installers: it masks every
    # subsequent account switch.
    _user_email = (os.environ.get("AKTO_USER_EMAIL") or "").strip()
    if _user_email:
        return _user_email

    try:
        home = os.path.expanduser("~")
        # launchd/root context: ~ is /var/root, so resolve the console user's home.
        if pwd is not None and hasattr(os, "getuid") and os.getuid() == 0:
            home = pwd.getpwnam(get_username()).pw_dir
        with open(os.path.join(home, ".claude.json"), encoding="utf-8") as f:
            account = json.load(f).get("oauthAccount") or {}
        email = str(account.get("emailAddress") or "").strip()
        if "@" in email:
            _user_email = email
    except Exception:
        # Fail open: identity resolution must never break the hook. No fallback to
        # `claude auth status` here - a subprocess on the interactive prompt path is
        # not worth it under the hook's 10s timeout.
        pass
    return _user_email


if __name__ == "__main__":
    # Print machine ID when script is executed directly
    print(get_machine_id())
# done