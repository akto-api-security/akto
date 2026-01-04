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
        <Box style={{
            width: '100%',
            marginBottom: '32px',
            display: 'flex',
            justifyContent: 'center',
            opacity: hide ? 0 : 1,
            maxHeight: hide ? 0 : '500px',
            overflow: 'hidden',
            transition: 'opacity 0.3s ease, max-height 0.3s ease, margin-bottom 0.3s ease',
            marginBottom: hide ? 0 : '32px'
        }}>
            <Box style={{ width: '520px' }}>
                <VerticalStack gap="4">
                {suggestions.map((suggestion, index) => (
                    <Box
                        key={index}
                        paddingBlockEnd="100"
                        style={{ cursor: 'pointer' }}
                        onClick={() => handleClick(suggestion)}
                    >
                        <HorizontalStack gap="2" blockAlign="center">
                            <Box style={{ width: '20px', height: '20px' }}>
                                <img
                                    src="/public/suggestion.svg"
                                    alt="Suggestion"
                                    style={{ width: '100%', height: '100%', display: 'block' }}
                                />
                            </Box>
                            <Box>
                                <Text
                                    variant="bodySm"
                                    as="p"
                                    tone="subdued"
                                >
                                    <span style={{
                                        color: '#6D7175CC',
                                        fontFamily: 'SF Pro Text, -apple-system, BlinkMacSystemFont, San Francisco, Segoe UI, Roboto, Helvetica Neue, sans-serif',
                                        fontSize: '12px',
                                        fontStyle: 'normal',
                                        fontWeight: 400,
                                        lineHeight: '16px',
                                        fontFeatureSettings: "'liga' off, 'clig' off"
                                    }}>
                                        {suggestion}
                                    </span>
                                </Text>
                            </Box>
                        </HorizontalStack>
                    </Box>
                ))}
                </VerticalStack>
            </Box>
        </Box>
    );
}

export default AgenticSuggestions;
