import { Banner, Box, Text, BlockStack } from '@shopify/polaris'
import React from 'react'

function AlertsBanner({severity, content, type, onDismiss, index}) {
    return (
        <Banner tone={severity} onDismiss={() => onDismiss(index)}>
            <Box width="280px">
                <BlockStack>
                    <Text variant="headingMd">{type}</Text>
                    <Text variant="bodySm">{content}</Text>
                </BlockStack>
            </Box>
        </Banner>
    );
}

export default AlertsBanner