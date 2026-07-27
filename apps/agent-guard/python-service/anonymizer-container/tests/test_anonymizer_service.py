"""Payloads reach us inner-JSON-escaped; escapes must not become false-positive PII."""

import pytest

from anonymizer_service import AnonymizeRequest, _analysis_view, anonymize


class _FakeRequest:
    client = None


def _sanitize(text: str) -> str:
    return anonymize(AnonymizeRequest(text=text), _FakeRequest()).sanitized_text


# Offsets from the analysis view are applied to the original, so it must not resize.
@pytest.mark.parametrize("text", [
    r"a\nb", r"\n\n1. x", r"tabs\there", "no escapes at all", r"\r\n\f\b\v", "",
])
def test_analysis_view_preserves_length(text):
    assert len(_analysis_view(text)) == len(text)


# The bug: US_DRIVER_LICENSE matched the "n1" in "\n\n1." and ate the list markers.
def test_escaped_list_markers_survive():
    out = _sanitize(r'{"body": "see `demo@kaot.io`:\n\n1. first\n\n2. second"}')
    assert r"\n\n1. first" in out
    assert r"\n\n2. second" in out
    assert "demo@kaot.io" not in out


# spaCy read "make\n3" as a PERSON before the escapes were blanked for analysis.
def test_prose_around_escapes_survives():
    text = r'{"body": "Steps:\n\n1. clone repo\n2. run make\n3. deploy\n\n4. tag it"}'
    assert _sanitize(text) == text


# A value glued to a preceding escape used to be detected only partially, if at all:
# "card:\n4111 1111 1111 1111" matched "n4111" and left the rest of the PAN in the clear.
@pytest.mark.parametrize("body,secret", [
    (r'{"body":"contact:\njane@corp.com is the owner"}', "jane@corp.com"),
    (r'{"body":"card:\n4111 1111 1111 1111"}', "4111 1111 1111 1111"),
    (r'{"body":"ssn:\n457-55-5462"}', "457-55-5462"),
    (r'{"body":"ip:\n192.168.1.10"}', "192.168.1.10"),
])
def test_pii_after_an_escape_is_fully_redacted(body, secret):
    assert secret not in _sanitize(body)


# Note: Presidio rejects known-fake SSNs (123-45-6789), so use a valid-format one.
def test_real_pii_still_redacted():
    out = _sanitize(
        r'{"body": "email jane.doe@example.com, phone +1-415-555-2671,\n'
        r'card 4111 1111 1111 1111, driver license C1234567, SSN 457-55-5462,\n'
        r'IBAN GB33BUKB20201555555555, IP 192.168.1.10, name Michael Thompson"}'
    )
    for secret in ("jane.doe@example.com", "+1-415-555-2671", "4111 1111 1111 1111",
                   "C1234567", "457-55-5462", "GB33BUKB20201555555555", "192.168.1.10",
                   "Michael Thompson"):
        assert secret not in out, secret
