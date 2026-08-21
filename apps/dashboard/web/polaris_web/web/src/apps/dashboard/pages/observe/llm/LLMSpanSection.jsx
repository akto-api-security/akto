import { useState, useMemo } from "react";
import { Badge, Box, Collapsible, Divider, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from "@shopify/polaris";
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons";
import { ModelChip } from "./LLMCellRenderers";
import GuardrailVerdict, { GuardrailVerdictBadge } from "./GuardrailVerdict";
import { formatDurationMs, truncate } from "./constants";

// ─── Helpers ──────────────────────────────────────────────────────────────────

const ROLE_LABELS = { system: "SYSTEM", user: "USER", assistant: "ASSISTANT", tool: "TOOL", function: "FUNCTION" };
function roleLabel(role) { return ROLE_LABELS[role] || String(role || "").toUpperCase(); }

function tryFormatJson(str) {
    try { return JSON.stringify(JSON.parse(str), null, 2); }
    catch (_) { return str; }
}

// Normalizes a payload that is a single turn object rather than a provider messages array —
// the shape agent/MCP spans emit: { role, text, tool_calls, tool_results }.
// Returns null when the payload is not that shape.
function agentTurnMessage(obj, fallbackRole) {
    if (!obj || typeof obj !== "object") return null;
    const hasTurnFields = typeof obj.text === "string"
        || Array.isArray(obj.tool_calls) || Array.isArray(obj.tool_results);
    if (!hasTurnFields) return null;
    return {
        role:         obj.role || fallbackRole,
        content:      obj.text || "",
        tool_calls:   obj.tool_calls,
        tool_results: obj.tool_results,
    };
}

function messageText(msg) {
    if (typeof msg.content === "string") return msg.content;
    if (Array.isArray(msg.content)) return msg.content.map(c => c.text || "").filter(Boolean).join("\n");
    return typeof msg.text === "string" ? msg.text : "";
}

// ─── Message content ──────────────────────────────────────────────────────────

function ToolBlock({ label, name, body, status }) {
    return (
        <Box background="bg-subdued" borderRadius="2" padding="2" borderWidth="1" borderColor="border-subdued">
            <VerticalStack gap="1">
                <HorizontalStack gap="2" blockAlign="center">
                    <Text variant="bodySm" color="subdued" fontWeight="semibold">{label}</Text>
                    {name && <Text variant="bodySm" fontWeight="semibold" breakWord>{name}</Text>}
                    {status && <Badge status={status.tone} size="small">{status.label}</Badge>}
                </HorizontalStack>
                {body && <Text variant="bodySm" color="subdued" breakWord>{tryFormatJson(body)}</Text>}
            </VerticalStack>
        </Box>
    );
}

// OpenAI sends { function: { name, arguments } }; agent/MCP spans send { name, input }.
function ToolCallBlock({ tc }) {
    const args = tc.function?.arguments ?? tc.input ?? tc.arguments;
    return (
        <ToolBlock
            label="TOOL CALL"
            name={tc.function?.name || tc.name}
            body={typeof args === "string" ? args : (args !== undefined ? JSON.stringify(args) : "")}
        />
    );
}

function ToolResultBlock({ tr }) {
    const out = tr.output ?? tr.content;
    return (
        <ToolBlock
            label="TOOL RESULT"
            name={tr.name}
            body={typeof out === "string" ? out : (out !== undefined ? JSON.stringify(out) : "")}
            status={tr.is_error ? { tone: "critical", label: "Error" } : null}
        />
    );
}

function MessageContent({ msg }) {
    if (!msg) return null;

    const toolCalls   = Array.isArray(msg.tool_calls)   ? msg.tool_calls   : [];
    const toolResults = Array.isArray(msg.tool_results) ? msg.tool_results : [];
    const text        = messageText(msg);

    // Legacy tool role: the whole content is the raw result payload.
    if (msg.role === "tool" && !toolCalls.length && !toolResults.length) {
        const raw = typeof msg.content === "string" ? msg.content : JSON.stringify(msg.content || "");
        return raw ? <ToolBlock label="TOOL RESULT" body={raw} /> : null;
    }

    if (!text && !toolCalls.length && !toolResults.length) return null;

    return (
        <VerticalStack gap="2">
            {text && <Text variant="bodySm" breakWord>{text}</Text>}
            {toolCalls.map((tc, i)   => <ToolCallBlock   key={`call-${i}`}   tc={tc} />)}
            {toolResults.map((tr, i) => <ToolResultBlock key={`result-${i}`} tr={tr} />)}
        </VerticalStack>
    );
}

// ─── Message row ──────────────────────────────────────────────────────────────

function MessageRow({ msg, isLast, id }) {
    return (
        <Box padding={"1"}>
            <VerticalStack gap={"1"}>
                <Text variant="headingXs" color="subdued">{roleLabel(msg.role)}</Text>
                 <MessageContent msg={msg} />
                 {!isLast && <Divider />}
            </VerticalStack>
        </Box>
    );
}

// ─── Input / Output section ───────────────────────────────────────────────────

function InputOutputSection({ label, tokens, messages }) {
    if (!messages.length) return null;
    return (
        <VerticalStack gap="2">
            <HorizontalStack gap="2" blockAlign="center">
                <Text variant="bodySm" fontWeight="semibold" color="subdued">{label}</Text>
                {tokens > 0 && (
                    <Text variant="bodySm" color="subdued">
                        {Number(tokens).toLocaleString("en-US")} tokens
                    </Text>
                )}
            </HorizontalStack>
            {/* white background so tool-call blocks (bg-subdued) contrast clearly */}
            <Box padding={"1"} borderWidth="1" borderColor="border" borderRadius="2">
                {messages.map((msg, i) => (
                    <MessageRow key={i} id={`${label}-msg-${i}`} msg={msg} isLast={i === messages.length - 1} />
                ))}
            </Box>
        </VerticalStack>
    );
}

// ─── SpanSection ──────────────────────────────────────────────────────────────

export default function SpanSection({ span, index }) {
    const [collapsed, setCollapsed] = useState(false);

    const inputMessages = useMemo(() => {
        if (span.queryPayload) {
            try {
                const obj = JSON.parse(span.queryPayload);
                if (Array.isArray(obj.messages) && obj.messages.length) return obj.messages;
                const turn = agentTurnMessage(obj, "user");
                if (turn) return [turn];
            } catch (_) {}
        }
        if (span._promptText) return [{ role: "user", content: span._promptText }];
        return [];
    }, [span.queryPayload, span._promptText]);

    const outputMessages = useMemo(() => {
        if (span.responsePayload) {
            try {
                const obj = JSON.parse(span.responsePayload);
                if (Array.isArray(obj.choices) && obj.choices[0]?.message) {
                    return [obj.choices[0].message];
                }
                const turn = agentTurnMessage(obj, "assistant");
                if (turn) return [turn];
            } catch (_) {}
        }
        if (span._responseText) return [{ role: "assistant", content: span._responseText }];
        return [];
    }, [span.responsePayload, span._responseText]);

    const spanName  = span._promptText || `Span ${index + 1}`;
    const inputTok  = Number(span.inputTokens  || span._inputTokens  || 0);
    const outputTok = Number(span.outputTokens || span._outputTokens || 0);

    return (
        <Box borderWidth="1" borderColor="border" borderRadius="2" background="bg" padding={"2"}>
            {/* ── Header ── */}
            <Box
                onClick={() => setCollapsed(c => !c)}
            >
                <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="3">
                    {/* left: badge + title — allowed to shrink/truncate */}
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <Badge status="success" size="small">LLM</Badge>
                        <Tooltip content={spanName} dismissOnMouseOut>
                            <Text variant="bodySm" fontWeight="semibold" truncate>{truncate(spanName, 45)}</Text>
                        </Tooltip>
                    </HorizontalStack>
                    {/* right: meta + chevron — never wraps */}
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <GuardrailVerdictBadge span={span} />
                        <Text variant="bodySm" color="subdued">{formatDurationMs(span.durationMs)}</Text>
                        {span._model && <ModelChip model={span._model} />}
                        <Icon source={collapsed ? ChevronDownMinor : ChevronUpMinor} color="subdued" />
                    </HorizontalStack>
                </HorizontalStack>
            </Box>

            {/* ── Body ── */}
            <Collapsible open={!collapsed} id={`span-${index}`} transition={{ duration: "200ms", timingFunction: "ease-in-out" }}>
                <Box paddingBlockStart={"2"} paddingBlockEnd={"2"}>
                    <Divider/>
                </Box>
                <VerticalStack gap={"2"}>
                <GuardrailVerdict span={span} />
                {inputMessages.length > 0 && (
                    <InputOutputSection
                        label="INPUT"
                        tokens={inputTok}
                        messages={inputMessages}
                    />
                )}
                {outputMessages.length > 0 && (
                    <InputOutputSection
                        label="OUTPUT"
                        tokens={outputTok}
                        messages={outputMessages}
                    />
                )}
                </VerticalStack>
            </Collapsible>
        </Box>
    );
}
