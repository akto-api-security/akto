import postureSummaryMock from './mockData/postureSummary.mock.json'
import dashboardApi from '../api'

// Only sections rendered under a "Coming soon" overlay use sample data; real sections never fall back to it.
const MOCK_DELAY_MS = 250

function delay(value) {
    return new Promise((resolve) => setTimeout(() => resolve(value), MOCK_DELAY_MS))
}

// Posture score never falls back to mock; a failed/missing response renders as a data gap.
const POSTURE_SCORE_FETCH_ERROR = {
    value: null,
    agentsScored: 0,
    agentsWithNoSignal: 0,
    dataGaps: [{ source: 'ARGUS_POSTURE_API', reason: 'REQUEST_FAILED', impact: 'Could not load the posture score right now.' }],
}

// Changes is a separate call (cached server-side for a day) so it never slows the summary; neither falls back to mock.
async function fetchPostureSummary(startTimestamp, endTimestamp, environment) {
    const env = environment || 'all'
    const [summary, changes] = await Promise.all([
        dashboardApi.fetchArgusPostureSummary(startTimestamp, endTimestamp, env).catch((error) => { console.error('fetchArgusPostureSummary failed:', error); return null }),
        dashboardApi.fetchArgusPostureChanges(startTimestamp, endTimestamp, env).catch((error) => { console.error('fetchArgusPostureChanges failed:', error); return null }),
    ])
    return {
        dangerousPaths: postureSummaryMock.dangerousPaths,
        coverageGovernance: postureSummaryMock.coverageGovernance,
        ...summary,
        environments: summary?.environments || [],
        kpis: summary?.kpis || [],
        postureScore: summary?.postureScore || POSTURE_SCORE_FETCH_ERROR,
        highestRiskAgents: summary?.highestRiskAgents || [],
        changesThisWeek: changes?.rows || [],
    }
}

// No mock fallback: a page of invented values is worse than an empty state.
async function fetchAgentDetail(collectionId, finding) {
    try {
        return await dashboardApi.fetchArgusAgentDetail(collectionId, finding)
    } catch (error) {
        console.error('fetchArgusAgentDetail failed:', error)
        return null
    }
}

// The 5 Argus posture insight cards (red-team breakdown/hotspot, guardrail breakdown/hotspot,
// observability) — fast, Java-only data, no LLM call. No mock fallback: a card with no real
// numbers behind it would be actively misleading, so a failure just means no cards render.
async function fetchInsightCards(startTimestamp, endTimestamp) {
    try {
        const cards = await dashboardApi.fetchArgusPostureInsights(startTimestamp, endTimestamp)
        return Array.isArray(cards) ? cards : []
    } catch (error) {
        console.error('fetchArgusPostureInsights failed:', error)
        return []
    }
}

// Deliberately separate from fetchInsightCards: this one triggers a real LLM call per card
// (in parallel server-side) on a cache miss, so the caller must fetch it after the cards have
// already rendered, never await it before first paint. Returns {cardId: {summary,impact,
// recommendation}} for every card except ATTACK_FLOW_ANALYSIS, which returns {flows:[...]}.
async function fetchInsightCardSummaries(startTimestamp, endTimestamp) {
    try {
        const summaries = await dashboardApi.fetchArgusPostureInsightSummaries(startTimestamp, endTimestamp)
        return summaries && typeof summaries === 'object' ? summaries : {}
    } catch (error) {
        console.error('fetchArgusPostureInsightSummaries failed:', error)
        return {}
    }
}

// One insight card's drilldown flyout — a thin passthrough (no mock fallback, same reasoning as
// fetchInsightCards: a drill with no real rows behind it would be actively misleading). Signature
// matches dashboardApi.fetchPostureDrill's own shape 1:1 so PostureDrillFlyout.jsx (built for that
// endpoint) can be reused verbatim by just swapping which fetch function it's given.
async function fetchInsightCardDrill(drillId, path, startTimestamp, endTimestamp, environment, skip, limit) {
    return await dashboardApi.fetchArgusPostureDrill(drillId, path, startTimestamp, endTimestamp, environment, skip, limit)
}

export default { fetchPostureSummary, fetchAgentDetail, fetchInsightCards, fetchInsightCardSummaries, fetchInsightCardDrill }
