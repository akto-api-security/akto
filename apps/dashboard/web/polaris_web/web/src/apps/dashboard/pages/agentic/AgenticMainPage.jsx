
import React from 'react';
import { Page, Layout, Box } from '@shopify/polaris';
import AgenticWelcomeHeader from './components/AgenticWelcomeHeader';
import AgenticSearchInput from './components/AgenticSearchInput';
import AgenticHistoryCards from './components/AgenticHistoryCards';

function AgenticMainPage() {
    // In a real app, this might come from a context or prop
    const username = window.USER_FULL_NAME || window.USER_NAME || "User";

    return (
        <Page fullWidth>
            <Layout>
                <Layout.Section>
                    <Box paddingBlockStart="10">
                        <Text variant="headingLg" as="h1">Agentic Main Page</Text>
                        {/* <AgenticWelcomeHeader username={username} />
                        <AgenticSearchInput />
                        <AgenticHistoryCards /> */}
                    </Box>
                </Layout.Section>
            </Layout>
        </Page>
    );
}

export default AgenticMainPage;
