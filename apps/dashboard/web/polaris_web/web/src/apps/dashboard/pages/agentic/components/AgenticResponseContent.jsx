import { Box, Text, VerticalStack } from '@shopify/polaris';
import MarkdownViewer from '../../../components/shared/MarkdownViewer';

function AgenticResponseContent({ content, timeTaken }) {
    console.log('content', content);
    return (
        <VerticalStack gap="2" align="start">
            {/* Time taken */}
            {timeTaken && (
                <Box paddingInlineStart="5">
                    <Text variant="bodySm" as="p" tone="subdued">
                        Thought for {timeTaken} seconds
                    </Text>
                </Box>
            )}

            {/* Markdown content */}
            <Box
                // background="bg-transparent-active-experimental"
                borderRadius='3'
                borderRadiusEndStart='0'
            >
                <MarkdownViewer markdown={content} />
            </Box>
        </VerticalStack>
    );
}

export default AgenticResponseContent;
