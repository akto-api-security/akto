"""Regression tests for SCORE_THRESHOLD in anonymizer_service.py."""

from anonymizer_service import AnonymizeRequest, anonymize


class _FakeRequest:
    client = None


def _sanitize(text: str) -> str:
    return anonymize(AnonymizeRequest(text=text), _FakeRequest()).sanitized_text


# The reported bug: weak scores on "\n\n1."/"\n\n2." ate the list markers.
def test_escaped_list_markers_survive_the_reported_wording():
    out = _sanitize(
        r'{"body": "I notice you have shared an email address (`demo@kaot.io`), but I want '
        r'to clarify before updating anything:\n\n1. Is this intentional, or did you mean '
        r'`demo@akto.io` (which matches your existing contact info in memory)?\n\n2. Are you '
        r'asking me to update your contact information in memory, or are you sharing this '
        r'for a different purpose?"}'
    )
    assert r"\n\n1. Is this intentional" in out
    assert r"\n\n2. Are you asking" in out
    assert "demo@kaot.io" not in out
    assert "demo@akto.io" not in out


# Email glued to an escape still gets caught.
def test_email_after_escape_is_redacted():
    out = _sanitize(r'{"body":"contact:\njane@corp.com is the owner"}')
    assert "jane@corp.com" not in out


# Note: Presidio rejects known-fake SSNs (123-45-6789), so use a valid-format one.
def test_real_pii_still_redacted():
    out = _sanitize(
        r'{"body": "email jane.doe@example.com, phone +1-415-555-2671, '
        r'card 4111 1111 1111 1111, driver license C1234567, SSN 457-55-5462, '
        r'IBAN GB33BUKB20201555555555, IP 192.168.1.10, name Michael Thompson"}'
    )
    for secret in ("jane.doe@example.com", "+1-415-555-2671", "4111 1111 1111 1111",
                   "C1234567", "457-55-5462", "GB33BUKB20201555555555", "192.168.1.10",
                   "Michael Thompson"):
        assert secret not in out, secret
