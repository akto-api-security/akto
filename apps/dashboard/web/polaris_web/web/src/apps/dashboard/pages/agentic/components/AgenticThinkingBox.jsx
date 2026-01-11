import { Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';
import '../AgenticConversationPage.css';

function AgenticThinkingBox({ thinkingItems }) {
    return (
        <HorizontalStack align="start" blockAlign="start">
            <Box width="100%">
                <Box
                    padding="3"
                    paddingInlineStart="4"
                    paddingInlineEnd="4"
                    background="bg-transparent-active-experimental"
                    borderRadius='3'
                    borderRadiusEndStart='0'
                >
                    <VerticalStack gap="2">
                        {/* Top text with animation */}
                        <Text variant="bodyMd" as="p">
                            <span className="thinking-text">
                                Pondering, stand by...
                            </span>
                        </Text>

                        {/* List of thinking items */}
                        <VerticalStack gap="2">
                            {thinkingItems.map((item, itemIndex) => (
                                <HorizontalStack key={itemIndex} gap="2" blockAlign="start">
                                    <Box className="thinking-bullet" />
                                    <Text variant="bodySm" as="p">
                                        <span className="thinking-item">
                                            {item}
                                        </span>
                                    </Text>
                                </HorizontalStack>
                            ))}
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </Box>
        </HorizontalStack>
    );
}

export default AgenticThinkingBox;
