import React from "react";
import { HorizontalStack, Link, Text } from "@shopify/polaris";
import func from "@/util/func";
import { formatCost, formatDurationMs, latencyColor, truncate } from "./constants";

// ─── Shared LLM Observability cell renderers ──────────────────────────────────
// Pure Polaris: layout via HorizontalStack/Box, every value via <Text>. No custom
// CSS classes. Icons use the same Google favicon approach as AssetIcon.jsx.

const DASH = "-";

// Map a model string to the provider domain used for the Google favicon service.
function modelDomain(model) {
    if (!model) return null;
    const m = model.toLowerCase();
    if (/claude|anthropic/.test(m)) return "claude.ai";
    if (/gpt|openai|text-embedding|o1|o3/.test(m)) return "openai.com";
    if (/gemini|bison|palm/.test(m)) return "gemini.google.com";
    if (/llama|meta/.test(m)) return "meta.com";
    if (/mistral/.test(m)) return "mistral.ai";
    if (/grok/.test(m)) return "x.ai";
    if (/cohere/.test(m)) return "cohere.com";
    return null;
}

// Provider → Polaris Text color token.
function modelColor(model) {
    if (!model) return "subdued";
    if (/claude|anthropic/i.test(model)) return "warning";
    if (/gpt|openai|text-embedding|o1|o3/i.test(model)) return "success";
    if (/gemini|palm|bison/i.test(model)) return "interactive";
    return "subdued";
}

// 16×16 provider favicon icon — same pattern as AssetIcon.jsx.
export function ModelIcon({ model, size = 16 }) {
    const domain = modelDomain(model);
    if (!domain) return null;
    return (
        <img
            src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`}
            width={size}
            height={size}
            alt=""
            style={{ flexShrink: 0, borderRadius: 3 }}
        />
    );
}

// ── cells ─────────────────────────────────────────────────────────────────────

// Title: prompt text in interactive blue so it reads as a clickable row label.
export function TitleCell({ data }) {
    if (!data) return null;
    const name = data._promptText ? truncate(data._promptText, 90) : (data.traceId || data.sessionIdentifier || DASH);
    return <Text variant="bodySm" color="interactive" truncate>{name}</Text>;
}

// Monospace-ish id (plain subdued Text).
export function IdCell({ value }) {
    if (!value) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return <Text variant="bodySm" color="subdued" truncate>{value}</Text>;
}

// Application / service name (text only).
export function AppCell({ value }) {
    if (!value) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return <Text variant="bodySm" truncate>{value}</Text>;
}

// Icon + name chip for a single model.
export function ModelChip({ model }) {
    if (!model) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <ModelIcon model={model} size={14} />
            <Text variant="bodySm" color={modelColor(model)} truncate>{model}</Text>
        </HorizontalStack>
    );
}

export function ModelChipCellRenderer({ data }) {
    return <ModelChip model={data?._model} />;
}

// Session models: icon cluster for each distinct model + "+N" overflow.
export function ModelsCell({ data }) {
    const models = (data?._models && data._models.length) ? data._models : (data?._model ? [data._model] : []);
    if (!models.length) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    const visible = models.slice(0, 2);
    const extra = models.length - visible.length;
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            {visible.map((m, i) => <ModelIcon key={i} model={m} size={16} />)}
            <Text variant="bodySm" color={modelColor(visible[0])} truncate>{visible[0]}</Text>
            {extra > 0 && <Text variant="bodySm" color="subdued">+{extra}</Text>}
        </HorizontalStack>
    );
}

// "in / out" tokens.
export function TokensCell({ data }) {
    if (!data) return null;
    const input = Number(data._inputTokens ?? data.inputTokens ?? 0);
    const output = Number(data._outputTokens ?? data.outputTokens ?? 0);
    return <Text variant="bodySm">{input.toLocaleString("en-US")} / {output.toLocaleString("en-US")}</Text>;
}

// Derived duration, coloured by magnitude.
export function DurationCell({ value }) {
    return <Text variant="bodySm" color={latencyColor(value)}>{formatDurationMs(value)}</Text>;
}

// Derived cost from token counts.
export function CostCell({ data }) {
    if (!data) return null;
    const input = Number(data._inputTokens ?? data.inputTokens ?? 0);
    const output = Number(data._outputTokens ?? data.outputTokens ?? 0);
    return <Text variant="bodySm">{formatCost(input, output)}</Text>;
}

// Plain numeric count (traces / spans).
export function CountCell({ value }) {
    return <Text variant="bodySm">{Number(value || 0).toLocaleString("en-US")}</Text>;
}

// Relative time from an epoch-ms value.
export function TimeCell({ value }) {
    return <Text variant="bodySm" color="subdued">{func.prettifyEpoch(Math.floor((value || 0) / 1000))}</Text>;
}

// Clickable session id (used in unscoped Traces table).
export function SessionLinkCell({ data, onSessionClick }) {
    const sid = data?.sessionIdentifier;
    if (!sid) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return (
        <Link removeUnderline monochrome onClick={() => onSessionClick?.(sid)}>
            <Text variant="bodySm">{sid}</Text>
        </Link>
    );
}
