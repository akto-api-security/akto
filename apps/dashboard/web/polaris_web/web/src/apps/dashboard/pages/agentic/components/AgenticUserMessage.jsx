import { Box, Text } from '@shopify/polaris';

function AgenticUserMessage({ content }) {
    return (
        <Box
            style={{
                width: '100%',
                display: 'flex',
                justifyContent: 'flex-end'
            }}
        >
            <Box
                style={{
                    maxWidth: '70%',
                    padding: '12px 16px',
                    borderRadius: '12px 12px 4px 12px',
                    border: '1px solid #C9CCCF',
                    background: 'rgba(255, 255, 255, 0.40)'
                }}
            >
                <Text variant="bodyMd" as="p">
                    <span style={{
                        color: '#202223',
                        fontFeatureSettings: "'liga' off, 'clig' off",
                        fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                        fontSize: '12px',
                        fontStyle: 'normal',
                        fontWeight: 400,
                        lineHeight: '16px'
                    }}>
                        {content}
                    </span>
                </Text>
            </Box>
        </Box>
    );
}

export default AgenticUserMessage;
