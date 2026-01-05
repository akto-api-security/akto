import { useState, useCallback } from 'react';
import { Page, Box, VerticalStack } from '@shopify/polaris';
import AgenticWelcomeHeader from './components/AgenticWelcomeHeader';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticSuggestions from './components/AgenticSuggestions';
import AgenticHistoryCards from './components/AgenticHistoryCards';
import AgenticConversationPage from './AgenticConversationPage';

function AgenticMainPage() {
    // In a real app, this might come from a context or prop
    const username = window.USER_FULL_NAME || window.USER_NAME || "User";

    const [searchValue, setSearchValue] = useState('');
    const [showConversation, setShowConversation] = useState(false);
    const [currentQuery, setCurrentQuery] = useState('');

    const handleSearchSubmit = useCallback((query) => {
        console.log('Search submitted:', query);
        setCurrentQuery(query);
        setShowConversation(true);
        // Add your search logic here
        // For example: navigate to results page, fetch data, etc.
    }, []);

    const handleSuggestionClick = useCallback((suggestion) => {
        setSearchValue(suggestion);
        handleSearchSubmit(suggestion);
    }, [handleSearchSubmit]);

    // If conversation is active, show the conversation page
    if (showConversation) {
        return <AgenticConversationPage initialQuery={currentQuery} />;
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
                            <AgenticSearchInput
                                value={searchValue}
                                onChange={setSearchValue}
                                onSubmit={handleSearchSubmit}
                            />
                            <AgenticSuggestions
                                onSuggestionClick={handleSuggestionClick}
                                hide={searchValue.trim().length > 0}
                            />
                        </Box>
                        <Box width="100%" style={{ display: 'flex', justifyContent: 'center' }}>
                            <Box style={{ width: '520px' }}>
                                <AgenticHistoryCards />
                            </Box>
                        </Box>
                    </VerticalStack>
                </Box>
            </Box>
        </Page>
    );
}

export default AgenticMainPage;
