import { useMemo, useState } from "react";
import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import DonutChart from "@/apps/dashboard/components/shared/DonutChart";
import DiscoveryCard from "../shared/DiscoveryCard";
import StatusToggle from "../shared/StatusToggle";
import { CATEGORY_LABELS, STATUS_META, STATUS_ORDER } from "../constants";
import "../shared/discovery.css";

const DONUT_SIZE = 160;
const INNER_SIZE = "65%";
// DonutChart adds a margin around the ring; the wrapper keeps the flex row from squeezing it.
const DONUT_MARGIN = 20;
const COUNT_COLUMN_WIDTH = "40px";
const PERCENT_COLUMN_WIDTH = "44px";
// The categories the donut covers, named as on the tiles at the top of the page.
const COVERED_CATEGORIES = [CATEGORY_LABELS.agents, CATEGORY_LABELS.ide, CATEGORY_LABELS.mcp, CATEGORY_LABELS.browser].join(", ");

// DonutChart wants { label: { text: count, color } }; a picked status leaves only its slice.
const donutData = (counts, selected) => Object.fromEntries(STATUS_ORDER.filter((key) => !selected || key === selected).map((key) => [
    STATUS_META[key].label,
    { text: counts[key] || 0, color: STATUS_META[key].color },
]));

function LegendRow({ statusKey, count, total, dimmed, selected, onClick }) {
    return (
        <StatusToggle onClick={onClick} accessibilityLabel={`Show only ${STATUS_META[statusKey].label}, ${count} tools`} selected={selected} dimmed={dimmed}>
            <Box padding="1">
                <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                        <span className="discovery-swatch" style={{ "--swatch-color": STATUS_META[statusKey].color }} />
                        <Text as="span">{STATUS_META[statusKey].label}</Text>
                    </HorizontalStack>
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        <Box minWidth={COUNT_COLUMN_WIDTH}><Text as="p" fontWeight="semibold" alignment="end">{count}</Text></Box>
                        <Box minWidth={PERCENT_COLUMN_WIDTH}><Text as="p" color="subdued" alignment="end">{Math.round((100 * count) / total)}%</Text></Box>
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
        </StatusToggle>
    );
}

export default function StatusDonutCard({ counts, status }) {
    const total = counts?.total || 0;
    const [selected, setSelected] = useState(null);
    const data = useMemo(() => (counts ? donutData(counts, selected) : {}), [counts, selected]);
    return (
        <DiscoveryCard
            title="Inventory by status"
            subtitle={counts ? `${total} across ${COVERED_CATEGORIES}` : undefined}
            status={status}
            empty={total === 0}
            emptyText="No tools discovered yet."
        >
            {counts && (
                <HorizontalStack gap="8" blockAlign="center" wrap={false}>
                    <Box minWidth={`${DONUT_SIZE + DONUT_MARGIN}px`}>
                        <DonutChart
                            data={data}
                            size={DONUT_SIZE}
                            pieInnerSize={INNER_SIZE}
                            title={String(selected ? counts[selected] || 0 : counts.needsDecision || 0)}
                            subtitle={selected ? STATUS_META[selected].label.toLowerCase() : "need a decision"}
                            titleFontWeight="600"
                            sliceBorderWidth={0}
                        />
                    </Box>
                    <Box width="100%">
                        <VerticalStack gap="2">
                            {STATUS_ORDER.map((key) => (
                                <LegendRow
                                    key={key}
                                    statusKey={key}
                                    count={counts[key] || 0}
                                    total={total}
                                    dimmed={selected && selected !== key}
                                    selected={selected === key}
                                    onClick={() => setSelected(selected === key ? null : key)}
                                />
                            ))}
                        </VerticalStack>
                    </Box>
                </HorizontalStack>
            )}
        </DiscoveryCard>
    );
}
