import { useCallback, useEffect, useState } from "react"
import { Divider, LegacyCard, Text, VerticalStack } from "@shopify/polaris"
import learningsApi from "./api"
import { DraftList, LOAD_STATE, LoadStateBanner, ProfileSummary, loadStateFromError } from "./LearningsView"

/**
 * What one run learned about the agents it tested. Renders nothing for a run that learned nothing (most runs are
 * not agentic), but a failed read is always shown.
 */
function RunLearningsCard({ summaryHexId }) {
    const [loadState, setLoadState] = useState(LOAD_STATE.LOADING)
    const [runLearnings, setRunLearnings] = useState(null)
    const [learningsEnabled, setLearningsEnabled] = useState(null)

    const load = useCallback(async () => {
        if (!summaryHexId) return
        setLoadState(LOAD_STATE.LOADING)
        try {
            const resp = await learningsApi.fetchRunRedteamLearnings(summaryHexId)
            setRunLearnings(resp?.runLearnings || null)
            setLearningsEnabled(resp?.learningsEnabled ?? null)
            setLoadState(resp?.runLearnings ? LOAD_STATE.LOADED : LOAD_STATE.UNAVAILABLE)
        } catch (e) {
            setLoadState(loadStateFromError(e))
        }
    }, [summaryHexId])

    useEffect(() => { load() }, [load])

    if (!summaryHexId) return null
    // Most runs are not agentic and learned nothing: no card while loading, and none when the read says there is nothing.
    if (loadState === LOAD_STATE.LOADING) return null
    if (loadState === LOAD_STATE.LOADED && runLearnings?.state !== "READY") return null
    // Account setting off (or unreadable): no card and no banner. A failed learnings read is still shown below.
    if (loadState === LOAD_STATE.LOADED && learningsEnabled !== true) return null

    const removeDraft = (draftId) => setRunLearnings((prev) => ({ ...prev, drafts: (prev?.drafts || []).filter((d) => d.id !== draftId) }))

    return (
        <LegacyCard title="Red-team learnings from this run" sectioned key="redteam-learnings">
            <LoadStateBanner loadState={loadState} onRetry={load} subject="This run's learnings" />
            {loadState === LOAD_STATE.LOADED ? (
                <VerticalStack gap="4">
                    {runLearnings.versionsTruncated ? (
                        <Text color="subdued">This run wrote more versions than are read; only the newest are used below.</Text>
                    ) : null}
                    {runLearnings.agentsTruncated ? (
                        <Text color="subdued">This run tested more agents than are shown; only the most recently updated are listed.</Text>
                    ) : null}
                    {(runLearnings.agentProfiles || []).map((profile) => (
                        <VerticalStack gap="3" key={profile.id}>
                            <ProfileSummary profile={profile} heading={`${profile.method} ${profile.url}`} />
                            <Divider />
                        </VerticalStack>
                    ))}
                    <DraftList drafts={runLearnings.drafts} onDismissed={removeDraft} />
                </VerticalStack>
            ) : null}
        </LegacyCard>
    )
}

export default RunLearningsCard
