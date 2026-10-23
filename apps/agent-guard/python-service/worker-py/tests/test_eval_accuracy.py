"""scripts/eval_accuracy.py — scanner selection, sampling, metrics, report and batching (no network)."""

import importlib.util
import json
import time
from pathlib import Path

import pytest

_spec = importlib.util.spec_from_file_location(
    "eval_accuracy", Path(__file__).parent.parent / "scripts" / "eval_accuracy.py"
)
ev = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ev)


def test_scanners_for_changed_files():
    p = "apps/agent-guard/python-service/worker-py/"
    assert ev.scanners_for([p + "src/prompts/toxicity.py"]) == ["Toxicity"]
    assert ev.scanners_for([p + "src/prompts/prompt_injection.py"]) == ["PromptInjection"]
    assert ev.scanners_for([p + "src/prompts/_abcd.py"]) == ["Toxicity", "PromptInjection"]
    assert ev.scanners_for([p + "src/prompts/ban_topics.py", "README.md"]) == []


def _dataset(tmp_path):
    d = tmp_path / "toxicity" / "data"
    d.mkdir(parents=True)
    rows = (
        [{"prompt": f"h{i}", "category": "hate", "severity": "clear", "split": "test"} for i in range(5)]
        + [{"prompt": "b", "category": "hate", "severity": "borderline", "split": "test"}]
        + [{"prompt": "t", "category": "hate", "severity": "clear", "split": "train"}]
        + [{"prompt": f"s{i}", "category": None, "severity": None, "split": "test"} for i in range(9)]
    )
    (d / "all.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
    (tmp_path / "jailbreak.json").write_text(json.dumps([{"prompt": "j", "label": "unsafe"}]))
    (tmp_path / "legitimate.json").write_text(json.dumps([{"prompt": "ok1"}, {"prompt": "ok2"}]))
    return tmp_path


def test_load_rows_samples_test_split_without_borderline_and_balances_safe(tmp_path):
    rows = ev.load_rows("Toxicity", _dataset(tmp_path), per_class=3, seed=1)
    unsafe = [t for t, c in rows if c != "safe"]
    assert len(unsafe) == 3 and all(t.startswith("h") for t in unsafe)  # no borderline, no train rows
    assert sum(c == "safe" for _, c in rows) == 3  # as many safe as unsafe
    assert rows == ev.load_rows("Toxicity", tmp_path, per_class=3, seed=1)  # same rows for master and PR


def test_load_rows_prompt_injection_files(tmp_path):
    rows = ev.load_rows("PromptInjection", _dataset(tmp_path), per_class=60, seed=1)
    assert ("j", "jailbreak") in rows and sum(c == "safe" for _, c in rows) == 1


def test_metrics():
    rows = [("a", "hate"), ("b", "hate"), ("c", "safe"), ("d", "safe"), ("e", "hate")]
    m = ev.metrics(rows, [True, False, True, False, None])  # tp, fn, fp, tn, error
    assert m["errors"] == 1
    assert m["detection_rate"] == 0.5 and m["false_alarm_rate"] == 0.5 and m["accuracy"] == 0.5
    assert m["per_class"] == {"hate": 0.5, "safe": 0.5}


def test_report_marks_regressions_only():
    base = {"scanners": {"Toxicity": ev.metrics([("a", "hate"), ("b", "safe")], [True, False])}}
    head = {
        "model": "m",
        "format": "abcd",
        "scanners": {"Toxicity": ev.metrics([("a", "hate"), ("b", "safe")], [True, True])},
    }
    md = ev.report(base, head)
    assert md.startswith(ev.MARKER)
    assert "| False-alarm rate (safe blocked) | 0.0% | 100.0% | +100.0 ⚠️ |" in md
    assert "| Detection rate (attacks / toxic caught) | 100.0% | 100.0% | ±0 |" in md


async def test_scan_all_batches_and_rate_limits():
    in_flight, peak, starts = [0], [0], []

    async def scan(text):
        starts.append(time.monotonic())
        in_flight[0] += 1
        peak[0] = max(peak[0], in_flight[0])
        in_flight[0] -= 1
        return {"is_valid": text != "bad"}

    rows = [("bad", "hate"), ("ok", "safe")] * 3
    out = await ev._scan_all(scan, rows, batch=3, rps=30.0)
    assert out == [True, False] * 3
    assert peak[0] <= 3
    # never more than rps starts in any one-second-equivalent window (asyncio can wake ~1ms early; the 2% margin
    # in the spacing absorbs that)
    window = 1.0 / 30 * 3
    assert max(sum(1 for t in starts if s <= t < s + window) for s in starts) <= 3


async def test_scan_all_aborts_when_endpoint_is_down(monkeypatch):
    async def scan(text):
        raise ConnectionError("down")

    async def no_sleep(_):
        return None

    monkeypatch.setattr(ev.asyncio, "sleep", no_sleep)
    with pytest.raises(SystemExit):
        await ev._scan_all(scan, [("x", "safe")] * 30, batch=3, rps=1000.0)
