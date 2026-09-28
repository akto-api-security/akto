import { useMemo } from "react"
import { Text, VerticalStack } from "@shopify/polaris"
import AgenticStreamingResponse from "@/apps/dashboard/pages/agentic/components/AgenticStreamingResponse"
import ActionRow from "./ActionRow"
import { buildResults } from "../palette/resolveCommand"
import { applyNavigationSideEffects } from "../palette/paletteHelpers"
import { SUGGESTED_PROMPTS_BY_DOMAIN } from "../palette/commandRegistry"

// One assistant turn — design_handoff_ask_akto_overlay/README.md, "4. Answer view" (the
// trace-line step is intentionally not rendered here: there is no streamed tool-call event
// reaching the dashboard over /api/chatAndStore today, only a single final response, so a
// step-by-step trace would have to be fabricated. AgenticThinkingBox's generic "thinking…" line,
// rendered by the caller while `loading`, covers the wait honestly instead).
//
// Write-tool confirmation is detected the same way the old ActionChips.jsx did: the write
// tools' two-phase discipline lives in the prompt + tool layer (COMMAND_PALETTE_PROMPT's
// "preview then wait" rule), not in a structured field on the response, so a confirmation
// question in the model's own wording is the only signal available.
const CONFIRM_PATTERN = /\b(confirm|would you like me to (proceed|continue|go ahead)|shall i (proceed|continue|go ahead))\b/i

export default function ChatMessage({ message, userPrompt, domain, actionState, onReviewAction, onCancelAction, onConfirmAction, onUndoAction, onOpenRoute, onAsk }) {
    const navAction = useMemo(() => {
        if (!userPrompt) return null
        const hit = buildResults(userPrompt, domain).find((o) => o.kind === "PAGE")
        return hit || null
    }, [userPrompt, domain])

    const showWriteAction = CONFIRM_PATTERN.test(message || "")
    const followups = SUGGESTED_PROMPTS_BY_DOMAIN[domain] || []

    return (
        <div className="ask-answer">
            <div className="ask-answer-body">
                <AgenticStreamingResponse content={message} />
            </div>

            {showWriteAction || navAction ? (
                <VerticalStack gap="2">
                    <Text variant="bodyMd" fontWeight="semibold" as="p">Suggested actions</Text>
                    <div className="ask-actions-card">
                        {showWriteAction ? (
                            <ActionRow
                                kind="write"
                                label="Apply this change"
                                desc="Nothing changes until you confirm."
                                state={actionState?.state || "idle"}
                                previewText={message}
                                doneText={actionState?.doneText}
                                onReview={onReviewAction}
                                onCancel={onCancelAction}
                                onConfirm={onConfirmAction}
                                onUndo={onUndoAction}
                            />
                        ) : null}
                        {navAction ? (
                            <ActionRow
                                kind="nav"
                                label={navAction.label}
                                desc={navAction.breadcrumb || "Open this page"}
                                onOpenNav={() => {
                                    applyNavigationSideEffects(navAction)
                                    onOpenRoute(navAction.route, navAction.params)
                                }}
                            />
                        ) : null}
                    </div>
                </VerticalStack>
            ) : null}

            {followups.length ? (
                <VerticalStack gap="2">
                    <Text variant="bodySm" color="subdued" as="p">Ask a follow-up</Text>
                    <div className="ask-followup-list">
                        {followups.map((f) => (
                            <button key={f} type="button" className="ask-followup-chip" onClick={() => onAsk(f)}>{f}</button>
                        ))}
                    </div>
                </VerticalStack>
            ) : null}
        </div>
    )
}
