import { Box, Card, HorizontalGrid, Text, VerticalStack } from "@shopify/polaris";

export default function KpiStrip({ items }) {
    return (
        <Card padding="0">
            <HorizontalGrid columns={{ xs: 1, sm: 2, md: items.length }}>
                {items.map((item, i) => (
                    <div key={item.label} onClick={item.onClick} style={{ cursor: item.onClick ? "pointer" : "default" }}>
                        <Box padding="4" minHeight="100%" borderInlineEndWidth={i < items.length - 1 ? "1" : undefined} borderColor="border-subdued">
                            <VerticalStack gap="1">
                                <Text variant="bodySm" color="subdued">{item.label}</Text>
                                <Text variant="headingXl" as="p" color={item.critical ? "critical" : undefined}>{item.value}</Text>
                                <Text variant="bodySm" color="subdued">{item.sub}</Text>
                            </VerticalStack>
                        </Box>
                    </div>
                ))}
            </HorizontalGrid>
        </Card>
    );
}
