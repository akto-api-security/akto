import request from "@/util/request"

// Reads opt out of the global 403 toast: the panels show "no access" themselves.
export default {
    fetchAgentRedteamLearnings(apiCollectionId, url, method) {
        return request({
            url: '/api/fetchAgentRedteamLearnings',
            method: 'post',
            data: { apiCollectionId, url, method },
            suppress403Toast: true,
        })
    },
    fetchRunRedteamLearnings(testingRunResultSummaryId) {
        return request({
            url: '/api/fetchRunRedteamLearnings',
            method: 'post',
            data: { testingRunResultSummaryId },
            suppress403Toast: true,
        })
    },
    updateRedteamDraftStatus(draftId, status, savedTemplateId) {
        return request({
            url: '/api/updateRedteamDraftStatus',
            method: 'post',
            data: { draftId, status, ...(savedTemplateId ? { savedTemplateId } : {}) },
        })
    },
}
