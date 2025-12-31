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

function AiAnalysisCard({ summary }) {
    const [isExpanded, setIsExpanded] = useState(false);

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

                {isExpanded && (
                    <Box paddingBlockStart="2">
                        <Text variant="bodyMd" as="p" color="subdued">
                            {summary || ANALYSIS_TEXT.LOADING}
                        </Text>
                    </Box>
                )}
            </VerticalStack>
        </Box>
    );
}

AiAnalysisCard.propTypes = {
    summary: PropTypes.string,
};

AiAnalysisCard.defaultProps = {
    summary: null,
};

export default AiAnalysisCard;
