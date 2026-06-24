import request from "@/util/request";
import { enrichRow } from "./utils";

function llmMessagesRequest(data) {
    return request({ url: "/api/fetchLLMMessages", method: "post", data });
}

export default {
    // Per-session summaries (terms agg, top-500) — used by summary cards.
    fetchSessions(startTime, endTime, filters) {
        return request({
            url: "/api/fetchLLMSessions",
            method: "post",
            data: {
                startTime, endTime,
                sessionsLimit: 0,
                userName:  filters?.userName  || "",
                deviceId:  filters?.deviceId  || "",
                serviceId: filters?.serviceId || "",
            },
        }).then(r => r?.sessions ?? []);
    },

    // Cursor-paginated sessions (composite agg) — used by the sessions table.
    // Returns { sessions, nextAfterKey, total }.
    fetchSessionsPaged({ startTime, endTime, limit, afterKey, filters, searchString }) {
        return request({
            url: "/api/fetchLLMSessions",
            method: "post",
            data: {
                startTime,
                endTime,
                sessionsLimit:    limit    || 20,
                sessionsAfterKey: afterKey || "",
                userNames:  filters?.userName  || [],
                serviceIds: filters?.serviceId || [],
                searchString: searchString.length >= 3 ? searchString: ""
            },
        }).then(r => ({
            sessions:     (r?.sessions ?? []).map(enrichRow),
            nextAfterKey: r?.nextAfterKey || null,
            total:        r?.totalSessions || 0,
        }));
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
    // sessionId: when set, scopes results to a single session (backend filters by sessionIdentifier.keyword).
    // Returns: { value: [...enrichedRows], total: N }
    searchPrompts({ startTime, endTime, traceId, sessionId, sortKey, sortOrder, skip, limit, filters, searchAfterJson, searchString }) {
        return request({
            url: "/api/searchLLMPrompts",
            method: "post",
            data: {
                startTime,
                endTime,
                traceId:         traceId       || "",
                sessionId:       sessionId     || "",
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

    // Aggregated stats for summary cards — accurate cardinality, token sums, top users.
    // Returns { totalSessions, totalInputTokens, totalOutputTokens, topUsers, userBreakdown }.
    fetchSessionStats(startTime, endTime) {
        return request({
            url: "/api/fetchLLMSessionStats",
            method: "post",
            data: { startTime, endTime },
        }).then(r => ({
            totalSessions:     r?.aggTotalSessions    || 0,
            totalInputTokens:  r?.aggInputTokens      || 0,
            totalOutputTokens: r?.aggOutputTokens     || 0,
            topUsers:          r?.aggTopUsers         || [],
            userBreakdown:     r?.aggUserBreakdown    || [],
            sessionSpark:      r?.aggSessionSpark?.length  ? r.aggSessionSpark  : [0],
            sessionSparkTs:    r?.aggSessionSparkTs        || [],
        }));
    },

    // Aggregated stats for the Argus top-cards — total span count (matches table footer),
    // token sums, top apps, top traces, and pre-bucketed sparkline arrays.
    fetchArgusStats(startTime, endTime) {
        return request({
            url: "/api/fetchArgusStats",
            method: "post",
            data: { startTime, endTime },
        }).then(r => ({
            totalSpans:        r?.aggTotalSpans        || 0,
            totalInputTokens:  r?.aggInputTokens       || 0,
            totalOutputTokens: r?.aggOutputTokens      || 0,
            topApps:           r?.aggTopApps           || [],
            appBreakdown:      r?.aggAppBreakdown      || [],
            topTraces:         (r?.aggTopTraces        || []).map(enrichRow),
            traceSpark:        r?.aggTraceSpark?.length  ? r.aggTraceSpark  : [0],
            tokenSpark:        r?.aggTokenSpark?.length  ? r.aggTokenSpark  : [0],
            traceSparkTs:      r?.aggTraceSparkTs       || [],
        }));
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
