import { HorizontalStack, Text } from "@shopify/polaris";
import { LegendDot } from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import StatusToggle from "../shared/StatusToggle";
import { STATUS_META, STATUS_ORDER } from "../constants";

// Status colour key; each entry filters the chart to that status, click again to clear.
export default function StatusLegend({ statuses = STATUS_ORDER, colors, selected, onSelect }) {
    return (
        <HorizontalStack gap="1">
            {statuses.map((status) => {
                const dimmed = selected && selected !== status;
                return (
                    <StatusToggle
                        key={status}
                        onClick={() => onSelect(selected === status ? null : status)}
                        accessibilityLabel={`Show only ${STATUS_META[status].label}`}
                        selected={selected === status}
                        dimmed={dimmed}
                        paddingBlockStart="05"
                        paddingBlockEnd="05"
                        paddingInlineStart="2"
                        paddingInlineEnd="2"
                    >
                        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                            <LegendDot color={colors ? colors[status] : STATUS_META[status].color} />
                            <Text variant="bodySm" color={dimmed || !selected ? "subdued" : undefined} fontWeight={selected === status ? "semibold" : undefined}>
                                {STATUS_META[status].label}
                            </Text>
                        </HorizontalStack>
                    </StatusToggle>
                );
            })}
        </HorizontalStack>
    );
}
