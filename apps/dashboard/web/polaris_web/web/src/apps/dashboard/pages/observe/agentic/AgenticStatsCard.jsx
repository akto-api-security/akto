import React, { useEffect, useRef, useState } from "react";
import {
    Badge,
    Box,
    Card,
    HorizontalStack,
    Text,
    Tooltip,
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

// Renders breakdown legend dots; shows all that fit, "+N" only when items actually overflow.
const CHIP_GAP = 4;
const OVERFLOW_BADGE_W = 48;

function BreakdownLegend({ breakdown, onFilterClick, activeFilter }) {
    const wrapRef = useRef(null);
    const measureRef = useRef(null);
    const [visibleCount, setVisibleCount] = useState(breakdown.length);

    useEffect(() => {
        const wrap = wrapRef.current;
        const measure = measureRef.current;
        if (!wrap || !measure) return;

        function recalc() {
            const availW = wrap.offsetWidth;
            const chips = measure.children;
            let used = 0;
            let count = 0;
            for (let i = 0; i < chips.length; i++) {
                const chipW = chips[i].offsetWidth;
                const isLast = i === chips.length - 1;
                const needed = used + (i > 0 ? CHIP_GAP : 0) + chipW + (!isLast ? CHIP_GAP + OVERFLOW_BADGE_W : 0);
                if (needed > availW && count > 0) break;
                used += (i > 0 ? CHIP_GAP : 0) + chipW;
                count = i + 1;
            }
            setVisibleCount(Math.max(1, count));
        }

        const ro = new ResizeObserver(recalc);
        ro.observe(wrap);
        recalc();
        return () => ro.disconnect();
    }, [breakdown]);

    const overflow = breakdown.length - visibleCount;

    return (
        <Box ref={wrapRef} position="relative">
            {/* Hidden full-set layer for measuring natural chip widths */}
            <Box ref={measureRef} aria-hidden="true" className="breakdown-legend-measure">
                {breakdown.map((b) => (
                    <Box key={b.label} className="agentic-chip">
                        <LegendDot color={b.color} />
                        <Text variant="bodySm" truncate>
                            {b.label} ({typeof b.count === "number" ? b.count.toLocaleString("en-US") : b.count})
                        </Text>
                    </Box>
                ))}
            </Box>
            {/* Visible row */}
            <HorizontalStack gap="1" wrap={false}>
                {breakdown.slice(0, visibleCount).map((b) => {
                    const active = activeFilter?.has(b.key ?? b.label);
                    return (
                        <Box
                            key={b.label}
                            onClick={() => onFilterClick?.(b.key ?? b.label)}
                            className={active ? "agentic-chip agentic-chip--active" : "agentic-chip"}
                        >
                            <HorizontalStack gap="1" blockAlign="center">
                                <LegendDot color={b.color} />
                                <Text variant="bodySm" color="subdued">
                                    {b.label} ({typeof b.count === "number" ? b.count.toLocaleString("en-US") : b.count})
                                </Text>
                            </HorizontalStack>
                        </Box>
                    );
                })}
                {overflow > 0 && (
                    <Tooltip
                        content={
                            <VerticalStack gap="1">
                                {breakdown.slice(visibleCount).map((b) => (
                                    <HorizontalStack key={b.label} gap="1" blockAlign="center">
                                        <LegendDot color={b.color} />
                                        <Text variant="bodySm">
                                            {b.label} ({typeof b.count === "number" ? b.count.toLocaleString("en-US") : b.count})
                                        </Text>
                                    </HorizontalStack>
                                ))}
                            </VerticalStack>
                        }
                    >
                        <Box className="cursor-pointer">
                            <Badge size="small">+{overflow}</Badge>
                        </Box>
                    </Tooltip>
                )}
            </HorizontalStack>
        </Box>
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
}) {
    const inner = (
        <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3">
            <VerticalStack gap={bodyGap}>
                <HorizontalStack gap="1" blockAlign="center">
                    <Text variant="headingSm" fontWeight="semibold">{title}</Text>
                    <InfoTooltipIcon content={titleTooltip} />
                </HorizontalStack>
                <HorizontalStack align="space-between" blockAlign="center" gap="3">
                    <HorizontalStack gap="2" blockAlign="baseline">
                        <Text variant="heading2xl" as="p" color={totalColor}>{observeFunc.formatNumberWithCommas(total)}</Text>
                        {delta > 0 && <Text variant="bodySm" color="subdued">+{observeFunc.formatNumberWithCommas(delta)}</Text>}
                        {delta < 0 && <Text variant="bodySm" color="subdued">{observeFunc.formatNumberWithCommas(delta)}</Text>}
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
