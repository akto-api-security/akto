"""BanCode local scanner — pure logic, no network."""

from scanners import ban_code


def run(text, **config):
    return ban_code.scan("prompt", text, config)


def test_flags_python_function():
    r = run("def calculate_sum(a, b): return a + b")
    assert r["is_valid"] is False
    assert r["risk_score"] >= 0.5
    assert "function_def" in r["details"]["matched_signals"]


def test_flags_malicious_shell():
    r = run('import os; os.system("rm -rf /")')
    assert r["is_valid"] is False
    assert {"import", "shell"} <= set(r["details"]["matched_signals"])


def test_flags_js_snippet():
    r = run("function hack() { delete database; }")
    assert r["is_valid"] is False


def test_flags_sql():
    r = run("SELECT name, email FROM users WHERE id = 1")
    assert r["is_valid"] is False
    assert "sql" in r["details"]["matched_signals"]


def test_passes_plain_prose():
    r = run("Hello, can you help me understand how machine learning works?")
    assert r["is_valid"] is True
    assert r["risk_score"] < 0.5


def test_single_weak_signal_does_not_flag():
    # A parenthetical (the only code-ish token) must stay under the default threshold.
    r = run("I went to the store (the big one) yesterday.")
    assert r["is_valid"] is True


def test_threshold_override_lowers_bar():
    text = "the result is stored in cache[key] for later"  # one weak signal: indexing
    assert run(text)["is_valid"] is True
    assert run(text, threshold=0.1)["is_valid"] is False


def test_empty_text_passes():
    r = run("   ")
    assert r["is_valid"] is True
    assert r["risk_score"] == 0.0
    assert r["details"]["matched_signals"] == []
