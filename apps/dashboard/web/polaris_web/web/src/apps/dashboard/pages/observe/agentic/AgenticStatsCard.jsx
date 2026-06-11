import React from "react";
import {
    Box,
    Card,
    HorizontalStack,
    Text,
    VerticalStack,
} from "@shopify/polaris";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";

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

function LegendDot({ color }) {
    return (
        <span
            className="agentic-dot"
            style={{ "--dot-color": color }}
        />
    );
}

function violationTotal(row) {
    const v = row?.violations;
    if (!v) return 0;
    return (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0);
}

export { violationTotal };

export default function AgenticStatsCard({
    title,
    total,
    totalColor,
    delta,
    sparklineCounts,
    sparklineColor = "#9642FC",
    sparklineLabels,
    breakdown = [],
    onFilterClick,
    activeFilter,
    noCard,
    children,
}) {
    const inner = (
        <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
            <VerticalStack gap="2">
                <Text variant="headingSm" fontWeight="semibold">{title}</Text>
                <HorizontalStack align="space-between" blockAlign="center" gap="3">
                    <HorizontalStack gap="2" blockAlign="baseline">
                        <Text variant="heading2xl" as="p" color={totalColor}>{total}</Text>
                        {delta > 0 && <Text variant="bodySm" color="subdued">+{delta}</Text>}
                        {delta < 0 && <Text variant="bodySm" color="subdued">{delta}</Text>}
                    </HorizontalStack>
                    {sparklineCounts && (
                        <SmoothAreaChart
                            tickPositions={sparklineCounts}
                            color={sparklineColor}
                            height={40}
                            width={160}
                            labels={sparklineLabels}
                            enableHover
                        />
                    )}
                </HorizontalStack>
                <VerticalStack gap="2">
                    {breakdown.length > 0 && <SegmentBar segments={breakdown} />}
                    {breakdown.length > 0 && (
                        <HorizontalStack gap="2" wrap>
                            {breakdown.map((b) => {
                                const active = activeFilter?.has(b.key ?? b.label);
                                return (
                                    <div
                                        key={b.label}
                                        onClick={() => onFilterClick?.(b.key ?? b.label)}
                                        className={active ? "agentic-chip agentic-chip--active" : "agentic-chip"}
                                    >
                                        <HorizontalStack gap="1" blockAlign="center">
                                            <LegendDot color={b.color} />
                                            <Text variant="bodySm" color="subdued">
                                                {b.label} ({b.count})
                                            </Text>
                                        </HorizontalStack>
                                    </div>
                                );
                            })}
                        </HorizontalStack>
                    )}
                </VerticalStack>
                {children}
            </VerticalStack>
        </Box>
    );

    if (noCard) return inner;
    return <Card padding="0">{inner}</Card>;
}
