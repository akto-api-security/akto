export function parsePromptText(queryPayload) {
    if (!queryPayload) return "";
    try {
        const obj = JSON.parse(queryPayload);
        // body is a plain string — use it directly
        if (typeof obj.body === "string") return obj.body;
        // body is an object — check for tool call shape
        if (obj.body && typeof obj.body === "object") {
            const toolName = obj.toolName || obj.body.toolName;
            const toolArgs = obj.body.toolArgs || obj.body.arguments || obj.body;
            if (toolName) {
                return `[${toolName}] ${JSON.stringify(toolArgs)}`;
            }
            return JSON.stringify(obj.body);
        }
        // top-level toolName (toolArgs at root)
        if (obj.toolName) {
            const toolArgs = obj.toolArgs || obj.params || obj.arguments || {};
            return `[${obj.toolName}] ${JSON.stringify(toolArgs)}`;
        }
        return obj.prompt || obj.message || obj.text || JSON.stringify(obj);
    } catch (_) {
        return queryPayload;
    }
}

export function parseResponseText(responsePayload) {
    if (!responsePayload) return "";
    try {
        const obj = JSON.parse(responsePayload);
        // Empty object — nothing to show
        if (obj && typeof obj === "object" && Object.keys(obj).length === 0) return "";
        // body.result — tool call result
        if (obj.body && obj.body.result) {
            const result = obj.body.result;
            // file read result
            if (result.file && result.file.content) return result.file.content;
            // bash/command stdout
            if (result.stdout !== undefined) return result.stdout || result.stderr || "";
            // generic text output
            if (result.output) {
                if (Array.isArray(result.output)) {
                    return result.output.map(o => (typeof o === "object" ? o.text || JSON.stringify(o) : String(o))).join("\n");
                }
                return String(result.output);
            }
            return JSON.stringify(result);
        }
        // body is a plain string
        if (typeof obj.body === "string") return obj.body;
        // body is a non-result object
        if (obj.body && typeof obj.body === "object") return JSON.stringify(obj.body);
        // Standard shapes
        const content = obj.content;
        if (Array.isArray(content) && content.length > 0) {
            return content[0].text || "";
        }
        return obj.text || obj.message || "";
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

// Human-readable duration from a millisecond span.
export function formatDurationMs(ms) {
    const n = Number(ms) || 0;
    if (n <= 0) return "-";
    if (n >= 3600000) return (n / 3600000).toFixed(1).replace(/\.0$/, "") + " h";
    if (n >= 60000) return (n / 60000).toFixed(1).replace(/\.0$/, "") + " m";
    if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, "") + " s";
    return n + " ms";
}

// Compact token / count formatting (e.g. 356678 → 356.7k).
export function formatCompact(n) {
    const v = Number(n) || 0;
    if (v >= 1e9) return (v / 1e9).toFixed(1).replace(/\.0$/, "") + "B";
    if (v >= 1e6) return (v / 1e6).toFixed(1).replace(/\.0$/, "") + "M";
    if (v >= 1e3) return (v / 1e3).toFixed(1).replace(/\.0$/, "") + "k";
    return String(v);
}

// Polaris Text `color` token for a latency/duration magnitude.
export function latencyColor(ms) {
    const n = Number(ms) || 0;
    if (n >= 60000) return "critical";
    if (n >= 20000) return "warning";
    return undefined; // default text color
}


// AG Grid column definitions for the LLM tables live in columns.jsx (they reference
// React cell renderers). This file keeps only payload parsing + formatting helpers
// and the span-kind maps used by SpansPanel.

// Span type → short label for the message-preview badge.
export const SPAN_TYPE_LABEL = {
    llm: "LLM", tool: "Tool", bash: "Bash", read: "Read", edit: "Edit",
    search: "Search", mcp: "MCP", retrieval: "Retrieval", guardrail: "Guardrail", default: "Span",
};

// Classify a span (enriched row) into one of the SPAN_TYPE_* keys from its parsed text.
export function classifySpan(span) {
    const text = (span && (span._promptText || "")) + "";
    const m = text.match(/^\[(\w+)\]/); // tool spans render as "[Bash] {...}"
    if (m) {
        const tool = m[1].toLowerCase();
        if (tool.includes("bash")) return "bash";
        if (tool.includes("read")) return "read";
        if (tool.includes("edit") || tool.includes("write")) return "edit";
        if (tool.includes("glob") || tool.includes("grep") || tool.includes("search")) return "search";
        if (tool.includes("mcp")) return "mcp";
        return "tool";
    }
    if (span && (span._inputTokens || span._outputTokens)) return "llm";
    return "default";
}

// Duration → colour ramp (fast → slow). Green for quick spans, through amber, to red
// for the slowest. `maxMs` is the slowest span in the trace, so the ramp is relative.
const DURATION_RAMP = ["#3BAFA4", "#5FB85B", "#EAB308", "#F59E0B", "#F97316", "#DC2626"];
export function durationColor(ms, maxMs) {
    const max = Number(maxMs) || 1;
    const frac = Math.min(1, Math.max(0, (Number(ms) || 0) / max));
    const idx = Math.min(DURATION_RAMP.length - 1, Math.floor(frac * DURATION_RAMP.length));
    return DURATION_RAMP[idx];
}

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
