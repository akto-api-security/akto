import { useState, useCallback, useEffect } from 'react';
import { Page, Box, VerticalStack } from '@shopify/polaris';
import AgenticWelcomeHeader from './components/AgenticWelcomeHeader';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticSuggestions from './components/AgenticSuggestions';
import AgenticHistoryCards from './components/AgenticHistoryCards';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import AgenticConversationPageV2 from './AgenticConversationPageV2';
import { setupMockAIAdapter } from './services/mockAIAdapter';

/**
 * AgenticMainPageV2 - AI SDK Compatible
 *
 * Main landing page for agentic AI features.
 * Uses AgenticSearchInput with controlled state pattern.
 */
function AgenticMainPageV2() {
    // Initialize mock adapter once
    useEffect(() => {
        setupMockAIAdapter();
    }, []);

    const username = window.USER_FULL_NAME || window.USER_NAME || "User";

    // Local state for the search input on main page
    const [searchInput, setSearchInput] = useState('');
    const [showConversation, setShowConversation] = useState(false);
    const [currentQuery, setCurrentQuery] = useState('');
    const [loadConversationId, setLoadConversationId] = useState(null);
    const [showHistoryModal, setShowHistoryModal] = useState(false);

    // Handle search submission
    const handleSearchSubmit = useCallback((e) => {
        if (e && typeof e.preventDefault === 'function') {
            e.preventDefault();
        }

        if (searchInput.trim()) {
            console.log('Search submitted:', searchInput);
            setCurrentQuery(searchInput);
            setLoadConversationId(null);
            setShowConversation(true);
        }
    }, [searchInput]);

    // Handle input change
    const handleInputChange = useCallback((e) => {
        const value = typeof e === 'string' ? e : e.target?.value || '';
        setSearchInput(value);
    }, []);

    const handleSuggestionClick = useCallback((suggestion) => {
        setSearchInput(suggestion);
        setCurrentQuery(suggestion);
        setLoadConversationId(null);
        setShowConversation(true);
    }, []);

    const handleHistoryClick = useCallback((conversationId, title) => {
        console.log('History clicked:', conversationId, title);
        setLoadConversationId(conversationId);
        setCurrentQuery('');
        setShowConversation(true);
    }, []);

    const handleViewAllClick = useCallback(() => {
        setShowHistoryModal(true);
    }, []);

    // If conversation is active, show the AI SDK powered conversation page
    if (showConversation) {
        return (
            <AgenticConversationPageV2
                initialQuery={currentQuery}
                existingConversationId={loadConversationId}
                onBack={() => {
                    setShowConversation(false);
                    setSearchInput('');
                }}
            />
        );
    }

    return (
       <Page fullWidth>
            <Box
                style={{
                    height: '100vh',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '40px 20px',
                    overflow: 'hidden',
                    margin: '-20px -32px',
                    width: 'calc(100% + 64px)',
                    background: 'radial-gradient(68.5% 70.87% at 49.29% 41.78%, #FAFAFA 27.4%, #FAFAFA 54.33%, #F9F6FF 69.17%, #FFF 86.54%, #F0FAFF 98.08%)'
                }}
            >
                <Box maxWidth="900px" width="100%">
                    <VerticalStack gap="0">
                        <Box width="100%" style={{ marginBottom: '60px' }}>
                            <AgenticWelcomeHeader username={username} />

                            {/* AI SDK compatible search input */}
                            <AgenticSearchInput
                                input={searchInput}
                                handleInputChange={handleInputChange}
                                handleSubmit={handleSearchSubmit}
                                isLoading={false}
                            />

                            <AgenticSuggestions
                                onSuggestionClick={handleSuggestionClick}
                                hide={searchInput.trim().length > 0}
                            />
                        </Box>

                        <Box width="100%" style={{ display: 'flex', justifyContent: 'center' }}>
                            <Box style={{ width: '520px' }}>
                                <AgenticHistoryCards
                                    onHistoryClick={handleHistoryClick}
                                    onViewAllClick={handleViewAllClick}
                                />
                            </Box>
                        </Box>
                    </VerticalStack>
                </Box>
            </Box>

            {/* History Modal */}
            <AgenticHistoryModal
                isOpen={showHistoryModal}
                onClose={() => setShowHistoryModal(false)}
                onHistoryClick={handleHistoryClick}
            />
        </Page>
    );
}

export default AgenticMainPageV2;
