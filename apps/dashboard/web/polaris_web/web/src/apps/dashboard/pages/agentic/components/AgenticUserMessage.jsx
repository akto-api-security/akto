import { Box, HorizontalStack, Text } from '@shopify/polaris';

function AgenticUserMessage({ content }) {
    return (
        <HorizontalStack align="end" blockAlign="center">
            <Box
                maxWidth="70%"
                padding="3"
                paddingInlineStart="4"
                paddingInlineEnd="4"
                borderWidth="1"
                borderColor="border"
                background="bg-surface"
                borderRadius='3'
                borderRadiusStartEnd='1'
            >
                <Text variant="bodyMd" as="p" color="subdued">
                    {content}
                </Text>
            </Box>
        </HorizontalStack>
    );
}

export default AgenticUserMessage;
