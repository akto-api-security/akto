import request from "@/util/request"

// Which dashboard's tiles come back is decided by the x-context-source header request.js adds to
// every call — nothing about the dashboard is sent in the body.
const api = {
    // Recommendations, CRITICAL/HIGH insight tiles and the "what changed" feed in one round trip.
    fetchAskOverlay: async () => request({
        url: '/api/fetchAskOverlay',
        method: 'post',
        data: {}
    }),
    // AI curation of those same tiles ({status, picks: [{tileId, prompt}]}); slower, fetched after
    // the tiles have rendered.
    fetchAskOverlayCuration: async () => request({
        url: '/api/fetchAskOverlayCuration',
        method: 'post',
        data: {}
    }),
}

export default api
