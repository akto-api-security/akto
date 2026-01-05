import { Box, Text } from '@shopify/polaris';

function AgenticSuggestionsList({ suggestions, onSuggestionClick }) {
    return (
        <Box
            style={{
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                gap: '4px'
            }}
        >
            {suggestions.map((suggestion, suggestionIndex) => (
                <Box
                    key={suggestionIndex}
                    onClick={() => onSuggestionClick(suggestion)}
                    style={{
                        padding: '12px 16px',
                        borderRadius: '8px',
                        cursor: 'pointer',
                        transition: 'opacity 0.2s ease'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.opacity = '0.8'}
                    onMouseLeave={(e) => e.currentTarget.style.opacity = '1'}
                >
                    <Box style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Box style={{ width: '20px', height: '20px', flexShrink: 0 }}>
                            <img
                                src="/public/suggestion.svg"
                                alt="Suggestion"
                                style={{ width: '100%', height: '100%', display: 'block' }}
                            />
                        </Box>
                        <Text variant="bodySm" as="p">
                            <span style={{ color: 'rgba(109, 113, 117, 0.8)' }}>
                                {suggestion}
                            </span>
                        </Text>
                    </Box>
                </Box>
            ))}
        </Box>
    );
}

export default AgenticSuggestionsList;
