"""BanSubstrings local scanner — pure logic, no network."""

from scanners import ban_substrings


def run(text, **config):
    return ban_substrings.scan("prompt", text, config)


def test_flags_when_substring_present():
    r = run("this is confidential", substrings=["confidential"])
    assert r["is_valid"] is False
    assert r["risk_score"] == 1.0
    assert r["details"]["matched"] == ["confidential"]


def test_passes_when_absent():
    r = run("hello world", substrings=["confidential"])
    assert r["is_valid"] is True
    assert r["risk_score"] == 0.0
    assert r["details"]["matched"] == []


def test_case_insensitive_default():
    r = run("This Is CONFIDENTIAL", substrings=["confidential"])
    assert r["is_valid"] is False


def test_case_sensitive_no_match():
    r = run("This Is CONFIDENTIAL", substrings=["confidential"], case_sensitive=True)
    assert r["is_valid"] is True


def test_word_match_respects_boundaries():
    # "cat" should not match inside "category" under word matching
    assert run("a category list", substrings=["cat"], match_type="word")["is_valid"] is True
    assert run("the cat sat", substrings=["cat"], match_type="word")["is_valid"] is False


def test_contains_all_true_requires_every_substring():
    cfg = {"substrings": ["alpha", "beta"], "contains_all": True}
    assert ban_substrings.scan("prompt", "alpha only", cfg)["is_valid"] is True
    assert ban_substrings.scan("prompt", "alpha and beta", cfg)["is_valid"] is False


def test_redact_replaces_match():
    r = run("my secret token here", substrings=["secret"], redact=True)
    assert "secret" not in r["sanitized_text"]
    assert ban_substrings.REDACTED in r["sanitized_text"]


def test_empty_substrings_passes():
    r = run("anything goes", substrings=[])
    assert r["is_valid"] is True
