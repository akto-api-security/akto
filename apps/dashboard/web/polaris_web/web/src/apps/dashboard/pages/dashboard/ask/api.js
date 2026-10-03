import request from "@/util/request"

// One round trip for the whole overlay landing state — recommendations (always cheap), the
// CRITICAL/HIGH insight tiles, and the on-the-fly "what changed" feed. `domain` picks which
// dashboard's tile set + default insight groups come back — "API" | "AGENTIC" | "ENDPOINT" — see
// com.akto.action.AskOverlayAction#fetchAskOverlay on the dashboard side.
const api = {
    fetchAskOverlay: async (domain) => request({
        url: '/api/fetchAskOverlay',
        method: 'post',
        data: { domain }
    }),
}

export default api
