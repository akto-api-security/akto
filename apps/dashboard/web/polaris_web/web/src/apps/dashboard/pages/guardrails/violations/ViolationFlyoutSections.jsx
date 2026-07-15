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
import { HighlightedText } from "@/apps/dashboard/components/shared/MarkdownComponents";
import ConversationHistory from "@/apps/dashboard/pages/testing/TestRunResultPage/components/ConversationHistory";
import func from "@/util/func";
import { getDashboardCategory, categoryToShortName } from "@/apps/main/labelHelper";
import { GUARDRAIL_SECTIONS } from "@/apps/dashboard/pages/threat_detection/constants/guardrailDescriptions";
import { getGuardrailRuleInfo } from "@/apps/dashboard/pages/threat_detection/constants/guardrailRuleDefinitions";
import { getOwaspThreatsForRule } from "@/apps/dashboard/pages/guardrails/components/owaspConfig";
import OwaspTag from "@/apps/dashboard/pages/guardrails/components/OwaspTag";
import ComplianceTags from "@/apps/dashboard/pages/guardrails/components/ComplianceTags";

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
                        ? <Avatar size="small" name={evidence.author} initials={func.initials(evidence.author)} />
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

// Render the trigger reason, linking the policy name when present.
function TriggerReason({ reason, policyName }) {
    if (!reason) return null;
    if (policyName && reason.includes(policyName)) {
        const [before, after] = reason.split(policyName);
        // Include the current category (AGENTIC/ENDPOINT) so a fresh tab — which has no
        // PersistStore session and would otherwise default to API_SECURITY — lands in the
        // right module instead of hanging on "Loading guardrail policies...".
        const categoryShort = categoryToShortName[getDashboardCategory()];
        const policyUrl = `/dashboard/guardrails/policies?policy=${encodeURIComponent(policyName)}`
            + (categoryShort ? `&category=${categoryShort}` : "");
        return (
            <Text variant="bodyMd" as="span">
                {before}
                <Link url={policyUrl} external>{policyName}</Link>
                {after}
            </Text>
        );
    }
    return <Text variant="bodyMd">{reason}</Text>;
}

// ─── Overview tab ───────────────────────────────────────────────────────────────

// ─── Topology builder ───────────────────────────────────────────────────────────
// Builds ReactFlow nodes + edges from a violation topology object so OverviewSection
// stays lean. Node type "topoNode" is registered in AssetTopologyGraph (TOPO_NODE_TYPES).

function buildViolationTopo(topology) {
    if (!topology) return null;
    const { user, agent, model, skill, tool } = topology;
    const node = (id, category, type, label, x) => ({
        id, type: "topoNode", draggable: false,
        position: { x, y: 0 },
        data: { component: { category, type, label } },
    });
    const edge = (id, source, target, stroke) => ({
        id, source, target, type: "smoothstep", style: { stroke, strokeWidth: 1.5 },
    });

    if (skill) {
        return {
            nodes: [
                node("user",  "external",  "User",     user,  0),
                node("agent", "agent",     "AI Agent", agent, 200),
                node("skill", "skill",     "Skill",    skill, 400),
                node("model", "ai-model",  "AI Model", model, 600),
            ],
            edges: [
                edge("e1", "user",  "agent", "#9CA3AF"),
                edge("e2", "agent", "skill", "#7C3AED"),
                edge("e3", "skill", "model", "#ec4899"),
            ],
        };
    }
    if (tool) {
        return {
            nodes: [
                node("user",  "external", "User",      user,  0),
                node("agent", "agent",    "AI Agent",  agent, 260),
                node("tool",  "mcp",      "Tool Call", tool,  520),
            ],
            edges: [
                edge("e1", "user",  "agent", "#9CA3AF"),
                edge("e2", "agent", "tool",  "#4cbebb"),
            ],
        };
    }
    return {
        nodes: [
            node("user",  "external",  "User",     user,  0),
            node("agent", "agent",     "AI Agent", agent, 260),
            node("model", "ai-model",  "AI Model", model, 520),
        ],
        edges: [
            edge("e1", "user",  "agent", "#9CA3AF"),
            edge("e2", "agent", "model", "#ec4899"),
        ],
    };
}

export function OverviewSection({ row, detail }) {
    const topo = useMemo(() => buildViolationTopo(detail?.topology), [detail]);

    const guardrailRuleInfo = useMemo(
        () => getGuardrailRuleInfo(row.violation, row.policyName),
        [row.violation, row.policyName],
    );

    const guardrailSections = useMemo(() => {
        if (guardrailRuleInfo) {
            return [{
                heading: guardrailRuleInfo.heading,
                description: null,
                subSections: guardrailRuleInfo.overview.map(o => ({ subHeading: o.heading, description: o.body })),
            }];
        }
        return GUARDRAIL_SECTIONS;
    }, [guardrailRuleInfo]);

    const owaspThreats = useMemo(() => getOwaspThreatsForRule(row.violation), [row.violation]);

    const gridItems = [
        { label: "Detected", value: func.epochToDateTime(row.detected) },
        { label: "Device ID", value: detail?.deviceId || "N/A" },
        { label: "Session ID", value: detail?.sessionId || "N/A" },
    ];

    const hasCompliance = row.complianceMap && Object.keys(row.complianceMap).length > 0;
    const hasBottomSection = owaspThreats.length > 0 || hasCompliance;

    return (
        <VerticalStack gap="0">
            {/* 1. Guardrail Violation — evidence + trigger reason */}
            <Box padding="4" background="bg-critical-subdued">
                <VerticalStack gap="3">
                    <EvidenceBlock evidence={detail?.evidence} />
                    {detail?.triggerReason && (
                        <TriggerReason reason={detail.triggerReason} policyName={detail.policyName} />
                    )}
                </VerticalStack>
            </Box>

            <Divider />

            {/* 2. Detected / Device ID / Session ID */}
            <Box padding="4">
                <DetailGrid items={gridItems} columns={3} />
            </Box>

            <Divider />

            {/* 3. Numbered guardrail sections */}
            <Box padding="4">
                <VerticalStack gap="5">
                    {guardrailSections.map((section, sectionIdx) => (
                        <React.Fragment key={sectionIdx}>
                            <VerticalStack gap="3">
                                <Text variant="headingMd" fontWeight="bold">
                                    {sectionIdx + 1}. {section.heading}
                                </Text>
                                {section.description && (
                                    <Text variant="bodyMd">{section.description}</Text>
                                )}
                                {section.subSections && section.subSections.length > 0 && (
                                    <VerticalStack gap="3" paddingBlockStart="2">
                                        {section.subSections.map((sub, subIdx) => (
                                            <VerticalStack key={subIdx} gap="2">
                                                <Text variant="headingSm" fontWeight="semibold">
                                                    {sub.subHeading}:
                                                </Text>
                                                {sub.description && (
                                                    <Text variant="bodyMd">{sub.description}</Text>
                                                )}
                                                {sub.items && sub.items.length > 0 && (
                                                    <Box paddingInlineStart="4" paddingBlockStart="1">
                                                        <VerticalStack gap="2">
                                                            {sub.items.map((item, itemIdx) => (
                                                                <Text key={itemIdx} variant="bodyMd">• {item}</Text>
                                                            ))}
                                                        </VerticalStack>
                                                    </Box>
                                                )}
                                            </VerticalStack>
                                        ))}
                                    </VerticalStack>
                                )}
                            </VerticalStack>
                            {sectionIdx < guardrailSections.length - 1 && (
                                <Box paddingBlockStart="4"><Divider /></Box>
                            )}
                        </React.Fragment>
                    ))}
                </VerticalStack>
            </Box>

            {topo && (
                <>
                    <Divider />

                    {/* 4. Asset topology */}
                    <Box padding="4">
                        <AssetTopologyGraph nodes={topo.nodes} edges={topo.edges} />
                    </Box>
                </>
            )}

            {/* 5. OWASP + Compliance — only when data exists */}
            {hasBottomSection && (
                <>
                    <Divider />
                    <Box padding="4">
                        <VerticalStack gap="4">
                            {owaspThreats.length > 0 && <OwaspTag threats={owaspThreats} />}
                            {hasCompliance && <ComplianceTags complianceMap={row.complianceMap} showDivider={false} />}
                        </VerticalStack>
                    </Box>
                </>
            )}
        </VerticalStack>
    );
}

// ─── Chat Session tab ───────────────────────────────────────────────────────────
// Normalises violation message format → ConversationHistory's expected shape, then
// delegates rendering to the shared ConversationHistory component.

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

    const conversations = messages.map(m => ({
        role: m.type === "request" ? "user" : "assistant",
        message: m.text,
        creationTimestamp: m.timestamp,
        validation: !!m.isVulnerable,
        customLabel: m.author,
    }));

    return (
        <Box padding="2">
            <ConversationHistory conversations={conversations} highlights={highlights} enableBlockedStyling />
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
