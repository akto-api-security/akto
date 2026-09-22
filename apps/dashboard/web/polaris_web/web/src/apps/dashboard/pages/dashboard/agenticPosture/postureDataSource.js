import postureSummaryMock from './mockData/postureSummary.mock.json'
import agentDetailMock from './mockData/agentDetail.mock.json'

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

// startTimestamp/endTimestamp are accepted (and ignored) now so call sites don't need to change
// shape once this starts forwarding to dashboardApi.fetchPostureSummary(...).
async function fetchPostureSummary(startTimestamp, endTimestamp) {
    return delay(postureSummaryMock)
}

async function fetchAgentDetail(groupKey, startTimestamp, endTimestamp) {
    return delay(agentDetailMock[groupKey] || null)
}

export default { fetchPostureSummary, fetchAgentDetail }
