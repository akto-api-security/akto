import { Banner, Box, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function AlertsBanner({severity, content, type, onDismiss, index}) {
    return (
        <Banner status={severity} onDismiss={() => onDismiss(index)}>
            <Box width="280px">
                <VerticalStack>
                    <Text variant="headingMd">{type}</Text>
                    <Text variant="bodySm">{content}</Text>
                </VerticalStack>
            </Box>
        </Banner>
    )
}

export default AlertsBanner