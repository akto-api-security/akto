import { Box, Text, HorizontalStack, Avatar } from '@shopify/polaris';

/**
 * UserMessage - Displays a user message in the conversation
 * Uses only Polaris components, no HTML tags
 */
function UserMessage({ content }) {
    return (
        <Box
            background="bg-surface-secondary"
            padding="400"
            borderRadius="200"
        >
            <HorizontalStack gap="300" align="start" blockAlign="start">
                <Avatar customer size="medium" name="User" />
                <Box paddingBlockStart="100">
                    <Text variant="bodyMd" as="span">
                        {content}
                    </Text>
                </Box>
            </HorizontalStack>
        </Box>
    );
}

export default UserMessage;
