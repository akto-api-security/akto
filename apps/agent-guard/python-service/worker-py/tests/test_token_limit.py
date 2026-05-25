"""TokenLimit local scanner — exercises tiktoken + embedded cl100k_base vocab."""

from scanners import token_limit


def run(text, **config):
    return token_limit.scan("prompt", text, config)


def test_under_limit_passes():
    r = run("short text", limit=20)
    assert r["is_valid"] is True
    assert r["risk_score"] == 0.0
    assert r["details"]["token_count"] == 2  # "short", " text"


def test_over_limit_flags():
    text = (
        "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod "
        "tempor incididunt ut labore et dolore magna aliqua ut enim ad minim veniam"
    )
    r = run(text, limit=20)
    assert r["is_valid"] is False
    assert r["risk_score"] == 1.0
    assert r["details"]["token_count"] > 20


def test_no_limit_always_valid():
    r = run("anything at all goes here", limit=None)
    assert r["is_valid"] is True
    assert r["details"]["token_count"] > 0


def test_encoding_roundtrips():
    # A correctly-built BPE encoding must decode back to the original text —
    # this proves the embedded vocab was parsed correctly.
    enc = token_limit._get_encoding()
    for text in ["tiktoken is great!", "Hello, world 123", "def f(x): return x*2"]:
        assert enc.decode(enc.encode(text)) == text


def test_count_is_deterministic():
    a = run("the quick brown fox", limit=1000)["details"]["token_count"]
    b = run("the quick brown fox", limit=1000)["details"]["token_count"]
    assert a == b and a > 0


def test_special_token_text_counts_literally():
    # disallowed_special=() means "<|endoftext|>" is counted as text, not raised.
    r = run("<|endoftext|>", limit=1000)
    assert r["details"]["token_count"] > 0
