import { useState, useCallback, useEffect } from 'react';
import { Page, VerticalStack, HorizontalStack } from '@shopify/polaris';
import AgenticWelcomeHeader from './components/AgenticWelcomeHeader';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticSuggestions from './components/AgenticSuggestions';
import AgenticHistoryCards from './components/AgenticHistoryCards';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import AgenticConversationPage from './AgenticConversationPage';
import { getConversationsList } from './services/agenticService';
import func from '@/util/func';

function AgenticMainPage() {
    // In a real app, this might come from a context or prop
    const username = (window.USER_FULL_NAME?.length > 0) ? window.USER_FULL_NAME : func.extractEmailDetails(window.USER_NAME)?.username || ""

    const [searchValue, setSearchValue] = useState('');
    const [showConversation, setShowConversation] = useState(false);
    const [currentQuery, setCurrentQuery] = useState('');
    const [loadConversationId, setLoadConversationId] = useState(null);
    const [showHistoryModal, setShowHistoryModal] = useState(false);
    const [historyItems, setHistoryItems] = useState([]);
    const [historySearchQuery, setHistorySearchQuery] = useState('');
    const [isLoadingHistory, setIsLoadingHistory] = useState(false);

    const handleSearchSubmit = useCallback((query) => {
        setCurrentQuery(query);
        setLoadConversationId(null); // Clear conversation ID for new search
        setShowConversation(true);
    }, []);

    const handleSuggestionClick = useCallback((suggestion) => {
        setSearchValue(suggestion);
        handleSearchSubmit(suggestion);
    }, [handleSearchSubmit]);

    const handleHistoryClick = useCallback((conversationId) => {
        setLoadConversationId(conversationId); // Load existing conversation
        setCurrentQuery(''); // Clear query for history load
        setShowConversation(true);
    }, []);

    const handleViewAllClick = useCallback(() => {
        setShowHistoryModal(true);
    }, []);

    const loadHistory = useCallback(async (limit = 50, searchQuery = "") => {
        setIsLoadingHistory(true);
        try {
            const conversations = await getConversationsList(limit, searchQuery);
            if(conversations.history && conversations.history.length > 0) {
                setHistoryItems(conversations.history.map(item => ({
                    ...item,
                    id: item._id._id
                })));
            }
        } catch (error) {
            console.error('Error loading conversation history:', error);
            setHistoryItems([]);
        }
        setIsLoadingHistory(false);
    }, []);

    useEffect(() => {
        if (showHistoryModal) {
            loadHistory(50, historySearchQuery);
        }else{
            loadHistory(3, "");
        }
    }, [showHistoryModal, historySearchQuery, loadHistory]);

    // If conversation is active, show the conversation page
    if (showConversation) {
        return (
            <AgenticConversationPage
                initialQuery={currentQuery}
                existingConversationId={loadConversationId}
                existingMessages={historyItems.find(item => item.id === loadConversationId)?.messages || []}
                onBack={() => {
                    setShowConversation(false);
                    setSearchValue(''); // Clear search input when going back
                }}
            />
        );
    }

    return (
        <Page id="agentic-main-page" fullWidth>
            <div style={{height: '100vh', display: 'flex', justifyContent: 'center'}}>
            <HorizontalStack align="center" blockAlign="center">
                <VerticalStack gap="16" align="center">
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
                        historyItems={historyItems}
                        onHistoryClick={handleHistoryClick}
                        onViewAllClick={handleViewAllClick}
                    />
                </VerticalStack>
            </HorizontalStack>
            {/* History Modal */}
            <AgenticHistoryModal
                isOpen={showHistoryModal}
                onClose={() => {
                    setShowHistoryModal(false);
                    setHistorySearchQuery(''); // Reset search query when closing
                }}
                onHistoryClick={handleHistoryClick}
                historyItems={historyItems}
                searchQuery={historySearchQuery}
                onSearchQueryChange={setHistorySearchQuery}
                isLoading={isLoadingHistory}
                onDelete={(conversationId) => {
                    setHistoryItems(historyItems.filter(item => item.id !== conversationId));
                }}
            />
            </div>
        </Page>
    
    );
}

export default AgenticMainPage;
