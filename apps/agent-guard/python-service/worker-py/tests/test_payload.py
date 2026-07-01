"""intent.payload — JSON-stripping + sentence chunking."""

import json

from intent import payload


# --------------------------------------------------------------------------- #
# strip_to_nl
# --------------------------------------------------------------------------- #
def test_plain_string_taken_whole():
    out = payload.strip_to_nl("refund all the orders please")
    assert out == [("refund all the orders please", payload._WEIGHT_BARE)]


def test_empty_is_empty():
    assert payload.strip_to_nl("") == []
    assert payload.strip_to_nl("   ") == []


def test_prompt_key_kept_machine_fields_dropped():
    body = json.dumps({
        "prompt": "Investigate why checkout-service is failing",
        "model": "gpt-4o-mini",
        "request_id": "9f8c1e2a4b6d8f0a",
        "temperature": 0.2,
        "stream": True,
    })
    texts = [t for t, _ in payload.strip_to_nl(body)]
    assert "Investigate why checkout-service is failing" in texts
    assert all("gpt-4o-mini" not in t for t in texts)
    assert all("9f8c1e2a" not in t for t in texts)


def test_messages_roles_weighting():
    body = json.dumps({"messages": [
        {"role": "system", "content": "You are a devops helper."},
        {"role": "user", "content": "Summarize errors from the last deployment"},
        {"role": "assistant", "content": "Sure, here is a summary."},
        {"role": "tool", "content": "raw-tool-dump-should-drop"},
    ]})
    out = payload.strip_to_nl(body)
    by_text = {t: w for t, w in out}
    assert "Summarize errors from the last deployment" in by_text
    assert by_text["Summarize errors from the last deployment"] == payload._WEIGHT_NL_KEY
    # assistant kept but down-weighted, tool dropped
    assert any("summary" in t for t in by_text)
    assert all("raw-tool-dump" not in t for t in by_text)
    assert by_text["Sure, here is a summary."] < by_text["Summarize errors from the last deployment"]


def test_double_encoded_json():
    inner = json.dumps({"prompt": "delete the production database now"})
    body = json.dumps({"payload": inner})
    texts = [t for t, _ in payload.strip_to_nl(body)]
    assert any("delete the production database now" in t for t in texts)


def test_json_with_no_nl_falls_back_to_raw():
    body = json.dumps({"a": 1, "b": [2, 3], "flag": True})
    out = payload.strip_to_nl(body)
    assert len(out) == 1  # low-signal fallback to raw body


# --------------------------------------------------------------------------- #
# normalize (strip + chunk)
# --------------------------------------------------------------------------- #
def test_normalize_single_short_prompt_one_chunk():
    out = payload.normalize(json.dumps({"prompt": "check the logs"}))
    assert len(out) == 1
    assert out[0][0] == "check the logs"


def test_normalize_splits_long_text_into_chunks():
    long = " ".join(f"Sentence number {i} about the system." for i in range(30))
    out = payload.normalize(long, max_chunks=16)
    assert len(out) > 1
    assert len(out) <= 16


def test_normalize_caps_chunks_keeping_highest_weight():
    # Many user sentences (weight 1.0) + one low-weight assistant line; cap to few.
    msgs = {"messages": [
        {"role": "user", "content": " ".join(f"Do step {i}." for i in range(40))},
        {"role": "assistant", "content": "ok."},
    ]}
    out = payload.normalize(json.dumps(msgs), max_chunks=3)
    assert len(out) == 3
    # the retained chunks should be the high-weight user ones
    assert all(w >= payload._WEIGHT_NL_KEY for _, w in out)


# --------------------------------------------------------------------------- #
# extract_payload_structure — instruction/data split (sibling to strip_to_nl,
# feeds intent/segmenter.py's tier 1+2 instead of the semantic cache)
# --------------------------------------------------------------------------- #
def test_explicit_instruction_and_data_keys():
    body = json.dumps({"instruction": "delete the record", "document": "a very long unrelated document"})
    out = payload.extract_payload_structure(body)
    assert [t for t, _ in out["instruction_candidates"]] == ["delete the record"]
    assert [t for t, _ in out["data_candidates"]] == ["a very long unrelated document"]
    assert out["ambiguous_candidates"] == []


def test_user_role_defaults_to_instruction_bucket():
    body = json.dumps({"messages": [{"role": "user", "content": "book a flight to bombay"}]})
    out = payload.extract_payload_structure(body)
    assert [t for t, _ in out["instruction_candidates"]] == ["book a flight to bombay"]


def test_system_role_excluded_entirely():
    body = json.dumps({"messages": [
        {"role": "system", "content": "You are a helpful assistant with tools."},
        {"role": "user", "content": "book a flight to bombay"},
    ]})
    out = payload.extract_payload_structure(body)
    all_text = (
        [t for t, _ in out["instruction_candidates"]]
        + [t for t, _ in out["data_candidates"]]
        + [t for t, _ in out["ambiguous_candidates"]]
    )
    assert not any("helpful assistant" in t for t in all_text)


def test_assistant_and_tool_content_forced_to_data():
    body = json.dumps({"messages": [
        {"role": "assistant", "content": "here are your options"},
        {"role": "tool", "content": "raw tool dump", "content_type": "application/json"},
    ]})
    out = payload.extract_payload_structure(body)
    data_texts = [t for t, _ in out["data_candidates"]]
    assert "here are your options" in data_texts
    assert "raw tool dump" in data_texts
    assert out["instruction_candidates"] == []


def test_content_type_override_forces_data_even_under_ambiguous_key():
    body = json.dumps({"text": "some log dump", "content_type": "text/x-log"})
    out = payload.extract_payload_structure(body)
    assert [t for t, _ in out["data_candidates"]] == ["some log dump"]
    assert out["instruction_candidates"] == []


def test_plain_non_json_text_is_ambiguous():
    out = payload.extract_payload_structure("please summarize the errors")
    assert [t for t, _ in out["ambiguous_candidates"]] == ["please summarize the errors"]


def test_empty_text_returns_empty_buckets():
    out = payload.extract_payload_structure("")
    assert out == {"instruction_candidates": [], "data_candidates": [], "ambiguous_candidates": []}


def test_has_structural_data_marker():
    assert payload.has_structural_data_marker("```code```") is True
    assert payload.has_structural_data_marker("2024-01-01T00:00:00 ERROR broke") is True
    assert payload.has_structural_data_marker("please book a flight") is False


def test_extra_instruction_keys_promote_a_learned_agent_specific_key():
    body = json.dumps({"userAsk": "delete the record", "payload": "unrelated data blob"})
    generic = payload.extract_payload_structure(body)
    assert generic["instruction_candidates"] == []  # "userAsk" unknown to the generic lexicon

    learned = payload.extract_payload_structure(
        body, extra_instruction_keys=frozenset({"userask"}), extra_data_keys=frozenset({"payload"}))
    assert [t for t, _ in learned["instruction_candidates"]] == ["delete the record"]
    assert [t for t, _ in learned["data_candidates"]] == ["unrelated data blob"]
