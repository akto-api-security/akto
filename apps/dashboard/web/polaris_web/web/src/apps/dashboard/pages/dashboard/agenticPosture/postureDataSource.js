import postureSummaryMock from './mockData/postureSummary.mock.json'
import agentDetailMock from './mockData/agentDetail.mock.json'
import dashboardApi from '../api'

// Real API keys override mock; sections the backend doesn't return yet still read from mock.
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
        ...postureSummaryMock,
        ...summary,
        postureScore: summary?.postureScore || POSTURE_SCORE_FETCH_ERROR,
        highestRiskAgents: summary?.highestRiskAgents || [],
        changesThisWeek: changes?.rows || [],
    }
}

async function fetchAgentDetail(groupKey, startTimestamp, endTimestamp) {
    return delay(agentDetailMock[groupKey] || null)
}

export default { fetchPostureSummary, fetchAgentDetail }
