import { useNavigate } from "react-router-dom";
import { Box, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "@/apps/dashboard/pages/dashboard/ask/components/Pressable";
import DiscoveryCard from "../shared/DiscoveryCard";
import { GOVERNED_INVENTORY_CATEGORIES, RISKY_CONFIG_META } from "../constants";
import openInventory from "../shared/openInventory";

// A row opens the inventory on the tools with that configuration; one the inventory cannot filter asks Atlas instead.
export default function RiskyConfigsCard({ rows, status, onAsk }) {
    const navigate = useNavigate();
    return (
        <DiscoveryCard title="Risky configurations" status={status}>
            <VerticalStack gap="2">
                {(rows || []).map((row) => {
                    const meta = RISKY_CONFIG_META[row.id];
                    if (!meta) return null;
                    return (
                        <Pressable key={row.id} onClick={() => (meta.filters ? openInventory(navigate, meta.categories || GOVERNED_INVENTORY_CATEGORIES, meta.filters) : onAsk(meta.question))} accessibilityLabel={meta.filters ? `Open ${meta.label} in the inventory` : `Ask Atlas: ${meta.question}`} hoverBackground="bg-subdued" borderRadius="2">
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
