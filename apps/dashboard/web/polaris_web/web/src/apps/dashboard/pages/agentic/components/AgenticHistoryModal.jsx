import { useState } from 'react';
import { Box, Text, TextField, Icon, VerticalStack, HorizontalStack, Button } from '@shopify/polaris';
import { SearchMinor, DeleteMinor } from '@shopify/polaris-icons';
import { clearConversationFromLocal } from '../services/agenticService';
import FlyLayout from '../../../components/layouts/FlyLayout';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import TooltipText from '../../../components/shared/TooltipText';
import func from '@/util/func';

function AgenticHistoryModal({ isOpen, onClose, onHistoryClick, historyItems = [], searchQuery = '', onSearchQueryChange, isLoading = false, onDelete }) {
    const [hoveredItemId, setHoveredItemId] = useState(null);
    const handleDelete = (e, conversationId) => {
        e.stopPropagation();
        clearConversationFromLocal(conversationId);
        if (onDelete) {
            onDelete(conversationId);
        }
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
                        onChange={onSearchQueryChange}
                        placeholder="Search history"
                        autoComplete="off"
                        prefix={<Icon source={SearchMinor} />}
                        focused={false}
                    />
                </div>

                {isLoading ? (
                    <SpinnerCentered />
                ) : historyItems.length === 0 ? (
                    <HorizontalStack align='center'>
                        <Text variant="bodyMd" tone="subdued" alignment="center">
                            {searchQuery ? 'No results found' : 'No history yet'}
                        </Text>
                    </HorizontalStack>
                ) : (
                    <Box>
                        {historyItems.map((item) => (
                            <Box
                                key={item.id}
                                padding="3"
                                borderRadius='2'
                                onClick={() => handleItemClick(item.id, item.title)}
                                onMouseEnter={() => setHoveredItemId(item.id)}
                                onMouseLeave={() => setHoveredItemId(null)}
                                background={hoveredItemId === item.id ? "bg-hover" : ''}
                            >
                                <HorizontalStack align='space-between' blockAlign="center" wrap={false}>
                                    <Box maxWidth="250px">
                                    <TooltipText
                                        textProps={{ variant: "bodyMd" }}
                                        tooltip={item.title}
                                        text={item.title}
                                    />
                                    </Box>
                                    {
                                        hoveredItemId === item.id ? (
                                            <Button monochrome icon={DeleteMinor} plain
                                                onClick={(e) => handleDelete(e, item.id)}
                                            />
                                        ) : <Text variant="bodyMd" tone="subdued">{func.prettifyEpoch(item.lastUpdatedAt)}</Text>
                                    }
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
