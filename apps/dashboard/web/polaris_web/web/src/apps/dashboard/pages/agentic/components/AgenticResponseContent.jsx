import { Box, Text } from '@shopify/polaris';

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

            {/* Response content */}
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
                            padding: '16px 20px',
                            borderRadius: '12px 12px 12px 0px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '16px',
                            background: 'transparent'
                        }}
                    >
                        {/* Response title */}
                        {content.title && (
                            <Text variant="headingMd" as="h3">
                                {content.title}
                            </Text>
                        )}

                        {/* Response sections */}
                        {content.sections?.map((section, sectionIndex) => (
                            <Box key={sectionIndex} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <Text variant="bodyMd" as="p" fontWeight="semibold">
                                    {section.header}
                                </Text>
                                <Box style={{ display: 'flex', flexDirection: 'column', gap: '4px', paddingLeft: '4px' }}>
                                    {section.items.map((item, itemIndex) => (
                                        <Box
                                            key={itemIndex}
                                            style={{
                                                display: 'flex',
                                                gap: '8px',
                                                alignItems: 'flex-start'
                                            }}
                                        >
                                            <Box style={{
                                                width: '4px',
                                                height: '4px',
                                                borderRadius: '50%',
                                                background: '#202223',
                                                marginTop: '8px',
                                                flexShrink: 0
                                            }} />
                                            <Text variant="bodySm" as="p">
                                                <span style={{
                                                    color: '#202223',
                                                    fontFeatureSettings: "'liga' off, 'clig' off",
                                                    fontFamily: '"SF Pro Text", -apple-system, BlinkMacSystemFont, "San Francisco", "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
                                                    fontSize: '12px',
                                                    fontStyle: 'normal',
                                                    fontWeight: 400,
                                                    lineHeight: '16px'
                                                }}>
                                                    {item}
                                                </span>
                                            </Text>
                                        </Box>
                                    ))}
                                </Box>
                            </Box>
                        ))}
                    </Box>
                </Box>
            </Box>
        </Box>
    );
}

export default AgenticResponseContent;
