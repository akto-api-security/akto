import { HorizontalStack, Text } from "@shopify/polaris";
import { LegendDot } from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import StatusToggle from "../shared/StatusToggle";

// Colour key; items are [{ key, label, color }], each filters its chart to that key, click again to clear.
export default function FilterLegend({ items, selected, onSelect }) {
    return (
        <HorizontalStack gap="1">
            {items.map((item) => {
                const dimmed = selected && selected !== item.key;
                return (
                    <StatusToggle
                        key={item.key}
                        onClick={() => onSelect(selected === item.key ? null : item.key)}
                        accessibilityLabel={`Show only ${item.label}`}
                        selected={selected === item.key}
                        dimmed={dimmed}
                        paddingBlockStart="05"
                        paddingBlockEnd="05"
                        paddingInlineStart="2"
                        paddingInlineEnd="2"
                    >
                        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                            <LegendDot color={item.color} />
                            <Text variant="bodySm" color={dimmed || !selected ? "subdued" : undefined} fontWeight={selected === item.key ? "semibold" : undefined}>
                                {item.label}
                            </Text>
                        </HorizontalStack>
                    </StatusToggle>
                );
            })}
        </HorizontalStack>
    );
}
