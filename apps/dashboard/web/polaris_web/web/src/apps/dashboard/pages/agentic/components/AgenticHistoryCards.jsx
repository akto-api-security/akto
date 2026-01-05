import { useState, useEffect } from 'react';
import { Box, Text, HorizontalStack } from '@shopify/polaris';
import { getConversationsList } from '../services/agenticService';

function AgenticHistoryCards({ onHistoryClick, onViewAllClick }) {
    const [historyItems, setHistoryItems] = useState([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        // Load conversation history from service
        const loadHistory = async () => {
            setIsLoading(true);
            try {
                const conversations = await getConversationsList(3); // Get 3 most recent
                setHistoryItems(conversations);
            } catch (error) {
                console.error('Error loading conversation history:', error);
                setHistoryItems([]);
            } finally {
                setIsLoading(false);
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
            {/* Whole container divided into two parts with gap of 16px */}
            <Box style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {/* First box: Header section with History text (left) and View all button (right) */}
                <Box width="100%">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingSm" as="h2">
                            History
                        </Text>
                        <div
                            onClick={onViewAllClick}
                            style={{ cursor: 'pointer' }}
                        >
                            <Text
                                variant="bodySm"
                                as="span"
                                tone="interactive"
                            >
                                View all
                            </Text>
                        </div>
                    </HorizontalStack>
                </Box>

                {/* Second container: Tiles with horizontal gap of 16px */}
                <Box width="100%" style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                    {historyItems.map((item) => (
                        <Box
                            key={item.id}
                            onClick={() => onHistoryClick && onHistoryClick(item.id, item.title)}
                            style={{
                                cursor: 'pointer',
                                padding: '12px', // Inner padding of 12px for each card
                                borderRadius: '8px',
                                background: '#FAFAFA',
                                boxShadow: '0 0 5px 0 rgba(0, 0, 0, 0.05), 0 1px 2px 0 rgba(0, 0, 0, 0.15)',
                                flex: '0 0 calc(33.333% - 11px)', // 3 columns with 16px gap, no grow
                                minWidth: '150px',
                                maxWidth: 'calc(33.333% - 11px)' // Prevent single card from stretching
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
