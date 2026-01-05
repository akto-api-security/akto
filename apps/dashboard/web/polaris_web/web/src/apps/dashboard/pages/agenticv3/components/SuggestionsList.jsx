import { Box, Text, Button, HorizontalStack, VerticalStack } from '@shopify/polaris';

/**
 * SuggestionsList - Displays clickable suggestion chips
 * Uses only Polaris components, no HTML tags
 */
function SuggestionsList({ suggestions, onSuggestionClick }) {
    if (!suggestions || suggestions.length === 0) {
        return null;
    }

    return (
        <Box paddingBlockStart="300">
            <VerticalStack gap="200">
                <Text variant="bodySm" as="span" tone="subdued">
                    Suggestions:
                </Text>
                <HorizontalStack gap="200" wrap>
                    {suggestions.map((suggestion, index) => (
                        <Button
                            key={index}
                            size="slim"
                            onClick={() => onSuggestionClick(suggestion)}
                        >
                            {suggestion}
                        </Button>
                    ))}
                </HorizontalStack>
            </VerticalStack>
        </Box>
    );
}

export default SuggestionsList;
