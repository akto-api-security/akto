import { useCallback, useEffect, useState } from "react"
import { Divider, LegacyCard, Spinner, Text, VerticalStack } from "@shopify/polaris"
import learningsApi from "./api"
import { AccountSettingBanner, DraftList, LOAD_STATE, LoadStateBanner, ProfileSummary, loadStateFromError } from "./LearningsView"

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
    if (loadState === LOAD_STATE.LOADED && runLearnings?.state !== "READY") return null

    const setDrafts = (drafts) => setRunLearnings((prev) => ({ ...prev, drafts }))

    return (
        <LegacyCard title="Red-team learnings from this run" sectioned key="redteam-learnings">
            {loadState === LOAD_STATE.LOADING ? <Spinner size="small" /> : null}
            <LoadStateBanner loadState={loadState} onRetry={load} subject="This run's learnings" />
            {loadState === LOAD_STATE.LOADED ? (
                <VerticalStack gap="4">
                    <AccountSettingBanner learningsEnabled={learningsEnabled} />
                    {runLearnings.versionsTruncated ? (
                        <Text color="subdued">This run wrote more versions than are shown; the newest are listed.</Text>
                    ) : null}
                    {(runLearnings.agentProfiles || []).map((profile) => (
                        <VerticalStack gap="3" key={profile.id}>
                            <ProfileSummary profile={profile} heading={`${profile.method} ${profile.url}`} />
                            <Divider />
                        </VerticalStack>
                    ))}
                    <DraftList drafts={runLearnings.drafts} onDraftsChange={setDrafts} />
                </VerticalStack>
            ) : null}
        </LegacyCard>
    )
}

export default RunLearningsCard
