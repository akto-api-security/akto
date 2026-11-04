import { HorizontalStack, Text } from "@shopify/polaris";
import { LegendDot } from "@/apps/dashboard/pages/observe/agentic/AgenticStatsCard";
import { CHAIN_LEGEND } from "../constants";

// Supply chain cues: node borders by status, red for a risky path.
export default function ColorLegend() {
    return (
        <HorizontalStack gap="3">
            {CHAIN_LEGEND.map((item) => (
                <HorizontalStack key={item.label} gap="1" blockAlign="center" wrap={false}>
                    <LegendDot color={item.color} />
                    <Text variant="bodySm" color="subdued">{item.label}</Text>
                </HorizontalStack>
            ))}
        </HorizontalStack>
    );
}
