import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import AssetTagBadge from "@/apps/dashboard/components/shared/AssetTagBadge";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";

export default function TopAssetsCard({ assets }) {
    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Agents, apps and servers involved" />
            </Box>
            {assets.map((asset) => (
                <OverviewListRow key={asset.name}>
                    <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                        <Box width="100%">
                            <VerticalStack gap="0">
                                <Text variant="bodyMd" fontWeight="medium">{asset.name}</Text>
                                <Text variant="bodySm" color="subdued">{asset.kind}</Text>
                            </VerticalStack>
                        </Box>
                        <AssetTagBadge tag={asset.tag} />
                        <Box minWidth="40px">
                            <Text variant="bodyMd" fontWeight="semibold" alignment="end">{asset.count}</Text>
                        </Box>
                    </HorizontalStack>
                </OverviewListRow>
            ))}
        </Card>
    );
}
