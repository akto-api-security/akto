import request from "@/util/request";
import { enrichRow } from "./utils";

function llmMessagesRequest(data) {
    return request({ url: "/api/fetchLLMMessages", method: "post", data });
}

export default {
    // Per-session summaries (grouped by sessionIdentifier).
    fetchSessions(startTime, endTime, filters) {
        return request({
            url: "/api/fetchLLMSessions",
            method: "post",
            data: {
                startTime, endTime,
                userName:  filters?.userName  || "",
                deviceId:  filters?.deviceId  || "",
                serviceId: filters?.serviceId || "",
            },
        }).then(r => Array.isArray(r) ? r : (r?.sessions ?? []));
    },

    // Trace-level aggregations — one bucket per traceId. Optionally scoped to a session.
    // filters: { sessionId }
    fetchMessages(startTime, endTime, filters) {
        return llmMessagesRequest({
            startTime, endTime,
            sessionId: filters?.sessionId || "",
            userName:  filters?.userName  || "",
            deviceId:  filters?.deviceId  || "",
            serviceId: filters?.serviceId || "",
        }).then(r => Array.isArray(r) ? r : (r?.messages ?? []));
    },

    // Paginated flat prompt/span rows for the Messages tab.
    // traceId: when set, scopes results to a single trace (backend filters by traceId.keyword).
    // Returns: { value: [...enrichedRows], total: N }
    searchPrompts({ startTime, endTime, traceId, sortKey, sortOrder, skip, limit, filters, searchAfterJson, searchString }) {
        return request({
            url: "/api/searchLLMPrompts",
            method: "post",
            data: {
                startTime,
                endTime,
                traceId:         traceId       || "",
                sortKey:         sortKey        || "timestamp",
                sortOrder:       sortOrder === -1 ? -1 : 1,
                skip:            skip           || 0,
                limit:           limit          || 20,
                userNames:       filters?.userName  || [],
                serviceIds:      filters?.serviceId || [],
                searchString:    searchString   || "",
                searchAfterJson: searchAfterJson || "",
            },
        }).then(r => {
            if (r && r.prompts) return { value: r.prompts.map(enrichRow), total: r.total };
            if (Array.isArray(r))  return { value: r.map(enrichRow), total: r.length };
            return { value: [], total: 0 };
        });
    },

    // Distinct values for filterable columns (userName, deviceId, serviceId).
    fetchFilterChoices(startTime, endTime) {
        return request({
            url: "/api/fetchLLMPromptFilters",
            method: "post",
            data: { startTime, endTime },
        }).then(r => ({
            userName:  r?.userName  || [],
            deviceId:  r?.deviceId  || [],
            serviceId: r?.serviceId || [],
        }));
    },

    // Spans for a single trace, ordered by timestamp asc.
    fetchTraceDetail(traceId) {
        return request({
            url: "/api/fetchLLMTraceDetail",
            method: "post",
            data: { traceId },
        }).then(r => Array.isArray(r) ? r : (r?.spans ?? []));
    },
};
