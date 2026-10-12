import { useState, useCallback, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Page, VerticalStack, HorizontalStack } from '@shopify/polaris';
import AgenticWelcomeHeader from './components/AgenticWelcomeHeader';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticSuggestions from './components/AgenticSuggestions';
import AgenticHistoryCards from './components/AgenticHistoryCards';
import AgenticHistoryModal from './components/AgenticHistoryModal';
import AgenticConversationPage from './AgenticConversationPage';
import { getConversationsList } from './services/agenticService';
import func from '@/util/func';
import { usePermissions, NO_PERMISSION_REASON } from '@/util/permissions';

function AgenticMainPage() {
    const { canCall } = usePermissions();
    const canChat = canCall('api/chatAndStore');
    // In a real app, this might come from a context or prop
    const username = (window.USER_FULL_NAME?.length > 0) ? window.USER_FULL_NAME : func.extractEmailDetails(window.USER_NAME)?.username || ""

    const [searchParams, setSearchParams] = useSearchParams();
    // Open conversation is kept in the URL so reloads, shared links and back/forward work
    const activeConversationId = searchParams.get('conversation');

    const [searchValue, setSearchValue] = useState('');
    // Query of a new conversation that has no id yet
    const [newConversationQuery, setNewConversationQuery] = useState(null);
    const [showHistoryModal, setShowHistoryModal] = useState(false);
    const [historyItems, setHistoryItems] = useState([]);

    const showConversation = Boolean(activeConversationId) || newConversationQuery !== null;

    const handleSearchSubmit = useCallback((query) => {
        setNewConversationQuery(query);
    }, []);

    const handleSuggestionClick = useCallback((suggestion) => {
        setSearchValue(suggestion);
        handleSearchSubmit(suggestion);
    }, [handleSearchSubmit]);

    const handleHistoryClick = useCallback((conversationId) => {
        setNewConversationQuery(null);
        setSearchParams({ conversation: conversationId });
    }, [setSearchParams]);

    // New conversation got its id; push it so browser back returns to the Ask Akto home page
    const handleConversationCreated = useCallback((conversationId) => {
        setSearchParams({ conversation: conversationId });
        setNewConversationQuery(null);
    }, [setSearchParams]);

    const handleBack = useCallback(() => {
        setNewConversationQuery(null);
        setSearchValue(''); // Clear search input when going back
        setSearchParams({});
    }, [setSearchParams]);

    const handleViewAllClick = useCallback(() => {
        setShowHistoryModal(true);
    }, []);

    // Only feeds the 3 recent-history cards; the full paged/searchable list lives in AgenticHistoryModal
    const loadRecentHistory = useCallback(async () => {
        try {
            const conversations = await getConversationsList(3, "");
            const sortedHistory = [...(conversations?.history || [])].sort((a, b) => b.lastUpdatedAt - a.lastUpdatedAt);
            setHistoryItems(sortedHistory.map(item => ({
                ...item,
                id: item._id?._id || item._id
            })));
        } catch (error) {
            console.error('Error loading conversation history:', error);
            setHistoryItems([]);
        }
    }, []);

    useEffect(() => {
        if (showConversation) return;
        loadRecentHistory();
    }, [showConversation, showHistoryModal, loadRecentHistory]);

    // If conversation is active, show the conversation page
    if (showConversation) {
        return (
            <AgenticConversationPage
                initialQuery={newConversationQuery || ''}
                existingConversationId={activeConversationId}
                onBack={handleBack}
                onLoadConversation={handleHistoryClick}
                onConversationCreated={handleConversationCreated}
                conversationType="ASK_AKTO"
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
                            {...(canChat ? {} : { disabled: true, placeholder: NO_PERMISSION_REASON })}
                        />
                        <AgenticSuggestions
                            onSuggestionClick={handleSuggestionClick}
                            hide={searchValue.trim().length > 0 || !canChat}
                        />
                    </VerticalStack>    
                    <AgenticHistoryCards
                        historyItems={historyItems.slice(0, 3)}
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
                onDelete={(conversationId) => {
                    setHistoryItems(prev => prev.filter(item => item.id !== conversationId));
                }}
            />
            </div>
        </Page>
    
    );
}

export default AgenticMainPage;
