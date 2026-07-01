"""intent.segmenter — instruction/data extraction cascade, multi-action
splitting, and per-agent structure-profile lexicon learning."""

import json

from intent import segmenter


# --------------------------------------------------------------------------- #
# segment() — tier 1 (structured)
# --------------------------------------------------------------------------- #
def test_instruction_and_data_keys_split_structurally():
    body = json.dumps({
        "instruction": "summarize this",
        "document": "Flight delays surged across Delhi and Bombay this week due to weather.",
    })
    out = segmenter.segment(body)
    assert out["units"] == ["summarize this"]
    assert out["data_spans"] == ["Flight delays surged across Delhi and Bombay this week due to weather."]
    assert out["method"] == "structured"
    assert out["extraction_confidence"] == 1.0


def test_chat_roles_split_user_as_instruction_others_as_data():
    body = json.dumps({"messages": [
        {"role": "system", "content": "You are a travel agent."},
        {"role": "user", "content": "book a flight from delhi to bombay"},
        {"role": "assistant", "content": "Sure, here are some options."},
        {"role": "tool", "content": "{\"flights\": [1, 2, 3]}", "content_type": "application/json"},
    ]})
    out = segmenter.segment(body)
    assert out["units"] == ["book a flight from delhi to bombay"]
    assert "Sure, here are some options." in out["data_spans"]
    assert '{"flights": [1, 2, 3]}' in out["data_spans"]
    assert out["method"] == "structured"


def test_unit_sources_and_methods_are_parallel_to_units():
    body = json.dumps({"instruction": "delete the record", "document": "unrelated data"})
    out = segmenter.segment(body)
    assert out["unit_sources"] == ["instruction"]
    assert out["unit_methods"] == ["structured"]


# --------------------------------------------------------------------------- #
# segment() — structure_profile: per-agent learned key/verb lexicon
# --------------------------------------------------------------------------- #
def test_structure_profile_promotes_a_learned_key_to_tier_1():
    # "reconcile" isn't in the generic imperative lexicon, so without a
    # learned key hint this text can't even reach tier-4's heuristic bucket.
    body = json.dumps({"userAsk": "reconcile the ledger", "payload": "unrelated data blob"})
    generic = segmenter.segment(body)
    assert generic["units"] == []

    learned = segmenter.segment(body, structure_profile={
        "instruction_keys": ["userask"], "data_keys": ["payload"],
    })
    assert learned["units"] == ["reconcile the ledger"]
    assert learned["unit_sources"] == ["userask"]
    assert learned["unit_methods"] == ["structured"]
    assert learned["method"] == "structured"
    assert learned["extraction_confidence"] == 1.0


def test_structure_profile_learned_verb_enables_multi_action_split():
    # "reconcile" isn't in the generic imperative lexicon.
    text = "reconcile the ledger and email me a summary"
    assert segmenter.split_instruction_units(text) == [text]  # no split: unknown verb on one side
    assert segmenter.split_instruction_units(text, extra_verbs=frozenset({"reconcile"})) == [
        "reconcile the ledger", "email me a summary",
    ]


def test_missing_structure_profile_behaves_exactly_like_before():
    body = json.dumps({"instruction": "delete the record"})
    assert segmenter.segment(body) == segmenter.segment(body, structure_profile=None)


# --------------------------------------------------------------------------- #
# segment() — tier 3 (deterministic structural markers) + tier 4 (heuristic)
# --------------------------------------------------------------------------- #
def test_fenced_code_block_removed_leaving_instruction_via_heuristic():
    body = "Can you review this function? ```def foo():\n    return 1```"
    out = segmenter.segment(body)
    assert any("def foo" in s for s in out["data_spans"])
    assert any("review this function" in u for u in out["units"])
    assert out["method"] == "heuristic"  # tier 4 fired for the residual sentence
    assert out["extraction_confidence"] < 1.0


def test_log_lines_removed_as_data():
    body = "please check the errors below\n2024-01-01T00:00:00 ERROR something broke\n2024-01-01T00:00:05 ERROR broke again"
    out = segmenter.segment(body)
    assert any("ERROR" in s for s in out["data_spans"])
    assert not any("ERROR" in u for u in out["units"])


# --------------------------------------------------------------------------- #
# Core regression: the bug this whole redesign fixes — a short instruction
# should never be swamped by a topically-unrelated large data blob.
# --------------------------------------------------------------------------- #
def test_instruction_isolated_from_dominant_topic_of_attached_data():
    body = json.dumps({
        "instruction": "please summarize the key points",
        "data": " ".join(["flight delays and hotel cancellations in Bombay"] * 50),
    })
    out = segmenter.segment(body)
    assert out["units"] == ["please summarize the key points"]
    assert all("flight" not in u for u in out["units"])


# --------------------------------------------------------------------------- #
# split_instruction_units — multi-action vs single-action-two-object
# --------------------------------------------------------------------------- #
def test_splits_genuine_two_action_instruction():
    units = segmenter.split_instruction_units("delete these records and email me a summary")
    assert units == ["delete these records", "email me a summary"]


def test_does_not_split_single_action_two_object_instruction():
    units = segmenter.split_instruction_units("update the record and its timestamp")
    assert units == ["update the record and its timestamp"]


def test_empty_text_returns_no_units():
    assert segmenter.split_instruction_units("") == []
    assert segmenter.split_instruction_units("   ") == []


# --------------------------------------------------------------------------- #
# Fail-open
# --------------------------------------------------------------------------- #
def test_segment_never_raises_on_garbage_input():
    out = segmenter.segment(None)
    assert out["units"] == []
    assert out["data_spans"] == []


def test_empty_text_segments_to_nothing():
    out = segmenter.segment("")
    assert out["units"] == []
    assert out["data_spans"] == []
