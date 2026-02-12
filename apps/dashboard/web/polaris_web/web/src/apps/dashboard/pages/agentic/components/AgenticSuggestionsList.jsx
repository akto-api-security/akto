import { Box, Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';

function AgenticSuggestionsList({ suggestions, onSuggestionClick }) {
    return (
        <VerticalStack gap="0">
            {suggestions.map((suggestion, suggestionIndex) => (
                <Button
                    key={suggestionIndex}
                    plain
                    onClick={() => onSuggestionClick(suggestion)}
                    fullWidth
                    textAlign="left"
                    style={{
                        padding: '12px 16px',
                        borderRadius: '8px'
                    }}
                >
                    <HorizontalStack gap="2" blockAlign="center">
                        <Box width="20px" height="20px" flexShrink={0}>
                            <img
                                src="/public/suggestion.svg"
                                alt="Suggestion"
                                style={{ width: '100%', height: '100%', display: 'block' }}
                            />
                        </Box>
                        <Text variant="bodySm" as="p" tone="subdued">
                            {suggestion}
                        </Text>
                    </HorizontalStack>
                </Button>
            ))}
        </VerticalStack>
    );
}

export default AgenticSuggestionsList;
