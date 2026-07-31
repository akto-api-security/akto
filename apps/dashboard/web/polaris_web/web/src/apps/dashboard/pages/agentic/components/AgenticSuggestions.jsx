import { Text, Box, HorizontalStack, VerticalStack } from '@shopify/polaris';

function AgenticSuggestions({ onSuggestionClick, hide }) {
    const suggestions = [
        'Help me understand what kind of guardrails should I use?',
        'Summarize my agentic security posture in one view',
        'What attacks should I red-team against my agents first?'
    ];

    const handleClick = (suggestion) => {
        if (onSuggestionClick) {
            onSuggestionClick(suggestion);
        }
    };

    return (
        <HorizontalStack align="start">
        <Box opacity={hide ? 0 : 1} visibility={hide ? 'hidden' : 'visible'} transition="opacity 0.3s ease, visibility 0.3s ease" pointerEvents={hide ? 'none' : 'auto'} paddingInlineEnd={"3"} paddingInlineStart={"3"}>
            <Box>
                <VerticalStack gap="3">
                {suggestions.map((suggestion, index) => (
                    <Box
                        key={index}
                        style={{ cursor: 'pointer' }}
                        onClick={() => handleClick(suggestion)}
                    >
                        <HorizontalStack gap="2">
                            <Box style={{ width: '20px', height: '20px' }}>
                                <img
                                    src="/public/suggestion.svg"
                                    alt="Suggestion"
                                    style={{ width: '100%', height: '100%', display: 'block' }}
                                />
                            </Box>
                            <Box>
                                <Text
                                    color="subdued"
                                    variant="bodyMd"
                                    fontWeight="regular"
                                >
                                    {suggestion}
                                </Text>
                            </Box>
                        </HorizontalStack>
                        <style jsx>{`
                            .suggestion-item:hover :global(.Polaris-Text) {
                                color: var(--p-color-text, #202223) !important;
                            }
                        `}</style>
                    </Box>
                ))}
                </VerticalStack>
            </Box>
        </Box>
        </HorizontalStack>
    );
}

export default AgenticSuggestions;
