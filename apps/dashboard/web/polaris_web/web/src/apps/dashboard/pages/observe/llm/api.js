import request from "@/util/request";

export default {
    // Per-session summaries (grouped by sessionIdentifier).
    fetchSessions(startTime, endTime) {
        return request({
            url: "/api/fetchLLMSessions",
            method: "post",
            data: { startTime, endTime },
        }).then(r => Array.isArray(r) ? r : (r?.sessions ?? []));
    },

    // Per-message summaries (grouped by traceId). Optionally scoped to a session.
    fetchMessages(startTime, endTime, sessionId) {
        return request({
            url: "/api/fetchLLMMessages",
            method: "post",
            data: { startTime, endTime, sessionId: sessionId || "" },
        }).then(r => Array.isArray(r) ? r : (r?.messages ?? []));
    },

    // Paginated, sorted, filtered flat prompt table.
    // filters: { userName: ["alice"], serviceId: ["svc-a"] }
    // Returns: { value: [...rows], total: N }
    searchPrompts({ startTime, endTime, sortKey, sortOrder, skip, limit, filters, searchAfterJson }) {
        return request({
            url: "/api/searchLLMPrompts",
            method: "post",
            data: {
                startTime,
                endTime,
                sortKey:         sortKey   || "timestamp",
                sortOrder:       sortOrder === -1 ? -1 : 1,
                skip:            skip      || 0,
                limit:           limit     || 20,
                userName:        (filters?.userName?.[0])  || "",
                serviceId:       (filters?.serviceId?.[0]) || "",
                searchString:    filters?.searchString?.[0] || "",
                searchAfterJson: searchAfterJson || "",
            },
        }).then(r => {
            if (r && r.prompts) return { value: r.prompts.map(enrichForTable), total: r.total };
            if (Array.isArray(r)) return { value: r.map(enrichForTable), total: r.length };
            return { value: [], total: 0 };
        });
    },

    // Distinct values for filterable columns (userName, serviceId), from a backend aggregation.
    fetchFilterChoices(startTime, endTime) {
        return request({
            url: "/api/fetchLLMPromptFilters",
            method: "post",
            data: { startTime, endTime },
        }).then(r => ({
            userName:  r?.userName  || [],
            serviceId: r?.serviceId || [],
        }));
    },

    // Spans (docs) for a single message/trace, ordered by timestamp asc.
    fetchTraceDetail(traceId) {
        return request({
            url: "/api/fetchLLMTraceDetail",
            method: "post",
            data: { traceId },
        }).then(r => Array.isArray(r) ? r : (r?.spans ?? []));
    },
};

export function enrichForTable(row) {
    let promptText = row.queryPayload || "";
    try {
        const obj = JSON.parse(row.queryPayload);
        promptText = obj.prompt || obj.body || obj.message || obj.text || promptText;
    } catch (_) {}

    let model = row.model || "";
    let inputTokens  = row.inputTokens  || 0;
    let outputTokens = row.outputTokens || 0;
    if (!model || (!inputTokens && !outputTokens)) {
        try {
            const resp = JSON.parse(row.responsePayload);
            model        = model        || resp.model                  || "";
            inputTokens  = inputTokens  || resp.usage?.input_tokens    || 0;
            outputTokens = outputTokens || resp.usage?.output_tokens   || 0;
        } catch (_) {}
    }

    return {
        ...row,
        id:            row.docId || row.id,
        _promptText:   promptText,
        _model:        model,
        _inputTokens:  inputTokens,
        _outputTokens: outputTokens,
        _tokens:       inputTokens + " / " + outputTokens,
    };
}
