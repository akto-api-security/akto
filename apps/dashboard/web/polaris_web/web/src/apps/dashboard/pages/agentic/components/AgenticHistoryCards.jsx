import { Box, HorizontalGrid, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris';
import func from '@/util/func';

function AgenticHistoryCards({ historyItems = [], onHistoryClick, onViewAllClick }) {

    // Don't render if no history
    if (historyItems.length === 0) {
        return null;
    }

    return (
        <Box width="550px" maxWidth="100%">
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
                        <div key={item.id} onClick={() => onHistoryClick(item.id)} style={{ cursor: 'pointer', height: '100%' }}>
                            {/* Box (not Card) so the tile can fill its grid cell and all three stay the same height */}
                            <Box background="bg-magic-subdued-active" padding="3" borderRadius="3" shadow="md" minHeight="100%">
                                <VerticalStack gap="8">
                                    {/* Room for three title lines, so the date sits at the same height on every tile */}
                                    <Box minHeight="48px">
                                        <Text variant="bodySm" fontWeight="medium" as="p" breakWord>
                                            {item.title}
                                        </Text>
                                    </Box>
                                    <Text variant="bodyXs" tone="subdued" as="span">
                                        {func.prettifyEpoch(item.lastUpdatedAt)}
                                    </Text>
                                </VerticalStack>
                            </Box>
                        </div>
                        
                    ))}
                </HorizontalGrid>
            </VerticalStack>
        </Box>
    );
}

export default AgenticHistoryCards;
