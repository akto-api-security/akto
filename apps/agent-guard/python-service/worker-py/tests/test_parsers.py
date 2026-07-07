"""Result-parsing for the cascade scanners — pure functions, no network."""

import math

import llm_scanner
from providers import _confidence_from_logprobs, parse_qwen3guard_result


# ── llm_scanner._clean_json ──────────────────────────────────────────────────


def test_clean_json_extracts_object_from_chatty_reply():
    raw = 'Sure! Here you go:\n{"isInjection": true, "confidence": 0.9}\nHope that helps.'
    assert llm_scanner._clean_json(raw) == '{"isInjection": true, "confidence": 0.9}'


def test_clean_json_empty_raises():
    import pytest
    with pytest.raises(ValueError):
        llm_scanner._clean_json("")


# ── llm_scanner.parse_llm_result ─────────────────────────────────────────────


def test_parse_prompt_injection_flagged():
    r = llm_scanner.parse_llm_result(
        "PromptInjection",
        '{"isInjection": true, "confidence": 0.92, "reason": "override attempt"}',
    )
    assert r["is_valid"] is False
    assert r["risk_score"] == 0.92
    assert r["decision_confidence"] == 0.92
    assert r["details"]["reason"] == "override attempt"


def test_parse_prompt_injection_clean_inverts_confidence():
    r = llm_scanner.parse_llm_result(
        "PromptInjection", '{"isInjection": false, "confidence": 0.1}'
    )
    assert r["is_valid"] is True
    # not flagged -> decision_confidence is confidence-in-the-SAFE-call
    assert r["decision_confidence"] == 0.9


def test_parse_ban_topics_surfaces_matched_topic():
    r = llm_scanner.parse_llm_result(
        "BanTopics",
        '{"isBanned": true, "confidence": 0.8, "matchedTopic": "weapons"}',
    )
    assert r["is_valid"] is False
    assert r["details"]["matchedTopic"] == "weapons"


def test_parse_toxicity_without_config_trusts_model_boolean():
    # No threshold in config -> falls back to the model's own isToxic verdict.
    r = llm_scanner.parse_llm_result(
        "Toxicity", '{"isToxic": false, "confidence": 0.6, "reason": "borderline"}'
    )
    assert r["is_valid"] is True


def test_parse_toxicity_low_threshold_flags_on_weak_signal():
    # High sensitivity (0.9) -> threshold 0.1: even a "not toxic" call with some
    # signal gets blocked, because the category was configured to catch more.
    r = llm_scanner.parse_llm_result(
        "Toxicity",
        '{"isToxic": false, "confidence": 0.6, "reason": "mild insult"}',
        {"threshold": 0.1, "category": "insults"},
    )
    assert r["is_valid"] is False
    assert r["details"]["category"] == "insults"


def test_parse_toxicity_high_threshold_requires_strong_signal():
    # Low sensitivity -> threshold 0.9: even a flagged call needs high confidence.
    r = llm_scanner.parse_llm_result(
        "Toxicity",
        '{"isToxic": true, "confidence": 0.6, "reason": "ambiguous"}',
        {"threshold": 0.9, "category": "insults"},
    )
    assert r["is_valid"] is True


def test_parse_gibberish_without_config_trusts_model_boolean():
    r = llm_scanner.parse_llm_result(
        "Gibberish", '{"isGibberish": false, "confidence": 0.3}'
    )
    assert r["is_valid"] is True


def test_parse_gibberish_strict_threshold_flags_on_weak_signal():
    # Dashboard: "lower values are stricter" -> a low threshold blocks on weak signal.
    r = llm_scanner.parse_llm_result(
        "Gibberish", '{"isGibberish": false, "confidence": 0.08}', {"threshold": 0.05}
    )
    assert r["is_valid"] is False


def test_parse_gibberish_permissive_threshold_requires_strong_signal():
    # Dashboard: "higher values are more permissive" -> needs high confidence to block.
    r = llm_scanner.parse_llm_result(
        "Gibberish", '{"isGibberish": true, "confidence": 0.6}', {"threshold": 0.9}
    )
    assert r["is_valid"] is True


def test_parse_prompt_injection_ignores_threshold_config():
    # Scope check: threshold override is Toxicity/Gibberish/BanCode only.
    # PromptInjection keeps trusting its own boolean even with a threshold in config.
    r = llm_scanner.parse_llm_result(
        "PromptInjection",
        '{"isInjection": false, "confidence": 0.6}',
        {"threshold": 0.05},
    )
    assert r["is_valid"] is True


def test_parse_bancode_strict_threshold_flags_on_weak_signal():
    # Backs both "Ban code detection" and "Code Detection Level" sliders.
    r = llm_scanner.parse_llm_result(
        "BanCode", '{"isCode": false, "confidence": 0.08}', {"threshold": 0.05}
    )
    assert r["is_valid"] is False


def test_parse_bancode_permissive_threshold_requires_strong_signal():
    r = llm_scanner.parse_llm_result(
        "BanCode", '{"isCode": true, "confidence": 0.6}', {"threshold": 0.9}
    )
    assert r["is_valid"] is True


# ── providers.parse_qwen3guard_result ────────────────────────────────────────


def test_qwen_safe():
    r = parse_qwen3guard_result("Toxicity", "Safety: Safe\nCategories: None")
    assert r["is_valid"] is True
    assert r["risk_score"] == 0.0


def test_qwen_unsafe():
    r = parse_qwen3guard_result("Toxicity", "Safety: Unsafe\nCategories: Violent")
    assert r["is_valid"] is False
    assert r["risk_score"] == 1.0
    assert r["details"]["categories"] == "Violent"


def test_qwen_controversial_is_mid_risk():
    r = parse_qwen3guard_result("Toxicity", "Safety: Controversial")
    assert r["is_valid"] is False
    assert r["risk_score"] == 0.5


def test_qwen_ban_topics_sets_matched_topic():
    r = parse_qwen3guard_result("BanTopics", "Safety: Unsafe\nCategories: Weapons")
    assert r["details"]["matchedTopic"] == "Weapons"


def test_qwen_unknown_safety_raises():
    import pytest
    with pytest.raises(ValueError):
        parse_qwen3guard_result("Toxicity", "Safety: Maybe")


# ── providers._confidence_from_logprobs ──────────────────────────────────────


def _lp(token, p):
    return {"token": token, "logprob": math.log(p)}


def test_confidence_from_logprobs_builds_distribution():
    # tokens stream: "Safety:" then the label " Safe" carrying top_logprobs
    content_lp = [
        {"token": "Safety:", "logprob": math.log(0.99)},
        {
            "token": " Safe",
            "logprob": math.log(0.8),
            "top_logprobs": [_lp("Safe", 0.8), _lp("Unsafe", 0.15), _lp("Controversial", 0.05)],
        },
    ]
    conf, dist, source = _confidence_from_logprobs(content_lp, "safe")
    assert source == "logprobs"
    assert abs(sum(dist.values()) - 1.0) < 1e-6
    assert dist["safe"] > dist["unsafe"] > dist["controversial"]
    assert conf == dist["safe"]


def test_confidence_from_logprobs_unavailable():
    conf, dist, source = _confidence_from_logprobs(None, "safe")
    assert conf is None and dist is None and source == "unavailable"
