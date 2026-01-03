
import React from 'react';
import { Box, Card, Text, HorizontalGrid, VerticalStack, Icon, HorizontalStack } from '@shopify/polaris';
import { ClockMinor } from '@shopify/polaris-icons';

function AgenticHistoryCards() {
    const historyItems = [
        {
            id: 1,
            title: 'Validate agent resilience to instruction override attacks',
            time: '2 hours ago',
        },
        {
            id: 2,
            title: 'Prompt injection risks across agents',
            time: 'Yesterday',
        },
        {
            id: 3,
            title: 'Investigating agent behavior',
            time: '2 days ago',
        },
        {
            id: 4,
            title: 'Security policy update check',
            time: '3 days ago',
        },
    ];

    return (
        <Box paddingBlock="4" maxWidth="1000px" marginInline="auto">
            <Box paddingBlockEnd="4">
                <Text variant="headingMd" as="h2" color="subdued">
                    History
                </Text>
            </Box>
            <HorizontalGrid columns={{ xs: 1, sm: 2, md: 4 }} gap="4">
                {historyItems.map((item) => (
                    <Card key={item.id}>
                        {/* Using a clickable container logic if needed, for now just static layout */}
                        <Box padding="4" minHeight="120px">
                            <VerticalStack gap="4" justify="space-between">
                                <Text variant="bodyLg" fontWeight="semibold" as="h3">
                                    {item.title}
                                </Text>
                                <HorizontalStack gap="1" align="start">
                                    <Icon source={ClockMinor} color="subdued" />
                                    <Text variant="bodySm" color="subdued">
                                        {item.time}
                                    </Text>
                                </HorizontalStack>
                            </VerticalStack>
                        </Box>
                    </Card>
                ))}
            </HorizontalGrid>
        </Box>
    );
}

export default AgenticHistoryCards;
