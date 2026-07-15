// ─── Flyout detail helpers ───────────────────────────────────────────────────────

function _parseAktoOuter(payloadStr) {
    if (!payloadStr) return null;
    try { return JSON.parse(payloadStr); } catch { return null; }
}

function _parseJson(str) {
    if (!str) return null;
    try { return JSON.parse(str); } catch { return null; }
}

// requestPayload.body / .evidence aren't always strings — some tool calls store them as an
// object (e.g. {toolName, toolArgs}). React crashes ("Objects are not valid as a React child")
// if that object is rendered directly in a <Text>, so coerce to a displayable string here,
// at the source, rather than in every place that consumes these fields.
export function coerceToText(value) {
    if (value == null) return null;
    if (typeof value === "string") return value;
    try { return JSON.stringify(value); } catch { return String(value); }
}

// Raw request bodies can be captured terminal output — full of ANSI colour/cursor escape
// codes that render as garbled glyphs (e.g. the literal bytes behind "î °") — and can run to
// several KB, which is unreadable and unnecessary in a table/flyout evidence cell. Strip
// escape codes and other non-printable control characters, then cap the length.
export function sanitizeDisplayText(text, max = 500) {
    if (!text) return text;
    let s = String(text)
        .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, "") // ANSI CSI sequences (colors, cursor moves)
        .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, "") // other control chars, keep \n \t
        .trim();
    return s.length > max ? `${s.slice(0, max).trim()}...` : s;
}

// Request bodies are sometimes JSON-inside-JSON — a field like `requestPayload` whose value
// is itself a JSON-encoded string (e.g. a proxied/forwarded request captured as a string
// field). Printed raw, every nested quote shows up as a literal backslash ("\"..\""), which
// reads as a wall of slashes. Recursively parse any string field that looks like JSON and
// re-embed it as a real nested object, then pretty-print the whole thing — so it renders the
// same clean, indented way the Chat Session tab's code viewer does.
function _unwrapNestedJson(value, depth = 0) {
    if (depth > 4 || typeof value !== "string") return value;
    const trimmed = value.trim();
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return value;
    try {
        const parsed = JSON.parse(trimmed);
        return _unwrapNestedJsonValue(parsed, depth + 1);
    } catch {
        return value;
    }
}
function _unwrapNestedJsonValue(obj, depth) {
    if (depth > 4 || obj == null || typeof obj !== "object") return obj;
    if (Array.isArray(obj)) return obj.map(v => _unwrapNestedJsonValue(_unwrapNestedJson(v, depth), depth + 1));
    const out = {};
    for (const key of Object.keys(obj)) {
        out[key] = typeof obj[key] === "string"
            ? _unwrapNestedJson(obj[key], depth)
            : _unwrapNestedJsonValue(obj[key], depth + 1);
    }
    return out;
}

// Returns { text, isJson } — pretty-printed JSON (with nested JSON-strings unwrapped) when
// the input looks like JSON, otherwise the original text untouched.
export function prettyPrintIfJson(text) {
    if (!text) return { text, isJson: false };
    const unwrapped = _unwrapNestedJson(text);
    if (unwrapped === text) return { text, isJson: false };
    return { text: JSON.stringify(unwrapped, null, 2), isJson: true };
}

// Some backends pre-combine metadata.reason as "<Title>: <message>" (e.g. config-scan
// events: "Bypass Execution Policy: The command uses..."). That inner colon collides with
// our own "<policy> policy: <reason>" wrapper, reading as "policy: Title: message" — turn
// the short leading title into its own sentence instead. Only touches a colon within the
// first 60 chars so it doesn't mangle a colon that's naturally part of a longer sentence.
export function normalizeReasonPunctuation(reason) {
    if (!reason) return reason;
    const idx = reason.indexOf(": ");
    if (idx === -1 || idx > 60) return reason;
    return `${reason.slice(0, idx)}.${reason.slice(idx + 1)}`;
}

function _extractGuardrailReason(resp, req) {
    const respReason = resp?.error?.data?.reason || resp?.error?.message || resp?.message || resp?.reason;
    if (respReason) {
        const match = String(respReason).match(/static-policy-check\s+([^\s.]+)/i);
        return match ? `Static policy ${match[1]} matched` : String(respReason);
    }
    // config scan events: reason in requestPayload.message
    const reqMsg = req?.message || req?.title;
    if (reqMsg) return String(reqMsg);
    return null;
}

// Builds flyout detail from a real MongoDB violation row.
export function buildFallbackDetail(row) {
    if (!row) return {};

    let chatSession = null;
    let fileContent = null;
    let fileTabLabel = null;
    let guardrailReason = null;
    let skillName = null;

    if (row.payload) {
        const outer = _parseAktoOuter(row.payload);

        if (outer) {
            // Standard Akto payload: {requestPayload, responsePayload, ...}
            const req = _parseJson(outer.requestPayload);
            const resp = _parseJson(outer.responsePayload);

            guardrailReason = _extractGuardrailReason(resp, req);

            // Chat session: messages from requestPayload (Claude API / MCP format)
            if (row.type === "Prompt") {
                const msgs = req?.messages || req?.body?.messages;
                if (Array.isArray(msgs) && msgs.length > 0) {
                    chatSession = msgs.map((m, i) => ({
                        type: m.role === "user" ? "request" : "response",
                        text: typeof m.content === "string" ? m.content
                            : Array.isArray(m.content) ? m.content.map(c => c.text || "").join("\n")
                            : JSON.stringify(m.content),
                        author: m.role === "user" ? (row.user || "User") : "AI Model",
                        timestamp: row.detected - (msgs.length - i) * 60,
                        isVulnerable: m.role === "user" && i === msgs.length - 1,
                    }));
                } else if (Array.isArray(outer.messages)) {
                    // Legacy: direct messages array
                    chatSession = outer.messages.map((m, i) => ({
                        type: m.role === "user" ? "request" : "response",
                        text: typeof m.content === "string" ? m.content
                            : Array.isArray(m.content) ? m.content.map(c => c.text || "").join("\n")
                            : JSON.stringify(m.content),
                        author: m.role === "user" ? (row.user || "User") : "AI Model",
                        timestamp: row.detected - (outer.messages.length - i) * 60,
                        isVulnerable: m.role === "user",
                    }));
                }
            } else if (row.type === "Config") {
                // Show the actual config file content (config_content), pretty-printed —
                // not the whole diagnostic wrapper (path/field/evidence/title/message) around it.
                const { text: prettyConfig } = prettyPrintIfJson(req?.config_content);
                fileContent = prettyConfig || req?.config_content || (req ? JSON.stringify(req, null, 2) : outer.requestPayload);
                fileTabLabel = "Config.json";
            } else if (row.type === "Skill" || row.type === "Tool") {
                if (row.type === "Skill") skillName = req?.skill_name || null;
                if (req?.skill_name || req?.skill_description) {
                    const lines = [];
                    if (req.skill_name) lines.push(`# ${req.skill_name}`);
                    if (req.skill_description) lines.push(`\n**${req.skill_description}**`);
                    if (req.skill_content) lines.push(`\n${req.skill_content}`);
                    if (req.file_path) lines.push(`\n**Path:** ${req.file_path}`);
                    if (req.agent) lines.push(`**Agent:** ${req.agent}`);
                    fileContent = lines.join("\n");
                    fileTabLabel = "Skill Info";
                } else {
                    fileContent = req ? JSON.stringify(req, null, 2) : (outer.requestPayload || JSON.stringify(outer, null, 2));
                    fileTabLabel = row.type === "Tool" ? "Tool call" : "Skill.md";
                }
            }
        } else {
            // Not Akto format — try parsing as direct payload
            try {
                const parsed = JSON.parse(row.payload);
                if (row.type === "Prompt" && Array.isArray(parsed?.messages)) {
                    chatSession = parsed.messages.map((m, i) => ({
                        type: m.role === "user" ? "request" : "response",
                        text: typeof m.content === "string" ? m.content
                            : Array.isArray(m.content) ? m.content.map(c => c.text || "").join("\n")
                            : JSON.stringify(m.content),
                        author: m.role === "user" ? (row.user || "User") : "AI Model",
                        timestamp: row.detected - (parsed.messages.length - i) * 60,
                        isVulnerable: m.role === "user",
                    }));
                } else if (row.type !== "Prompt") {
                    fileContent = typeof parsed === "object" ? JSON.stringify(parsed, null, 2) : row.payload;
                    fileTabLabel = row.type === "Config" ? "Config.json" : row.type === "Tool" ? "Tool call" : "Skill.md";
                }
            } catch {
                if (row.type !== "Prompt") {
                    fileContent = row.payload;
                    fileTabLabel = row.type === "Config" ? "Config.json" : "Skill.md";
                }
            }
        }
    }

    // Fallback: show evidence text as a single flagged message when no chat session
    if (!chatSession && row.type === "Prompt" && (row.evidenceText || guardrailReason)) {
        chatSession = [{
            type: "request",
            text: row.evidenceText || guardrailReason,
            author: row.user || "User",
            timestamp: row.detected,
            isVulnerable: true,
        }];
    }

    // Straight from the raw event, no derived surface classification: Prompt & Tool show
    // requestPayload.body; Skill & Config (and anything else) show the evidence field —
    // requestPayload.evidence for Config, responsePayload.evidence for Skill — plus
    // metadata.reason and the policy name.
    const meta = _parseJson(row.metadata) || {};
    const outer = _parseAktoOuter(row.payload) || {};
    const req = _parseJson(outer.requestPayload);
    const resp = _parseJson(outer.responsePayload);

    const reason = normalizeReasonPunctuation(meta.reason) || null;
    const policyName = meta.policyName || (row.policyName && row.policyName !== "-" ? row.policyName : null);
    const isPromptOrTool = row.type === "Prompt" || row.type === "Tool";
    const rawPrimaryValue = coerceToText(isPromptOrTool
        ? (req?.body || null)
        : row.type === "Skill" ? (resp?.evidence || null) : (req?.evidence || null));
    // If the value is JSON (or JSON nested inside JSON, e.g. a proxied request captured as a
    // string field), unwrap and pretty-print it instead of showing raw escaped quotes.
    const { text: prettyPrimaryValue, isJson } = prettyPrintIfJson(rawPrimaryValue);
    const primaryValue = sanitizeDisplayText(prettyPrimaryValue, 1500);
    const evidenceText = primaryValue || row.evidenceText || row.violation;
    const evidenceIsMono = isJson && !!primaryValue && evidenceText === primaryValue;

    // Don't repeat `reason` in the trigger line when it's already the exact text shown
    // above in the evidence box (happens when there's no body/evidence field, so evidenceText
    // itself fell back to `reason`) — that reads as the same sentence said twice.
    const reasonAlreadyShown = reason && reason === evidenceText;
    const triggerReason = policyName
        ? (reason && !reasonAlreadyShown
            ? `Triggered by the "${policyName}" policy: ${reason}`
            : `Triggered by the "${policyName}" policy monitoring ${row.type} activity on this agentic asset.`)
        : (reason && !reasonAlreadyShown
            ? `Triggered because the request matched a guardrail policy rule: ${reason}`
            : `Triggered by a guardrail policy monitoring ${row.type} activity on this agentic asset.`);

    return {
        evidence: {
            title: "Guardrail Violation",
            text: evidenceText,
            highlights: undefined,
            mono: evidenceIsMono,
            author: row.type === "Prompt" ? (row.user || undefined) : undefined,
            assetName: row.type === "Skill" ? (row.agenticAsset || undefined) : undefined,
            apiCollectionId: row.type === "Skill" ? (row.apiCollectionId || undefined) : undefined,
        },
        triggerReason,
        policyName,
        description: `Akto ${row.action === "Blocked" ? "blocked" : "flagged"} "${row.violation}" on ${row.agenticAsset || row.user}, attributed to ${row.user}. This ${row.severity?.toLowerCase()}-severity violation was detected by Agentic Guardrails.`,
        impact: `Review this ${row.severity?.toLowerCase()}-severity violation to confirm whether the ${row.type?.toLowerCase()} activity is expected, and tighten the relevant policy if needed.`,
        // Match the main table's "User" column exactly (row.user is the resolved/friendly
        // name) instead of the raw host string, which can look different from what's shown
        // in the table for the same row.
        deviceId: row.user || row.deviceId || row.agenticAsset || "N/A",
        sessionId: row.sessionId || "N/A",
        chatSession: chatSession || undefined,
        fileContent: fileContent || undefined,
        fileTabLabel: fileTabLabel || undefined,
        skillName: skillName || undefined,
        remediation: `### Recommended actions\n\n1. Review the ${row.type} activity on **${row.agenticAsset || row.user}**.\n2. Confirm whether **${row.user}** is authorized for this action.\n3. Update the relevant guardrail policy if this should be blocked going forward.`,
    };
}
