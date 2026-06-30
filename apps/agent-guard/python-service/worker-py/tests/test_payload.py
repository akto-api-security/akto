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
