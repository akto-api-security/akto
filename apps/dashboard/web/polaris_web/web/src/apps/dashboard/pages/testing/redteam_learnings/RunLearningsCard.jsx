import { useCallback, useEffect, useState } from "react"
import { Divider, LegacyCard, Text, VerticalStack } from "@shopify/polaris"
import learningsApi from "./api"
import { DraftList, LOAD_STATE, ProfileSummary, loadStateFromError } from "./LearningsView"

/**
 * What one run learned about the agents it tested. Renders nothing unless the account has red-team learnings on and
 * the run learned something.
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
    // Nothing at all unless the account has red-team learnings on and this run learned something: no card while loading,
    // none for a run that learned nothing (most runs are not agentic), and none when the setting is off. The setting
    // arrives with the read, so a failed read cannot tell on from off and hides the card like an off account does
    // (same as the Learnings tab in ApiDetails.jsx).
    if (loadState !== LOAD_STATE.LOADED || learningsEnabled !== true || runLearnings?.state !== "READY") return null

    const removeDraft = (draftId) => setRunLearnings((prev) => ({ ...prev, drafts: (prev?.drafts || []).filter((d) => d.id !== draftId) }))

    return (
        <LegacyCard title="Red-team learnings from this run" sectioned key="redteam-learnings">
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
        </LegacyCard>
    )
}

export default RunLearningsCard
