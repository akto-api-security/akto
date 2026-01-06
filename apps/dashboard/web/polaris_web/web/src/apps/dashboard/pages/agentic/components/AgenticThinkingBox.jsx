import { Box, Text } from '@shopify/polaris';
import '../AgenticConversationPage.css';

function AgenticThinkingBox({ thinkingItems }) {
    return (
        <>
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
                            <span className="thinking-text">
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
                                <Box className="thinking-bullet" />
                                <Text variant="bodySm" as="p">
                                    <span className="thinking-item">
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
