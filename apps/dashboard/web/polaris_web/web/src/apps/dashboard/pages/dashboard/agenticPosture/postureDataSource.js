import postureSummaryMock from './mockData/postureSummary.mock.json'
import agentDetailMock from './mockData/agentDetail.mock.json'
import dashboardApi from '../api'

// The single seam between the posture pages and their data. Every export here resolves to
// exactly the shape the posture actions will eventually return (see the
// posture plan's backend section) — so wiring up a real phase later means changing this
// file's internals only, never the page/section components, and can be done one field at a time:
// merge a real API response's populated keys over the mock object's remaining keys so one section
// goes live while the rest still reads from mock.
const MOCK_DELAY_MS = 250

function delay(value) {
    return new Promise((resolve) => setTimeout(() => resolve(value), MOCK_DELAY_MS))
}

// Posture Summary (environments + kpis) is live; every other section still reads mock. The real
// response's keys win, so a section goes live the moment its endpoint starts returning its key.
async function fetchPostureSummary(startTimestamp, endTimestamp, environment) {
    try {
        const resp = await dashboardApi.fetchArgusPostureSummary(startTimestamp, endTimestamp, environment)
        return { ...postureSummaryMock, ...(resp || {}) }
    } catch (error) {
        console.error('fetchArgusPostureSummary failed, falling back to mock:', error)
        return delay(postureSummaryMock)
    }
}

async function fetchAgentDetail(groupKey, startTimestamp, endTimestamp) {
    return delay(agentDetailMock[groupKey] || null)
}

export default { fetchPostureSummary, fetchAgentDetail }
