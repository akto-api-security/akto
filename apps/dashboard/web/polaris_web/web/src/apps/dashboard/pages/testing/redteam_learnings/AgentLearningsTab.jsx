import { useCallback, useEffect, useState } from "react"
import { Box, DataTable, Divider, Spinner, Text, VerticalStack } from "@shopify/polaris"
import func from "@/util/func"
import learningsApi from "./api"
import { AccountSettingBanner, DraftList, LOAD_STATE, LoadStateBanner, ProfileSummary, loadStateFromError } from "./LearningsView"

function historyRows(versions) {
    return (versions || []).map((v) => {
        const totals = v.totals || {}
        return [
            v.createdAtMs ? func.prettifyEpoch(Math.floor(v.createdAtMs / 1000)) : "—",
            v.testingRunResultSummaryId,
            v.finalVersion ? "final" : `batch ${v.batchNo}`,
            totals.judged || 0,
            totals.critical || 0,
        ]
    })
}

/** Everything learned about one agent across runs: the latest profile, its history and the open template drafts. */
function AgentLearningsTab({ apiCollectionId, url, method }) {
    const [loadState, setLoadState] = useState(LOAD_STATE.LOADING)
    const [agentLearnings, setAgentLearnings] = useState(null)
    const [learningsEnabled, setLearningsEnabled] = useState(null)

    const load = useCallback(async () => {
        setLoadState(LOAD_STATE.LOADING)
        try {
            const resp = await learningsApi.fetchAgentRedteamLearnings(apiCollectionId, url, method)
            setAgentLearnings(resp?.agentLearnings || null)
            setLearningsEnabled(resp?.learningsEnabled ?? null)
            setLoadState(resp?.agentLearnings ? LOAD_STATE.LOADED : LOAD_STATE.UNAVAILABLE)
        } catch (e) {
            setLoadState(loadStateFromError(e))
        }
    }, [apiCollectionId, url, method])

    useEffect(() => { load() }, [load])

    const setDrafts = (openDrafts) => setAgentLearnings((prev) => ({ ...prev, openDrafts }))

    return (
        <Box paddingBlockStart="4">
            {loadState === LOAD_STATE.LOADING ? <Spinner size="small" /> : null}
            <LoadStateBanner loadState={loadState} onRetry={load} subject="This agent's learnings" />
            {loadState === LOAD_STATE.LOADED ? (
                <VerticalStack gap="4">
                    <AccountSettingBanner learningsEnabled={learningsEnabled} />
                    {agentLearnings.state !== "READY" ? (
                        <Text color="subdued">Nothing has been learned about this agent yet. Learnings build up as agentic tests run against it.</Text>
                    ) : (
                        <>
                            <ProfileSummary profile={agentLearnings.latestProfile} heading="Latest profile" />
                            <Divider />
                            <VerticalStack gap="2">
                                <Text variant="headingSm">{`History (newest ${agentLearnings.versions?.length || 0})`}</Text>
                                <DataTable
                                    columnContentTypes={["text", "text", "text", "numeric", "numeric"]}
                                    headings={["Updated", "Run", "Version", "Judged", "Got through"]}
                                    rows={historyRows(agentLearnings.versions)}
                                    truncate
                                />
                            </VerticalStack>
                        </>
                    )}
                    <DraftList drafts={agentLearnings.openDrafts} onDraftsChange={setDrafts} />
                </VerticalStack>
            ) : null}
        </Box>
    )
}

export default AgentLearningsTab
