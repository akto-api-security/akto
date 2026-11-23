import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { Badge, Banner, Box, Button, DataTable, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import func from "@/util/func"
import { usePermissions } from "@/util/permissions"
import learningsApi from "./api"
import { stashDraftForEditor } from "./draftHandoff"

// Same order and titles the attack enhancer sees them in (test-editor-services targetProfile.ts).
const INSIGHT_KINDS = [
    { kind: "BYPASS", title: "What got past it" },
    { kind: "REFUSAL_STYLE", title: "How it refuses" },
    { kind: "BLOCKED", title: "What it consistently blocks" },
    { kind: "TOOLS", title: "Tools and data it exposes" },
    { kind: "ADVICE", title: "Worth trying next" },
]
const MAX_GRID_ROWS = 10

export const LOAD_STATE = { LOADING: "LOADING", LOADED: "LOADED", UNAVAILABLE: "UNAVAILABLE", FORBIDDEN: "FORBIDDEN" }

/** How a failed read is shown: no access, or unavailable (never as "nothing learned"). */
export function loadStateFromError(error) {
    return error?.response?.status === 403 ? LOAD_STATE.FORBIDDEN : LOAD_STATE.UNAVAILABLE
}

function percent(part, whole) {
    return whole > 0 ? `${Math.round((part * 100) / whole)}%` : "—"
}

function since(ms) {
    return ms ? func.prettifyEpoch(Math.floor(ms / 1000)) : "—"
}

export function LoadStateBanner({ loadState, onRetry, subject }) {
    if (loadState === LOAD_STATE.FORBIDDEN) {
        return <Banner status="warning">You don't have access to the collections behind these learnings.</Banner>
    }
    if (loadState === LOAD_STATE.UNAVAILABLE) {
        return (
            <Banner status="critical" action={onRetry ? { content: "Retry", onAction: onRetry } : undefined}>
                {`${subject} are unavailable right now. This is a read failure, not an empty result.`}
            </Banner>
        )
    }
    return null
}

// true: on; false: switched off; null: the setting could not be read, which is not the same as on.
export function AccountSettingBanner({ learningsEnabled }) {
    if (learningsEnabled === true) return null
    if (learningsEnabled === null || learningsEnabled === undefined) {
        return (
            <Banner status="warning">
                Whether red-team learnings are turned on for this account could not be read. What is shown was learned before.
            </Banner>
        )
    }
    return (
        <Banner status="info">
            Red-team learnings are turned off for this account (Testing → User config). What is shown was learned before.
        </Banner>
    )
}

function Totals({ profile }) {
    const totals = profile?.totals || {}
    const judged = totals.judged || 0
    return (
        <VerticalStack gap="1">
            <HorizontalStack gap="4" wrap>
                <Text>{`Judged conversations: ${judged}`}</Text>
                <Text>{`Attack success rate: ${percent(totals.critical || 0, judged)} (${totals.critical || 0} of ${judged}; higher means more vulnerable)`}</Text>
                <Text>{`Near misses: ${totals.attempted || 0}`}</Text>
            </HorizontalStack>
            {profile?.unjudgedConversations > 0 ? (
                <Text color="subdued">{`${profile.unjudgedConversations} conversations could not be judged and are not counted.`}</Text>
            ) : null}
        </VerticalStack>
    )
}

function Insights({ insights }) {
    if (!insights || insights.length === 0) return <Text color="subdued">No insights in this version.</Text>
    return (
        <VerticalStack gap="3">
            {INSIGHT_KINDS.map(({ kind, title }) => {
                const ofKind = insights.filter((i) => i.kind === kind).sort((a, b) => b.importance - a.importance)
                if (ofKind.length === 0) return null
                return (
                    <VerticalStack gap="1" key={kind}>
                        <Text variant="headingSm">{title}</Text>
                        {ofKind.map((insight) => (
                            <Text key={insight.id} as="p">{`• ${insight.text}`}</Text>
                        ))}
                    </VerticalStack>
                )
            })}
        </VerticalStack>
    )
}

function cellLabel(cell) {
    const key = cell.gridKey || {}
    const prompt = key.basePromptLabel || cell.testSubType || "—"
    return [prompt, key.strategyLabel || "—"]
}

function Grid({ cells }) {
    const judgedCells = (cells || []).filter((c) => (c?.counts?.judged || 0) > 0)
    if (judgedCells.length === 0) return null
    const rows = [...judgedCells]
        .sort((a, b) => (b.counts.critical - a.counts.critical) || (b.counts.judged - a.counts.judged))
        .slice(0, MAX_GRID_ROWS)
        .map((c) => [...cellLabel(c), c.counts.judged, c.counts.critical, percent(c.counts.critical, c.counts.judged)])
    return (
        <VerticalStack gap="2">
            <Text variant="headingSm">{`Base prompt × strategy (top ${rows.length} of ${judgedCells.length} tested)`}</Text>
            <DataTable
                columnContentTypes={["text", "text", "numeric", "numeric", "numeric"]}
                headings={["Base prompt", "Strategy", "Judged", "Got through", "Attack success rate"]}
                rows={rows}
                truncate
            />
        </VerticalStack>
    )
}

export function ProfileSummary({ profile, heading }) {
    if (!profile) return null
    return (
        <VerticalStack gap="3">
            <HorizontalStack gap="2" blockAlign="center" wrap>
                {heading ? <Text variant="headingMd">{heading}</Text> : null}
                <Badge status={profile.finalVersion ? "success" : "attention"}>
                    {profile.finalVersion ? "Final for its run" : `Batch ${profile.batchNo} (run in progress or final pending)`}
                </Badge>
                <Text color="subdued">{`Updated ${since(profile.createdAtMs)}`}</Text>
            </HorizontalStack>
            <Totals profile={profile} />
            <Insights insights={profile.insights} />
            <Grid cells={profile.cells} />
        </VerticalStack>
    )
}

/** Suggested templates: open one in the Test Editor to review and save it, or dismiss it. */
export function DraftList({ drafts, onDismissed }) {
    const navigate = useNavigate()
    const { canCall } = usePermissions()
    const [busyId, setBusyId] = useState(null)
    const open = (drafts || []).filter((d) => d.status === "DRAFT")
    if (open.length === 0) return null

    const openInEditor = (draft) => {
        const testId = stashDraftForEditor(draft)
        if (!testId) {
            func.setToast(true, true, "Could not open the draft: this browser blocked session storage.")
            return
        }
        navigate(`/dashboard/test-editor/${testId}`)
    }

    const dismiss = async (draft) => {
        setBusyId(draft.id)
        try {
            await learningsApi.updateRedteamDraftStatus(draft.id, "DISMISSED")
            // The parent removes it from its current list, not from the one this render saw.
            onDismissed?.(draft.id)
            func.setToast(true, false, "Draft dismissed")
        } catch (e) {
            // the request helper already showed the error
        } finally {
            setBusyId(null)
        }
    }

    return (
        <VerticalStack gap="3">
            <Text variant="headingSm">{`Suggested test templates (${open.length})`}</Text>
            <Text color="subdued">Drafts written from what the runs showed. Nothing runs until you review and save one.</Text>
            {open.map((draft) => (
                <Box key={draft.id} padding="3" borderWidth="1" borderColor="border-subdued" borderRadius="2">
                    <VerticalStack gap="2">
                        <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                            <Text variant="headingSm" truncate>{draft.name}</Text>
                            <HorizontalStack gap="2">
                                <Button size="slim" onClick={() => openInEditor(draft)} disabled={!canCall('api/saveTestEditorFile')}>Open in Test Editor</Button>
                                <Button size="slim" plain destructive loading={busyId === draft.id}
                                    disabled={!canCall('api/updateRedteamDraftStatus')} onClick={() => dismiss(draft)}>Dismiss</Button>
                            </HorizontalStack>
                        </HorizontalStack>
                        {draft.rationale ? <Text as="p">{draft.rationale}</Text> : null}
                        <Text color="subdued">{`${(draft.strategyKeys || []).length} strategies${draft.testCategory ? ` · ${draft.testCategory}` : ""}`}</Text>
                    </VerticalStack>
                </Box>
            ))}
        </VerticalStack>
    )
}
