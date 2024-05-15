import { Box, HorizontalStack, Text } from '@shopify/polaris'
import React from 'react'

function LineComponent({title,value}){
    return(
        <HorizontalStack gap={5}>
            <Box width='180px'>
                <HorizontalStack align="end">
                    <Text variant="headingSm">{title}: </Text>
                </HorizontalStack>
            </Box>
            <Text variant="bodyLg">{value}</Text>
        </HorizontalStack>
    )
}

export default LineComponent