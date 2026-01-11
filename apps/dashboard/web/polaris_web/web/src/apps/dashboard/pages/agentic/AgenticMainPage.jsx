import { useState, useCallback } from 'react';
import { Page, VerticalStack, HorizontalStack, Box } from '@shopify/polaris';
import AgenticWelcomeHeader from './components/AgenticWelcomeHeader';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticSuggestions from './components/AgenticSuggestions';
import AgenticHistoryCards from './components/AgenticHistoryCards';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import AgenticConversationPage from './AgenticConversationPage';
import func from '@/util/func';

function AgenticMainPage() {
    // In a real app, this might come from a context or prop
    const username = (window.USER_FULL_NAME?.length > 0) ? window.USER_FULL_NAME : func.extractEmailDetails(window.USER_NAME)?.username || ""

    const [searchValue, setSearchValue] = useState('');
    const [showConversation, setShowConversation] = useState(false);
    const [currentQuery, setCurrentQuery] = useState('');
    const [loadConversationId, setLoadConversationId] = useState(null);
    const [showHistoryModal, setShowHistoryModal] = useState(false);

    const handleSearchSubmit = useCallback((query) => {
        setCurrentQuery(query);
        setLoadConversationId(null); // Clear conversation ID for new search
        setShowConversation(true);
    }, []);

    const handleSuggestionClick = useCallback((suggestion) => {
        setSearchValue(suggestion);
        handleSearchSubmit(suggestion);
    }, [handleSearchSubmit]);

    const handleHistoryClick = useCallback((conversationId, title) => {
        setLoadConversationId(conversationId); // Load existing conversation
        setCurrentQuery(''); // Clear query for history load
        setShowConversation(true);
    }, []);

    const handleViewAllClick = useCallback(() => {
        setShowHistoryModal(true);
    }, []);

    // If conversation is active, show the conversation page
    if (showConversation) {
        return (
            <AgenticConversationPage
                initialQuery={currentQuery}
                existingConversationId={loadConversationId}
                onBack={() => {
                    setShowConversation(false);
                    setSearchValue(''); // Clear search input when going back
                }}
            />
        );
    }

    return (
            <Page id="agentic-main-page" fullWidth>
                <HorizontalStack align="center">
                    <VerticalStack gap="15" align="center">
                        <VerticalStack gap={"8"}>
                            <AgenticWelcomeHeader username={username} />
                            <AgenticSearchInput
                                value={searchValue}
                                onChange={setSearchValue}
                                onSubmit={handleSearchSubmit}
                            />
                        <AgenticSuggestions
                                onSuggestionClick={handleSuggestionClick}
                                hide={searchValue.trim().length > 0}
                            />
                        </VerticalStack>    
                        <AgenticHistoryCards
                            onHistoryClick={handleHistoryClick}
                            onViewAllClick={handleViewAllClick}
                        />
                    </VerticalStack>
                </HorizontalStack>
            {/* History Modal */}
            <AgenticHistoryModal
                isOpen={showHistoryModal}
                onClose={() => setShowHistoryModal(false)}
                onHistoryClick={handleHistoryClick}
            />
        </Page>
    
    );
}

export default AgenticMainPage;
