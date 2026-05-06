#!/usr/bin/env python3
"""Helpers for Akto Claude CLI argus hooks."""
import socket

_device_ip = None


def get_device_ip() -> str:
    """
    Local LAN IP of the primary outbound interface.
    UDP-socket trick: connect to 8.8.8.8 (no packet sent) and read getsockname().
    Returns "" on failure (e.g. no network).
    """
    global _device_ip
    if _device_ip is not None:
        return _device_ip
    s = None
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(1)
        s.connect(("8.8.8.8", 80))
        _device_ip = s.getsockname()[0]
    except Exception:
        _device_ip = ""
    finally:
        if s is not None:
            try:
                s.close()
            except Exception:
                pass
    return _device_ip
