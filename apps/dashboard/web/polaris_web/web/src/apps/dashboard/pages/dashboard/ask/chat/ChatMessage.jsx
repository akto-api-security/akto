import { Fragment, useMemo } from "react"
import { Box, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import AgenticStreamingResponse from "@/apps/dashboard/pages/agentic/components/AgenticStreamingResponse"
import ActionRow from "./ActionRow"
import Pressable from "../components/Pressable"
import { buildResults } from "../palette/resolveCommand"
import { applyNavigationSideEffects } from "../palette/paletteHelpers"
import { suggestedPrompts } from "../palette/commandRegistry"
import useDashboardCategory from "../palette/useDashboardCategory"

// One assistant turn — design_handoff_ask_akto_overlay/README.md, "4. Answer view". No
// step-by-step trace: /api/chatAndStore returns only the final response, so a trace would have to
// be invented. The caller's AgenticThinkingBox covers the wait instead.
//
// The write tools' two-phase discipline lives in the prompt + tool layer, not in a structured
// response field, so the model's own confirmation question is the only signal of a pending write.
const CONFIRM_PATTERN = /\b(confirm|would you like me to (proceed|continue|go ahead)|shall i (proceed|continue|go ahead))\b/i

export default function ChatMessage({ message, userPrompt, skipStreaming, onStreamingComplete, actionState, onReviewAction, onCancelAction, onConfirmAction, onUndoAction, onOpenRoute, onAsk }) {
    const category = useDashboardCategory()
    const navAction = useMemo(() => {
        if (!userPrompt) return null
        return buildResults(userPrompt, category).find((o) => o.kind === "PAGE") || null
    }, [userPrompt, category])

    const actionRows = []
    if (CONFIRM_PATTERN.test(message || "")) {
        actionRows.push(
            <ActionRow
                key="write"
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
        )
    }
    if (navAction) {
        actionRows.push(
            <ActionRow
                key="nav"
                kind="nav"
                label={navAction.label}
                desc={navAction.breadcrumb || "Open this page"}
                onOpenNav={() => {
                    applyNavigationSideEffects(navAction)
                    onOpenRoute(navAction.route, navAction.params)
                }}
            />
        )
    }

    const followups = suggestedPrompts(category)

    return (
        <VerticalStack gap="4">
            <AgenticStreamingResponse content={message} skipStreaming={skipStreaming} onStreamingComplete={onStreamingComplete} />

            {actionRows.length ? (
                <VerticalStack gap="2">
                    <Text as="p" variant="bodyMd" fontWeight="semibold">Suggested actions</Text>
                    <Box
                        background="bg-ask-action"
                        borderWidth="1"
                        borderColor="border-ask-magic"
                        borderRadius="3"
                        overflowX="hidden"
                        overflowY="hidden"
                    >
                        {actionRows.map((row, i) => (
                            <Fragment key={row.key}>
                                {i > 0 ? <Divider borderColor="border-ask-divider" /> : null}
                                {row}
                            </Fragment>
                        ))}
                    </Box>
                </VerticalStack>
            ) : null}

            {followups.length ? (
                <VerticalStack gap="2">
                    <Text as="p" variant="bodySm" color="subdued">Ask a follow-up</Text>
                    <HorizontalStack gap="2">
                        {followups.map((f) => (
                            <Pressable
                                key={f}
                                onClick={() => onAsk(f)}
                                background="bg-primary-subdued-hover"
                                hoverBackground="bg-ask-magic-hover"
                                borderWidth="1"
                                borderColor="border-ask-magic"
                                borderRadius="full"
                                color="text-ask-magic"
                                paddingBlockStart="1_5-experimental"
                                paddingBlockEnd="1_5-experimental"
                                paddingInlineStart="3"
                                paddingInlineEnd="3"
                            >
                                <Text as="span" variant="bodyMd" fontWeight="medium">{f}</Text>
                            </Pressable>
                        ))}
                    </HorizontalStack>
                </VerticalStack>
            ) : null}
        </VerticalStack>
    )
}
