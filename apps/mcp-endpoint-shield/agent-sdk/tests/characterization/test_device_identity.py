"""Characterization: pin the CURRENT behavior of the chosen canonical device identity.

Canonical = github-cli-hooks/akto_machine_id.py (249 lines, the most complete: mirrors
the Go implementation, handles Windows/macOS/Linux + root/console-user detection).
The other 8 copies are drift — see tests/DRIFT_REPORT.md. Phase 2 collapses them onto
this behavior in engine/device_identity.py.

Only deterministic, env/hostname-driven branches are pinned (subprocess-based
hardware id lookup is machine-specific and intentionally not asserted).
"""
import akto_machine_id as mid


def test_username_prefers_sudo_user(monkeypatch):
    monkeypatch.setattr(mid, "_username", None)          # reset module cache
    monkeypatch.setattr(mid.platform, "system", lambda: "Linux")
    monkeypatch.setenv("SUDO_USER", "alice")
    assert mid.get_username() == "alice"


def test_username_caches_after_first_call(monkeypatch):
    monkeypatch.setattr(mid, "_username", None)
    monkeypatch.setattr(mid.platform, "system", lambda: "Linux")
    monkeypatch.setenv("SUDO_USER", "bob")
    first = mid.get_username()
    monkeypatch.setenv("SUDO_USER", "carol")             # changing env must not matter
    assert mid.get_username() == first == "bob"


def test_device_name_normalized_lowercase_dashes(monkeypatch):
    # hostname → strip .local → non-alphanumerics to '-' → lowercase.
    monkeypatch.setattr(mid, "_machine_id", None)
    monkeypatch.setattr(mid.platform, "system", lambda: "Linux")
    monkeypatch.setattr(mid.socket, "gethostname", lambda: "Test.Box.local")
    assert mid.get_machine_id() == "test-box"
