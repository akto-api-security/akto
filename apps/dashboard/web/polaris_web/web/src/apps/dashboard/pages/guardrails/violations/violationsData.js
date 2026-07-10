// Last 7 months ending today — matches the 7-point sparkline arrays on the Violations page.
const MONTH_NAMES = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const _now = new Date();
export const SPARKLINE_LABELS = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(_now.getFullYear(), _now.getMonth() - (6 - i), 1);
    return `${MONTH_NAMES[d.getMonth()]} ${d.getFullYear()}`;
});

// ─── Flyout detail helpers ───────────────────────────────────────────────────────

function _parseAktoOuter(payloadStr) {
    if (!payloadStr) return null;
    try { return JSON.parse(payloadStr); } catch { return null; }
}

function _parseJson(str) {
    if (!str) return null;
    try { return JSON.parse(str); } catch { return null; }
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
            } else if (row.type === "Skill" || row.type === "Config" || row.type === "Tool") {
                // When the payload is skill metadata (has skill_name/skill_description but no
                // actual file body), format it as readable text instead of raw JSON and use
                // a label that doesn't imply the full file is present.
                if (req?.skill_name || req?.skill_description) {
                    const lines = [];
                    if (req.skill_name) lines.push(`# ${req.skill_name}`);
                    if (req.skill_description) lines.push(`\n${req.skill_description}`);
                    if (req.file_path) lines.push(`\n**Path:** ${req.file_path}`);
                    if (req.agent) lines.push(`**Agent:** ${req.agent}`);
                    if (req.content_length != null) lines.push(`\n> Full skill file (${req.content_length} bytes) was validated but is not stored in the event payload.`);
                    fileContent = lines.join("\n");
                    fileTabLabel = "Skill Info";
                } else {
                    fileContent = req ? JSON.stringify(req, null, 2) : (outer.requestPayload || JSON.stringify(outer, null, 2));
                    fileTabLabel = row.type === "Config" ? "Config.json" : row.type === "Tool" ? "Tool.json" : "Skill.md";
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
                    fileTabLabel = row.type === "Config" ? "Config.json" : row.type === "Tool" ? "Tool.json" : "Skill.md";
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

    const evidenceText = row.evidenceText || guardrailReason || row.violation;

    return {
        evidence: {
            title: guardrailReason ? "Guardrails Violation" : row.action === "Blocked" ? "Blocked Request" : "Flagged Activity",
            text: evidenceText,
            highlights: guardrailReason ? [guardrailReason] : undefined,
            author: row.type === "Prompt" ? (row.user || undefined) : undefined,
        },
        triggerReason: guardrailReason
            ? `Triggered because the request matched a guardrail policy rule: ${guardrailReason}`
            : `Triggered by the guardrail policy monitoring ${row.type} activity for this agentic asset.`,
        description: `Akto ${row.action === "Blocked" ? "blocked" : "flagged"} "${row.violation}" on ${row.agenticAsset || row.user}, attributed to ${row.user}. This ${row.severity?.toLowerCase()}-severity violation was detected by Agentic Guardrails.`,
        impact: `Review this ${row.severity?.toLowerCase()}-severity violation to confirm whether the ${row.type?.toLowerCase()} activity is expected, and tighten the relevant policy if needed.`,
        deviceId: row.deviceId || row.agenticAsset || "—",
        sessionId: row.sessionId || "—",
        chatSession: chatSession || undefined,
        fileContent: fileContent || undefined,
        fileTabLabel: fileTabLabel || undefined,
        remediation: `### Recommended actions\n\n1. Review the ${row.type} activity on **${row.agenticAsset || row.user}**.\n2. Confirm whether **${row.user}** is authorized for this action.\n3. Update the relevant guardrail policy if this should be blocked going forward.`,
    };
}
