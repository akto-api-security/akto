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


def test_detects_github_token():
    # detect-secrets' GitHubTokenDetector requires the real format: prefix + 36 chars.
    token = "ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
    r = run(f"my github token is {token}")
    assert r["is_valid"] is False
    assert "GitHub Token" in r["details"]["types"]
    # whole token must be masked, not just the "ghp" prefix group
    assert token not in r["sanitized_text"]
    assert "ghp_" not in r["sanitized_text"]
    assert r["details"]["types"]  # sanity
    assert "A1b2C3d4" not in r["sanitized_text"]


def test_malformed_short_github_token_not_flagged():
    # Too short to be a valid GitHub token (12 chars after prefix, not 36) and
    # too low-entropy for the entropy detectors -> not flagged. Documents the
    # boundary so the behaviour is intentional, not a silent miss.
    r = run("a github token ghp_abc123xyz456")
    assert r["is_valid"] is True


def test_detects_openai_key():
    r = run("OPENAI_API_KEY=sk-" + "A1b2C3d4E5f6G7h8I9j0T3BlbkFJ" + "a1B2c3D4e5F6g7H8i9J0k1L2")
    assert r["is_valid"] is False


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
