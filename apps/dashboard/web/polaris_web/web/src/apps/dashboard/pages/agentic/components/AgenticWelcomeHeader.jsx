
import React from 'react';
import { Text, VerticalStack, Box } from '@shopify/polaris';

function AgenticWelcomeHeader({ username }) {
    // Fallback if username is not provided
    const name = username || 'User';

    return (
        <Box paddingBlockStart="10" paddingBlockEnd="8">
            <VerticalStack gap="2" align="center">
                <Text variant="heading3xl" as="h1" alignment="center" fontWeight="bold">
                    Hi {name}, Welcome back!
                </Text>
            </VerticalStack>
        </Box>
    );
}

export default AgenticWelcomeHeader;
