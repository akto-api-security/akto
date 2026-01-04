import { Box, Text, HorizontalStack } from '@shopify/polaris';

function AgenticHistoryCards() {
    const historyItems = [
        {
            id: 1,
            title: 'Validate agent resilience to instruction override attacks',
            time: '1 day ago',
        },
        {
            id: 2,
            title: 'Prompt injection risks across agents',
            time: '1 day ago',
        },
        {
            id: 3,
            title: 'Investigating agent behavior',
            time: '1 day ago',
        },
    ];

    return (
        <Box width="100%">
            {/* Whole container divided into two parts with gap of 16px */}
            <Box style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {/* First box: Header section with History text (left) and View all button (right) */}
                <Box width="100%">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingSm" as="h2">
                            History
                        </Text>
                        <Text variant="bodySm" as="span" tone="interactive" style={{ cursor: 'pointer' }}>
                            View all
                        </Text>
                    </HorizontalStack>
                </Box>

                {/* Second container: Tiles with horizontal gap of 16px */}
                <Box width="100%" style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                    {historyItems.map((item) => (
                        <Box
                            key={item.id}
                            style={{
                                cursor: 'pointer',
                                padding: '12px', // Inner padding of 12px for each card
                                borderRadius: '8px',
                                background: '#FAFAFA',
                                boxShadow: '0 0 5px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.15)',
                                flex: '1 1 calc(33.333% - 11px)', // 3 columns with 16px gap
                                minWidth: '150px'
                            }}
                        >
                            {/* Each tile contains two vertical boxes with gap of 32px */}
                            <Box style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between', minHeight: '80px' }}>
                                <Text
                                    variant="bodySm"
                                    fontWeight="medium"
                                    as="p"
                                >
                                    {item.title}
                                </Text>
                                <Text variant="bodyXs" tone="subdued" as="span">
                                    {item.time}
                                </Text>
                            </Box>
                        </Box>
                    ))}
                </Box>
            </Box>
        </Box>
    );
}

export default AgenticHistoryCards;
