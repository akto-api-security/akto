#!/usr/bin/env python3
"""Accuracy of agent-guard's LLM scanner prompts on the guardrails-prompt-dataset (used by the PR accuracy workflow).

    # which scanners a set of changed files affects (comma-separated, empty if none)
    python scripts/eval_accuracy.py scanners <changed file> ...

    # score one checkout's prompts; VLLM_BASE_URL / VLLM_API_KEY / VLLM_MODEL pick the model
    python scripts/eval_accuracy.py run --scanners Toxicity,PromptInjection --src src \\
        --dataset <guardrails-prompt-dataset checkout> --out head.json

    # markdown comparison of two runs (master vs this PR) for the PR comment; a missing or unreadable run file
    # gives a comment saying the run failed
    python scripts/eval_accuracy.py report base.json head.json

`scanners` and `report` use only the standard library, so the workflow runs them with plain python, before any
dependencies are installed and even when installing them failed.

Each row goes through the production single-model path: LLMScanner(OpenAIProvider(...)).scan(), i.e. the same
prompt builder, request settings and parser agent-guard runs. A reply that isn't a verdict is scored as a block, as
production's cascade counts a scanner exception as unsafe, and counted separately; it is not retried. A network or
HTTP failure is retried and, if it persists, leaves the row unscored. The report scores only the rows that both
runs scored, so master and the PR are always compared on the same rows. Requests go out in batches of --batch (the
next batch waits for every result), at most --rps per second; 20 failed scans in a row abort the run. Run files hold
per-row outcomes and stay on the runner; the report carries only aggregate numbers, because the dataset is private
and the report lands on a public PR.
"""

import argparse
import asyncio
import json
import os
import random
import sys
import time
from collections import defaultdict
from pathlib import Path

MARKER = "<!-- agent-guard-accuracy -->"
TITLE = "### Agent Guard prompt accuracy"
TOXICITY_CATEGORIES = ("hate", "insults", "sexual", "violence", "misconduct")
PI_FILES = (  # guardrails-prompt-dataset files whose prompts PromptInjection should block
    "prompt_injection",
    "jailbreak",
    "indirect_prompt_injection",
    "data_exfiltration",
    "social_engineering",
    "model_extraction_poisoning",
)
WORKER = "apps/agent-guard/python-service/worker-py/"
# A change to one of these repo paths re-scores the scanner; a change to a SHARED path re-scores every scanner.
# Keep the workflow's `paths:` trigger in step with both.
SCANNER_FILES = {
    "Toxicity": (WORKER + "src/prompts/toxicity.py",),
    "PromptInjection": (WORKER + "src/prompts/prompt_injection.py",),
}
SHARED = (
    WORKER + "src/prompts/__init__.py",
    WORKER + "src/prompts/_abcd.py",
    WORKER + "src/llm_scanner.py",
    WORKER + "src/providers.py",  # temperature and max_tokens change verdicts
    WORKER + "src/settings.py",
    WORKER + "scripts/eval_accuracy.py",
    ".github/workflows/akto-agent-guard-accuracy.yml",
)
CONFIG = {
    "Toxicity": {"harmfulCategories": {c: "MEDIUM" for c in TOXICITY_CATEGORIES}},
    "PromptInjection": {},
}
BLOCK, ALLOW, UNPARSED = "block", "allow", "unparsed"  # per-row outcomes; None = the scan kept failing


def _warn(msg: str) -> None:
    print(f"warning: {msg}", file=sys.stderr)


def scanners_for(changed: list[str]) -> list[str]:
    changed_set = set(changed)
    if changed_set & set(SHARED):
        return list(SCANNER_FILES)
    return [name for name, files in SCANNER_FILES.items() if changed_set & set(files)]


def load_rows(scanner: str, dataset: Path, per_class: int, seed: int) -> list[tuple[str, str]]:
    """(text, class) pairs; class 'safe' or the unsafe category/file. Only test-split rows (rows without a split
    field are all test rows). At most per_class per unsafe class, and as many safe rows as unsafe ones in total, so
    the false-alarm rate is measured on a comparable sample. Rows missing a required field are skipped with a
    warning rather than failing the run."""
    by: dict[str, list[str]] = defaultdict(list)
    if scanner == "Toxicity":
        for f in sorted((dataset / "toxicity" / "data").glob("*.jsonl")):
            with open(f, encoding="utf-8") as fh:
                for n, line in enumerate(fh, 1):
                    r = json.loads(line)
                    if not {"prompt", "split", "category"} <= r.keys():
                        _warn(f"{f.name}:{n} lacks prompt, split or category; skipped")
                        continue
                    # MEDIUM sensitivity: 'borderline' rows are only toxic at HIGH, so they are not scored here
                    if r["split"] == "test" and r.get("severity") != "borderline":
                        by[r["category"] or "safe"].append(r["prompt"])
    elif scanner == "PromptInjection":
        for name in (*PI_FILES, "legitimate"):
            f = dataset / f"{name}.json"
            if not f.exists():
                continue
            for n, r in enumerate(json.loads(f.read_text(encoding="utf-8"))):
                if "prompt" not in r:
                    _warn(f"{f.name}[{n}] has no prompt; skipped")
                elif r.get("split", "test") == "test":
                    by["safe" if name == "legitimate" else name].append(r["prompt"])
    rng = random.Random(seed)
    rows: list[tuple[str, str]] = []
    for cls in sorted(c for c in by if c != "safe"):
        rows += [(t, cls) for t in rng.sample(by[cls], min(per_class, len(by[cls])))]
    safe = by.get("safe", [])
    rows += [(t, "safe") for t in rng.sample(safe, min(len(rows), len(safe)))]
    return rows


async def _scan_all(scan, rows, batch: int, rps: float) -> list[str | None]:
    """Per row: BLOCK, ALLOW, UNPARSED (the model replied, but not with a verdict), or None (the scan kept failing)."""
    out: list[str | None] = [None] * len(rows)
    gap, next_slot, fails_in_row = 1.02 / rps, [0.0], [0]

    async def one(i: int) -> tuple[int, str | None]:
        for attempt in range(3):
            now = time.monotonic()
            start = max(now, next_slot[0])  # request starts (retries included) spaced >= gap apart
            next_slot[0] = start + gap
            await asyncio.sleep(start - now)
            try:
                result = await scan(rows[i][0])
            except ValueError:  # the parsers raise on a reply that isn't a verdict; asking again won't fix that
                fails_in_row[0] = 0
                return i, UNPARSED
            except Exception:  # noqa: BLE001 - network and HTTP errors
                if attempt < 2:
                    await asyncio.sleep((5, 15)[attempt])
                continue
            fails_in_row[0] = 0
            return i, ALLOW if result["is_valid"] else BLOCK
        fails_in_row[0] += 1
        return i, None

    for k in range(0, len(rows), batch):  # one batch, wait for every result, then the next
        for i, outcome in await asyncio.gather(*(one(i) for i in range(k, min(k + batch, len(rows))))):
            out[i] = outcome
        if fails_in_row[0] >= 20:
            raise SystemExit("20 scans failed in a row: the model endpoint looks down")
    return out


def metrics(classes: list[str], outcomes: list[str | None]) -> dict:
    """Scores one run. An UNPARSED reply counts as a block, as in production; None (failed) rows are not scored."""
    tp = fp = tn = fn = 0
    per_class: dict[str, list[int]] = defaultdict(lambda: [0, 0])  # class -> [correct, scored]
    for cls, outcome in zip(classes, outcomes):
        if outcome is None:
            continue
        unsafe, blocked = cls != "safe", outcome != ALLOW
        tp += unsafe and blocked
        fn += unsafe and not blocked
        fp += not unsafe and blocked
        tn += not unsafe and not blocked
        per_class[cls][0] += blocked == unsafe
        per_class[cls][1] += 1
    n = tp + fp + tn + fn
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    return {
        "rows": len(classes),
        "errors": sum(o is None for o in outcomes),
        "unparseable": sum(o == UNPARSED for o in outcomes),
        "accuracy": (tp + tn) / n if n else 0.0,
        "detection_rate": recall,
        "false_alarm_rate": fp / (fp + tn) if fp + tn else 0.0,
        "precision": precision,
        "f1": 2 * precision * recall / (precision + recall) if precision + recall else 0.0,
        "per_class": {c: v[0] / v[1] for c, v in sorted(per_class.items()) if v[1]},
    }


async def run(args) -> None:
    # Imported here, after putting --src first on sys.path, so one copy of this script can score two checkouts:
    # master's src for the base run and the PR's for the head run. Top-level imports would also stop `scanners`
    # and `report` from running with plain python before the dependencies are installed.
    sys.path.insert(0, str(Path(args.src).resolve()))
    from llm_scanner import LLMScanner
    from providers import OpenAIProvider
    from settings import settings

    settings.init_from_env()
    model = os.environ["VLLM_MODEL"]
    provider = OpenAIProvider(os.environ.get("VLLM_API_KEY", ""), model, base_url=os.environ["VLLM_BASE_URL"])
    scanner = LLMScanner(provider, args.format)
    result = {"model": model, "format": args.format or "json", "scanners": {}}
    for name in [s for s in args.scanners.split(",") if s]:
        rows = load_rows(name, Path(args.dataset), args.per_class, args.seed)
        classes = [cls for _, cls in rows]
        outcomes = (
            await _scan_all(lambda text, n=name: scanner.scan(n, "prompt", text, CONFIG[n]), rows, args.batch, args.rps)
            if rows
            else []
        )
        result["scanners"][name] = {"classes": classes, "outcomes": outcomes}
        summary = {k: v for k, v in metrics(classes, outcomes).items() if k != "per_class"}
        print(f"{name}: {json.dumps(summary)}")
    Path(args.out).write_text(json.dumps(result, indent=1))


def _pct(x: float) -> str:
    return f"{100 * x:.1f}%"


def _delta(base: float, head: float, higher_is_better: bool = True) -> str:
    d = 100 * (head - base)
    if abs(d) < 0.05:
        return "±0"
    worse = d < 0 if higher_is_better else d > 0
    return f"{d:+.1f}" + (" ⚠️" if worse and abs(d) >= 2 else "")


def failure_report(missing: list[str]) -> str:
    return (
        f"{MARKER}\n{TITLE}\n⚠️ The accuracy run failed: no scores for {' and '.join(missing)}. "
        "Nothing about this PR's accuracy is known from this run; see the workflow logs.\n"
    )


def report(base: dict, head: dict) -> str:
    lines = [
        MARKER,
        TITLE,
        f"Model `{head['model']}` · answer format `{head['format']}` · master's and this PR's prompts are scored on "
        "the same rows: a row that failed in either run is left out of both. A reply that isn't a verdict counts as "
        "a block, as in production. ⚠️ marks a change of 2+ points in the wrong direction; differences under ~2-3 "
        "points are within run-to-run noise at these sample sizes.",
    ]
    rows_spec = [
        ("Accuracy", "accuracy", True),
        ("Detection rate (attacks / toxic caught)", "detection_rate", True),
        ("False-alarm rate (safe blocked)", "false_alarm_rate", False),
        ("Precision", "precision", True),
        ("F1", "f1", True),
    ]
    for name, h in head["scanners"].items():
        b = base["scanners"].get(name)
        lines += ["", f"#### {name}"]
        if not h["classes"]:
            lines.append("_No dataset rows found for this scanner._")
            continue
        if b is None or b["classes"] != h["classes"]:
            lines.append("_master and this PR did not score the same rows, so they can't be compared._")
            continue
        both = [i for i, (bo, ho) in enumerate(zip(b["outcomes"], h["outcomes"])) if bo is not None and ho is not None]

        def scored(run_: dict, keep: list[int] = both) -> list[str | None]:
            return [run_["outcomes"][i] for i in keep]

        classes = [h["classes"][i] for i in both]
        bm, hm = metrics(classes, scored(b)), metrics(classes, scored(h))
        unparsed = f"master {bm['unparseable']}, this PR {hm['unparseable']}"
        if hm["unparseable"] > bm["unparseable"]:
            unparsed += " ⚠️"
        lines += [
            f"{len(both)} of {len(h['classes'])} rows scored in both runs (failed: master "
            f"{sum(o is None for o in b['outcomes'])}, this PR {sum(o is None for o in h['outcomes'])}). "
            f"Replies that weren't a verdict, scored as blocks: {unparsed}.",
            "",
            "| | master | this PR | change |",
            "|---|---:|---:|---:|",
        ]
        for label, key, better in rows_spec:
            lines.append(f"| {label} | {_pct(bm[key])} | {_pct(hm[key])} | {_delta(bm[key], hm[key], better)} |")
        lines += ["", "| Class (share scored correctly) | master | this PR | change |", "|---|---:|---:|---:|"]
        for cls, v in hm["per_class"].items():
            bv = bm["per_class"].get(cls, 0.0)
            lines.append(f"| {cls} | {_pct(bv)} | {_pct(v)} | {_delta(bv, v)} |")
    return "\n".join(lines) + "\n"


def _load_run(path: str) -> dict | None:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        _warn(f"cannot read run file {path}: {exc}")
        return None


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("scanners")
    p.add_argument("files", nargs="*")
    p = sub.add_parser("run")
    p.add_argument("--scanners", required=True)
    p.add_argument("--src", required=True, help="the worker's src/ directory whose prompts are scored")
    p.add_argument("--dataset", required=True, help="guardrails-prompt-dataset checkout")
    p.add_argument("--out", required=True)
    p.add_argument("--format", default="abcd", help="answer contract: abcd or json")
    p.add_argument("--per-class", type=int, default=60)
    p.add_argument("--seed", type=int, default=20261022)
    p.add_argument("--batch", type=int, default=3)
    p.add_argument("--rps", type=float, default=3.0)
    p = sub.add_parser("report")
    p.add_argument("base")
    p.add_argument("head")
    args = ap.parse_args()
    if args.cmd == "scanners":
        print(",".join(scanners_for(args.files)))
    elif args.cmd == "run":
        asyncio.run(run(args))
    else:
        base, head = _load_run(args.base), _load_run(args.head)
        missing = [label for label, r in (("master", base), ("this PR", head)) if r is None]
        print(failure_report(missing) if missing else report(base, head), end="")


if __name__ == "__main__":
    main()
