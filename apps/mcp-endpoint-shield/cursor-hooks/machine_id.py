#!/usr/bin/env python3
"""
Machine ID generation utility for device identification.
Mimics the Go implementation for generating unique device identifiers.
"""
import uuid
import socket


_machine_id = None


def _generate_machine_id() -> str:
    """
    Generate a unique machine ID using multiple fallback methods.

    Priority:
    1. UUID-based node ID (MAC address)
    2. First non-empty network interface MAC address

    Returns:
        Machine ID as a lowercase string without dashes
    """
    # Try uuid.getnode() - returns MAC address as integer
    try:
        node_id = uuid.getnode()
        if node_id != 0:
            # Convert to MAC address format
            mac = ':'.join(['{:02x}'.format((node_id >> i) & 0xff)
                           for i in range(0, 48, 8)][::-1])
            return mac.replace('-', '').replace(':', '')
    except Exception:
        pass

    # Fallback: Try to get MAC from network interfaces
    try:
        # Get hostname and associated network info
        hostname = socket.gethostname()
        # This is a best-effort fallback
        # In practice, uuid.getnode() should work on most systems
        node_id = uuid.getnode()
        if node_id != 0:
            mac = ':'.join(['{:02x}'.format((node_id >> i) & 0xff)
                           for i in range(0, 48, 8)][::-1])
            return mac.replace('-', '').replace(':', '')
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
        _machine_id = _generate_machine_id().lower()
    return _machine_id
