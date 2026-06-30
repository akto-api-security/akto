"""Fire-and-forget alerting for cascade scans.

Async sinks, all no-ops when their target isn't configured:
  - post_slack(...)         → Slack incoming webhook (SLACK_WEBHOOK_URL)
  - post_cache_shadow(...)  → separate webhook (CACHE_SHADOW_SLACK_WEBHOOK_URL)
  - store_results(...)      → DB abstractor /api/storeGuardrailModelResults

All are scheduled via the runtime's fire-and-forget hook (waitUntil on
Cloudflare, asyncio.create_task on the container) so they never block the scan
response. None ever raises into the caller.
"""

import logging
from typing import Any, Dict, List

import httpx

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


def _build_blocks(scanner_name: str, scanner_type: str, text: str,
                  result: Dict[str, Any]) -> List[Dict[str, Any]]:
    details = result.get("details") or {}
    is_valid = bool(result.get("is_valid", True))
    verdict = "✅ ALLOWED" if is_valid else "🚫 BLOCKED"
    risk = _fmt_num(result.get("risk_score", 0.0))
    cascade = details.get("cascade_decision", "—")
    reason = details.get("reason", "")
    provider = details.get("llm_provider", "—")

    preview = text if len(text) <= _TEXT_PREVIEW_CHARS else text[:_TEXT_PREVIEW_CHARS] + "…"

    model_rows: List[str] = []
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

    blocks: List[Dict[str, Any]] = [
        {"type": "section", "text": {"type": "mrkdwn", "text": (
            f"*{verdict}* — `{scanner_name}` ({scanner_type})\n"
            f"*winner:* `{provider}`   *cascade:* `{cascade}`   *risk:* `{risk}`")}},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*Input:*\n```{preview}```"}},
    ]
    if reason:
        blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*Reason:* {reason}"}})
    if model_rows:
        blocks.append({"type": "section", "text": {"type": "mrkdwn",
                       "text": "*Per-model decisions:*\n" + "\n".join(model_rows)}})
    return blocks


async def post_slack(scanner_name: str, scanner_type: str, text: str,
                     result: Dict[str, Any]) -> None:
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


def _verdict(is_valid: bool) -> str:
    return "✅ ALLOWED" if is_valid else "🚫 BLOCKED"


def _cache_shadow_blocks(info: Dict[str, Any]) -> List[Dict[str, Any]]:
    outcome = info.get("outcome", "miss")
    scanner = f"`{info.get('scanner_name')}` ({info.get('scanner_type')})"
    key = info.get("scanner_key", "—")
    real = _verdict(bool(info.get("real_is_valid", True)))
    latency = _fmt_num(info.get("latency_ms"))
    text = info.get("text", "") or ""
    preview = text if len(text) <= _TEXT_PREVIEW_CHARS else text[:_TEXT_PREVIEW_CHARS] + "…"

    if outcome == "error":
        header = f"⚠️ SHADOW ERROR — guardrails-cache\nerror: `{info.get('error', '—')}`"
    elif outcome == "served":
        header = "SERVED ⚡ FROM CACHE — guardrails-cache"
    elif outcome == "miss":
        header = "MISS — guardrails-cache"
    elif outcome == "hit_match":
        header = "HIT ✅ MATCH — guardrails-cache"
    else:
        header = "HIT ⚠️ MISMATCH — guardrails-cache"

    if outcome == "served":
        # Show the actual served verdict — blocks can be served now (exact-repeat),
        # not just safe hits, so this must reflect real_is_valid, never hardcoded.
        line = f"*served:* {real}   *scanner_key:* `{key}`"
    else:
        line = f"*real:* {real}   *scanner_key:* `{key}`   *latency:* `{latency}ms`"
    extra: List[str] = []
    if outcome in ("hit_match", "hit_mismatch"):
        extra.append(f"*cache:* {_verdict(bool(info.get('cached_is_valid', True)))}")
    if "distance" in info:  # a nearest neighbour existed (present even on a near-miss)
        extra.append(f"*distance:* `{_fmt_num(info['distance'])}`")
        extra.append(f"*threshold:* `{_fmt_num(info.get('threshold'))}`")
    if "age_s" in info:  # nearest neighbour's age vs TTL
        age_line = f"*age:* `{_fmt_num(info['age_s'])}s` / TTL `{_fmt_num(info.get('ttl_s'))}s`"
        if info.get("stale"):
            age_line += " ⏰ expired → miss"
        extra.append(age_line)
    if extra:
        line += "\n" + "   ".join(extra)

    blocks: List[Dict[str, Any]] = [
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*{header}* — {scanner}\n{line}"}},
        {"type": "section", "text": {"type": "mrkdwn", "text": f"*Input:*\n```{preview}```"}},
    ]
    reason = info.get("real_reason") or ""
    if reason:
        blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*Reason:* {reason}"}})
    return blocks


async def post_cache_shadow(info: Dict[str, Any]) -> None:
    """Post a semantic-cache event (miss / hit match / hit mismatch / served).

    Uses its own webhook (CACHE_SHADOW_SLACK_WEBHOOK_URL), not SLACK_WEBHOOK_URL,
    so cache noise stays out of the production scan-alert channel. No fallback:
    unset → no-op.
    """
    webhook = (settings.CACHE_SHADOW_SLACK_WEBHOOK_URL or "").strip()
    if not webhook:
        return
    try:
        payload = {"blocks": _cache_shadow_blocks(info)}
        async with httpx.AsyncClient(timeout=_SLACK_TIMEOUT_S) as client:
            resp = await client.post(webhook, headers=_IDENTITY, json=payload)
        if resp.status_code >= 400:
            logger.warning(f"[Slack] cache-shadow webhook returned {resp.status_code}")
    except Exception as exc:
        logger.warning(f"[Slack] cache-shadow post failed: {exc}")


async def store_results(completed: List[Dict[str, Any]], scanner_name: str) -> None:
    base = (settings.DATABASE_ABSTRACTOR_SERVICE_URL or "").strip().rstrip("/")
    if not base:
        return
    try:
        payload = {"scannerName": scanner_name, "modelResults": completed}
        async with httpx.AsyncClient(timeout=_STORE_TIMEOUT_S) as client:
            resp = await client.post(f"{base}/api/storeGuardrailModelResults",
                                     headers=_IDENTITY, json=payload)
        if resp.status_code >= 400:
            logger.warning(f"[Store] returned {resp.status_code} for scanner={scanner_name}")
    except Exception as exc:
        logger.warning(f"[Store] failed for scanner={scanner_name}: {exc}")
