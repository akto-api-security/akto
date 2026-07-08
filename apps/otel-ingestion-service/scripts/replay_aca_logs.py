#!/usr/bin/env python3
"""
Parse otel-ingestion-service ACA log dumps (otel_event JSON lines) and emit
Cowork OTLP ExportLogsServiceRequest JSON for POST /v1/logs.

Input format: lines from container logs, e.g. data-adhoc/logs-otel.txt
  ... stderr F {"level":"info","msg":"otel_event","event_name":"claude_code.user_prompt",...}
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from typing import Any

ZAP_META = frozenset({
    "level",
    "ts",
    "caller",
    "msg",
    "account_id",
    "source",
    "signal_type",
    "event_name",
    "correlation_id",
    "event_timestamp",
})

RESOURCE_KEYS = ("service.name", "service.version")

# Synthetic rows from local OTLP smoke tests (prompt-test-abc / 1700000000 timestamps).
TEST_FIXTURE_CORRELATION_IDS = frozenset({"prompt-test-abc"})
TEST_FIXTURE_SESSION_IDS = frozenset({"session-test-xyz"})
MIN_REAL_EVENT_TIMESTAMP = 1_700_000_100


def is_good_event(event: dict[str, Any]) -> bool:
    if event.get("correlation_id") in TEST_FIXTURE_CORRELATION_IDS:
        return False
    if event.get("prompt.id") in TEST_FIXTURE_CORRELATION_IDS:
        return False
    if event.get("session.id") in TEST_FIXTURE_SESSION_IDS:
        return False
    if float(event.get("event_timestamp") or 0) < MIN_REAL_EVENT_TIMESTAMP:
        return False
    if not event.get("user.id"):
        return False
    return True


def parse_log_line(line: str) -> dict[str, Any] | None:
    line = line.strip()
    if "otel_event" not in line:
        return None
    idx = line.find("{")
    if idx < 0:
        return None
    try:
        obj = json.loads(line[idx:])
    except json.JSONDecodeError:
        return None
    if obj.get("msg") != "otel_event":
        return None
    return obj


def event_name_for_otlp(raw: str) -> str:
    if raw.startswith("claude_code."):
        return raw[len("claude_code.") :]
    return raw


def to_unix_nano(ts: Any) -> str:
    if ts is None:
        return "0"
    if isinstance(ts, (int, float)):
        val = float(ts)
        if val < 1e12:
            val *= 1_000_000_000
        else:
            val *= 1_000_000_000 if val < 1e15 else 1
        return str(int(val))
    return "0"


def attr_value(key: str, value: Any) -> dict[str, Any]:
    if value is None:
        return {"stringValue": ""}
    if isinstance(value, bool):
        return {"boolValue": value}
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if key.endswith("_ms") or key.endswith("_bytes") or key.endswith("_tokens") or key == "prompt_length":
            return {"intValue": str(int(value))}
        if "cost" in key or "usd" in key:
            return {"doubleValue": float(value)}
        if float(value).is_integer() and abs(float(value)) < 1e15:
            return {"intValue": str(int(value))}
        return {"doubleValue": float(value)}
    return {"stringValue": str(value)}


def build_record(event: dict[str, Any]) -> dict[str, Any]:
    attrs: list[dict[str, Any]] = []
    for key, value in sorted(event.items()):
        if key in ZAP_META:
            continue
        if key in RESOURCE_KEYS:
            continue
        if value == "" or value is None:
            continue
        attrs.append({"key": key, "value": attr_value(key, value)})

    return {
        "eventName": event_name_for_otlp(str(event.get("event_name", ""))),
        "timeUnixNano": to_unix_nano(event.get("event_timestamp")),
        "attributes": attrs,
    }


def dedupe_key(event: dict[str, Any]) -> tuple:
    return (
        event.get("event_name"),
        event.get("event_timestamp"),
        event.get("correlation_id"),
        event.get("event.sequence"),
    )


def build_otlp(events: list[dict[str, Any]]) -> dict[str, Any]:
    if not events:
        return {"resourceLogs": []}

    service_name = "cowork"
    service_version = ""
    for ev in events:
        if ev.get("service.name"):
            service_name = str(ev["service.name"])
        if ev.get("service.version"):
            service_version = str(ev["service.version"])
        if service_name and service_version:
            break

    resource_attrs = [{"key": "service.name", "value": {"stringValue": service_name}}]
    if service_version:
        resource_attrs.append(
            {"key": "service.version", "value": {"stringValue": service_version}}
        )

    records = [build_record(ev) for ev in events]
    return {
        "resourceLogs": [
            {
                "resource": {"attributes": resource_attrs},
                "scopeLogs": [{"logRecords": records}],
            }
        ]
    }


def load_events(path: str, *, good_only: bool = True, shift_to_now: bool = False) -> list[dict[str, Any]]:
    seen: set[tuple] = set()
    events: list[dict[str, Any]] = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            parsed = parse_log_line(line)
            if parsed is None:
                continue
            if good_only and not is_good_event(parsed):
                continue
            key = dedupe_key(parsed)
            if key in seen:
                continue
            seen.add(key)
            events.append(parsed)
    events.sort(key=lambda e: (float(e.get("event_timestamp") or 0), str(e.get("event_name", ""))))
    if shift_to_now and events:
        now = time.time()
        max_ts = max(float(e.get("event_timestamp") or 0) for e in events)
        if max_ts > 0:
            offset = now - max_ts
            for e in events:
                e["event_timestamp"] = float(e.get("event_timestamp") or 0) + offset
    return events


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert ACA otel_event logs to OTLP JSON")
    parser.add_argument(
        "logs_file",
        nargs="?",
        default=None,
        help="Path to logs-otel.txt (default: repo data-adhoc/logs-otel.txt)",
    )
    parser.add_argument("-o", "--out", help="Write OTLP JSON to file instead of stdout")
    parser.add_argument(
        "--print-account-ids",
        action="store_true",
        help="Print unique account_id values from the log and exit",
    )
    parser.add_argument(
        "--include-test-fixtures",
        action="store_true",
        help="Include synthetic local test rows (prompt-test-abc, 1700000000 timestamps)",
    )
    parser.add_argument(
        "--shift-to-now",
        action="store_true",
        help="Shift all event_timestamp values so the latest event is at current time",
    )
    args = parser.parse_args()

    logs_path = args.logs_file
    if not logs_path:
        # scripts/ -> apps/otel-ingestion-service -> apps -> repo root
        import os

        script_dir = os.path.dirname(os.path.abspath(__file__))
        logs_path = os.path.normpath(
            os.path.join(script_dir, "..", "..", "..", "data-adhoc", "logs-otel-good.txt")
        )

    events = load_events(logs_path, good_only=not args.include_test_fixtures, shift_to_now=args.shift_to_now)
    if not events:
        print(f"No otel_event lines found in {logs_path}", file=sys.stderr)
        return 1

    account_ids = {int(e["account_id"]) for e in events if e.get("account_id") is not None}
    if args.print_account_ids:
        print(json.dumps(sorted(account_ids)))
        return 0

    if len(account_ids) > 1:
        print(f"Warning: multiple account_ids in log: {sorted(account_ids)}", file=sys.stderr)
    elif account_ids:
        print(f"# account_id={next(iter(account_ids))}", file=sys.stderr)

    payload = build_otlp(events)
    text = json.dumps(payload, indent=2)
    print(f"# {len(events)} unique otel_event records", file=sys.stderr)

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text)
        print(f"Wrote {args.out}", file=sys.stderr)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
