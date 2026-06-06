import React, { useMemo } from "react";
import {
    Box,
    Card,
    Divider,
    HorizontalStack,
    Text,
    VerticalStack,
} from "@shopify/polaris";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";
import { cumulativeByMonth } from "./agenticPageBuilders";

// Proportion bar — each segment width is data-driven (flexGrow) and cannot use Polaris tokens.
function SegmentBar({ segments }) {
    return (
        <Box className="agentic-seg-bar">
            {segments.map(
                (s) =>
                    s.count > 0 && (
                        <Box
                            key={s.label}
                            className="agentic-seg"
                            title={`${s.label}: ${s.count}`}
                            style={{ flexGrow: s.count, background: s.color }}
                        />
                    ),
            )}
        </Box>
    );
}

// Small legend dot — colour is data-driven; CSS custom property bridges class ↔ dynamic value.
function LegendDot({ color, active }) {
    return (
        <Box
            className={active ? "agentic-dot agentic-dot--active" : "agentic-dot"}
            style={{ "--dot-color": color }}
        />
    );
}

// Shift a cumulative series so its final point equals `total`, preserving the month-over-month shape.
function anchorSeriesToTotal(series, total) {
    const counts = series.counts || [];
    if (!counts.length || total == null) return series;
    const diff = total - counts[counts.length - 1];
    if (diff === 0) return series;
    return { ...series, counts: counts.map((c) => Math.max(0, c + diff)) };
}

function violationTotal(row) {
    const v = row?.violations;
    if (!v) return 0;
    return (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0);
}

export { violationTotal };

export default function AgenticStatsCard({
    agenticFlatData = [],
    violationRows = [],
    startTimestamp = 0,
    endTimestamp = 0,
    onTypeFilter,
    activeTypeFilter,
    onViolSevFilter,
    activeViolSevFilter,
}) {
    const aiCount = agenticFlatData.filter((r) => r.type === "AI Agent").length;
    const mcpCount = agenticFlatData.filter((r) => r.type === "MCP Server").length;
    const llmCount = agenticFlatData.filter((r) => r.type === "LLM").length;
    const skillCount = agenticFlatData.filter((r) => r.type === "Skill").length;
    const total = agenticFlatData.length;

    const critV = agenticFlatData.reduce((s, r) => s + (r.violations?.critical || 0), 0);
    const highV = agenticFlatData.reduce((s, r) => s + (r.violations?.high || 0), 0);
    const medV = agenticFlatData.reduce((s, r) => s + (r.violations?.medium || 0), 0);
    const lowV = agenticFlatData.reduce((s, r) => s + (r.violations?.low || 0), 0);
    const totalV = critV + highV + medV + lowV;

    const agenticViolationRows = useMemo(() => {
        const collectionIds = new Set();
        agenticFlatData.forEach((r) =>
            (r.collectionIds || []).forEach((id) => collectionIds.add(Number(id))),
        );
        return violationRows.filter((v) => collectionIds.has(Number(v.apiCollectionId)));
    }, [agenticFlatData, violationRows]);

    const assetSeries = useMemo(
        () =>
            anchorSeriesToTotal(
                cumulativeByMonth(agenticFlatData, (r) => r.lastSeenEpoch || 0, startTimestamp, endTimestamp),
                total,
            ),
        [agenticFlatData, startTimestamp, endTimestamp, total],
    );

    const violSeries = useMemo(
        () =>
            anchorSeriesToTotal(
                cumulativeByMonth(agenticViolationRows, (v) => v.timeEpoch || 0, startTimestamp, endTimestamp),
                totalV,
            ),
        [agenticViolationRows, startTimestamp, endTimestamp, totalV],
    );

    const windowDelta = (counts) =>
        counts && counts.length >= 2 ? Math.max(0, counts[counts.length - 1] - counts[0]) : 0;
    const assetDelta = windowDelta(assetSeries.counts);
    const violDelta = windowDelta(violSeries.counts);

    const typeBreakdown = [
        { label: "Agents", count: aiCount, color: "#9642FC", typeKey: "AI Agent" },
        { label: "MCP Servers", count: mcpCount, color: "#4cbebb", typeKey: "MCP Server" },
        { label: "LLMs", count: llmCount, color: "#EAB308", typeKey: "LLM" },
        { label: "Skills", count: skillCount, color: "#D1D5DB", typeKey: "Skill" },
    ];
    const violBreakdown = [
        { label: "Critical", key: "critical", count: critV, color: "#DC2626" },
        { label: "High", key: "high", count: highV, color: "#F97316" },
        { label: "Medium", key: "medium", count: medV, color: "#EAB308" },
        { label: "Low", key: "low", count: lowV, color: "#D1D5DB" },
    ];

    return (
        <Card padding="0">
            <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
                <VerticalStack gap="2">
                    <Text variant="headingSm" fontWeight="semibold">Agentic Assets</Text>
                    <HorizontalStack align="space-between" blockAlign="center" gap="3">
                        <HorizontalStack gap="2" blockAlign="baseline">
                            <Text variant="heading2xl" as="p">{total}</Text>
                            {assetDelta > 0 && (
                                <Text variant="bodySm" color="success">+{assetDelta}</Text>
                            )}
                        </HorizontalStack>
                        <SmoothAreaChart
                            tickPositions={assetSeries.counts}
                            color="#9642FC"
                            height={40}
                            width={160}
                            labels={assetSeries.labels}
                            enableHover
                        />
                    </HorizontalStack>
                    <VerticalStack gap="2">
                        {total > 0 && <SegmentBar segments={typeBreakdown} />}
                        <HorizontalStack gap="3" wrap>
                            {typeBreakdown.map((b) => {
                                const active = activeTypeFilter?.has(b.typeKey);
                                return (
                                    <Box
                                        key={b.label}
                                        onClick={() => onTypeFilter?.(b.typeKey)}
                                        paddingInlineStart="2"
                                        paddingInlineEnd="2"
                                        paddingBlockStart="1"
                                        paddingBlockEnd="1"
                                        borderRadius="full"
                                        className="agentic-chip"
                                    >
                                        <HorizontalStack gap="1" blockAlign="center">
                                            <LegendDot color={b.color} active={active} />
                                            <Text variant="bodySm" color="subdued">
                                                {b.label} ({b.count})
                                            </Text>
                                        </HorizontalStack>
                                    </Box>
                                );
                            })}
                        </HorizontalStack>
                    </VerticalStack>
                </VerticalStack>
            </Box>
            <Box paddingBlockStart="4" paddingBlockEnd="4">
                <Divider />
            </Box>
            <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="3" paddingBlockEnd="4">
                <VerticalStack gap="2">
                    <Text variant="headingSm" fontWeight="semibold">Violations</Text>
                    <HorizontalStack align="space-between" blockAlign="center" gap="3">
                        <HorizontalStack gap="2" blockAlign="baseline">
                            <Text variant="heading2xl" as="p">{totalV}</Text>
                            {violDelta > 0 && (
                                <Text variant="bodySm" color="critical">+{violDelta}</Text>
                            )}
                        </HorizontalStack>
                        <SmoothAreaChart
                            tickPositions={violSeries.counts}
                            color="#DC2626"
                            height={40}
                            width={160}
                            labels={violSeries.labels}
                            enableHover
                        />
                    </HorizontalStack>
                    <VerticalStack gap="2">
                        {totalV > 0 && <SegmentBar segments={violBreakdown} />}
                        <HorizontalStack gap="3" wrap>
                            {violBreakdown.map((b) => {
                                const active = activeViolSevFilter?.has(b.key);
                                return (
                                    <Box
                                        key={b.label}
                                        onClick={() => onViolSevFilter?.(b.key)}
                                        paddingInlineStart="2"
                                        paddingInlineEnd="2"
                                        paddingBlockStart="1"
                                        paddingBlockEnd="1"
                                        borderRadius="full"
                                        className="agentic-chip"
                                    >
                                        <HorizontalStack gap="1" blockAlign="center">
                                            <LegendDot color={b.color} active={active} />
                                            <Text variant="bodySm" color="subdued">{b.label}</Text>
                                        </HorizontalStack>
                                    </Box>
                                );
                            })}
                        </HorizontalStack>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        </Card>
    );
}
