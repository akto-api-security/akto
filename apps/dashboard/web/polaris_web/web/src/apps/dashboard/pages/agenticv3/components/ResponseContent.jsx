import { Box, Text, VerticalStack, HorizontalStack, Avatar, Badge, Icon } from '@shopify/polaris';
import { ClockMinor } from '@shopify/polaris-icons';

/**
 * ResponseContent - Displays AI assistant response
 * Uses only Polaris components, no HTML tags
 */
function ResponseContent({ content, timeTaken }) {
    // Parse content if it's a string
    const parseContent = () => {
        if (!content) return null;

        // If streaming and content is partial JSON string
        if (typeof content === 'string') {
            try {
                // Try to parse JSON
                return JSON.parse(content);
            } catch {
                // If not valid JSON, display as plain text
                return {
                    title: 'Response',
                    sections: [
                        {
                            header: 'Result',
                            items: [content]
                        }
                    ]
                };
            }
        }

        // Already an object
        return content;
    };

    const parsedContent = parseContent();

    if (!parsedContent) {
        return (
            <Box
                background="bg-surface"
                padding="400"
                borderRadius="200"
                borderWidth="025"
                borderColor="border"
            >
                <HorizontalStack gap="300" align="start">
                    <Avatar name="AI" />
                    <Text variant="bodyMd" as="span" tone="subdued">
                        Thinking...
                    </Text>
                </HorizontalStack>
            </Box>
        );
    }

    return (
        <Box
            background="bg-surface"
            padding="400"
            borderRadius="200"
            borderWidth="025"
            borderColor="border"
        >
            <VerticalStack gap="400">
                {/* Header with Avatar and Title */}
                <HorizontalStack gap="300" align="space-between" blockAlign="start">
                    <HorizontalStack gap="300" align="start">
                        <Avatar name="AI" />
                        <Box paddingBlockStart="100">
                            <Text variant="headingSm" as="h3">
                                {parsedContent.title || 'Response'}
                            </Text>
                        </Box>
                    </HorizontalStack>

                    {/* Time taken badge */}
                    {timeTaken && (
                        <HorizontalStack gap="200" blockAlign="center">
                            <Icon source={ClockMinor} tone="subdued" />
                            <Text variant="bodySm" as="span" tone="subdued">
                                {timeTaken}s
                            </Text>
                        </HorizontalStack>
                    )}
                </HorizontalStack>

                {/* Sections */}
                {parsedContent.sections && parsedContent.sections.length > 0 && (
                    <VerticalStack gap="400">
                        {parsedContent.sections.map((section, sectionIndex) => (
                            <Box key={sectionIndex}>
                                <VerticalStack gap="200">
                                    {/* Section Header */}
                                    {section.header && (
                                        <Text variant="headingXs" as="h4">
                                            {section.header}
                                        </Text>
                                    )}

                                    {/* Section Items */}
                                    {section.items && section.items.length > 0 && (
                                        <VerticalStack gap="150">
                                            {section.items.map((item, itemIndex) => (
                                                <HorizontalStack key={itemIndex} gap="200" blockAlign="start">
                                                    <Box paddingBlockStart="100">
                                                        <Text variant="bodySm" as="span" tone="subdued">
                                                            •
                                                        </Text>
                                                    </Box>
                                                    <Box>
                                                        <Text variant="bodyMd" as="span">
                                                            {item}
                                                        </Text>
                                                    </Box>
                                                </HorizontalStack>
                                            ))}
                                        </VerticalStack>
                                    )}
                                </VerticalStack>
                            </Box>
                        ))}
                    </VerticalStack>
                )}
            </VerticalStack>
        </Box>
    );
}

export default ResponseContent;
