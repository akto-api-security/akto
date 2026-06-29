export const TOKEN_ESTIMATE_TOOLTIP = "Approximate token count.";

// Converts a currDateRange object (from the date range reducer) to epoch seconds.
// Centralised here to avoid the same Math.floor/Date.parse expression in every view.
export function getEpochsFromRange(currDateRange) {
    return {
        since: Math.floor(Date.parse(currDateRange.period.since) / 1000),
        until: Math.floor(Date.parse(currDateRange.period.until) / 1000),
    };
}

// ─── Content normalization ────────────────────────────────────────────────────
// Single helper that converts ANY LLM content value to a plain string so
// ChatMessage / ConversationHistory never receive an object.  Handles:
//   • plain string
//   • array of {text} / {type, text} blocks  (Anthropic, Bedrock, multi-modal)
//   • tool_use / function_call blocks
//   • anything else → JSON.stringify
function extractContentText(content) {
    if (typeof content === "string") return content;
    if (Array.isArray(content)) {
        return content.map(c => {
            if (typeof c === "string") return c;
            if (typeof c.text === "string") return c.text;
            // Tool-call / tool_use blocks
            const name = c.name || c.function?.name;
            const args = c.input ?? c.function?.arguments ?? c.arguments ?? {};
            if (c.type === "tool_use" || c.type === "tool_calls" || name) {
                return `[${name || "tool"}] ${typeof args === "string" ? args : JSON.stringify(args)}`;
            }
            return JSON.stringify(c);
        }).filter(Boolean).join("\n");
    }
    if (content !== null && typeof content === "object") return JSON.stringify(content);
    return String(content ?? "");
}

// Returns the first array-valued messages field found across common provider keys.
function findMessagesArray(obj) {
    return (Array.isArray(obj.messages)       ? obj.messages       : null)
        || (Array.isArray(obj.input)           ? obj.input           : null) // OpenAI Responses API
        || (Array.isArray(obj.body?.messages)  ? obj.body.messages  : null)
        || (Array.isArray(obj.body?.input)     ? obj.body.input     : null)
        || null;
}

export function parsePromptText(queryPayload) {
    if (!queryPayload) return "";
    try {
        const obj = JSON.parse(queryPayload);
        // Any provider that uses a messages/input array (OpenAI, Anthropic, Bedrock, n8n, …)
        const msgs = findMessagesArray(obj);
        if (msgs?.length) {
            const userMsg = [...msgs].reverse().find(m => m.role === "user");
            if (userMsg) return extractContentText(userMsg.content);
            const last = msgs[msgs.length - 1];
            if (last?.content !== undefined) return extractContentText(last.content);
        }
        // Tool call shape
        const toolName = obj.toolName || obj.body?.toolName;
        if (toolName) {
            const args = obj.toolArgs ?? obj.params ?? obj.body?.toolArgs ?? obj.body?.arguments ?? obj.body ?? {};
            return `[${toolName}] ${typeof args === "string" ? args : JSON.stringify(args)}`;
        }
        // Single-message object: { role, content }
        if (obj.role && obj.content !== undefined) return extractContentText(obj.content);
        // Plain scalar fields — string-typed only to avoid returning objects
        if (typeof obj.prompt  === "string") return obj.prompt;
        if (typeof obj.message === "string") return obj.message;
        if (typeof obj.text    === "string") return obj.text;
        if (typeof obj.body    === "string") return obj.body;
        // Final fallback — always a string
        return JSON.stringify(obj, null, 2);
    } catch (_) {
        return String(queryPayload);
    }
}

export function parseResponseText(responsePayload) {
    if (!responsePayload) return "";
    try {
        const obj = JSON.parse(responsePayload);
        if (!obj || (typeof obj === "object" && Object.keys(obj).length === 0)) return "";
        // OpenAI Chat Completions: { choices: [{ message }] }
        if (Array.isArray(obj.choices) && obj.choices.length) {
            const msg = obj.choices[0]?.message;
            if (msg) {
                if (msg.tool_calls?.length) {
                    return msg.tool_calls
                        .map(tc => `[${tc.function?.name}] ${tc.function?.arguments || ""}`)
                        .join("\n");
                }
                return extractContentText(msg.content);
            }
        }
        // OpenAI Responses API: { output: [{ type: "message", content: [...] }] }
        if (Array.isArray(obj.output)) {
            for (const item of obj.output) {
                if (item.type === "message" && item.content !== undefined) {
                    const text = extractContentText(item.content);
                    if (text) return text;
                }
            }
        }
        // AWS Bedrock / providers wrapping: { output: { message: { content } } }
        if (obj.output?.message?.content !== undefined) {
            return extractContentText(obj.output.message.content);
        }
        // Anthropic Messages API direct: { content: [...] }
        if (Array.isArray(obj.content)) {
            return extractContentText(obj.content);
        }
        // body.result — tool execution results (Argus agent spans)
        if (obj.body?.result) {
            const r = obj.body.result;
            if (r.file?.content) return r.file.content;
            if (r.stdout !== undefined) return r.stdout || r.stderr || "";
            if (r.output !== undefined) {
                return Array.isArray(r.output)
                    ? r.output.map(o => (typeof o === "object" ? o.text || JSON.stringify(o) : String(o))).join("\n")
                    : String(r.output);
            }
            return JSON.stringify(r);
        }
        if (typeof obj.body === "string") return obj.body;
        if (obj.body && typeof obj.body === "object") return JSON.stringify(obj.body);
        // Plain scalar fields — string-typed only
        if (typeof obj.text    === "string") return obj.text;
        if (typeof obj.message === "string") return obj.message;
        // Final fallback — always a string
        return JSON.stringify(obj, null, 2);
    } catch (_) {
        return String(responsePayload);
    }
}

export function parseTokens(row) {
    let input = row.inputTokens || 0;
    let output = row.outputTokens || 0;
    if (input === 0 && output === 0 && row.responsePayload) {
        try {
            const obj = JSON.parse(row.responsePayload);
            const usage = obj.usage || {};
            // Support both Anthropic (input_tokens) and OpenAI (prompt_tokens) formats
            input = usage.input_tokens || usage.prompt_tokens || 0;
            output = usage.output_tokens || usage.completion_tokens || 0;
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


export function truncate(str, len = 80) {
    if (!str) return "";
    return str.length > len ? str.substring(0, len) + "..." : str;
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
