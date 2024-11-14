import { Box, InlineStack, Text } from '@shopify/polaris'
import React from 'react'

function LineComponent({title,value}){
    return (
        <InlineStack gap={5}>
            <Box width='180px'>
                <InlineStack align="end">
                    <Text variant="headingSm">{title}: </Text>
                </InlineStack>
            </Box>
            <Text variant="bodyLg">{value}</Text>
        </InlineStack>
    );
}

export default LineComponent