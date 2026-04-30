#!/usr/bin/env python3
"""
Machine ID generation utility for device identification.
Mimics the Go implementation for generating unique device identifiers.
"""
import os
import platform
import subprocess
import uuid
import re
import socket
import json
import tempfile
from pathlib import Path
from urllib.parse import urlparse

try:
    import pwd
except ImportError:
    pwd = None



_machine_id = None


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
    Get the cached machine ID, generating it on first call.

    Returns:
        Machine ID as a lowercase string without dashes
    """
    global _machine_id
    if _machine_id is None:
        _machine_id = _resolve_device_name_source()
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
# done


"""
Resolve Claude MCP server name -> URL.

Reads two files Claude actually uses:
  1. ~/.claude.json          — projects.<path>.mcpServers (written by `claude mcp add`)
  2. .mcp.json (cwd/given)   — top-level mcpServers (project-scoped, git-tracked)

Cache: /tmp/mcp_resolver_cache.json, invalidated by mtime of source files.

Usage:
    python mcp_resolver.py <server-name>           # prints URL
    python mcp_resolver.py <server-name> --domain  # prints just domain
    python mcp_resolver.py --list                  # prints all as JSON

Library:
    from mcp_resolver import resolve, resolve_domain, build_index
"""

_CACHE_FILE = Path(tempfile.gettempdir()) / "akto_mcp_resolver_cache.json"


def _source_paths(project_dir: Path | None = None) -> list[Path]:
    base = project_dir or Path.cwd()
    return [
        Path.home() / ".claude.json",
        base / ".mcp.json",
    ]


def _parse(path: Path) -> dict[str, str]:
    """Return {server_name: url} for HTTP servers in a single config file."""
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}

    result: dict[str, str] = {}

    def _collect(servers: dict) -> None:
        for name, cfg in servers.items():
            if not isinstance(cfg, dict):
                continue
            url = cfg.get("url") or cfg.get("serverUrl")
            try:
                result[name] = urlparse(url).netloc if url else name
            except Exception:
                result[name] = name

    # ~/.claude.json: servers live under projects.<path>.mcpServers
    if "projects" in data:
        for project_data in data["projects"].values():
            if isinstance(project_data, dict) and "mcpServers" in project_data:
                _collect(project_data["mcpServers"])

    # .mcp.json / fallback: top-level mcpServers
    if "mcpServers" in data:
        _collect(data["mcpServers"])

    return result


def _combined_mtime(paths: list[Path]) -> float:
    return sum(p.stat().st_mtime for p in paths if p.exists())


def build_index(project_dir: Path | None = None) -> dict[str, str]:
    """Return {server_name: url} for all Claude HTTP MCP servers."""
    paths = _source_paths(project_dir)
    mtime = _combined_mtime(paths)

    if _CACHE_FILE.exists():
        try:
            cached = json.loads(_CACHE_FILE.read_text(encoding="utf-8"))
            if cached.get("mtime") == mtime:
                return cached["index"]
        except (json.JSONDecodeError, OSError, KeyError):
            pass

    index: dict[str, str] = {}
    for p in paths:
        index.update(_parse(p))

    try:
        _CACHE_FILE.write_text(json.dumps({"mtime": mtime, "index": index}), encoding="utf-8")
    except OSError:
        pass

    return index


def resolve(server_name: str, project_dir: Path | None = None) -> str:
    """Resolve server name to domain (HTTP) or name (stdio). Always returns a string."""
    try:
        return build_index(project_dir).get(server_name) or server_name
    except Exception:
        return server_name


def resolve_domain(server_name: str, project_dir: Path | None = None) -> str | None:
    """Resolve server name to domain/host only. Returns None if not found."""
    url = resolve(server_name, project_dir)
    return urlparse(url).netloc if url else None
