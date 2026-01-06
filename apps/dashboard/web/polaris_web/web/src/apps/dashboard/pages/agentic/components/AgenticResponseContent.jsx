import { Box, Text } from '@shopify/polaris';
import MarkdownViewer from '../../../components/shared/MarkdownViewer';

function AgenticResponseContent({ content, timeTaken }) {
    return (
        <Box
            style={{
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px'
            }}
        >
            {/* Time taken */}
            {timeTaken && (
                <Box
                    style={{
                        width: '100%',
                        paddingLeft: '20px'
                    }}
                >
                    <Text variant="bodySm" as="p" tone="subdued">
                        Thought for {timeTaken} seconds
                    </Text>
                </Box>
            )}

            {/* Markdown content */}
            <Box
                style={{
                    padding: '16px 20px',
                    borderRadius: '12px 12px 12px 0px',
                    background: 'transparent'
                }}
            >
                <MarkdownViewer markdown={content} />
            </Box>
        </Box>
    );
}

export default AgenticResponseContent;
