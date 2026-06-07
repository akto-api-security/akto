import func from "@/util/func";

export function parsePromptText(queryPayload) {
    if (!queryPayload) return "";
    try {
        const obj = JSON.parse(queryPayload);
        return obj.prompt || obj.body || obj.message || obj.text || JSON.stringify(obj);
    } catch (_) {
        return queryPayload;
    }
}

export function parseResponseText(responsePayload) {
    if (!responsePayload) return "";
    try {
        const obj = JSON.parse(responsePayload);
        const content = obj.content;
        if (Array.isArray(content) && content.length > 0) {
            return content[0].text || "";
        }
        return obj.body || obj.text || obj.message || "";
    } catch (_) {
        return responsePayload;
    }
}

export function parseTokens(row) {
    let input = row.inputTokens || 0;
    let output = row.outputTokens || 0;
    if (input === 0 && output === 0 && row.responsePayload) {
        try {
            const obj = JSON.parse(row.responsePayload);
            const usage = obj.usage || {};
            input = usage.input_tokens || 0;
            output = usage.output_tokens || 0;
        } catch (_) {}
    }
    return { input, output };
}

export function parseModel(row) {
    if (row.responsePayload) {
        try {
            const obj = JSON.parse(row.responsePayload);
            if (obj.model) return obj.model;
        } catch (_) {}
    }
    return row.model || "";
}

export function formatCost(inputTokens, outputTokens) {
    const cost = (inputTokens * 15 + outputTokens * 75) / 1e9;
    return "$" + cost.toFixed(3);
}

export function truncate(str, len = 80) {
    if (!str) return "";
    return str.length > len ? str.substring(0, len) + "…" : str;
}

// Shared base columns for all message tables (session drill-down + standalone messages view)
const MESSAGE_BASE_COLS = [
    {
        headerName: "Time",
        field: "latestTimestamp",
        width: 160,
        valueFormatter: (p) => func.prettifyEpoch(Math.floor((p.value || 0) / 1000)),
        sort: "desc",
    },
    {
        headerName: "Message",
        field: "_promptText",
        flex: 1,
        valueFormatter: (p) => truncate(p.value || "", 110),
    },
    {
        headerName: "Spans",
        field: "spanCount",
        width: 90,
    },
];

// Used in SessionsView (per-session drill-down): combined token count
export const MESSAGE_COLUMN_DEFS = [
    ...MESSAGE_BASE_COLS,
    {
        headerName: "Tokens",
        field: "totalTokens",
        width: 110,
    },
];

// Used in MessagesView (standalone): split token counts + filter/sort support
export const MESSAGE_COLUMN_DEFS_DETAIL = [
    ...MESSAGE_BASE_COLS,
    {
        headerName: "Tokens in",
        field: "_inputTokens",
        width: 110,
        filterAllowed: true,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    {
        headerName: "Tokens out",
        field: "_outputTokens",
        width: 110,
        filterAllowed: false,
        filter: false,
        sortable: false,
    },
];

// filterAllowed: true  — this column gets a set filter populated from fetchFilterChoices()
// filterAllowed: false — no filter on this column
export const PROMPT_COLUMN_DEFS = [
    {
        headerName: "Time",
        field: "timestamp",
        width: 155,
        filterAllowed: false,
        sortable: true,
        filter: false,
        valueFormatter: (p) => func.prettifyEpoch(Math.floor((p.value || 0) / 1000)),
        sort: "desc",
    },
    {
        headerName: "User",
        field: "userName",
        width: 120,
        filterAllowed: true,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    {
        headerName: "Service",
        field: "serviceId",
        width: 140,
        filterAllowed: true,
        filter: "agSetColumnFilter",
        sortable: true,
    },
    {
        headerName: "Prompt",
        field: "_promptText",
        flex: 1,
        filterAllowed: false,
        filter: false,
        sortable: false,
        valueFormatter: (p) => truncate(p.value || "", 100),
    },
    {
        headerName: "Tokens in/out",
        field: "_tokens",
        width: 115,
        filterAllowed: false,
        filter: false,
        sortable: false,
    },
];

export const SPAN_KIND_TONE = {
    agent: "info",
    llm: "success",
    tool: "warning",
    mcp_server: "new",
    workflow: "attention",
};

export const SPAN_KIND_LABEL = {
    agent: "Agent",
    llm: "LLM",
    tool: "Tool",
    mcp_server: "MCP",
    workflow: "Workflow",
    function: "Function",
    http: "HTTP",
    api: "API",
};
