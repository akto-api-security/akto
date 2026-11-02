import { HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "../../../dashboard/ask/components/Pressable";
import InfoCard from "../../../dashboard/new_components/InfoCard";
import { LegendDot } from "../../agentic/AgenticStatsCard";
import SegmentedBar from "./SegmentedBar";
import { CATEGORY_BACKGROUNDS, CATEGORY_LABELS, CATEGORY_ORDER, OS_LABELS, tokenVar } from "./peopleConstants";
import transform from "./transform";

const DARK_CATEGORIES = ["agents"];

function AiToolsByOsCard({ overview, status, activeCategory, onSelect, onSelectCategory }) {
    const rows = (overview?.osMatrix || []).filter((row) => row.os !== "unknown");
    const toolsAvailable = overview?.toolsAvailable !== false;

    const body = (
        <VerticalStack gap="4">
            <Text as="p" color="subdued">By operating system</Text>
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
                            background: CATEGORY_BACKGROUNDS[category],
                            lightText: DARK_CATEGORIES.includes(category),
                            tooltip: `${CATEGORY_LABELS[category]} on ${OS_LABELS[row.os]}: ${row.toolsByCategory[category] || 0} tools`,
                        }))}
                        onSelect={(category) => onSelect(row.os, category)}
                    />
                </VerticalStack>
            ))}
            <HorizontalStack gap="3" wrap>
                {CATEGORY_ORDER.map((category) => (
                    <Pressable
                        key={category}
                        accessibilityLabel={CATEGORY_LABELS[category]}
                        onClick={() => onSelectCategory(category)}
                        background={activeCategory === category ? "bg-primary-subdued-hover" : undefined}
                        hoverBackground="bg-subdued"
                        borderRadius="2"
                        padding="1"
                    >
                        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                            <LegendDot color={tokenVar(CATEGORY_BACKGROUNDS[category])} />
                            <Text as="span" variant="bodySm" color={activeCategory === category ? undefined : "subdued"}>
                                {CATEGORY_LABELS[category]} · {(overview?.categoryTotals?.[category] || 0).toLocaleString("en-US")}
                            </Text>
                        </HorizontalStack>
                    </Pressable>
                ))}
            </HorizontalStack>
        </VerticalStack>
    );

    return (
        <InfoCard
            title="AI tools per device"
            titleToolTip="Share of AI tools on each operating system, by tool category. Click a segment or a category to filter the table."
            component={transform.cardBody(status, body)}
        />
    );
}

export default AiToolsByOsCard;
