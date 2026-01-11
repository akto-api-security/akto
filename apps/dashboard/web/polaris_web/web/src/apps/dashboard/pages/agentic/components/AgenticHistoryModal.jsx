import { useState, useEffect } from 'react';
import { Box, Text, TextField, Icon, VerticalStack, HorizontalStack, Button } from '@shopify/polaris';
import { SearchMinor, DeleteMinor } from '@shopify/polaris-icons';
import { getConversationsList, clearConversationFromLocal } from '../services/agenticService';
import FlyLayout from '../../../components/layouts/FlyLayout';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import TooltipText from '../../../components/shared/TooltipText';

function AgenticHistoryModal({ isOpen, onClose, onHistoryClick }) {
    const [historyItems, setHistoryItems] = useState([]);
    const [filteredItems, setFilteredItems] = useState([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [hoveredItemId, setHoveredItemId] = useState(null);
    const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        if (isOpen) {
            loadHistory();
        }
    }, [isOpen]);

    useEffect(() => {
        // Filter history based on search query
        if (searchQuery.trim()) {
            const filtered = historyItems.filter(item =>
                item.title.toLowerCase().includes(searchQuery.toLowerCase())
            );
            setFilteredItems(filtered);
        } else {
            setFilteredItems(historyItems);
        }
    }, [searchQuery, historyItems]);

    const loadHistory = async () => {
        setIsLoading(true);
        try {
            const conversations = await getConversationsList(50); // Load more for modal
            setHistoryItems(conversations);
            setFilteredItems(conversations);
        } catch (error) {
            console.error('Error loading history:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const handleDelete = (e, conversationId) => {
        e.stopPropagation(); // Prevent opening conversation when deleting
        clearConversationFromLocal(conversationId);
        loadHistory(); // Reload after delete
    };

    const handleItemClick = (conversationId, title) => {
        onHistoryClick(conversationId, title);
        onClose();
    };

    const renderHistory = (
        <Box padding={"3"}>
            <VerticalStack gap="4">
                <div className="agentic-history-modal-search">
                    <TextField
                        value={searchQuery}
                        onChange={setSearchQuery}
                        placeholder="Search history"
                        autoComplete="off"
                        prefix={<Icon source={SearchMinor} />}
                        focused={false}
                    />
                </div>

                {isLoading ? (
                    <SpinnerCentered />
                ) : filteredItems.length === 0 ? (
                    <HorizontalStack align='center'>
                        <Text variant="bodyMd" tone="subdued" alignment="center">
                            {searchQuery ? 'No results found' : 'No history yet'}
                        </Text>
                    </HorizontalStack>
                ) : (
                    <Box>
                        {filteredItems.map((item) => (
                            <Box
                                key={item.id}
                                padding="3"
                                borderRadius='2'
                                onClick={() => handleItemClick(item.id, item.title)}
                                onMouseEnter={() => setHoveredItemId(item.id)}
                                onMouseLeave={() => setHoveredItemId(null)}
                                background={hoveredItemId === item.id ? 'bg-fill-subdued' : ''}
                            >
                                <HorizontalStack align='space-between' blockAlign="center">
                                    <Box maxWidth="300px">
                                        <TooltipText
                                            textProps={{ fontWeight: "bold", variant: "bodyMd" }} 
                                            tooltip={item.title}
                                            text={item.title}
                                        />
                                        {
                                            hoveredItemId === item.id ? (
                                                <Button monochrome icon={DeleteMinor} plain
                                                    onClick={() => handleDelete(item.id)}
                                                />
                                            ) : <Text variant="bodyMd" tone="subdued">{item.time}</Text>
                                        }
                                    </Box>
                                </HorizontalStack>
                            </Box>
                        ))}
                    </Box>
                )}
            </VerticalStack>
        </Box>
    )

    return (
        <FlyLayout
            title={"History"}
            show={isOpen}
            setShow={onClose}
            components={[renderHistory]}
            loading={false}
            showDivider={true}
            newComp={true}
            isHandleClose={false}
            width="400px"
        />
    );
}

export default AgenticHistoryModal;
