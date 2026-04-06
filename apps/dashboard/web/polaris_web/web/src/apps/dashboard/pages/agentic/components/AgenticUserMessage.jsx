import { Box, HorizontalStack, Text } from '@shopify/polaris';

function AgenticUserMessage({ content }) {
    return (
        <HorizontalStack align="end" blockAlign="center">
            <Box
                maxWidth="60%"
                padding="3"
                paddingInlineStart="4"
                paddingInlineEnd="4"
                background="bg-surface-secondary"
                borderRadius="full"
            >
                <Text variant="bodyMd" as="p">
                    {content}
                </Text>
            </Box>
        </HorizontalStack>
    );
}

export default AgenticUserMessage;
