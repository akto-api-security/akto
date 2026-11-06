import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";
import DiscoveryCard from "../shared/DiscoveryCard";
import { RISKY_CONFIG_META } from "../constants";

// Each row asks Atlas its question about that configuration.
export default function RiskyConfigsCard({ rows, status, onAsk }) {
    return (
        <DiscoveryCard title="Risky configurations" status={status}>
            <VerticalStack gap="2">
                {(rows || []).map((row) => {
                    const meta = RISKY_CONFIG_META[row.id];
                    return (
                        <Pressable key={row.id} onClick={() => onAsk(meta.question)} accessibilityLabel={`Ask Atlas: ${meta.question}`} hoverBackground="bg-subdued" borderRadius="2">
                            <Box padding="1">
                                <HorizontalStack align="space-between" gap="3" wrap={false}>
                                    <VerticalStack gap="0" inlineAlign="start">
                                        <Text as="p" fontWeight="medium">{meta.label}</Text>
                                        <Text as="p" variant="bodySm" color="subdued">{meta.sub}</Text>
                                    </VerticalStack>
                                    <Text as="span" fontWeight="semibold" color={row.count > 0 ? "critical" : undefined}>{row.count}</Text>
                                </HorizontalStack>
                            </Box>
                        </Pressable>
                    );
                })}
            </VerticalStack>
        </DiscoveryCard>
    );
}
