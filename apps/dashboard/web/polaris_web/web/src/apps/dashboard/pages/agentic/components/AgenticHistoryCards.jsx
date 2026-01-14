import { Box, Card, HorizontalGrid, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris';
import func from '@/util/func';

function AgenticHistoryCards({ historyItems = [], onHistoryClick, onViewAllClick }) {

    // Don't render if no history
    if (historyItems.length === 0) {
        return null;
    }

    return (
        <Box width="100%">
            <VerticalStack gap="4">
                <Box width="100%">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingSm" as="h2">
                            History
                        </Text>
                        <Link onClick={onViewAllClick} monochrome>
                            <Text variant="bodyMd" color='text-primary' as="span" tone="interactive">
                                View all
                            </Text>
                        </Link>
                    </HorizontalStack>
                </Box>

                <HorizontalGrid columns={{ xs: 1, sm: 2, md: 3 }} gap="4">
                    {historyItems.map((item) => (
                        <div key={item.id} onClick={() => onHistoryClick(item.id)} style={{ cursor: 'pointer' }}>
                            <Card  background="bg-magic-subdued-active" padding="3">
                                <VerticalStack gap="8">
                                    <Text variant="bodySm" fontWeight="medium" as="p">
                                        {item.title}
                                    </Text>
                                    <Text variant="bodyXs" tone="subdued" as="span">
                                        {func.prettifyEpoch(item.lastUpdatedAt)}
                                    </Text>
                                </VerticalStack>
                            </Card>
                        </div>
                        
                    ))}
                </HorizontalGrid>
            </VerticalStack>
        </Box>
    );
}

export default AgenticHistoryCards;
