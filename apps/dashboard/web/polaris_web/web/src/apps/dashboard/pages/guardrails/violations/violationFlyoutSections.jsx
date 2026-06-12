import React, { useMemo } from "react";
import {
    Avatar,
    Box,
    Divider,
    HorizontalStack,
    Icon,
    Link,
    Text,
    VerticalStack,
} from "@shopify/polaris";
import { NoteMinor } from "@shopify/polaris-icons";

import AssetTopologyGraph from "@/apps/dashboard/pages/observe/agentic/AssetTopologyGraph";
import DetailGrid from "@/apps/dashboard/pages/observe/agentic/DetailGrid";
import SampleData from "@/apps/dashboard/components/shared/SampleData";
import MarkdownViewer from "@/apps/dashboard/components/shared/MarkdownViewer";
import ChatMessage from "@/apps/dashboard/pages/testing/TestRunResultPage/components/ChatMessage";
import { MESSAGE_TYPES } from "@/apps/dashboard/pages/testing/TestRunResultPage/components/chatConstants";
import func from "@/util/func";

// ─── Inline highlighting ────────────────────────────────────────────────────────
// Splits `text` so any phrase in `highlights` is wrapped in a pink highlight span
// (.violation-evidence-highlight, style.css). Sequential first-match scan.

function splitHighlights(text, highlights = []) {
    const phrases = (highlights || []).filter(Boolean);
    if (!phrases.length) return [text];
    const parts = [];
    let remaining = text;
    let guard = 0;
    while (remaining.length && guard < 2000) {
        guard++;
        let best = null;
        for (const h of phrases) {
            const i = remaining.indexOf(h);
            if (i >= 0 && (best === null || i < best.i)) best = { i, h };
        }
        if (!best) { parts.push(remaining); break; }
        if (best.i > 0) parts.push(remaining.slice(0, best.i));
        parts.push({ hl: best.h });
        remaining = remaining.slice(best.i + best.h.length);
    }
    return parts;
}

function HighlightedText({ text, highlights, mono }) {
    const parts = useMemo(() => splitHighlights(text || "", highlights), [text, highlights]);
    const nodes = parts.map((p, i) =>
        typeof p === "string"
            ? <React.Fragment key={i}>{p}</React.Fragment>
            : <Box as="span" key={i} className="violation-evidence-highlight">{p.hl}</Box>
    );
    if (mono) {
        return <Box className="violation-evidence-pre">{nodes}</Box>;
    }
    return <Text variant="bodyMd" as="span">{nodes}</Text>;
}

// ─── Evidence block (Blocked Prompt / Suspicious Skill / Suspicious Config) ──────

export function EvidenceBlock({ evidence }) {
    if (!evidence) return null;
    const name = evidence.author || evidence.heading;
    return (
        <Box borderRadius="2">
            <VerticalStack gap="3">
                <Text variant="headingXs" color="subdued">{evidence.title}</Text>
                <Box className="violation-evidence-author-row">
                    {evidence.author
                        ? <Avatar size="small" name={evidence.author} initials={initials(evidence.author)} />
                        : <Box paddingBlockStart="05"><Icon source={NoteMinor} color="critical" /></Box>}
                    <Box className="violation-evidence-quote" width="100%">
                        <VerticalStack gap="1">
                            {name && (
                                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                                    <Box className="violation-evidence-name">
                                        <Text variant="bodySm" fontWeight="semibold">{name}</Text>
                                    </Box>
                                    {evidence.time && <Text variant="bodySm" color="subdued">{evidence.time}</Text>}
                                </HorizontalStack>
                            )}
                            <HighlightedText text={evidence.text} highlights={evidence.highlights} mono={evidence.mono} />
                        </VerticalStack>
                    </Box>
                </Box>
            </VerticalStack>
        </Box>
    );
}

function initials(name) {
    return (name || "")
        .split(" ")
        .map((w) => w[0])
        .filter(Boolean)
        .slice(0, 2)
        .join("")
        .toUpperCase();
}

// Render the trigger reason, linking the policy name when present.
function TriggerReason({ reason, policyName }) {
    if (!reason) return null;
    if (policyName && reason.includes(policyName)) {
        const [before, after] = reason.split(policyName);
        return (
            <Text variant="bodyMd" as="span">
                {before}
                <Link url="#" onClick={(e) => e.preventDefault()}>{policyName}</Link>
                {after}
            </Text>
        );
    }
    return <Text variant="bodyMd">{reason}</Text>;
}

// ─── Overview tab ───────────────────────────────────────────────────────────────

export function OverviewSection({ row, detail }) {
    const topo = useMemo(() => {
        if (!detail?.topology) return null;
        const { user, agent, model, skill, tool } = detail.topology;
        if (skill) {
            // Skill violation: User → Agent → Skill → Model (4 nodes)
            const nodes = [
                { id: "user",  type: "topoNode", draggable: false, position: { x: 0,   y: 0 }, data: { component: { category: "external",  type: "User",     label: user  } } },
                { id: "agent", type: "topoNode", draggable: false, position: { x: 200, y: 0 }, data: { component: { category: "agent",     type: "AI Agent", label: agent } } },
                { id: "skill", type: "topoNode", draggable: false, position: { x: 400, y: 0 }, data: { component: { category: "skill",     type: "Skill",    label: skill } } },
                { id: "model", type: "topoNode", draggable: false, position: { x: 600, y: 0 }, data: { component: { category: "ai-model",  type: "AI Model", label: model } } },
            ];
            const edges = [
                { id: "e1", source: "user",  target: "agent", type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } },
                { id: "e2", source: "agent", target: "skill", type: "smoothstep", style: { stroke: "#7C3AED", strokeWidth: 1.5 } },
                { id: "e3", source: "skill", target: "model", type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } },
            ];
            return { nodes, edges };
        }
        if (tool) {
            // Tool Call violation: User → Agent → Tool (3 nodes)
            const nodes = [
                { id: "user",  type: "topoNode", draggable: false, position: { x: 0,   y: 0 }, data: { component: { category: "external", type: "User",      label: user  } } },
                { id: "agent", type: "topoNode", draggable: false, position: { x: 260, y: 0 }, data: { component: { category: "agent",    type: "AI Agent",  label: agent } } },
                { id: "tool",  type: "topoNode", draggable: false, position: { x: 520, y: 0 }, data: { component: { category: "mcp",      type: "Tool Call", label: tool  } } },
            ];
            const edges = [
                { id: "e1", source: "user",  target: "agent", type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } },
                { id: "e2", source: "agent", target: "tool",  type: "smoothstep", style: { stroke: "#4cbebb", strokeWidth: 1.5 } },
            ];
            return { nodes, edges };
        }
        // Default: User → Agent → Model (3 nodes)
        const nodes = [
            { id: "user",  type: "topoNode", draggable: false, position: { x: 0,   y: 0 }, data: { component: { category: "external",  type: "User",     label: user  } } },
            { id: "agent", type: "topoNode", draggable: false, position: { x: 260, y: 0 }, data: { component: { category: "agent",     type: "AI Agent", label: agent } } },
            { id: "model", type: "topoNode", draggable: false, position: { x: 520, y: 0 }, data: { component: { category: "ai-model",  type: "AI Model", label: model } } },
        ];
        const edges = [
            { id: "e1", source: "user",  target: "agent", type: "smoothstep", style: { stroke: "#9CA3AF", strokeWidth: 1.5 } },
            { id: "e2", source: "agent", target: "model", type: "smoothstep", style: { stroke: "#ec4899", strokeWidth: 1.5 } },
        ];
        return { nodes, edges };
    }, [detail]);

    const gridItems = [
        { label: "Detected", value: func.epochToDateTime(row.detected) },
        { label: "Device ID", value: detail?.deviceId || "—" },
        { label: "Session ID", value: detail?.sessionId || "—", tooltip: detail?.sessionId || undefined },
    ];

    return (
        <VerticalStack gap="0">
            {/* Box 3 — Evidence + trigger reason */}
            <Box padding="4" background="bg-critical-subdued">
                <VerticalStack gap="3">
                    <EvidenceBlock evidence={detail?.evidence} />
                    {detail?.triggerReason && (
                        <TriggerReason reason={detail.triggerReason} policyName={detail.policyName} />
                    )}
                </VerticalStack>
            </Box>

            <Divider />

            {/* Box 4 — Description, topology, impact, metadata */}
            <Box padding="4">
                <VerticalStack gap="4">
                    {detail?.description && (
                        <VerticalStack gap="2">
                            <Text variant="headingXs" color="subdued">Description</Text>
                            <Text variant="bodyMd">{detail.description}</Text>
                        </VerticalStack>
                    )}

                    {topo && <AssetTopologyGraph nodes={topo.nodes} edges={topo.edges} />}

                    {detail?.impact && (
                        <>
                            <Divider />
                            <VerticalStack gap="2">
                                <Text variant="headingXs" color="subdued">Impact</Text>
                                <Text variant="bodyMd">{detail.impact}</Text>
                            </VerticalStack>
                        </>
                    )}

                    <Divider />
                    <DetailGrid items={gridItems} columns={3} />
                </VerticalStack>
            </Box>
        </VerticalStack>
    );
}

// ─── Chat Session tab ───────────────────────────────────────────────────────────
// Reuses the shared ChatMessage transcript component from the testing flow.

export function ChatSessionSection({ messages = [], highlights = [] }) {
    if (!messages.length) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No chat session</Text>
                    <Text variant="bodySm" color="subdued">This violation has no associated conversation.</Text>
                </VerticalStack>
            </Box>
        );
    }
    return (
        <Box padding="2">
            <VerticalStack gap="2">
                {messages.map((m, i) => (
                    <ChatMessage
                        key={i}
                        type={m.type === "request" ? MESSAGE_TYPES.REQUEST : MESSAGE_TYPES.RESPONSE}
                        customLabel={m.author}
                        content={m.text}
                        timestamp={m.timestamp}
                        isVulnerable={!!m.isVulnerable}
                        isCode={false}
                        toolsMetadata={{}}
                        highlights={m.isVulnerable ? highlights : []}
                    />
                ))}
            </VerticalStack>
        </Box>
    );
}

// ─── File tab (Skill.md / Config.json) ──────────────────────────────────────────

export function FileSection({ detail }) {
    if (!detail?.fileContent) return null;
    return (
        <Box padding="4">
            <SampleData
                data={{
                    message: detail.fileContent,
                    vulnerabilitySegments: (detail.fileHighlights || []).map((phrase) => ({ phrase })),
                }}
                editorLanguage={detail.fileLanguage || "json"}
                minHeight="640px"
                readOnly
                wordWrap
            />
        </Box>
    );
}

// ─── Remediation tab ────────────────────────────────────────────────────────────

export function RemediationSection({ markdown }) {
    if (!markdown) {
        return (
            <Box padding="8">
                <Text variant="bodySm" color="subdued">No remediation guidance available.</Text>
            </Box>
        );
    }
    return <MarkdownViewer markdown={markdown} />;
}
