import { useState, useEffect } from 'react';
import { Box, Text, TextField, Icon } from '@shopify/polaris';
import { SearchMinor, DeleteMinor } from '@shopify/polaris-icons';
import { getConversationsList, clearConversationFromLocal } from '../services/agenticService';
import RightSidePanel from '../../../components/RightSidePanel';

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

    return (
        <RightSidePanel
            isOpen={isOpen}
            onClose={onClose}
            title="History"
            width="480px"
        >
            {/* Search Bar */}
            <Box style={{
                marginBottom: '16px',
                background: '#F1F2F3',
                borderRadius: '8px',
                padding: '4px'
            }}>
                <TextField
                    value={searchQuery}
                    onChange={setSearchQuery}
                    placeholder="Search history"
                    autoComplete="off"
                    prefix={<Icon source={SearchMinor} />}
                />
            </Box>

            {/* History List */}
            <Box>
                {isLoading ? (
                    <Box style={{ padding: '40px', textAlign: 'center' }}>
                        <Text variant="bodyMd" tone="subdued">Loading history...</Text>
                    </Box>
                ) : filteredItems.length === 0 ? (
                    <Box style={{ padding: '40px', textAlign: 'center' }}>
                        <Text variant="bodyMd" tone="subdued">
                            {searchQuery ? 'No results found' : 'No history yet'}
                        </Text>
                    </Box>
                ) : (
                    <Box>
                        {filteredItems.map((item) => (
                            <Box
                                key={item.id}
                                style={{
                                    padding: '16px',
                                    borderBottom: '1px solid #E1E3E5',
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    transition: 'background 0.2s ease',
                                    background: hoveredItemId === item.id ? '#F6F6F7' : 'transparent'
                                }}
                                onClick={() => handleItemClick(item.id, item.title)}
                                onMouseEnter={() => setHoveredItemId(item.id)}
                                onMouseLeave={() => setHoveredItemId(null)}
                            >
                                <Box style={{
                                    flex: 1,
                                    paddingRight: '16px',
                                    overflow: 'hidden'
                                }}>
                                    <Text variant="bodySm" fontWeight="medium" as="p">
                                        <span style={{
                                            overflow: 'hidden',
                                            color: '#202223',
                                            fontFeatureSettings: "'liga' off, 'clig' off",
                                            textOverflow: 'ellipsis',
                                            fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                            fontSize: '12px',
                                            fontStyle: 'normal',
                                            fontWeight: 500,
                                            lineHeight: '16px',
                                            display: 'block',
                                            whiteSpace: 'nowrap'
                                        }}>
                                            {item.title}
                                        </span>
                                    </Text>
                                </Box>
                                <Box style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                    <Text variant="bodySm" tone="subdued">
                                        {item.time}
                                    </Text>
                                    {hoveredItemId === item.id && (
                                        <Box
                                            onClick={(e) => handleDelete(e, item.id)}
                                            style={{
                                                cursor: 'pointer',
                                                padding: '4px',
                                                borderRadius: '4px',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                transition: 'background 0.2s ease'
                                            }}
                                            onMouseEnter={(e) => {
                                                e.currentTarget.style.background = '#E1E3E5';
                                            }}
                                            onMouseLeave={(e) => {
                                                e.currentTarget.style.background = 'transparent';
                                            }}
                                        >
                                            <Icon source={DeleteMinor} tone="critical" />
                                        </Box>
                                    )}
                                </Box>
                            </Box>
                        ))}
                    </Box>
                )}
            </Box>
        </RightSidePanel>
    );
}

export default AgenticHistoryModal;
