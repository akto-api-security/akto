"""Password's compact answer contract, and the prompt-size work behind it.

Password is the one cascade scanner that can never answer in letters: its
verdict must carry the exact secret substrings the gateway masks
(mcp/pii_password_llm.go reads details["values"]). Its compact contract keeps
those and drops everything derivable or redundant — isPassword, riskScore, and
the reason that the JSON contract required to quote every secret verbatim.
"""

import pytest

from constants import apply_scanner_response_format, force_gemma_only
from llm_scanner import _FORMAT_PARSERS, parse_values_result
from prompts import build_scan_prompt, known_formats, password, resolve_response_format
from settings import settings

# Hard prefill-latency step on the Gemma Foundry deployment (chars of final prompt).
_LATENCY_CLIFF = 9600


# ── Prompt size ──────────────────────────────────────────────────────────────


def test_json_prompt_carries_no_duplicated_rule_block():
    """The ASSIGNMENT TRAP paragraph and WORKED EXAMPLES block were each present
    twice, back to back — ~1k chars of prefill on every Password scan, with the
    second copy a strict subset of the first."""
    assert password.GEMMA.count("ASSIGNMENT TRAP - the most common mistake") == 1
    assert password.GEMMA.count("WORKED EXAMPLES (snippet -> verdict):") == 1


def test_values_prompt_fits_under_the_latency_cliff():
    """The JSON Password prompt is the only one that exceeds the step; the point
    of the compact contract is to get under it."""
    prompt = build_scan_prompt("Password", "prompt", {}, "pwd=hunter2", response_format="values")
    assert len(prompt) < _LATENCY_CLIFF, f"{len(prompt)} chars"


def test_values_prompt_is_smaller_than_the_json_one():
    text = "pwd=hunter2"
    compact = build_scan_prompt("Password", "prompt", {}, text, response_format="values")
    full = build_scan_prompt("Password", "prompt", {}, text)
    assert len(compact) < len(full)


def test_values_prompt_names_no_dropped_field():
    """A prompt answering only with values must not discuss fields that are no
    longer in its contract — including in the worked examples."""
    assert "isPassword" not in password.GEMMA_VALUES
    assert "riskScore" not in password.GEMMA_VALUES
    # "reason" survives once as a rule ABOUT the payload (a credential buried in
    # a JSON "reason" field must still be caught), which is not a contract field.
    # Check the answer contract itself asks for nothing but values.
    contract = password.GEMMA_VALUES[password.GEMMA_VALUES.index("Respond with ONLY") :]
    assert '"reason"' not in contract
    assert '"isPassword"' not in contract
    # The contract asks for values and explicitly rules the rest out.
    assert "no reason, no other fields" in contract
    assert '{"values"' in contract


def test_values_prompt_keeps_the_detection_rules():
    """Only the answer contract changes; the hard-won rules are shared text."""
    for rule in (
        "DECISIVE RULE",
        "ASSIGNMENT TRAP",
        "WEAK PASSWORDS ARE STILL PASSWORDS",
        "RANDOM_GEN_PASSWORD_TOKEN",
        "AKIA5XYZ12ABCD34EFGH",
        "mandateReminderEnabled",
    ):
        assert rule in password.GEMMA_VALUES, rule


def test_payload_still_interpolates():
    prompt = build_scan_prompt("Password", "prompt", {}, "export DB_PASS=Hunter2024#", response_format="values")
    assert "export DB_PASS=Hunter2024#" in prompt


# ── Verdict parsing ──────────────────────────────────────────────────────────


def test_values_present_flags_and_carries_them_for_redaction():
    r = parse_values_result("Password", '{"values": ["Hunter2024#", "sk-Abc9XyZ0qP"]}')
    assert r["is_valid"] is False
    assert r["details"]["values"] == ["Hunter2024#", "sk-Abc9XyZ0qP"]
    assert r["risk_score"] >= 0.9


def test_empty_values_is_a_clean_allow():
    r = parse_values_result("Password", '{"values": []}')
    assert r["is_valid"] is True
    assert r["risk_score"] <= 0.09
    assert "values" not in r["details"]


def test_reason_never_quotes_the_secret():
    """The JSON contract required the reason to quote every value verbatim,
    which put raw credentials in the threat report."""
    secret = "Hunter2024#"
    r = parse_values_result("Password", f'{{"values": ["{secret}"]}}')
    assert secret not in r["details"]["reason"]
    assert "1 real secret value(s)" in r["details"]["reason"]


def test_non_string_and_empty_entries_are_dropped():
    r = parse_values_result("Password", '{"values": ["ok", "", null, 7]}')
    assert r["details"]["values"] == ["ok"]


@pytest.mark.parametrize("raw", ["{}", '{"values": "Hunter2024#"}', '{"isPassword": true}', "not json", ""])
def test_unusable_response_raises_rather_than_allowing(raw):
    """A raise counts as unsafe in the cascade; a silent {} would read as a
    clean allow and mask nothing."""
    with pytest.raises(ValueError):
        parse_values_result("Password", raw)


# ── Format selection ─────────────────────────────────────────────────────────


def test_password_supports_values_and_never_letters():
    assert resolve_response_format("Password", "prompt", "values") == "values"
    assert resolve_response_format("Password", "prompt", "abcd") == ""


def test_values_is_password_only():
    for scanner in ("PromptInjection", "Toxicity", "BanTopics", "Gibberish", "BanCode"):
        assert resolve_response_format(scanner, "prompt", "values") == ""


def test_one_setting_can_ask_every_scanner_for_its_own_contract():
    assert resolve_response_format("PromptInjection", "prompt", "abcd,values") == "abcd"
    assert resolve_response_format("Password", "prompt", "abcd,values") == "values"


def test_output_side_stays_on_json():
    assert resolve_response_format("Password", "output", "values") == ""


def test_every_known_format_has_a_parser():
    """A format with a template but no parser would be rendered and misread."""
    assert known_formats() <= set(_FORMAT_PARSERS)


# ── Reaching Password through the cascade ────────────────────────────────────


def test_values_reaches_password_despite_it_being_arbiter_only(monkeypatch):
    """force_gemma_only strips the fast tiers, so a fast-tiers-only override
    would never apply to Password at all."""
    monkeypatch.setattr(settings, "SCANNER_RESPONSE_FORMAT", "values")
    configs = apply_scanner_response_format(force_gemma_only(None))
    assert [e["modelRole"] for e in configs] == ["FINAL_ARBITER"]
    assert configs[0]["responseFormat"] == "values"


def test_abcd_never_reaches_an_arbiter(monkeypatch):
    """The letter contract has no reason string, and the arbiter's verdict is
    the one reported — so it stays off the arbiter even deployment-wide."""
    monkeypatch.setattr(settings, "SCANNER_RESPONSE_FORMAT", "abcd")
    configs = apply_scanner_response_format(force_gemma_only(None))
    assert configs[0].get("responseFormat", "") == ""


def test_combined_setting_splits_by_role(monkeypatch):
    monkeypatch.setattr(settings, "SCANNER_RESPONSE_FORMAT", "abcd,values")
    configs = apply_scanner_response_format(
        [
            {"provider": "gemma_foundry", "modelRole": "FAST_THREAT_FILTER"},
            {"provider": "gemma_vertexai", "modelRole": "FINAL_ARBITER"},
        ]
    )
    formats = {e["modelRole"]: e["responseFormat"] for e in configs}
    # Fast tiers get both and pick per scanner; the arbiter gets only the
    # arbiter-safe one, so a letter can never decide a reported verdict.
    assert formats == {"FAST_THREAT_FILTER": "abcd,values", "FINAL_ARBITER": "values"}


def test_kill_switch_clears_every_role(monkeypatch):
    monkeypatch.setattr(settings, "SCANNER_RESPONSE_FORMAT", "json")
    configs = apply_scanner_response_format(
        [
            {"provider": "gemma_foundry", "modelRole": "FAST_THREAT_FILTER", "responseFormat": "abcd"},
            {"provider": "gemma_vertexai", "modelRole": "FINAL_ARBITER", "responseFormat": "values"},
        ]
    )
    assert all(e["responseFormat"] == "" for e in configs)
