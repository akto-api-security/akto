"""Slack incoming-webhook alerts for modelMap scan verdicts.

Use the module-level `slack_alerter` instance — it is a no-op when
SLACK_WEBHOOK_URL is unset, so callers can fire unconditionally.
"""

import logging
import threading
from typing import Any, Dict, List

import httpx

from settings import settings

logger = logging.getLogger(__name__)


class SlackAlerter:
    """Posts modelMap scan verdicts to a Slack incoming webhook."""

    TEXT_PREVIEW_CHARS = 1500
    HTTP_TIMEOUT_S = 5.0

    def __init__(self, webhook_url: str = ""):
        self._webhook_url = (webhook_url or settings.SLACK_WEBHOOK_URL).strip()

    @property
    def enabled(self) -> bool:
        return bool(self._webhook_url)

    def fire(
        self,
        scanner_name: str,
        scanner_type: str,
        text: str,
        result: Dict[str, Any],
    ) -> None:
        """Spawn a daemon thread that POSTs to Slack; no-op when disabled."""
        if not self.enabled:
            return
        threading.Thread(
            target=self._post,
            args=(scanner_name, scanner_type, text, result),
            daemon=True,
        ).start()

    # ── Internals ────────────────────────────────────────────────────────

    def _post(
        self,
        scanner_name: str,
        scanner_type: str,
        text: str,
        result: Dict[str, Any],
    ) -> None:
        try:
            payload = {"blocks": self._build_blocks(scanner_name, scanner_type, text, result)}
            resp = httpx.post(self._webhook_url, json=payload, timeout=self.HTTP_TIMEOUT_S)
            if resp.status_code >= 400:
                logger.warning(f"[Slack] webhook returned {resp.status_code}: {resp.text[:200]}")
        except Exception as exc:
            logger.warning(f"[Slack] post failed: {exc}")

    def _build_blocks(
        self,
        scanner_name: str,
        scanner_type: str,
        text: str,
        result: Dict[str, Any],
    ) -> List[Dict[str, Any]]:
        details = result.get("details") or {}
        is_valid = bool(result.get("is_valid", True))
        verdict = "✅ ALLOWED" if is_valid else "🚫 BLOCKED"
        risk = self._fmt_num(result.get("risk_score", 0.0))
        cascade = details.get("cascade_decision", "—")
        reason = details.get("reason", "")
        provider = details.get("llm_provider", "—")

        preview = (
            text
            if len(text) <= self.TEXT_PREVIEW_CHARS
            else text[: self.TEXT_PREVIEW_CHARS] + "…"
        )

        # Per-model entries: dicts under `details` that carry a "completed" key.
        model_rows: List[str] = []
        for key, val in details.items():
            if not isinstance(val, dict) or "completed" not in val:
                continue
            if val.get("completed"):
                model_rows.append(
                    f"• `{key}` — is_valid=`{val.get('is_valid')}` "
                    f"risk=`{self._fmt_num(val.get('risk_score'))}` "
                    f"conf=`{self._fmt_num(val.get('decision_confidence'))}`"
                )
            else:
                model_rows.append(f"• `{key}` — _not consulted_")

        blocks: List[Dict[str, Any]] = [
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
            {
                "type": "section",
                "text": {"type": "mrkdwn", "text": f"*Input:*\n```{preview}```"},
            },
        ]
        if reason:
            blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": f"*Reason:* {reason}"}})
        if model_rows:
            blocks.append({"type": "section", "text": {"type": "mrkdwn", "text": "*Per-model decisions:*\n" + "\n".join(model_rows)}})
        return blocks

    @staticmethod
    def _fmt_num(v: Any) -> str:
        try:
            return f"{float(v):.3f}"
        except (TypeError, ValueError):
            return "—"


# Module-level singleton; mirrors the `settings` pattern.
slack_alerter = SlackAlerter()
