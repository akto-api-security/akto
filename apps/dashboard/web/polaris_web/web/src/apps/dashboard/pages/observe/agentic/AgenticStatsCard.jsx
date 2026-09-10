import React from "react";
import {
    Box,
    Card,
    HorizontalStack,
    Text,
    VerticalStack,
} from "@shopify/polaris";
import InfoTooltipIcon from "@/apps/dashboard/components/shared/InfoTooltipIcon";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";
import observeFunc from "../transform"

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
    return <Box className="agentic-dot" style={{ "--dot-color": color }} />;
}

function BreakdownChip({ b, activeFilter, onFilterClick }) {
    const active = activeFilter?.has(b.key ?? b.label);
    return (
        <Box
            onClick={() => onFilterClick?.(b.key ?? b.label)}
            className="agentic-chip"
            style={active ? { borderColor: "var(--p-color-border)", background: "var(--p-color-bg-subdued)" } : undefined}
        >
            <HorizontalStack gap="1" blockAlign="center">
                <LegendDot color={b.color} />
                <Text variant="bodySm" color={active ? undefined : "subdued"} fontWeight={active ? "semibold" : undefined}>
                    {b.label} ({typeof b.count === "number" ? b.count.toLocaleString("en-US") : b.count})
                </Text>
            </HorizontalStack>
        </Box>
    );
}

function BreakdownLegend({ breakdown, onFilterClick, activeFilter }) {
    return (
        <HorizontalStack gap="1">
            {breakdown.map((b) => (
                <BreakdownChip key={b.label} b={b} activeFilter={activeFilter} onFilterClick={onFilterClick} />
            ))}
        </HorizontalStack>
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
    titleTooltip,
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
    bodyGap = "2",
    sparklineWidth = 120,
}) {
    const inner = (
        <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
            <VerticalStack gap={bodyGap}>
                <HorizontalStack gap="1" blockAlign="center">
                    <Text variant="headingSm" fontWeight="semibold">{title}</Text>
                    <InfoTooltipIcon content={titleTooltip} />
                </HorizontalStack>
                {/* wrap={false}: in a narrow card (e.g. the 280px Endpoints column) the value
                    plus a 160px sparkline overflowed and the chart dropped to a second line. */}
                <HorizontalStack align="space-between" blockAlign="center" gap="3" wrap={false}>
                    <HorizontalStack gap="2" blockAlign="baseline" wrap={false}>
                        <Text variant="heading2xl" as="p" color={totalColor}>{observeFunc.formatNumberWithCommas(total)}</Text>
                        {delta > 0 && <Text variant="bodySm" color="subdued">+{observeFunc.formatNumberWithCommas(delta)}</Text>}
                        {delta < 0 && <Text variant="bodySm" color="subdued">{observeFunc.formatNumberWithCommas(delta)}</Text>}
                    </HorizontalStack>
                    {sparklineCounts && (
                        <SmoothAreaChart
                            tickPositions={sparklineCounts}
                            color={sparklineColor}
                            height={40}
                            width={sparklineWidth}
                            labels={sparklineLabels}
                            enableHover
                        />
                    )}
                </HorizontalStack>
                <VerticalStack gap="2">
                    {breakdown.length > 0 && <SegmentBar segments={breakdown} />}
                    {breakdown.length > 0 && (
                        <BreakdownLegend
                            breakdown={breakdown}
                            onFilterClick={onFilterClick}
                            activeFilter={activeFilter}
                        />
                    )}
                </VerticalStack>
                {children}
            </VerticalStack>
        </Box>
    );

    if (noCard) return inner;
    return <Card padding="0">{inner}</Card>;
}
