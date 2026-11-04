import { HorizontalStack, Text } from "@shopify/polaris";
import { LegendDot } from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";
import { STATUS_META, STATUS_ORDER } from "../constants";

// Status colour key; each entry filters the chart to that status, click again to clear.
export default function StatusLegend({ statuses = STATUS_ORDER, colors, selected, onSelect }) {
    return (
        <HorizontalStack gap="1">
            {statuses.map((status) => {
                const dimmed = selected && selected !== status;
                const entry = (
                    <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                        <LegendDot color={colors ? colors[status] : STATUS_META[status].color} />
                        <Text variant="bodySm" color={dimmed || !selected ? "subdued" : undefined} fontWeight={selected === status ? "semibold" : undefined}>
                            {STATUS_META[status].label}
                        </Text>
                    </HorizontalStack>
                );
                return (
                    <Pressable
                        key={status}
                        onClick={() => onSelect(selected === status ? null : status)}
                        accessibilityLabel={`Show only ${STATUS_META[status].label}`}
                        aria-pressed={selected === status}
                        hoverBackground="bg-subdued"
                        borderRadius="2"
                        opacity={dimmed ? "0.5" : undefined}
                        paddingBlockStart="05"
                        paddingBlockEnd="05"
                        paddingInlineStart="2"
                        paddingInlineEnd="2"
                    >
                        {entry}
                    </Pressable>
                );
            })}
        </HorizontalStack>
    );
}
