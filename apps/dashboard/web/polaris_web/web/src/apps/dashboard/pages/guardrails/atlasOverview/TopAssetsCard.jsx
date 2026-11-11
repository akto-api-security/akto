import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import AssetTagBadge from "@/apps/dashboard/components/shared/AssetTagBadge";
import { OverviewCardHeader, OverviewListRow } from "./OverviewCard";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";

export default function TopAssetsCard({ assets, loading }) {
    if (loading) return <Card><VerticalStack gap="3"><OverviewCardHeader title="Top agents, apps and servers involved" /><SpinnerCentered height="200px" /></VerticalStack></Card>;

    return (
        <Card padding="0">
            <Box padding="4" paddingBlockEnd="2">
                <OverviewCardHeader title="Top agents, apps and servers involved" />
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
