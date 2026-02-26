import { useState } from 'react';
import PropTypes from 'prop-types';
import { Box, Text, VerticalStack, HorizontalStack, Button } from '@shopify/polaris';
import { ChevronDownMinor, ChevronUpMinor } from '@shopify/polaris-icons';
import { CHAT_ASSETS, ANALYSIS_TEXT } from './chatConstants';

const styles = {
    iconContainer: {
        display: 'inline-flex',
        height: '20px',
        width: '20px',
    },
    iconImage: {
        height: '100%',
        width: '100%',
        objectFit: 'contain',
    },
};

function AiAnalysisCard({ summary, isLoading, children, footer, scrollRef }) {
    const [isExpanded, setIsExpanded] = useState(true);

    const toggleExpanded = () => setIsExpanded(prev => !prev);

    return (
        <Box
            background="bg-surface"
            borderRadius="3"
            borderWidth="1"
            borderColor="border"
            padding="4"
            shadow="card"
        >
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Box as="span" style={styles.iconContainer}>
                            <img
                                src={CHAT_ASSETS.MAGIC_ICON}
                                alt=""
                                aria-hidden="true"
                                style={styles.iconImage}
                            />
                        </Box>
                        <Text variant="headingSm" as="h3">
                            {ANALYSIS_TEXT.HEADER}
                        </Text>
                    </HorizontalStack>

                    <Button
                        plain
                        icon={isExpanded ? ChevronUpMinor : ChevronDownMinor}
                        onClick={toggleExpanded}
                        accessibilityLabel={isExpanded ? "Collapse AI analysis" : "Expand AI analysis"}
                    />
                </HorizontalStack>

                <Box style={{ display: isExpanded ? 'flex' : 'none', flexDirection: 'column', maxHeight: '40vh' }}>
                    <div ref={scrollRef} style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
                        <VerticalStack gap="4">
                            <Box paddingBlockStart="2">
                                {(isLoading || !summary) ? (
                                    <VerticalStack gap="2">
                                        <Box style={{ height: '12px', width: '90%', borderRadius: '4px', background: '#E4E5E7', animation: 'pulseFade 1.5s ease-in-out infinite' }} />
                                        <Box style={{ height: '12px', width: '70%', borderRadius: '4px', background: '#E4E5E7', animation: 'pulseFade 1.5s ease-in-out infinite' }} />
                                    </VerticalStack>
                                ) : (
                                    <Text variant="bodyMd" as="p" color="subdued">
                                        {summary}
                                    </Text>
                                )}
                            </Box>
                            {children}
                        </VerticalStack>
                    </div>
                    {footer && <Box paddingBlockStart="4">{footer}</Box>}
                </Box>
            </VerticalStack>
        </Box>
    );
}

AiAnalysisCard.propTypes = {
    summary: PropTypes.string,
    isLoading: PropTypes.bool,
};

AiAnalysisCard.defaultProps = {
    summary: null,
    isLoading: false,
};

export default AiAnalysisCard;
