import { useState, useEffect } from 'react';
import { Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';
import { getConversationsList } from '../services/agenticService';

function AgenticHistoryCards({ onHistoryClick, onViewAllClick }) {
    const [historyItems, setHistoryItems] = useState([]);

    useEffect(() => {
        const loadHistory = async () => {
            try {
                const conversations = await getConversationsList(3); // Get 3 most recent
                setHistoryItems(conversations);
            } catch (error) {
                console.error('Error loading conversation history:', error);
                setHistoryItems([]);
            }
        };

        loadHistory();
    }, []);

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
                        <Button plain onClick={onViewAllClick}>
                            <Text variant="bodySm" as="span" tone="interactive">
                                View all
                            </Text>
                        </Button>
                    </HorizontalStack>
                </Box>

                <HorizontalGrid columns={{ xs: 1, sm: 2, md: 3 }} gap="4">
                    {historyItems.map((item) => (
                        <div key={item.id} onClick={() => onHistoryClick(item.id)} style={{ cursor: 'pointer' }}>
                            <Card>
                                <VerticalStack gap="8" align="space-between">
                                    <Text variant="bodySm" fontWeight="medium" as="p">
                                        {item.title}
                                    </Text>
                                    <Text variant="bodyXs" tone="subdued" as="span">
                                        {item.time}
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
