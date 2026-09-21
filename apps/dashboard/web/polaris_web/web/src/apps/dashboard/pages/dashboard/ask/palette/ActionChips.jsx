import { HorizontalStack, Button } from "@shopify/polaris"
import { useMemo } from "react"
import { resolve } from "./resolveCommand"

// Chips under one AI answer. There is no structured "the AI called a write tool and is waiting
// for confirmation" signal on the wire — /api/chatAndStore returns plain text — so this is
// deliberately built on what IS available rather than inventing a fragile response parser:
//
// - A confirm chip appears when the answer's own wording asks for confirmation (the write
//   tools' two-phase discipline lives entirely in the prompt + tool layer, per
//   COMMAND_PALETTE_PROMPT's "preview then wait" rule — the frontend's only job is making
//   "yes" one click instead of a typed reply).
// - An "Open in X" chip reuses the SAME palette intent/command resolution the input itself uses,
//   run against the user's own prompt, so the destination a chip offers is exactly what typing
//   that prompt into the palette directly would have resolved to.
const CONFIRM_PATTERN = /\b(confirm|would you like me to (proceed|continue|go ahead)|shall i (proceed|continue|go ahead))\b/i

export default function ActionChips({ userPrompt, assistantText, conversationId, onSendFollowUp, onOpenRoute }) {
    const navSuggestion = useMemo(() => {
        if (!userPrompt) return null
        const { options, askFirst } = resolve(userPrompt)
        if (askFirst) return null
        const top = options.find((o) => o.kind === "INTENT" || o.kind === "COMMAND")
        return top || null
    }, [userPrompt])

    const showConfirm = CONFIRM_PATTERN.test(assistantText || "")

    if (!showConfirm && !navSuggestion && !conversationId) return null

    return (
        <HorizontalStack gap="2">
            {showConfirm ? (
                <Button primary size="slim" onClick={() => onSendFollowUp("Yes, go ahead — please proceed.")}>
                    Yes, go ahead
                </Button>
            ) : null}
            {navSuggestion ? (
                <Button size="slim" onClick={() => onOpenRoute(navSuggestion.route, navSuggestion.params)}>
                    Open in {navSuggestion.label} →
                </Button>
            ) : null}
            {conversationId ? (
                <Button plain onClick={() => onOpenRoute(`/dashboard/ask-ai?conversation=${encodeURIComponent(conversationId)}`, {}, { fullUrl: true })}>
                    Continue in Ask Akto ↗
                </Button>
            ) : null}
        </HorizontalStack>
    )
}
