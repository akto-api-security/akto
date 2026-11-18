"""scripts/eval_accuracy.py — scanner selection, sampling, metrics, report and batching (no network)."""

import asyncio
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

P = ev.WORKER


def test_scanners_for_changed_files():
    assert ev.scanners_for([P + "src/prompts/toxicity.py"]) == ["Toxicity"]
    assert ev.scanners_for([P + "src/prompts/prompt_injection.py"]) == ["PromptInjection"]
    assert ev.scanners_for([P + "src/prompts/_abcd.py"]) == ["Toxicity", "PromptInjection"]
    assert ev.scanners_for([P + "src/providers.py"]) == ["Toxicity", "PromptInjection"]  # temperature, max_tokens
    assert ev.scanners_for([P + "src/settings.py"]) == ["Toxicity", "PromptInjection"]
    assert ev.scanners_for([P + "src/prompts/ban_topics.py", "README.md"]) == []
    # a same-named file in another app must not trigger a run against the shared endpoint
    assert ev.scanners_for(["apps/other-service/src/prompts/__init__.py", "apps/x/src/prompts/toxicity.py"]) == []


def test_workflow_triggers_on_exactly_the_scored_files():
    workflow = (Path(__file__).parents[5] / ".github" / "workflows" / "akto-agent-guard-accuracy.yml").read_text()
    triggers = {line.strip()[3:-1] for line in workflow.splitlines() if line.strip().startswith('- "')}
    scored = set(ev.SHARED).union(*ev.SCANNER_FILES.values())
    assert triggers == scored


def _dataset(tmp_path):
    d = tmp_path / "toxicity" / "data"
    d.mkdir(parents=True)
    rows = (
        [{"prompt": f"h{i}", "category": "hate", "severity": "clear", "split": "test"} for i in range(5)]
        + [{"prompt": "b", "category": "hate", "severity": "borderline", "split": "test"}]
        + [{"prompt": "t", "category": "hate", "severity": "clear", "split": "train"}]
        + [{"prompt": "no-split", "category": "hate", "severity": "clear"}]  # malformed: skipped, not a crash
        + [{"prompt": f"s{i}", "category": None, "severity": None, "split": "test"} for i in range(9)]
    )
    (d / "all.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
    pi = [
        {"prompt": "j", "label": "unsafe"},
        {"prompt": "j-train", "label": "unsafe", "split": "train"},
        {"label": "x"},
    ]
    (tmp_path / "jailbreak.json").write_text(json.dumps(pi))
    (tmp_path / "legitimate.json").write_text(json.dumps([{"prompt": "ok1"}, {"prompt": "ok2"}]))
    return tmp_path


def test_load_rows_samples_test_split_without_borderline_and_balances_safe(tmp_path):
    rows = ev.load_rows("Toxicity", _dataset(tmp_path), per_class=3, seed=1)
    unsafe = [t for t, c in rows if c != "safe"]
    assert len(unsafe) == 3 and all(t.startswith("h") for t in unsafe)  # no borderline, train or malformed rows
    assert sum(c == "safe" for _, c in rows) == 3  # as many safe as unsafe
    assert rows == ev.load_rows("Toxicity", tmp_path, per_class=3, seed=1)  # same rows for master and PR


def test_load_rows_prompt_injection_skips_train_and_malformed_rows(tmp_path):
    rows = ev.load_rows("PromptInjection", _dataset(tmp_path), per_class=60, seed=1)
    assert ("j", "jailbreak") in rows and ("j-train", "jailbreak") not in rows
    assert sum(c != "safe" for _, c in rows) == 1 and sum(c == "safe" for _, c in rows) == 1


def test_metrics_scores_unparseable_replies_as_blocks():
    classes = ["hate", "hate", "safe", "safe", "hate", "safe"]
    outcomes = [ev.BLOCK, ev.ALLOW, ev.BLOCK, ev.ALLOW, None, ev.UNPARSED]  # tp, fn, fp, tn, failed, fp
    m = ev.metrics(classes, outcomes)
    assert m["errors"] == 1 and m["unparseable"] == 1
    assert m["detection_rate"] == 0.5 and m["false_alarm_rate"] == 2 / 3
    assert m["per_class"] == {"hate": 0.5, "safe": 1 / 3}


def _run(classes, outcomes):
    return {"model": "m", "format": "abcd", "scanners": {"Toxicity": {"classes": classes, "outcomes": outcomes}}}


def test_report_marks_regressions_only():
    base = _run(["hate", "safe"], [ev.BLOCK, ev.ALLOW])
    head = _run(["hate", "safe"], [ev.BLOCK, ev.BLOCK])
    md = ev.report(base, head)
    assert md.startswith(ev.MARKER)
    assert "| False-alarm rate (safe blocked) | 0.0% | 100.0% | +100.0 ⚠️ |" in md
    assert "| Detection rate (attacks / toxic caught) | 100.0% | 100.0% | ±0 |" in md


def test_report_compares_only_rows_both_runs_scored():
    # The PR fails on the two attacks master missed; dropping them only from the PR's run would show a fake 100%.
    classes = ["hate", "hate", "hate", "safe"]
    base = _run(classes, [ev.BLOCK, ev.ALLOW, ev.ALLOW, ev.ALLOW])
    head = _run(classes, [ev.BLOCK, None, None, ev.ALLOW])
    md = ev.report(base, head)
    assert "2 of 4 rows scored in both runs (failed: master 0, this PR 2)" in md
    assert "| Detection rate (attacks / toxic caught) | 100.0% | 100.0% | ±0 |" in md


def test_report_flags_more_unparseable_replies():
    base = _run(["hate", "safe"], [ev.BLOCK, ev.ALLOW])
    head = _run(["hate", "safe"], [ev.UNPARSED, ev.ALLOW])
    assert "Replies that weren't a verdict, scored as blocks: master 0, this PR 1 ⚠️" in ev.report(base, head)


def test_a_failed_run_still_posts_a_comment(tmp_path, capsys, monkeypatch):
    head = tmp_path / "head.json"
    head.write_text(json.dumps(_run(["hate"], [ev.BLOCK])))
    monkeypatch.setattr("sys.argv", ["eval_accuracy.py", "report", str(tmp_path / "missing.json"), str(head)])
    ev.main()
    out = capsys.readouterr().out
    assert out.startswith(ev.MARKER) and "failed: no scores for master" in out


async def test_scan_all_batches_and_rate_limits():
    in_flight, peak, starts = [0], [0], []
    rps = 100.0

    async def scan(text):
        starts.append(time.monotonic())
        in_flight[0] += 1
        peak[0] = max(peak[0], in_flight[0])
        await asyncio.sleep(0.05)  # longer than the start spacing, so a batch's scans overlap
        in_flight[0] -= 1
        return {"is_valid": text != "bad"}

    rows = [("bad", "hate"), ("ok", "safe")] * 3
    out = await ev._scan_all(scan, rows, batch=3, rps=rps)
    assert out == [ev.BLOCK, ev.ALLOW] * 3
    assert peak[0] == 3  # a whole batch in flight, never more: the next batch waits for this one
    # starts are spaced 1.02/rps apart; half that leaves room for asyncio waking a little early
    assert min(b - a for a, b in zip(starts, starts[1:])) >= 0.5 / rps


async def test_unparseable_reply_is_scored_as_a_block_without_a_retry():
    calls = []

    async def scan(text):
        calls.append(text)
        raise ValueError("expected one of A/B/C/D, got 'Answer: D'")

    out = await ev._scan_all(scan, [("x", "hate"), ("y", "safe")], batch=2, rps=1000.0)
    assert out == [ev.UNPARSED, ev.UNPARSED] and calls == ["x", "y"]


async def test_scan_all_aborts_when_endpoint_is_down(monkeypatch):
    async def scan(text):
        raise ConnectionError("down")

    async def no_sleep(_):
        return None

    monkeypatch.setattr(ev.asyncio, "sleep", no_sleep)
    with pytest.raises(SystemExit):
        await ev._scan_all(scan, [("x", "safe")] * 30, batch=3, rps=1000.0)
