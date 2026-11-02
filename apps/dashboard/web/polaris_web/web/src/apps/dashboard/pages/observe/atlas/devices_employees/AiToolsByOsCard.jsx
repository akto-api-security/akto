import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { LegendDot } from "../../agentic/AgenticStatsCard";
import InsightCard from "./InsightCard";
import SegmentedBar from "./SegmentedBar";
import { CATEGORY_COLORS, CATEGORY_LABELS, OS_LABELS } from "./peopleConstants";

const CATEGORY_ORDER = ["agents", "ide", "mcp", "browser"];
const DARK_CATEGORIES = ["agents", "ide"];

function AiToolsByOsCard({ overview, status, onSelect }) {
    const rows = overview?.osMatrix || [];
    const toolsAvailable = overview?.toolsAvailable !== false;

    return (
        <InsightCard title="AI tools per device" subtitle="By operating system" status={status}>
            {!toolsAvailable && <Text as="p" color="subdued">Tool data is unavailable right now.</Text>}
            {toolsAvailable && rows.length === 0 && <Text as="p" color="subdued">No managed devices in this scope.</Text>}
            {toolsAvailable && rows.map((row) => (
                <VerticalStack key={row.os} gap="1">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text as="span" fontWeight="semibold">{OS_LABELS[row.os] || row.os}</Text>
                        <Text as="span" variant="bodySm" color="subdued">
                            {row.devices.toLocaleString("en-US")} devices · avg {row.avgTools} tools
                        </Text>
                    </HorizontalStack>
                    <SegmentedBar
                        segments={CATEGORY_ORDER.map((category) => ({
                            key: category,
                            label: CATEGORY_LABELS[category],
                            count: row.toolsByCategory[category] || 0,
                            color: CATEGORY_COLORS[category],
                            lightText: DARK_CATEGORIES.includes(category),
                            tooltip: `${CATEGORY_LABELS[category]} on ${OS_LABELS[row.os]}: ${row.toolsByCategory[category] || 0} tools`,
                        }))}
                        onSelect={(category) => onSelect(row.os, category)}
                    />
                </VerticalStack>
            ))}
            <HorizontalStack gap="3" wrap>
                {CATEGORY_ORDER.map((category) => (
                    <HorizontalStack key={category} gap="1" blockAlign="center" wrap={false}>
                        <LegendDot color={CATEGORY_COLORS[category]} />
                        <Text as="span" variant="bodySm" color="subdued">{CATEGORY_LABELS[category]}</Text>
                    </HorizontalStack>
                ))}
            </HorizontalStack>
        </InsightCard>
    );
}

export default AiToolsByOsCard;
