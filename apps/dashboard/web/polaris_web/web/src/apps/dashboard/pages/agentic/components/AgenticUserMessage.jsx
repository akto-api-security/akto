import { Box, HorizontalStack, Text } from '@shopify/polaris';

function AgenticUserMessage({ content }) {
    return (
        <HorizontalStack align="end" blockAlign="center">
            <Box
                style={{
                    maxWidth: '60%',
                    padding: '10px 16px',
                    borderRadius: '18px',
                    background: '#f4f4f4',
                }}
            >
                <Text variant="bodyMd" as="p">
                    {content}
                </Text>
            </Box>
        </HorizontalStack>
    );
}

export default AgenticUserMessage;
