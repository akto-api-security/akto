import request from "@/util/request";
import { enrichRow } from "./utils";
import { parsePromptText, parseResponseText } from "./constants";

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

    // Simple (non-paginated) message fetch for session drill-down.
    // filters: { sessionId, userName, deviceId, serviceId }
    fetchMessages(startTime, endTime, filters) {
        return llmMessagesRequest({
            startTime, endTime,
            sessionId: filters?.sessionId || "",
            userName:  filters?.userName  || "",
            deviceId:  filters?.deviceId  || "",
            serviceId: filters?.serviceId || "",
        }).then(r => Array.isArray(r) ? r : (r?.messages ?? []));
    },

    // Paginated, sorted, filtered messages table (MessagesView).
    // Fetches 500 per backend call; AgGrid pages within that window.
    // Returns: { value: [...rows], total: N }
    searchMessages({ startTime, endTime, sortKey, sortOrder, skip, filters, searchAfterJson, searchString }) {
        return llmMessagesRequest({
            startTime,
            endTime,
            sortKey:         sortKey   || "latestTimestamp",
            sortOrder:       sortOrder === -1 ? -1 : 1,
            skip:            skip      || 0,
            limit:           500,
            userNames:       filters?.userName  || [],
            serviceIds:      filters?.serviceId || [],
            searchString:    searchString || "",
            searchAfterJson: searchAfterJson || "",
        }).then(r => {
            if (r && r.messages) return { value: r.messages.map(enrichRow), total: r.total };
            if (Array.isArray(r))  return { value: r.map(enrichRow), total: r.length };
            return { value: [], total: 0 };
        });
    },

    // Paginated, sorted, filtered flat prompt table (PromptsView).
    // Returns: { value: [...rows], total: N }
    searchPrompts({ startTime, endTime, sortKey, sortOrder, skip, limit, filters, searchAfterJson, searchString }) {
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
                userNames:       filters?.userName  || [],
                serviceIds:      filters?.serviceId || [],
                searchString:    searchString || "",
                searchAfterJson: searchAfterJson || "",
            },
        }).then(r => {
            if (r && r.prompts) return { value: r.prompts.map(enrichForTable), total: r.total };
            if (Array.isArray(r))  return { value: r.map(enrichForTable), total: r.length };
            return { value: [], total: 0 };
        });
    },

    // Distinct values for filterable columns (userName, deviceId, serviceId).
    // Shared by PromptsView and MessagesView.
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

export function enrichForTable(row) {
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
        _promptText:   parsePromptText(row.queryPayload),
        _responseText: parseResponseText(row.responsePayload),
        _model:        model,
        _inputTokens:  inputTokens,
        _outputTokens: outputTokens,
        _tokens:       inputTokens + " / " + outputTokens,
    };
}
