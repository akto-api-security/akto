#!/usr/bin/env python3
"""Accuracy of agent-guard's LLM scanner prompts on the guardrails-prompt-dataset (used by the PR accuracy workflow).

    # which scanners a set of changed files affects (comma-separated, empty if none)
    python scripts/eval_accuracy.py scanners <changed file> ...

    # score one checkout's prompts; VLLM_BASE_URL / VLLM_API_KEY / VLLM_MODEL pick the model
    python scripts/eval_accuracy.py run --scanners Toxicity,PromptInjection --src src \\
        --dataset <guardrails-prompt-dataset checkout> --out head.json

    # markdown comparison of two runs (master vs this PR) for the PR comment
    python scripts/eval_accuracy.py report base.json head.json

Each row goes through the production single-model path: LLMScanner(OpenAIProvider(...)).scan(), i.e. the same
prompt builder, request settings and parser agent-guard runs. Requests go out in batches of --batch (the next batch
waits for every result), at most --rps per second; 20 failed scans in a row abort the run. Only aggregate numbers are
printed or written: the dataset is private and the report lands on a public PR.
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
TOXICITY_CATEGORIES = ("hate", "insults", "sexual", "violence", "misconduct")
PI_FILES = (  # guardrails-prompt-dataset files whose prompts PromptInjection should block
    "prompt_injection",
    "jailbreak",
    "indirect_prompt_injection",
    "data_exfiltration",
    "social_engineering",
    "model_extraction_poisoning",
)
# A change to one of these files re-scores the scanner; a change to a SHARED file re-scores every scanner.
SCANNER_FILES = {
    "Toxicity": ("src/prompts/toxicity.py",),
    "PromptInjection": ("src/prompts/prompt_injection.py",),
}
SHARED = (
    "src/prompts/__init__.py",
    "src/prompts/_abcd.py",
    "src/llm_scanner.py",
    "scripts/eval_accuracy.py",
    ".github/workflows/akto-agent-guard-accuracy.yml",
)
CONFIG = {
    "Toxicity": {"harmfulCategories": {c: "MEDIUM" for c in TOXICITY_CATEGORIES}},
    "PromptInjection": {},
}


def scanners_for(changed: list[str]) -> list[str]:
    if any(f.endswith(s) for f in changed for s in SHARED):
        return list(SCANNER_FILES)
    return [name for name, files in SCANNER_FILES.items() if any(f.endswith(s) for f in changed for s in files)]


def load_rows(scanner: str, dataset: Path, per_class: int, seed: int) -> list[tuple[str, str]]:
    """(text, class) pairs; class 'safe' or the unsafe category/file. At most per_class per unsafe class, and as many
    safe rows as unsafe ones in total, so the false-alarm rate is measured on a comparable sample."""
    by: dict[str, list[str]] = defaultdict(list)
    if scanner == "Toxicity":
        for f in sorted((dataset / "toxicity" / "data").glob("*.jsonl")):
            for line in open(f, encoding="utf-8"):
                r = json.loads(line)
                # MEDIUM sensitivity: 'borderline' rows are only toxic at HIGH, so they are not scored here
                if r["split"] == "test" and r.get("severity") != "borderline":
                    by[r["category"] or "safe"].append(r["prompt"])
    elif scanner == "PromptInjection":
        for name in (*PI_FILES, "legitimate"):
            f = dataset / f"{name}.json"
            if f.exists():
                by["safe" if name == "legitimate" else name] += [x["prompt"] for x in json.load(open(f))]
    rng = random.Random(seed)
    rows: list[tuple[str, str]] = []
    for cls in sorted(c for c in by if c != "safe"):
        rows += [(t, cls) for t in rng.sample(by[cls], min(per_class, len(by[cls])))]
    safe = by.get("safe", [])
    rows += [(t, "safe") for t in rng.sample(safe, min(len(rows), len(safe)))]
    return rows


async def _scan_all(scan, rows, batch: int, rps: float) -> list[bool | None]:
    """True = blocked, False = allowed, None = the scan failed three times."""
    out: list[bool | None] = [None] * len(rows)
    gap, next_slot, fails_in_row = 1.02 / rps, [0.0], [0]

    async def one(i: int) -> tuple[int, bool | None]:
        for attempt in range(3):
            now = time.monotonic()
            start = max(now, next_slot[0])  # request starts (retries included) spaced >= gap apart
            next_slot[0] = start + gap
            await asyncio.sleep(start - now)
            try:
                blocked = not (await scan(rows[i][0]))["is_valid"]
                fails_in_row[0] = 0
                return i, blocked
            except Exception:  # noqa: BLE001 - network, HTTP and parse errors all count as a failed scan
                if attempt < 2:
                    await asyncio.sleep((5, 15)[attempt])
        fails_in_row[0] += 1
        return i, None

    for k in range(0, len(rows), batch):  # one batch, wait for every result, then the next
        for i, blocked in await asyncio.gather(*(one(i) for i in range(k, min(k + batch, len(rows))))):
            out[i] = blocked
        if fails_in_row[0] >= 20:
            raise SystemExit("20 scans failed in a row: the model endpoint looks down")
    return out


def metrics(rows: list[tuple[str, str]], blocked: list[bool | None]) -> dict:
    tp = fp = tn = fn = 0
    per_class: dict[str, list[int]] = defaultdict(lambda: [0, 0])  # class -> [correct, scored]
    for (_, cls), b in zip(rows, blocked):
        if b is None:
            continue
        unsafe = cls != "safe"
        tp += unsafe and b
        fn += unsafe and not b
        fp += not unsafe and b
        tn += not unsafe and not b
        per_class[cls][0] += b == unsafe
        per_class[cls][1] += 1
    n = tp + fp + tn + fn
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    return {
        "rows": len(rows),
        "errors": sum(b is None for b in blocked),
        "accuracy": (tp + tn) / n if n else 0.0,
        "detection_rate": recall,
        "false_alarm_rate": fp / (fp + tn) if fp + tn else 0.0,
        "precision": precision,
        "f1": 2 * precision * recall / (precision + recall) if precision + recall else 0.0,
        "per_class": {c: v[0] / v[1] for c, v in sorted(per_class.items()) if v[1]},
    }


async def run(args) -> None:
    sys.path.insert(0, str(Path(args.src).resolve()))  # this checkout's prompts, not the runner's
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
        if not rows:
            result["scanners"][name] = {"rows": 0}
            continue
        blocked = await _scan_all(
            lambda text, n=name: scanner.scan(n, "prompt", text, CONFIG[n]), rows, args.batch, args.rps
        )
        result["scanners"][name] = metrics(rows, blocked)
        print(f"{name}: {json.dumps({k: v for k, v in result['scanners'][name].items() if k != 'per_class'})}")
    Path(args.out).write_text(json.dumps(result, indent=1))


def _pct(x: float) -> str:
    return f"{100 * x:.1f}%"


def _delta(base: float, head: float, higher_is_better: bool = True) -> str:
    d = 100 * (head - base)
    if abs(d) < 0.05:
        return "±0"
    worse = d < 0 if higher_is_better else d > 0
    return f"{d:+.1f}" + (" ⚠️" if worse and abs(d) >= 2 else "")


def report(base: dict, head: dict) -> str:
    lines = [
        MARKER,
        "### Agent Guard prompt accuracy",
        f"Model `{head['model']}` · answer format `{head['format']}` · the same rows are scored with master's and this "
        "PR's prompts. ⚠️ marks a change of 2+ points in the wrong direction; differences under ~2-3 points are "
        "within run-to-run noise at these sample sizes.",
    ]
    rows_spec = [
        ("Accuracy", "accuracy", True),
        ("Detection rate (attacks / toxic caught)", "detection_rate", True),
        ("False-alarm rate (safe blocked)", "false_alarm_rate", False),
        ("Precision", "precision", True),
        ("F1", "f1", True),
    ]
    for name, h in head["scanners"].items():
        b = base["scanners"].get(name, {})
        lines += ["", f"#### {name}"]
        if not h.get("rows"):
            lines.append("_No dataset rows found for this scanner._")
            continue
        lines += [
            f"{h['rows']} rows ({h['errors']} failed scans this PR, {b.get('errors', 0)} on master)",
            "",
            "| | master | this PR | change |",
            "|---|---:|---:|---:|",
        ]
        for label, key, better in rows_spec:
            lines.append(f"| {label} | {_pct(b[key])} | {_pct(h[key])} | {_delta(b[key], h[key], better)} |")
        lines += ["", "| Class (share scored correctly) | master | this PR | change |", "|---|---:|---:|---:|"]
        for cls, v in h["per_class"].items():
            bv = b.get("per_class", {}).get(cls, 0.0)
            lines.append(f"| {cls} | {_pct(bv)} | {_pct(v)} | {_delta(bv, v)} |")
    return "\n".join(lines) + "\n"


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
        print(report(json.load(open(args.base)), json.load(open(args.head))), end="")


if __name__ == "__main__":
    main()
