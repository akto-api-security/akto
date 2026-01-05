import { Box, Text } from '@shopify/polaris';

function AgenticThinkingBox({ thinkingItems }) {
    return (
        <>
            <style>{`
                @keyframes pulseFade {
                    0%, 100% {
                        opacity: 1;
                    }
                    50% {
                        opacity: 0.5;
                    }
                }
            `}</style>
            <Box
                style={{
                    width: '100%',
                    display: 'flex',
                    justifyContent: 'flex-start'
                }}
            >
                <Box style={{ width: '100%' }}>
                    <Box
                        style={{
                            padding: '12px 16px',
                            borderRadius: '12px 12px 12px 0px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '8px',
                            background: 'transparent'
                        }}
                    >
                        {/* Top text with animation */}
                        <Text variant="bodyMd" as="p">
                            <span style={{
                                color: 'rgba(190, 190, 191, 1)',
                                fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                fontSize: '12px',
                                fontStyle: 'normal',
                                fontWeight: 400,
                                animation: 'pulseFade 2s ease-in-out infinite'
                            }}>
                                Pondering, stand by...
                            </span>
                        </Text>

                    {/* List of thinking items */}
                    <Box style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {thinkingItems.map((item, itemIndex) => (
                            <Box
                                key={itemIndex}
                                style={{
                                    display: 'flex',
                                    gap: '7px',
                                    alignItems: 'flex-start'
                                }}
                            >
                                <Box style={{
                                    width: '4px',
                                    height: '4px',
                                    borderRadius: '50%',
                                    background: '#8C9196',
                                    marginTop: '5px',
                                    flexShrink: 0
                                }} />
                                <Text variant="bodySm" as="p">
                                    <span style={{
                                        color: '#8C9196',
                                        fontFeatureSettings: "'liga' off, 'clig' off",
                                        fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                        fontSize: '10px',
                                        fontStyle: 'normal',
                                        fontWeight: 400,
                                        lineHeight: '14px'
                                    }}>
                                        {item}
                                    </span>
                                </Text>
                            </Box>
                        ))}
                    </Box>
                </Box>
            </Box>
        </Box>
        </>
    );
}

export default AgenticThinkingBox;
