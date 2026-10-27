"""Fire-and-forget alerting for cascade scans.

Async sinks, all no-ops when their target isn't configured:
  - post_slack(...)         → Slack incoming webhook (SLACK_WEBHOOK_URL)
  - store_results(...)      → DB abstractor /api/storeGuardrailModelResults

All are scheduled via the runtime's fire-and-forget hook (waitUntil on
Cloudflare, asyncio.create_task on the container) so they never block the scan
response. None ever raises into the caller.
"""

import logging
import time
from typing import Any

import httpx

import metrics_push
from settings import settings

logger = logging.getLogger(__name__)

_IDENTITY = {"Accept-Encoding": "identity"}
_SLACK_TIMEOUT_S = 5.0
_STORE_TIMEOUT_S = 10.0
_TEXT_PREVIEW_CHARS = 1500


def _fmt_num(v: Any) -> str:
    try:
        return f"{float(v):.3f}"
    except (TypeError, ValueError):
        return "—"


def _build_blocks(scanner_name: str, scanner_type: str, text: str, result: dict[str, Any]) -> list[dict[str, Any]]:
    details = result.get("details") or {}
    is_valid = bool(result.get("is_valid", True))
    error = details.get("error", "")
    # A fail-open verdict (no arbiter reachable, cascade infra broke) also has
    # is_valid=True — indistinguishable from a genuinely clean scan unless we
    # check details.error specifically. Without this, a fully-broken Foundry
    # cascade would post "✅ ALLOWED" to Slack for every single request, with
    # nothing to tell a human watching the channel that nothing was actually
    # scanned at all.
    if error:
        verdict = "⚠️ DEGRADED (fail-open — not actually scanned)"
    else:
        verdict = "✅ ALLOWED" if is_valid else "🚫 BLOCKED"
    risk = _fmt_num(result.get("risk_score", 0.0))
    cascade = details.get("cascade_decision", "—")
    reason = details.get("reason", "")
    provider = details.get("llm_provider", "—")

    preview = text if len(text) <= _TEXT_PREVIEW_CHARS else text[:_TEXT_PREVIEW_CHARS] + "…"

    model_rows: list[str] = []
    for key, val in details.items():
        if not isinstance(val, dict) or "completed" not in val:
            continue
        if val.get("completed"):
            model_rows.append(
                f"• `{key}` — is_valid=`{val.get('is_valid')}` "
                f"risk=`{_fmt_num(val.get('risk_score'))}` "
                f"conf=`{_fmt_num(val.get('decision_confidence'))}`"
            )
        else:
            model_rows.append(f"• `{key}` — _not consulted_")

    blocks: list[dict[str, Any]] = [
        {
            "type": "section",
            "text": {
                "type": "mrkdwn",
                "text": (
                    f"*{verdict}* — `{scanner_name}` ({scanner_type})\n"
                    f"*winner:* `{provider}`   *cascade:* `{cascade}`   *risk:* `{risk}`"
                ),
            },
        },
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*Input:*\n```{preview}```"}},
    ]
    if error:
        blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*Fail-open reason:* `{error}`"}})
    if reason:
        blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*Reason:* {reason}"}})
    if model_rows:
        blocks.append(
            {"type": "section", "text": {"type": "mrkdwn", "text": "*Per-model decisions:*\n" + "\n".join(model_rows)}}
        )
    return blocks


async def post_slack(scanner_name: str, scanner_type: str, text: str, result: dict[str, Any]) -> None:
    webhook = (settings.SLACK_WEBHOOK_URL or "").strip()
    if not webhook:
        return
    try:
        payload = {"blocks": _build_blocks(scanner_name, scanner_type, text, result)}
        async with httpx.AsyncClient(timeout=_SLACK_TIMEOUT_S) as client:
            resp = await client.post(webhook, headers=_IDENTITY, json=payload)
        if resp.status_code >= 400:
            logger.warning(f"[Slack] webhook returned {resp.status_code}")
    except Exception as exc:
        logger.warning(f"[Slack] post failed: {exc}")


async def store_results(completed: list[dict[str, Any]], scanner_name: str) -> None:
    base = (settings.DATABASE_ABSTRACTOR_SERVICE_URL or "").strip().rstrip("/")
    if not base:
        return
    started = time.perf_counter()
    try:
        payload = {"scannerName": scanner_name, "modelResults": completed}
        async with httpx.AsyncClient(timeout=_STORE_TIMEOUT_S) as client:
            resp = await client.post(f"{base}/api/storeGuardrailModelResults", headers=_IDENTITY, json=payload)
        metrics_push.SAMPLES["alert"].record("store_results", (time.perf_counter() - started) * 1000.0)
        if resp.status_code >= 400:
            metrics_push.COUNTS["alert_errors"].increment(f"store_results:status_{resp.status_code}")
            logger.warning(f"[Store] returned {resp.status_code} for scanner={scanner_name}")
    except Exception as exc:
        metrics_push.COUNTS["alert_errors"].increment(f"store_results:{type(exc).__name__}")
        logger.warning(f"[Store] failed for scanner={scanner_name}: {exc}")


# ── Model-fallback alerts (FALLBACK_SLACK_WEBHOOK_URL) ────────────────────────
# Providers only *record* a fallback (synchronous, no I/O on the scan path);
# scan_handler flushes the records through the runtime's fire-and-forget hook.
# The first fallback of a (primary, backup) pair posts at once, then at most one
# summary per window, so a dead or slow primary can't flood the channel.
# Health-check flips (primary DOWN / AVAILABLE again) post at once. State is
# per process (per isolate on Cloudflare).
_FALLBACK_WINDOW_S = 600.0
_fallbacks: dict[tuple[str, str], dict[str, Any]] = {}
_transitions: list[str] = []


def _fallback_webhook() -> str:
    return (settings.FALLBACK_SLACK_WEBHOOK_URL or "").strip()


def fallback_reason(exc: BaseException) -> str:
    """Short, groupable cause of a fallback: 'timeout', 'HTTP <status>', or the exception type."""
    if isinstance(exc, (TimeoutError, httpx.TimeoutException)):
        return "timeout"
    if isinstance(exc, httpx.HTTPStatusError):
        return f"HTTP {exc.response.status_code}"  # 400 (too long) and 503 (down) need different fixes
    return type(exc).__name__


def note_fallback(primary: str, backup: str, reason: str) -> None:
    """A request meant for `primary` was answered by `backup`. Never raises."""
    if not _fallback_webhook():
        return
    new = {"count": 0, "reasons": {}, "since": time.time(), "last_sent": 0.0}
    entry = _fallbacks.setdefault((primary, backup), new)
    entry["count"] += 1
    entry["reasons"][reason] = entry["reasons"].get(reason, 0) + 1


def note_availability(target: str, available: bool) -> None:
    """The health check changed a primary's state (called only on a change)."""
    if _fallback_webhook():
        _transitions.append(
            f"✅ primary `{target}` is AVAILABLE again"
            if available
            else f"🔴 primary `{target}` is DOWN, requests go to its backup"
        )


def has_pending_fallback_alerts() -> bool:
    now = time.time()
    return bool(_transitions) or any(
        e["count"] and now - e["last_sent"] >= _FALLBACK_WINDOW_S for e in _fallbacks.values()
    )


async def flush_fallback_alerts() -> None:
    """Post what is due. State is taken and reset before the first await: concurrent flushes can't double-post."""
    now = time.time()
    lines, _transitions[:] = list(_transitions), []
    for (primary, backup), e in _fallbacks.items():
        if e["count"] and now - e["last_sent"] >= _FALLBACK_WINDOW_S:
            reasons = ", ".join(f"{r} x{n}" for r, n in sorted(e["reasons"].items(), key=lambda kv: -kv[1]))
            minutes = max(1, round((now - e["since"]) / 60))
            lines.append(f"⚠️ `{primary}` fell back to `{backup}` {e['count']}x in the last {minutes} min ({reasons})")
            e.update(count=0, reasons={}, since=now, last_sent=now)
    webhook = _fallback_webhook()
    if not webhook or not lines:
        return
    try:
        payload = {"text": "*agent-guard model fallback*\n" + "\n".join(lines)}
        async with httpx.AsyncClient(timeout=_SLACK_TIMEOUT_S) as client:
            resp = await client.post(webhook, headers=_IDENTITY, json=payload)
        if resp.status_code >= 400:
            logger.warning(f"[Slack] fallback webhook returned {resp.status_code}")
    except Exception as exc:
        logger.warning(f"[Slack] fallback post failed: {exc}")
