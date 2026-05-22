"""Secrets local scanner — exercises detect-secrets (real library)."""

from scanners import secrets


def run(text):
    return secrets.scan("prompt", text, {})


def test_detects_aws_key():
    r = run("export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
    assert r["is_valid"] is False
    assert "AWS Access Key" in r["details"]["types"]


def test_clean_short_text_passes():
    r = run("Can you help me write a function to sort a list?")
    assert r["is_valid"] is True
    assert r["details"]["types"] == []


def test_clean_long_prose_no_false_positives():
    text = "\n".join(["This is a totally benign line about deployment runbooks."] * 40)
    r = run(text)
    assert r["is_valid"] is True, f"unexpected hits: {r['details']['types']}"


def test_secret_buried_in_long_multiline_prompt():
    lines = ["Please review this deployment runbook and summarize the steps."] * 24
    lines.append("export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
    lines += ["Then configure the load balancer and autoscaling group."] * 14
    r = run("\n".join(lines))
    assert r["is_valid"] is False
    assert r["details"]["types"] == ["AWS Access Key"]
    # exactly one hit, on the line where the key lives
    assert [s["line"] for s in r["details"]["secrets"]] == [25]


def test_detects_pem_private_key():
    text = (
        "here is my key\n"
        "-----BEGIN RSA PRIVATE KEY-----\n"
        "MIIEowIBAAKCAQEA1234567890abcdef\n"
        "-----END RSA PRIVATE KEY-----"
    )
    r = run(text)
    assert r["is_valid"] is False
    assert "Private Key" in r["details"]["types"]


def test_secret_value_redacted_from_output():
    r = run("export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE")
    assert "AKIAIOSFODNN7EXAMPLE" not in r["sanitized_text"]
    assert secrets.REDACT_MASK in r["sanitized_text"]


def test_empty_text_passes():
    r = run("   ")
    assert r["is_valid"] is True
    assert r["details"]["types"] == []
