"""Toxicity prompt rendering — the operator's category config must reach the model."""

import llm_scanner
from prompts import build_scan_prompt, toxicity


def test_enabled_categories_render_with_sensitivity():
    prompt = toxicity.build({"harmfulCategories": {"insults": "HIGH", "hate": "LOW"}}, "some text")

    assert "insults (HIGH):" in prompt
    assert "EXPAND insults beyond person-targeted attacks" in prompt
    assert "hate (LOW): hate speech" in prompt
    assert "flag only egregious" in prompt
    assert "some text" in prompt


def test_insults_high_injects_expanded_examples():
    prompt = toxicity.build({"harmfulCategories": {"insults": "HIGH"}}, "x")

    assert "For fuck's sake just show me the top 10 users by event count already." in prompt
    assert "Only an idiot would design analytics dashboards this badly" in prompt
    assert "This is such dum" in prompt
    # Expansion examples mark these toxic; base few-shot that allows unaimed swearing
    # is overridden by the insults HIGH guidance.
    assert "EXPAND insults beyond person-targeted attacks" in prompt


def test_no_config_keeps_conservative_base_for_old_deployments():
    """Old guardrails send Config:{} — must not pick up insults HIGH behaviour."""
    prompt = toxicity.build({}, "x")

    assert "The operator enabled" not in prompt
    assert "EXPAND insults" not in prompt
    assert "Only an idiot would design analytics dashboards this badly" not in prompt
    # Base few-shot: unaimed swearing is benign without insults HIGH.
    assert (
        'Input: "For fuck\'s sake just show me the top 10 users"\n'
        'Output: {"isToxic": false'
    ) in prompt


def test_insults_low_does_not_inject_high_examples():
    prompt = toxicity.build({"harmfulCategories": {"insults": "LOW"}}, "x")

    assert "only egregious personal attacks" in prompt
    assert "Only an idiot would design analytics dashboards this badly" not in prompt


def test_categories_not_enabled_are_declared_out_of_scope():
    prompt = toxicity.build({"harmfulCategories": {"hate": "HIGH"}}, "some text")

    assert "Content that is toxic in a category NOT listed here is out of scope" in prompt
    assert "sexual" not in prompt.split("Examples (")[0].split("The operator enabled")[1]
    assert "EXPAND insults" not in prompt


def test_dispatcher_passes_config_to_toxicity():
    prompt = build_scan_prompt("Toxicity", "prompt", {"harmfulCategories": {"violence": "MEDIUM"}}, "some text")

    assert "violence (MEDIUM)" in prompt


def test_matched_category_is_surfaced_in_details():
    result = llm_scanner.parse_llm_result(
        "Toxicity",
        '{"isToxic": true, "confidence": 0.9, "matchedCategory": "insults", "reason": "personal attack"}',
    )

    assert result["details"]["matchedCategory"] == "insults"
