import React from "react";
import { Badge, HorizontalStack, Text, VerticalStack, Link } from "@shopify/polaris";
import func from "@/util/func";
import { formatDurationMs, latencyColor, truncate } from "./constants";
import { OsIcon } from "../agentic/DeviceEndpoints";
import AssetIcon from "../agentic/AssetIcon";

export { OsIcon };

const DASH = "-";

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
    const name = data._promptText ? truncate(data._promptText, 90) : DASH;
    return <Text variant="bodySm" color="interactive" truncate>{name}</Text>;
}

// User cell: OS icon + username.
export function UserCell({ data }) {
    if (!data) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <OsIcon os={data.os} />
            <Text variant="bodySm" truncate>{data.userName || DASH}</Text>
        </HorizontalStack>
    );
}

// Monospace-ish id (plain Text).
export function IdCell({ value }) {
    if (!value) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return <Text variant="bodySm" truncate>{value}</Text>;
}

// Application / service name with favicon icon.
export function AppCell({ value }) {
    if (!value) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <AssetIcon type="Application" assetTagValue={value} size={20} />
            <Text variant="bodySm" truncate>{value}</Text>
        </HorizontalStack>
    );
}

// Icon + name chip for a single model.
export function ModelChip({ model }) {
    if (!model) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <ModelIcon model={model} size={14} />
            <Text variant="bodySm" truncate>{model}</Text>
        </HorizontalStack>
    );
}

export function ModelChipCellRenderer({ data }) {
    return <ModelChip model={data?._model} />;
}

// Primary model + "+N more" overflow — always exactly one icon.
export function ModelsCell({ data }) {
    const models = (data?._models && data._models.length) ? data._models : (data?._model ? [data._model] : []);
    if (!models.length) return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    const primary = models[0];
    const extra = models.length - 1;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <ModelIcon model={primary} size={24} />
            <Text variant="bodySm" truncate>{primary}</Text>
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


// Plain numeric count (traces / spans).
export function CountCell({ value }) {
    return <Text variant="bodySm">{Number(value || 0).toLocaleString("en-US")}</Text>;
}

// Relative time from an epoch-ms value.
export function TimeCell({ value }) {
    return <Text variant="bodySm">{func.prettifyEpoch(Math.floor((value || 0) / 1000))}</Text>;
}


export function TopicCell({ data }) {
    // Aggregated path: topicHierarchy = { domain: [subDomain, ...], ... }
    // Flat path (searchPrompts): topic + subTopic as plain strings
    let entries;
    if (data?.topicHierarchy && Object.keys(data.topicHierarchy).length > 0) {
        entries = Object.entries(data.topicHierarchy).slice(0, 3);
    } else if (data?.topic) {
        entries = [[data.topic, data.subTopic ? [data.subTopic] : []]];
    } else {
        return <Text variant="bodySm" color="subdued">{DASH}</Text>;
    }
    return (
        <VerticalStack gap="1">
            {entries.map(([domain, subTopics]) => {
                const subDomain = Array.isArray(subTopics) ? subTopics[0] : (subTopics || "");
                return (
                    <HorizontalStack key={domain} gap="1" blockAlign="center" wrap={false}>
                        <Badge status="info">{domain}</Badge>
                        {subDomain && (
                            <Text variant="bodySm" color="subdued" truncate>{subDomain}</Text>
                        )}
                    </HorizontalStack>
                );
            })}
        </VerticalStack>
    );
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
