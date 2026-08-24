import request from "@/util/request";

// Atlas Discovery insights — thin wrappers over InsightsAction. fetchInsightsList's root
// is "insights" (an array) and fetchInsightDetail/refreshInsightNarrative's root is
// "insight" (a single object), per struts.xml — so `request()` already resolves to the
// exact shape, no unwrapping needed.
export default {
    async fetchInsightsList({ startTimestamp, endTimestamp } = {}) {
        const insights = await request({
            url: '/api/fetchInsightsList',
            method: 'post',
            data: { startTimestamp, endTimestamp },
        });
        return Array.isArray(insights) ? insights : [];
    },

    async fetchInsightDetail({ insightId, startTimestamp, endTimestamp } = {}) {
        return await request({
            url: '/api/fetchInsightDetail',
            method: 'post',
            data: { insightId, startTimestamp, endTimestamp },
        });
    },

    async refreshInsightNarrative({ insightId, startTimestamp, endTimestamp } = {}) {
        return await request({
            url: '/api/refreshInsightNarrative',
            method: 'post',
            data: { insightId, startTimestamp, endTimestamp },
        });
    },
};
